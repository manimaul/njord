//! The `testdata/enc_cron/` golden tables: catalog parsing (`parsed.json`), cell selection
//! (`selection.json`), bundle layout (`bundle_layout.json`), configuration loading
//! (`config.json`) and the orphan delete guards (`delete_orphans.json`).

use std::cell::{Cell, RefCell};
use std::collections::{HashMap, HashSet};
use std::io::{BufReader, Read};
use std::path::{Path, PathBuf};

use indexmap::IndexMap;
use njord_enc_cron::bundle::BundleBuilder;
use njord_enc_cron::catalog::{self, EncCatalogEntry, EncProdCatParser};
use njord_enc_cron::config::{EncCronConfig, Sources};
use njord_enc_cron::run::delete_orphans;
use njord_enc_cron::select::{batches, deletable_orphans, select_orphans, select_stale};
use njord_enc_cron::transport::{EncTransport, Error};
use njord_testdata::{Failures, cases, first_difference, port_expected, testdata};
use serde::Deserialize;
use serde_json::{Value, json};

fn block_on<F: Future>(f: F) -> F::Output {
    tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
        .unwrap()
        .block_on(f)
}

fn compare(label: &str, expected: &Value, actual: &Value, failures: &mut Failures) {
    if let Some(d) = first_difference(expected, actual, "") {
        failures.push(format!("{label}: {d}\n  actual: {actual}"));
    }
}

/// An entry with its derived keys, as `parsed.json` records it.
fn entry_out(e: &EncCatalogEntry) -> Value {
    let mut v = serde_json::to_value(e).unwrap();
    let map = v.as_object_mut().unwrap();
    map.insert("chartName".into(), json!(e.chart_name()));
    map.insert("updateNumber".into(), json!(e.update_number()));
    map.insert("revisionKey".into(), json!(e.revision_key()));
    v
}

fn parsed_out(p: &EncProdCatParser) -> Value {
    json!({
        "recordsSeen": p.records_seen,
        "recordsSkipped": p.records_skipped,
        "entries": p.entries.iter().map(entry_out).collect::<Vec<_>>(),
    })
}

/// A reader that hands out at most `n` bytes per read, so text nodes split across buffers are
/// exercised.
struct Trickle<R>(R, usize);

impl<R: Read> Read for Trickle<R> {
    fn read(&mut self, buf: &mut [u8]) -> std::io::Result<usize> {
        let n = buf.len().min(self.1);
        self.0.read(&mut buf[..n])
    }
}

#[test]
fn catalog_parses_to_the_committed_records() {
    let mut failures = Failures::default();
    let all = cases("enc_cron/parsed.json");
    for case in &all {
        let path = testdata().join(case["input"].as_str().unwrap());
        let expected = port_expected(case);

        let file = catalog::parse_catalog_file(&path).unwrap();
        compare(
            "parse_catalog_file",
            expected,
            &parsed_out(&file),
            &mut failures,
        );

        let bytes = std::fs::read(&path).unwrap();
        let chunked =
            catalog::parse_reader(BufReader::with_capacity(64, Trickle(&bytes[..], 4096))).unwrap();
        compare("chunked", expected, &parsed_out(&chunked), &mut failures);

        let streamed = block_on(catalog::parse_async(tokio::io::BufReader::with_capacity(
            4096,
            &bytes[..],
        )))
        .unwrap();
        compare("async", expected, &parsed_out(&streamed), &mut failures);

        assert!(
            file.entries.iter().all(|e| e.cell != "0"),
            "the dataQualityInfo decoy title must never become a cell"
        );
    }
    failures.assert_none(all.len());
}

#[test]
fn a_truncated_or_foreign_document_fails() {
    let bytes = std::fs::read(testdata().join("enc_cron/catalog_sample.xml")).unwrap();
    let truncated = &bytes[..bytes.len() / 2];
    let err = catalog::parse_reader(truncated).expect_err("a truncated catalog must fail");
    assert!(
        err.contains("truncated") || err.contains("catalog XML"),
        "{err}"
    );

    assert!(
        catalog::parse_reader(&b""[..]).is_err(),
        "an empty body must fail"
    );
    let foreign = br#"<?xml version="1.0"?><html><body/></html>"#;
    assert!(
        catalog::parse_reader(&foreign[..])
            .unwrap_err()
            .contains("unexpected root element")
    );
    let rebound = br#"<DS_Series xmlns="http://www.isotc211.org/2005/gmd" xmlns:gco="http://example.com/gco"/>"#;
    assert!(
        catalog::parse_reader(&rebound[..])
            .unwrap_err()
            .contains("namespace binding changed")
    );
}

/// `EntryIn`: a catalog entry as test input; `url` is derived from the cell like NOAA's. The
/// fixtures were written with defaults encoded and nulls omitted, so an absent key is `null`.
#[derive(Deserialize)]
struct EntryIn {
    cell: String,
    edition: String,
    scale: Option<i32>,
    #[serde(rename = "sizeMb")]
    size_mb: Option<f64>,
    revision: Option<String>,
    publication: Option<String>,
}

impl EntryIn {
    fn entry(&self) -> EncCatalogEntry {
        EncCatalogEntry {
            cell: self.cell.clone(),
            edition: self.edition.clone(),
            url: format!("https://example.com/{}.zip", self.cell),
            scale: self.scale,
            size_mb: self.size_mb,
            revision_date: self.revision.clone(),
            publication_date: self.publication.clone(),
        }
    }
}

/// A catalog entry for `cell` with the fixtures' default fields.
fn entry(cell: &str) -> EncCatalogEntry {
    let defaults = json!({ "cell": cell, "edition": "1.0", "scale": 15000, "sizeMb": 0.1, "revision": "20240101", "publication": "20240102" });
    serde_json::from_value::<EntryIn>(defaults).unwrap().entry()
}

/// The configuration the fixtures were recorded with: the shipped defaults.
fn config(chart_temp_data: &Path) -> EncCronConfig {
    serde_json::from_value(json!({
        "catalogUrl": "https://charts.noaa.gov/ENCs/ENCProdCat_19115.xml",
        "chartEditionsUrl": "http://njord-chart-svc/v1/chart_editions",
        "chartTempData": chart_temp_data,
        "maxCellsPerRun": 500,
        "bundleSizeCells": 50,
        "maxBundleUncompressedBytes": 268435456,
        "scaleFilter": [],
        "requestTimeoutSeconds": 600,
        "maxRetries": 3,
    }))
    .unwrap()
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct SelectionCase {
    op: String,
    #[serde(default)]
    catalog: Vec<EntryIn>,
    #[serde(default)]
    have: IndexMap<String, String>,
    #[serde(default)]
    orphans: Vec<String>,
    #[serde(default)]
    scale_filter: Vec<i32>,
    bundle_size_cells: usize,
    max_bundle_uncompressed_bytes: i64,
}

fn cells(entries: &[&EncCatalogEntry]) -> Value {
    json!(entries.iter().map(|e| e.cell.as_str()).collect::<Vec<_>>())
}

#[test]
fn selection_logic_matches_the_committed_table() {
    let mut failures = Failures::default();
    let all = cases("enc_cron/selection.json");
    for (i, case) in all.iter().enumerate() {
        let c: SelectionCase = serde_json::from_value(case["input"].clone()).unwrap();
        let catalog: Vec<EncCatalogEntry> = c.catalog.iter().map(EntryIn::entry).collect();
        let mut config = config(Path::new("/unused"));
        config.scale_filter = c.scale_filter;
        config.bundle_size_cells = c.bundle_size_cells;
        config.max_bundle_uncompressed_bytes = c.max_bundle_uncompressed_bytes;
        let actual = match c.op.as_str() {
            "selectStale" => cells(&select_stale(&catalog, &c.have, &config)),
            "selectOrphans" => json!(select_orphans(&catalog, &c.have)),
            "deletableOrphans" => json!(deletable_orphans(&c.orphans, &catalog)),
            "batches" => {
                let all: Vec<&EncCatalogEntry> = catalog.iter().collect();
                json!(
                    batches(&all, &config)
                        .iter()
                        .map(|b| cells(b))
                        .collect::<Vec<_>>()
                )
            }
            op => panic!("unknown op {op}"),
        };
        compare(
            &format!("case {i} {}", c.op),
            port_expected(case),
            &actual,
            &mut failures,
        );
    }
    failures.assert_none(all.len());
}

/// The network as `BundleBuilder` and `delete_orphans` see it: cell zips served from disk,
/// failures on demand, and every delete recorded.
#[derive(Default)]
struct FakeTransport {
    sources: HashMap<String, PathBuf>,
    failing: HashSet<String>,
    gone: HashSet<String>,
    downloads: RefCell<Vec<String>>,
    signature_fetches: Cell<usize>,
    deletes: RefCell<Vec<String>>,
}

impl EncTransport for FakeTransport {
    async fn download(&self, url: &str, dest: &Path) -> Result<u64, Error> {
        self.downloads.borrow_mut().push(url.to_owned());
        if self.failing.contains(url) {
            return Err(format!("download {url} failed after 3 attempts").into());
        }
        let src = self.sources.get(url).ok_or_else(|| format!("404 {url}"))?;
        Ok(std::fs::copy(src, dest)?)
    }

    async fn fetch_admin_signature(&self, _: &str, _: &str, _: &str) -> Result<String, Error> {
        self.signature_fetches.set(self.signature_fetches.get() + 1);
        Ok(format!("signature-{}", self.signature_fetches.get()))
    }

    async fn delete_chart(&self, _: &str, name: &str, _: &str) -> Result<bool, Error> {
        self.deletes.borrow_mut().push(name.to_owned());
        Ok(!self.gone.contains(name))
    }
}

#[derive(Deserialize)]
struct BundleCase {
    name: String,
    cells: Vec<String>,
    #[serde(default)]
    failing: Vec<String>,
    #[serde(default)]
    slip: Vec<String>,
}

/// A NOAA-style cell zip: `ENC_ROOT/<CELL>/<files>` plus the per-cell CATALOG.031 and the
/// boilerplate text files, and optionally a zip-slip entry.
fn cell_zip(cell: &str, dir: &Path, slip: bool) -> PathBuf {
    let source = testdata().join(match cell {
        "US5WA22M" => "fixtures/US5WA22M",
        "US4ZZ01M" | "US5ZZ02M" => "fixtures/synthetic",
        _ => panic!("unknown cell {cell}"),
    });
    let mut names: Vec<String> = std::fs::read_dir(&source)
        .unwrap()
        .map(|e| e.unwrap().file_name().to_string_lossy().into_owned())
        .filter(|n| n.starts_with(cell) || (cell == "US5WA22M" && n.ends_with(".TXT")))
        .collect();
    names.sort();
    let zip_path = dir.join(format!("{cell}.zip"));
    let mut w = zip::ZipWriter::new(std::fs::File::create(&zip_path).unwrap());
    let options = zip::write::SimpleFileOptions::default();
    for name in names {
        w.start_file(format!("ENC_ROOT/{cell}/{name}"), options)
            .unwrap();
        std::io::Write::write_all(&mut w, &std::fs::read(source.join(&name)).unwrap()).unwrap();
    }
    for (name, body) in [
        (
            "ENC_ROOT/CATALOG.031".to_owned(),
            format!("catalog for {cell}"),
        ),
        ("README.TXT".to_owned(), "boilerplate".to_owned()),
        ("USERAGREEMENT.TXT".to_owned(), "boilerplate".to_owned()),
    ]
    .into_iter()
    .chain(slip.then(|| {
        (
            format!("ENC_ROOT/{cell}/../../../evil.txt"),
            format!("escaped {cell}"),
        )
    })) {
        w.start_file(name, options).unwrap();
        std::io::Write::write_all(&mut w, body.as_bytes()).unwrap();
    }
    w.finish().unwrap();
    zip_path
}

fn files_under(root: &Path) -> Vec<String> {
    fn walk(dir: &Path, root: &Path, out: &mut Vec<String>) {
        for e in std::fs::read_dir(dir).unwrap() {
            let p = e.unwrap().path();
            if p.is_dir() {
                walk(&p, root, out);
            } else {
                out.push(p.strip_prefix(root).unwrap().to_string_lossy().into_owned());
            }
        }
    }
    let mut out = Vec::new();
    walk(root, root, &mut out);
    out.sort();
    out
}

fn bundle_case(c: &BundleCase, root: &Path) -> Value {
    let case_root = root.join(&c.name);
    let _ = std::fs::remove_dir_all(&case_root);
    let zips = case_root.join("zips");
    let temp = case_root.join("chart_temp");
    std::fs::create_dir_all(&zips).unwrap();
    std::fs::create_dir_all(&temp).unwrap();
    let config = config(&temp);
    // publish_bundles() creates both before the first bundle; the builder relies on that.
    std::fs::create_dir_all(config.work_dir()).unwrap();
    std::fs::create_dir_all(config.save_dir()).unwrap();
    let transport = FakeTransport {
        sources: c
            .cells
            .iter()
            .map(|cell| {
                (
                    format!("https://example.com/{cell}.zip"),
                    cell_zip(cell, &zips, c.slip.contains(cell)),
                )
            })
            .collect(),
        failing: c
            .failing
            .iter()
            .map(|cell| format!("https://example.com/{cell}.zip"))
            .collect(),
        ..Default::default()
    };
    let entries: Vec<EncCatalogEntry> = c.cells.iter().map(|cell| entry(cell)).collect();
    let refs: Vec<&EncCatalogEntry> = entries.iter().collect();

    let published = block_on(
        BundleBuilder::new(&config, &transport)
            .build_and_publish(&refs, &format!("{}.zip", c.name)),
    )
    .unwrap();

    let escaped: Vec<String> = files_under(&temp)
        .into_iter()
        .filter(|f| !f.starts_with("save/"))
        .collect();
    let entries: Vec<String> = match &published {
        Some(p) => {
            let mut zip = zip::ZipArchive::new(std::fs::File::open(p).unwrap()).unwrap();
            let mut names: Vec<String> = (0..zip.len())
                .map(|i| zip.by_index(i).unwrap().name().unwrap().into_owned())
                .collect();
            names.sort();
            names
        }
        None => Vec::new(),
    };
    let mut out = json!({ "published": published.is_some() });
    if let Some(p) = &published {
        out["bundle"] = json!(p.strip_prefix(&temp).unwrap().to_string_lossy());
    }
    out["entries"] = json!(entries);
    out["stagingRemoved"] = json!(!config.work_dir().join("staging").exists());
    out["downloadRemoved"] = json!(!config.work_dir().join("download").exists());
    out["escaped"] = json!(escaped);
    out["downloads"] = json!(*transport.downloads.borrow());
    out
}

#[test]
fn bundle_layout_matches_the_committed_fixture() {
    let root = Path::new(env!("CARGO_TARGET_TMPDIR")).join("bundle_layout");
    let mut failures = Failures::default();
    let all = cases("enc_cron/bundle_layout.json");
    for case in &all {
        let c: BundleCase = serde_json::from_value(case["input"].clone()).unwrap();
        compare(
            &c.name,
            port_expected(case),
            &bundle_case(&c, &root),
            &mut failures,
        );
    }
    // Nothing escaped the case directories (a zip-slip entry would land in the work dir).
    assert!(!root.join("evil.txt").exists());
    let _ = std::fs::remove_dir_all(&root);
    failures.assert_none(all.len());
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct ConfigCase {
    name: String,
    file: Option<String>,
    opts: Option<String>,
    env_user: Option<String>,
    env_pass: Option<String>,
    resources_dir: bool,
}

#[test]
fn config_loading_matches_the_committed_table() {
    let root = Path::new(env!("CARGO_TARGET_TMPDIR")).join("enc_cron_config");
    let mut failures = Failures::default();
    let all = cases("enc_cron/config.json");
    for case in &all {
        let c: ConfigCase = serde_json::from_value(case["input"].clone()).unwrap();
        let dir = root.join(&c.name);
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).unwrap();
        if let Some(file) = &c.file {
            std::fs::create_dir_all(dir.join("config")).unwrap();
            std::fs::write(dir.join("config/enc_cron.json"), file).unwrap();
        }
        let loaded = EncCronConfig::load(&Sources {
            resources_dir: c.resources_dir.then_some(dir.as_path()),
            overrides: c.opts.as_deref(),
            admin_user: c.env_user.as_deref(),
            admin_pass: c.env_pass.as_deref(),
        });
        let expected = port_expected(case);
        match (loaded, expected.get("throws")) {
            (Err(_), Some(_)) => {}
            (Ok(config), None) => {
                let actual = json!({
                    "config": serde_json::to_value(&config).unwrap(),
                    "saveDir": config.save_dir(),
                    "workDir": config.work_dir(),
                    "njordBaseUrl": config.njord_base_url(),
                });
                compare(&c.name, expected, &actual, &mut failures);
            }
            (Ok(config), Some(_)) => {
                failures.push(format!("{}: expected an error, loaded {config:?}", c.name))
            }
            (Err(e), None) => failures.push(format!("{}: {e}", c.name)),
        }
    }
    let _ = std::fs::remove_dir_all(&root);
    failures.assert_none(all.len());
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct DeleteCase {
    name: String,
    delete_orphans: bool,
    chart_editions_url: String,
    admin_user: String,
    admin_pass: String,
    max_orphan_deletes: usize,
    dry_run: bool,
    orphans: Vec<String>,
    catalog_cells: Vec<String>,
    gone: Vec<String>,
}

#[test]
fn delete_orphans_guard_ordering_matches_the_decision_table() {
    let mut failures = Failures::default();
    let all = cases("enc_cron/delete_orphans.json");
    for case in &all {
        let c: DeleteCase = serde_json::from_value(case["input"].clone()).unwrap();
        let mut config = config(Path::new("/unused"));
        config.delete_orphans = c.delete_orphans;
        config.chart_editions_url = c.chart_editions_url;
        config.admin_user = c.admin_user;
        config.admin_pass = c.admin_pass;
        config.max_orphan_deletes = c.max_orphan_deletes;
        let transport = FakeTransport {
            gone: c.gone.into_iter().collect(),
            ..Default::default()
        };
        let catalog: Vec<EncCatalogEntry> =
            c.catalog_cells.iter().map(|cell| entry(cell)).collect();
        block_on(delete_orphans(
            &c.orphans, &catalog, &config, &transport, c.dry_run,
        ))
        .unwrap();
        let actual = json!({
            "signatureFetches": transport.signature_fetches.get(),
            "deleteCalls": *transport.deletes.borrow(),
        });
        compare(&c.name, port_expected(case), &actual, &mut failures);
    }
    failures.assert_none(all.len());
}
