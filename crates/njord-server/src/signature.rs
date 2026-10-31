//! Admin signature verification (`AdminUtil.verifySignature`, `requireSignature` in
//! `endpoints/AdminHandler.kt`), with decision D4 (TEST_COVERAGE_AUDIT.md §3.1): malformed input
//! is `false` (→ 401) instead of a Kotlin exception (→ 500), and the HMAC is compared as bytes in
//! constant time.
//!
//! A signature is HMAC-SHA256, keyed with `adminKey`, over the external base URL, the date as
//! Kotlin's `Instant.toString()` prints it and the uuid. It is rejected only when it is older
//! than `adminExpirationSeconds` in whole seconds; a future date passes and `expirationDate` is
//! never checked, both as in Kotlin, so signatures cross-verify between the two servers.
//!
//! Minting (`GET /v1/admin`, `POST /v1/admin/verify`; P-SEC-1 `testdata/admin/signature_vectors.json`)
//! is [`Verifier::mint`]: the `AdminResponse` carries the signature and its `signatureEncoded`
//! form, the base64 of the signature JSON URL-path-encoded as Ktor's `encodeURLPath(encodeSlash =
//! true)` does, which is what clients send back as the `signature` query parameter.

use base64::Engine;
use base64::engine::general_purpose::STANDARD;
use hmac::{Hmac, Mac};
use jiff::{SignedDuration, Timestamp};
use njord_model::{AdminResponse, AdminSignature};
use sha2::Sha256;
use subtle::ConstantTimeEq;

/// The key and lifetime signatures are checked against (`adminKey`, `adminExpirationSeconds`).
#[derive(Clone, Debug)]
pub struct Verifier {
    pub admin_key: String,
    pub expiration_seconds: i64,
}

impl Verifier {
    /// `createSignatureResponse(baseUrl, now, uuid)`: a signature over `base_url` dated `now`
    /// with nonce `uuid`, expiring `expiration_seconds` later (informational), and its encoded
    /// form.
    pub fn mint(&self, base_url: &str, now: Timestamp, uuid: &str) -> AdminResponse {
        let date = njord_model::instant::format(now);
        let mac = hmac(&self.admin_key, &[base_url, &date, uuid]);
        let signature = AdminSignature {
            date: now,
            signature: STANDARD.encode(mac),
            uuid: uuid.to_owned(),
            expiration_date: now
                .checked_add(SignedDuration::from_secs(self.expiration_seconds))
                .unwrap_or(now),
        };
        let json = serde_json::to_vec(&signature).expect("AdminSignature serialises");
        AdminResponse {
            signature_encoded: encode_url_path(&STANDARD.encode(json)),
            signature,
        }
    }

    /// [`mint`](Self::mint) with the clock and a fresh nonce (`Clock.System.now()`,
    /// `UUID.randomUUID()`).
    pub fn mint_now(&self, base_url: &str) -> AdminResponse {
        self.mint(base_url, Timestamp::now(), &random_uuid())
    }

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

/// Kotlin's `UUID.randomUUID()` (`util/UUID.kt`): not RFC 4122 but five groups of random
/// alphanumerics, `8-4-4-4-8`. Reproduced so a Kotlin client parsing its shape stays happy.
pub fn random_uuid() -> String {
    const ALPHABET: &[u8] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    let bytes = uuid::Uuid::new_v4().into_bytes();
    let mut more = uuid::Uuid::new_v4().into_bytes().to_vec();
    more.extend_from_slice(&bytes);
    let mut out = String::with_capacity(32);
    for (i, len) in [8usize, 4, 4, 4, 8].into_iter().enumerate() {
        if i > 0 {
            out.push('-');
        }
        for _ in 0..len {
            let b = more.pop().expect("32 random bytes for 28 characters");
            out.push(ALPHABET[usize::from(b) % ALPHABET.len()] as char);
        }
    }
    out
}

/// Ktor's `encodeURLPath(encodeSlash = true)` over base64 text: letters, digits and the
/// path-safe punctuation (`+` and `=` among them) stay, `/` becomes `%2F`.
pub fn encode_url_path(s: &str) -> String {
    const KEEP: &[u8] = b":@!$&'()*+,;=-._~";
    let mut out = String::with_capacity(s.len() + 8);
    for b in s.bytes() {
        if b.is_ascii_alphanumeric() || KEEP.contains(&b) {
            out.push(b as char);
        } else {
            out.push_str(&format!("%{b:02X}"));
        }
    }
    out
}

/// `HmacSHA256(key)`, updated with each chunk in turn.
pub fn hmac(key: &str, chunks: &[&str]) -> Vec<u8> {
    let mut mac = Hmac::<Sha256>::new_from_slice(key.as_bytes()).expect("HMAC takes any key");
    for chunk in chunks {
        mac.update(chunk.as_bytes());
    }
    mac.finalize().into_bytes().to_vec()
}
