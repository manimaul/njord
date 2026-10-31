//! P-RE-5 `testdata/regions/manifest.json`: `buildManifest` over a region directory, with the
//! archive stems read in `America/Los_Angeles` as the fixture was generated.

use njord_server::config::RegionExportConfig;
use njord_server::regions::Regions;
use njord_testdata::{Failures, cases, first_difference, strip_nulls};

const COVERAGE: &str = "POLYGON ((-123 47, -122 47, -122 48, -123 48, -123 47))";
const NEWEST: &str = "MANIFEST_TEST_2026-02-03T04-05-06.mbtiles";
const OLDER: &str = "MANIFEST_TEST_2026-01-02T03-04-05.mbtiles";
/// The archive contents `RegionExportTest` writes.
const ARCHIVE: &[u8] = b"newest archive bytes";

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
