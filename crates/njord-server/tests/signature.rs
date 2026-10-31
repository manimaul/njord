//! P-SEC-1 `testdata/admin/hmac_vectors.json` and P-SEC-2 `testdata/admin/verify_cases.json`
//! against [`njord_server::signature`], with each case's `port.expected` (decision D4) when it
//! has one.

use njord_model::AdminSignature;
use njord_server::signature::{Verifier, hmac};
use njord_testdata::{Failures, cases, port_expected};
use serde_json::Value;

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
