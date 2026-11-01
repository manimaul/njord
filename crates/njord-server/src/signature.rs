//! Admin signatures: minting them and verifying the ones clients send back.
//!
//! A signature is HMAC-SHA256, keyed with `adminKey`, over the external base URL, the date
//! (ISO-8601 with 0, 3, 6 or 9 fraction digits, [`njord_model::instant::format`]) and the uuid.
//! It is rejected only when it is older than `adminExpirationSeconds` in whole seconds; a future
//! date passes and `expirationDate` is never checked. These rules are the API contract pinned by
//! `testdata/admin/hmac_vectors.json`, so signatures that existing clients hold keep verifying.
//! Malformed input of any kind verifies as `false` (a 401, never a 500), and the HMAC is
//! compared as bytes in constant time.
//!
//! Minting (`GET /v1/admin`, `POST /v1/admin/verify`; `testdata/admin/signature_vectors.json`) is
//! [`Verifier::mint`]: the `AdminResponse` carries the signature and its `signatureEncoded` form,
//! the base64 of the signature JSON URL-path-encoded by [`encode_url_path`], which is what
//! clients send back as the `signature` query parameter.

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
    /// Mints a signature over `base_url` dated `now` with nonce `uuid`, and its encoded form.
    ///
    /// The signature's `expirationDate` is `expiration_seconds` after `now`; it is informational
    /// only, since [`verify`](Self::verify) checks the age of `date`.
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

    /// Calls [`mint`](Self::mint) with the current time and a fresh [`random_uuid`] nonce.
    pub fn mint_now(&self, base_url: &str) -> AdminResponse {
        self.mint(base_url, Timestamp::now(), &random_uuid())
    }

    /// Whether the `signature` query parameter `query` is a valid signature for `base_url` at
    /// `now`.
    ///
    /// `query` is the parameter as the router decoded it. It is percent-decoded once more, since
    /// clients send the already path-encoded `signatureEncoded`, then read as base64 of the
    /// [`AdminSignature`] JSON and checked by [`verify`](Self::verify).
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

    /// Whether `signature` is valid for `base_url` at `now`: not older than
    /// `expiration_seconds`, and its HMAC matches.
    pub fn verify(&self, signature: &AdminSignature, base_url: &str, now: Timestamp) -> bool {
        // Whole seconds, truncated toward zero.
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

/// A random signature nonce: five groups of random alphanumerics, `8-4-4-4-8`.
///
/// It is not an RFC 4122 UUID; the shape is the one existing clients and the signature vectors
/// expect.
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

/// URL-path-encodes base64 text: letters, digits and the path-safe punctuation (`+` and `=`
/// among them) stay, `/` becomes `%2F`.
///
/// This is the `signatureEncoded` form that clients send back verbatim.
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

/// HMAC-SHA256 keyed with `key` over the concatenation of `chunks`.
pub fn hmac(key: &str, chunks: &[&str]) -> Vec<u8> {
    let mut mac = Hmac::<Sha256>::new_from_slice(key.as_bytes()).expect("HMAC takes any key");
    for chunk in chunks {
        mac.update(chunk.as_bytes());
    }
    mac.finalize().into_bytes().to_vec()
}
