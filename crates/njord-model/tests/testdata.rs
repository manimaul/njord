//! Round trips of the `testdata/` fixtures through the wire types.

use base64::Engine;
use njord_model::{
    AdminResponse, AdminSignature, ChartInsert, IconInfo, IndexMap, RegionManifestEntry, Sprite,
    Style, WsMsg,
};
use njord_testdata::{Failures, Nulls, cases, json_files, round_trip, testdata};
use serde_json::Value;

/// Every [`WsMsg`] variant.
#[test]
fn ws_messages() {
    let mut failures = Failures::default();
    let cases = cases("ws/messages.json");
    for case in &cases {
        failures.check(
            &case["input"],
            round_trip::<WsMsg>(&case["expected"], Nulls::Explicit),
        );
    }
    failures.assert_none(cases.len());
}

/// The full style JSON for every depth × theme × custom theme.
#[test]
fn styles() {
    let mut failures = Failures::default();
    let mut checked = 0;
    for path in json_files(&testdata().join("symbology/style")) {
        let name = path.file_name().unwrap().to_string_lossy().into_owned();
        for case in njord_testdata::load(&path)["cases"].as_array().unwrap() {
            checked += 1;
            failures.check(
                &name,
                round_trip::<Style>(&case["expected"], Nulls::Explicit).map(|_| ()),
            );
        }
    }
    assert_eq!(checked, 27, "one style per depth × theme × custom theme");
    failures.assert_none(checked);
}

/// The sprite sheet index per theme, and the `Sprite` enum in declaration order.
#[test]
fn sprites() {
    let mut failures = Failures::default();
    let cases = cases("symbology/sprites.json");
    for case in &cases {
        if case["input"] == "ENUM" {
            let expected: Vec<&str> = case["expected"]
                .as_array()
                .unwrap()
                .iter()
                .map(|v| v.as_str().unwrap())
                .collect();
            let actual: Vec<&str> = Sprite::ALL.iter().map(|s| s.as_str()).collect();
            if expected != actual {
                failures.push(format!(
                    "Sprite::ALL differs from the fixture's order:\n{expected:?}\n{actual:?}"
                ));
            }
        } else {
            failures.check(
                &case["input"],
                round_trip::<IndexMap<Sprite, IconInfo>>(&case["expected"], Nulls::Explicit),
            );
        }
    }
    failures.assert_none(cases.len());
}

/// [`AdminSignature`] must serialise byte-identically to the signature vectors, because
/// `signatureEncoded` is the base64 of that JSON and signatures minted earlier must still verify.
#[test]
fn admin_signature_vectors() {
    let mut failures = Failures::default();
    let cases = cases("admin/signature_vectors.json");
    for case in &cases {
        let label = &case["input"]["now"];
        let mut expected = case["expected"].clone();
        let encoded = expected
            .as_object_mut()
            .unwrap()
            .remove("signatureEncoded")
            .unwrap();
        let encoded = encoded.as_str().unwrap();
        let Some(signature) = failures.check(
            label,
            round_trip::<AdminSignature>(&expected, Nulls::Explicit),
        ) else {
            continue;
        };
        let json = serde_json::to_string(&signature).unwrap();
        let ours = base64::engine::general_purpose::STANDARD.encode(json.as_bytes());
        let theirs = percent_decode(encoded);
        if ours != theirs {
            failures.push(format!(
                "{label}: base64 of {json} is {ours}, the vector has {theirs}"
            ));
        }
        let response = serde_json::json!({ "signature": expected, "signatureEncoded": encoded });
        failures.check(
            label,
            round_trip::<AdminResponse>(&response, Nulls::Explicit),
        );
    }
    failures.assert_none(cases.len());
}

/// Region manifest entries. The fixture omits `null` members.
#[test]
fn region_manifest() {
    let mut failures = Failures::default();
    let cases = cases("regions/manifest.json");
    for case in &cases {
        let label = &case["input"]["scenario"];
        match &case["expected"] {
            Value::Array(entries) => entries.iter().for_each(|e| {
                failures.check(label, round_trip::<RegionManifestEntry>(e, Nulls::Omitted));
            }),
            entry => {
                failures.check(
                    label,
                    round_trip::<RegionManifestEntry>(entry, Nulls::Omitted),
                );
            }
        }
    }
    failures.assert_none(cases.len());
}

/// The chart row ingest builds from a cell ([`ChartInsert`]).
#[test]
fn chart_insert() {
    let mut failures = Failures::default();
    let cases = cases("s57/chart_insert.json");
    for case in &cases {
        failures.check(
            &case["input"],
            round_trip::<ChartInsert>(&case["expected"], Nulls::Omitted),
        );
    }
    failures.assert_none(cases.len());
}

/// Undoes the URL-path percent-encoding of `signatureEncoded` (slashes included).
fn percent_decode(s: &str) -> String {
    let bytes = s.as_bytes();
    let mut out = Vec::with_capacity(bytes.len());
    let mut i = 0;
    while i < bytes.len() {
        if bytes[i] == b'%' && i + 2 < bytes.len() {
            let hex = std::str::from_utf8(&bytes[i + 1..i + 3]).unwrap();
            out.push(u8::from_str_radix(hex, 16).unwrap());
            i += 3;
        } else {
            out.push(bytes[i]);
            i += 1;
        }
    }
    String::from_utf8(out).unwrap()
}
