//! Admin signatures against the fixtures.
//!
//! `testdata/admin/hmac_vectors.json`, `signature_vectors.json` and `verify_cases.json` are
//! checked against [`njord_server::signature`], with each case's `port.expected` when it has
//! one.

use njord_model::AdminSignature;
use njord_server::signature::{Verifier, encode_url_path, hmac, random_uuid};
use njord_testdata::{Failures, cases, first_difference, port_expected};
use serde_json::{Value, json};

/// [`Verifier::mint`] with a fixed clock and nonce: the signature, its informational expiry and
/// the exact `signatureEncoded` text a client sends back.
#[test]
fn signature_vectors() {
    let mut failures = Failures::default();
    let cases = cases("admin/signature_vectors.json");
    for case in &cases {
        let input = &case["input"];
        let verifier = Verifier {
            admin_key: input["adminKey"].as_str().unwrap().to_owned(),
            expiration_seconds: input["expirationSeconds"].as_i64().unwrap(),
        };
        let now: jiff::Timestamp = input["now"].as_str().unwrap().parse().unwrap();
        let minted = verifier.mint(
            input["baseUrl"].as_str().unwrap(),
            now,
            input["uuid"].as_str().unwrap(),
        );
        let mut actual = serde_json::to_value(&minted.signature).unwrap();
        actual["signatureEncoded"] = json!(minted.signature_encoded);
        let label = format!("{} {}", input["baseUrl"], input["now"]);
        failures.check(
            &label,
            first_difference(&case["expected"], &actual, "").map_or(Ok(()), Err),
        );
        // and the minted token verifies, under the same base URL only
        let base = input["baseUrl"].as_str().unwrap();
        assert!(verifier.verify(&minted.signature, base, now), "{label}");
        assert!(!verifier.verify(&minted.signature, "https://elsewhere.test", now));
        assert!(verifier.verify_query(&minted.signature_encoded, base, now));
    }
    failures.assert_none(cases.len());
}

#[test]
fn url_path_encoding_and_uuid_shape() {
    assert_eq!(encode_url_path("ab+/=="), "ab+%2F==");
    let uuid = random_uuid();
    let groups: Vec<usize> = uuid.split('-').map(str::len).collect();
    assert_eq!(groups, [8, 4, 4, 4, 8], "{uuid}");
    assert!(uuid.chars().all(|c| c.is_ascii_alphanumeric() || c == '-'));
    assert_ne!(random_uuid(), uuid);
}

#[test]
fn hmac_vectors() {
    let mut failures = Failures::default();
    let cases = cases("admin/hmac_vectors.json");
    for case in &cases {
        let input = &case["input"];
        let chunks: Vec<&str> = input["data"]
            .as_array()
            .unwrap()
            .iter()
            .map(|c| c.as_str().unwrap())
            .collect();
        let digest: String = hmac(input["key"].as_str().unwrap(), &chunks)
            .iter()
            .map(|b| format!("{b:02x}"))
            .collect();
        let expected = case["expected"].as_str().unwrap();
        failures.check(
            input["key"].as_str().unwrap(),
            (digest == expected)
                .then_some(())
                .ok_or(format!("{digest} != {expected}")),
        );
    }
    failures.assert_none(cases.len());
}

#[test]
fn verify_cases() {
    let mut failures = Failures::default();
    let cases = cases("admin/verify_cases.json");
    for case in &cases {
        let input = &case["input"];
        let verifier = Verifier {
            admin_key: input["adminKey"].as_str().unwrap().to_owned(),
            expiration_seconds: 600,
        };
        let now: jiff::Timestamp = input["now"].as_str().unwrap().parse().unwrap();
        let base = input["baseUrl"].as_str().unwrap();
        let actual = match (input.get("signature"), input.get("query")) {
            (Some(sig), _) => {
                let sig: AdminSignature = serde_json::from_value(sig.clone()).unwrap();
                verifier.verify(&sig, base, now)
            }
            (_, Some(Value::String(q))) => verifier.verify_query(q, base, now),
            _ => panic!("{case}"),
        };
        let expected = port_expected(case);
        let label = input["description"].as_str().unwrap();
        failures.check(
            label,
            (Value::Bool(actual) == *expected)
                .then_some(())
                .ok_or(format!("expected {expected}, got {actual}")),
        );
    }
    failures.assert_none(cases.len());
}
