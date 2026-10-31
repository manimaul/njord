//! Admin signature verification (`AdminUtil.verifySignature`, `requireSignature` in
//! `endpoints/AdminHandler.kt`), with decision D4 (TEST_COVERAGE_AUDIT.md §3.1): malformed input
//! is `false` (→ 401) instead of a Kotlin exception (→ 500), and the HMAC is compared as bytes in
//! constant time.
//!
//! A signature is HMAC-SHA256, keyed with `adminKey`, over the external base URL, the date as
//! Kotlin's `Instant.toString()` prints it and the uuid. It is rejected only when it is older
//! than `adminExpirationSeconds` in whole seconds; a future date passes and `expirationDate` is
//! never checked, both as in Kotlin, so signatures cross-verify between the two servers.
//! Minting signatures (`GET /v1/admin`) is not part of the read-only server.

use base64::Engine;
use base64::engine::general_purpose::STANDARD;
use hmac::{Hmac, Mac};
use jiff::Timestamp;
use njord_model::AdminSignature;
use sha2::Sha256;
use subtle::ConstantTimeEq;

/// The key and lifetime signatures are checked against (`adminKey`, `adminExpirationSeconds`).
#[derive(Clone, Debug)]
pub struct Verifier {
    pub admin_key: String,
    pub expiration_seconds: i64,
}

impl Verifier {
    /// `verifySignature(query, baseUrl)`: `query` is the `signature` query parameter as the
    /// router decoded it, which Kotlin percent-decodes once more (`decodeURLPart`) before
    /// reading it as base64 of the [`AdminSignature`] JSON.
    pub fn verify_query(&self, query: &str, base_url: &str, now: Timestamp) -> bool {
        let Ok(decoded) = percent_encoding::percent_decode_str(query).decode_utf8() else {
            return false;
        };
        let Ok(json) = STANDARD.decode(decoded.as_bytes()) else {
            return false;
        };
        match serde_json::from_slice::<AdminSignature>(&json) {
            Ok(signature) => self.verify(&signature, base_url, now),
            Err(_) => false,
        }
    }

    pub fn verify(&self, signature: &AdminSignature, base_url: &str, now: Timestamp) -> bool {
        // `Instant.until(now, SECOND)`: whole seconds, truncated toward zero.
        let elapsed = now.duration_since(signature.date).as_secs();
        if elapsed > self.expiration_seconds {
            return false;
        }
        let Ok(presented) = STANDARD.decode(signature.signature.as_bytes()) else {
            return false;
        };
        let expected = hmac(
            &self.admin_key,
            &[
                base_url,
                &njord_model::instant::format(signature.date),
                &signature.uuid,
            ],
        );
        presented.len() == expected.len() && bool::from(presented.ct_eq(&expected))
    }
}

/// `HmacSHA256(key)`, updated with each chunk in turn.
pub fn hmac(key: &str, chunks: &[&str]) -> Vec<u8> {
    let mut mac = Hmac::<Sha256>::new_from_slice(key.as_bytes()).expect("HMAC takes any key");
    for chunk in chunks {
        mac.update(chunk.as_bytes());
    }
    mac.finalize().into_bytes().to_vec()
}
