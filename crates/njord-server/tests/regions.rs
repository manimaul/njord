//! P-RE-4 `testdata/regions/timestamps.json` (archive stems, names, ordering and pruning) and
//! P-RE-5 `testdata/regions/manifest.json` (`buildManifest` over a region directory), with the
//! archive stems read in `America/Los_Angeles` as the fixtures were generated.

use njord_server::config::RegionExportConfig;
use njord_server::regions::Regions;
use njord_testdata::{Failures, cases, first_difference, strip_nulls};

const COVERAGE: &str = "POLYGON ((-123 47, -122 47, -122 48, -123 48, -123 47))";
const NEWEST: &str = "MANIFEST_TEST_2026-02-03T04-05-06.mbtiles";
const OLDER: &str = "MANIFEST_TEST_2026-01-02T03-04-05.mbtiles";
/// The archive contents `RegionExportTest` writes.
const ARCHIVE: &[u8] = b"newest archive bytes";

/// Stems are server-local time without an offset, so they resolve in the configured zone,
/// DST transitions included; `archivesForRegion` matches on a plain name prefix (so `WA` sees
/// `WA_SOUND_*` too, whose stems fail to parse and sort last) and pruning keeps the two newest.
#[test]
fn timestamps() {
    let zone = jiff::tz::TimeZone::get("America/Los_Angeles").unwrap();
    let dir = std::path::Path::new(env!("CARGO_TARGET_TMPDIR")).join("njord-server-tests/stamps");
    let _ = std::fs::remove_dir_all(&dir);
    std::fs::create_dir_all(&dir).unwrap();
    for name in [
        "WA_2026-01-01T00-00-00.mbtiles",
        "WA_2026-02-01T00-00-00.mbtiles",
        "WA_2026-03-01T00-00-00.mbtiles",
        "WA_SOUND_2026-04-01T00-00-00.mbtiles",
        "WA_not-a-stamp.mbtiles",
        "WAX_2026-05-01T00-00-00.mbtiles",
        "WA_2026-02-01T00-00-00.mbtiles.sha256",
    ] {
        std::fs::write(dir.join(name), "x").unwrap();
    }
    let regions = Regions::new(&dir, zone);
    let names = |paths: Vec<std::path::PathBuf>| {
        paths
            .iter()
            .map(|p| p.file_name().unwrap().to_string_lossy().into_owned())
            .collect::<Vec<_>>()
            .join(",")
    };
    let mut failures = Failures::default();
    let cases = cases("regions/timestamps.json");
    for case in &cases {
        let input = &case["input"];
        let a = input["a"].as_str().unwrap();
        let actual = match input["op"].as_str().unwrap() {
            "parseTimestampStem" => regions
                .parse_stem(a)
                .map(|t| serde_json::Value::String(njord_model::instant::format(t)))
                .unwrap_or(serde_json::Value::Null),
            "parseArchiveTimestamp" => regions
                .archive_timestamp(a, input["b"].as_str().unwrap())
                .map(|t| serde_json::Value::String(njord_model::instant::format(t)))
                .unwrap_or(serde_json::Value::Null),
            "archivesForRegion" => names(regions.archives_for_region(a)).into(),
            "pruneOldArchives" => {
                regions.prune_old_archives(a);
                let mut left: Vec<String> = std::fs::read_dir(&dir)
                    .unwrap()
                    .map(|e| e.unwrap().file_name().to_string_lossy().into_owned())
                    .collect();
                left.sort();
                left.join(",").into()
            }
            other => panic!("{other}"),
        };
        failures.check(
            input.to_string(),
            first_difference(&case["expected"], &actual, "").map_or(Ok(()), Err),
        );
    }
    failures.assert_none(cases.len());
    // behavioural: the stem written now parses back to now
    let stamp = regions.current_timestamp();
    assert_eq!(stamp.len(), 19, "{stamp}");
    let parsed = regions.parse_stem(&stamp).unwrap();
    assert!(
        jiff::Timestamp::now().duration_since(parsed).abs() < jiff::SignedDuration::from_secs(2),
        "{stamp} -> {parsed}"
    );
}

#[test]
fn manifest() {
    let region = RegionExportConfig {
        name: "MANIFEST_TEST".into(),
        description: "manifest fixture".into(),
        coverage: COVERAGE.into(),
    };
    let zone = jiff::tz::TimeZone::get("America/Los_Angeles").unwrap();
    let mut failures = Failures::default();
    let cases = cases("regions/manifest.json");
    for case in &cases {
        let scenario = case["input"]["scenario"].as_str().unwrap();
        let dir = std::path::Path::new(env!("CARGO_TARGET_TMPDIR"))
            .join("njord-server-tests/manifest")
            .join(scenario.replace(' ', "_"));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).unwrap();
        match scenario {
            "no archives" => {}
            "two archives, newest has a sidecar" => {
                std::fs::write(dir.join(NEWEST), ARCHIVE).unwrap();
                std::fs::write(dir.join(OLDER), "older").unwrap();
                std::fs::write(
                    dir.join(format!("{NEWEST}.sha256")),
                    format!("{}  {NEWEST}\n", "0".repeat(64)),
                )
                .unwrap();
            }
            "archive without sidecar" => std::fs::write(dir.join(NEWEST), ARCHIVE).unwrap(),
            "archive with a corrupt sidecar" => {
                std::fs::write(dir.join(NEWEST), ARCHIVE).unwrap();
                std::fs::write(dir.join(format!("{NEWEST}.sha256")), "not a checksum\n").unwrap();
            }
            other => panic!("unknown scenario {other}"),
        }
        let regions = Regions::new(&dir, zone.clone());
        let mut actual =
            serde_json::to_value(regions.manifest(std::slice::from_ref(&region))).unwrap();
        strip_nulls(&mut actual);
        let actual = actual.as_array().unwrap()[0].clone();
        let result = match first_difference(&case["expected"], &actual, "") {
            None => Ok(()),
            Some(d) => Err(d),
        };
        if failures.check(scenario, result).is_some() && scenario == "archive without sidecar" {
            let sidecar = std::fs::read_to_string(dir.join(format!("{NEWEST}.sha256"))).unwrap();
            let expected = format!(
                "{}  {NEWEST}\n",
                case["expected"]["archiveSha256"].as_str().unwrap()
            );
            if sidecar != expected {
                failures.push(format!("{scenario}: sidecar {sidecar:?}"));
            }
        }
    }
    failures.assert_none(cases.len());
}
