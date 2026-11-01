//! The region exporter against `testdata/regions/schema.sql`, `metadata.json` and `archive.json`.
//!
//! It runs on the seeded database, rendering `REGION_TEST` (the fixture cells) and `WORLD`, then
//! checks the archive's DDL, metadata rows, tile coordinate set, chart rows, edge counts,
//! checksum sidecar and the export watermark.
//!
//! The archive tiles are gunzipped and decoded and must equal the live tile for the same
//! coordinates, and the committed golden tile where one exists, under the same tolerance as
//! tests/tiles.rs.

mod common;

use std::sync::Arc;

use njord_server::AppState;
use njord_server::regions::export::{ExportResult, export_next};
use njord_testdata::{Failures, cases, first_difference, load, testdata};
use rusqlite::Connection;
use serde_json::{Value, json};

const REGION: &str = "REGION_TEST";
const REGION_WKT: &str = "POLYGON ((-124 46.5, -122 46.5, -122 47.5, -124 47.5, -124 46.5))";
const WORLD_WKT: &str = "POLYGON ((-180 -90,180 -90,180 90,-180 90,-180 -90))";

async fn exporter_state() -> Arc<AppState> {
    let overrides = json!({
        "regionExports": [
            {"name": REGION, "description": "fixture cells", "coverage": REGION_WKT},
            {"name": "WORLD", "description": "World Base Map", "coverage": WORLD_WKT},
        ]
    });
    let db = common::seeded_db().await;
    db.clear_region_export_state(REGION).await.unwrap();
    db.clear_region_export_state("WORLD").await.unwrap();
    common::state_with("export", db, &overrides.to_string())
}

fn archives(state: &AppState, region: &str) -> Vec<std::path::PathBuf> {
    let mut found: Vec<_> = std::fs::read_dir(&state.regions.dir)
        .map(|entries| {
            entries
                .flatten()
                .map(|e| e.path())
                .filter(|p| {
                    let n = p.file_name().unwrap().to_string_lossy().into_owned();
                    n.starts_with(&format!("{region}_")) && n.ends_with(".mbtiles")
                })
                .collect()
        })
        .unwrap_or_default();
    found.sort();
    found
}

fn rows(conn: &Connection, sql: &str, width: usize) -> Vec<Vec<Value>> {
    let mut stmt = conn.prepare(sql).unwrap();
    let rows = stmt
        .query_map([], |row| {
            let mut out = Vec::with_capacity(width);
            for i in 0..width {
                let v: rusqlite::types::Value = row.get(i)?;
                out.push(match v {
                    rusqlite::types::Value::Null => Value::Null,
                    rusqlite::types::Value::Integer(i) => json!(i),
                    rusqlite::types::Value::Real(f) => json!(f),
                    rusqlite::types::Value::Text(t) => json!(t),
                    rusqlite::types::Value::Blob(b) => json!(b.len()),
                });
            }
            Ok(out)
        })
        .unwrap();
    rows.map(Result::unwrap).collect()
}

fn metadata(conn: &Connection) -> Vec<(String, String)> {
    rows(conn, "SELECT name, value FROM metadata ORDER BY rowid", 2)
        .into_iter()
        .map(|r| {
            (
                r[0].as_str().unwrap().to_owned(),
                r[1].as_str().unwrap().to_owned(),
            )
        })
        .collect()
}

fn metadata_json(meta: &[(String, String)]) -> Value {
    let mut map = serde_json::Map::new();
    for (k, v) in meta {
        let v = if k == "njord:generated_at" {
            "<instant>"
        } else {
            v
        };
        map.insert(k.clone(), json!(v));
    }
    Value::Object(map)
}

#[tokio::test]
async fn p_re_1_2_3_exported_archives() {
    let state = exporter_state().await;
    let db = &state.db;
    assert!(db.region_needs_rebuild(REGION_WKT, REGION).await.unwrap());
    assert_eq!(export_next(&state).await, ExportResult::Rendered);
    let archive = archives(&state, REGION);
    assert_eq!(archive.len(), 1, "{archive:?}");
    let archive = &archive[0];
    assert!(
        state.lock.is_free(),
        "the shared lock is released after the render"
    );
    assert!(
        !db.region_needs_rebuild(REGION_WKT, REGION).await.unwrap(),
        "markRegionExported moved the watermark"
    );
    let name = archive.file_name().unwrap().to_string_lossy().into_owned();
    let stem = name
        .strip_prefix(&format!("{REGION}_"))
        .unwrap()
        .strip_suffix(".mbtiles")
        .unwrap();
    let generated_at = state.regions.parse_stem(stem).unwrap();
    assert!(
        jiff::Timestamp::now().duration_since(generated_at).abs()
            < jiff::SignedDuration::from_secs(120)
    );
    let sidecar =
        std::fs::read_to_string(state.regions.dir.join(format!("{name}.sha256"))).unwrap();
    let digest = {
        use sha2::Digest;
        format!(
            "{:x}",
            sha2::Sha256::digest(std::fs::read(archive).unwrap())
        )
    };
    assert_eq!(sidecar, format!("{digest}  {name}\n"));
    assert_eq!(
        state.regions.manifest(&state.config.region_exports)[0]
            .archive_sha256
            .as_deref(),
        Some(digest.as_str())
    );

    // WORLD is the next stale region, then nothing is.
    assert_eq!(export_next(&state).await, ExportResult::Rendered);
    assert_eq!(export_next(&state).await, ExportResult::NothingToDo);
    let world = archives(&state, "WORLD");
    assert_eq!(world.len(), 1, "{world:?}");

    let conn = Connection::open(archive).unwrap();
    let mut failures = Failures::default();

    // Schema (`schema.sql`)
    let ddl: Vec<String> = rows(
        &conn,
        "SELECT sql FROM sqlite_master WHERE sql IS NOT NULL ORDER BY type, name",
        1,
    )
    .into_iter()
    .map(|r| r[0].as_str().unwrap().to_owned())
    .collect();
    let ddl = ddl.join(";\n\n") + ";\n";
    let fixture = std::fs::read_to_string(testdata().join("regions/schema.sql")).unwrap();
    let body = fixture
        .split("\n\n")
        .skip(1)
        .collect::<Vec<_>>()
        .join("\n\n");
    failures.check(
        "schema.sql",
        (ddl == body)
            .then_some(())
            .ok_or_else(|| format!("DDL differs:\n{ddl}\n--- fixture:\n{body}")),
    );

    // Metadata of both archives (`metadata.json`)
    let meta = metadata(&conn);
    assert_eq!(
        meta.iter()
            .find(|(k, _)| k == "njord:generated_at")
            .map(|(_, v)| v.as_str()),
        Some(njord_model::instant::format(generated_at).as_str()),
        "generated_at is derived from the archive stem"
    );
    let world_conn = Connection::open(&world[0]).unwrap();
    let world_meta = metadata(&world_conn);
    for case in cases("regions/metadata.json") {
        let which = case["input"].as_str().unwrap();
        let actual = metadata_json(if which == REGION { &meta } else { &world_meta });
        failures.check(
            format!("metadata {which}"),
            first_difference(&case["expected"], &actual, "").map_or(Ok(()), Err),
        );
    }

    // Catalog tables (`archive.json`)
    assert!(
        rows(&conn, "PRAGMA foreign_key_check", 1).is_empty(),
        "no dangling chart_tiles edge"
    );
    let tiles: Vec<Value> = rows(
        &conn,
        "SELECT zoom_level, tile_column, tile_row FROM tiles ORDER BY 1, 2, 3",
        3,
    )
    .into_iter()
    .map(Value::Array)
    .collect();
    let charts: Vec<Value> = rows(
        &conn,
        "SELECT name, scale, zoom, file_name, updated, issued, dsid_props FROM charts ORDER BY name",
        7,
    )
    .into_iter()
    .map(|r| {
        Value::Array(
            r.into_iter()
                .map(|v| match v {
                    Value::String(s) => json!(s),
                    other => json!(other.to_string()),
                })
                .collect(),
        )
    })
    .collect();
    let edges: serde_json::Map<String, Value> = rows(
        &conn,
        "SELECT chart_name, count(*) FROM chart_tiles GROUP BY chart_name ORDER BY chart_name",
        2,
    )
    .into_iter()
    .map(|r| (r[0].as_str().unwrap().to_owned(), r[1].clone()))
    .collect();
    let minmax = |k: &str| {
        meta.iter()
            .find(|(n, _)| n == k)
            .map(|(_, v)| v.parse::<i64>().unwrap())
            .unwrap()
    };
    let summary = json!({
        "tiles": tiles,
        "charts": charts,
        "chartTileEdges": edges,
        "minZoom": minmax("minzoom"),
        "maxZoom": minmax("maxzoom"),
    });
    let case = &cases("regions/archive.json")[0];
    assert_eq!(case["input"], REGION);
    failures.check(
        "archive.json",
        first_difference(&case["expected"], &summary, "").map_or(Ok(()), Err),
    );
    let edge_coords = rows(
        &conn,
        "SELECT DISTINCT zoom_level, tile_column, tile_row FROM chart_tiles",
        3,
    );
    for e in edge_coords {
        assert!(
            summary["tiles"]
                .as_array()
                .unwrap()
                .contains(&Value::Array(e.clone())),
            "edge {e:?} points at a written tile"
        );
    }

    // every tile gunzips, decodes and equals the live tile for the same xyz, and the committed
    // golden tile where there is one
    let golden_dir = testdata().join("geo/tiles");
    let mut golden_hits = 0;
    let mut stmt = conn
        .prepare("SELECT zoom_level, tile_column, tile_row, tile_data FROM tiles")
        .unwrap();
    let stored: Vec<(u32, i64, i64, Vec<u8>)> = stmt
        .query_map([], |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?, r.get(3)?)))
        .unwrap()
        .map(Result::unwrap)
        .collect();
    for (z, x, tms_row, gz) in stored {
        let y = ((1i64 << z) - 1) - tms_row;
        let mut bytes = Vec::new();
        std::io::Read::read_to_end(&mut flate2::read::GzDecoder::new(&gz[..]), &mut bytes).unwrap();
        let decoded = njord_geo::mvt::view::decode_to_json(&bytes).unwrap();
        let t = njord_server::tile_encoder::TileXyz { z, x, y };
        let live = njord_server::tile_encoder::tile(db, state.symbology.clone(), t, false)
            .await
            .unwrap();
        let live = njord_geo::mvt::view::decode_to_json(&live).unwrap();
        failures.check(
            format!("tile {z}/{x}/{y} equals the live tile"),
            common::tiles::compare_tile(&live, &decoded).map(|_| ()),
        );
        let golden = golden_dir.join(format!("{z}_{x}_{y}.json"));
        if golden.is_file() {
            golden_hits += 1;
            let committed = &load(&golden)["cases"][0]["expected"];
            // Same comparison as tests/tiles.rs: layers, features and props exact, vertices
            // within the pixel tolerance after canonicalising ring starts.
            if let Err(e) = common::tiles::compare_tile(committed, &decoded) {
                failures.push(format!("archive tile {z}/{x}/{y} vs golden: {e}"));
            }
        }
    }
    assert!(
        golden_hits >= 5,
        "the archive shares at least a handful of tiles with the golden set ({golden_hits})"
    );
    failures.assert_none(1);
}

/// `export_next` without configured regions does nothing; a lock held elsewhere yields LockBusy.
#[tokio::test]
async fn export_next_without_regions_and_with_a_busy_lock() {
    let db = common::seeded_db().await;
    let none = common::state_with("export_none", db.clone(), r#"{"regionExports": []}"#);
    assert_eq!(export_next(&none).await, ExportResult::NothingToDo);
    let state = exporter_state().await;
    // reuse the directory of the main test's state: a distinct temp dir
    let busy = common::state_with(
        "export_busy",
        db,
        &json!({"regionExports": [{"name": REGION, "description": "x", "coverage": REGION_WKT}]})
            .to_string(),
    );
    drop(state);
    std::fs::create_dir_all(busy.lock.path().parent().unwrap()).unwrap();
    std::fs::write(busy.lock.path(), "another-instance").unwrap();
    assert_eq!(export_next(&busy).await, ExportResult::LockBusy);
    assert!(archives(&busy, REGION).is_empty());
    busy.lock.try_clear();
}
