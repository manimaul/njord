//! Admin signatures (`GET /v1/admin`, `POST /v1/admin/verify`).

use jiff::Timestamp;
use serde::{Deserialize, Serialize};

/// The signed admin token. `signature` is the base64 HMAC-SHA256 the server computes; the field
/// order and [`crate::instant`] formatting are part of the contract, because
/// [`AdminResponse::signature_encoded`] is the base64 of this struct's JSON.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminSignature {
    #[serde(with = "crate::instant")]
    pub date: Timestamp,
    pub signature: String,
    pub uuid: String,
    #[serde(with = "crate::instant")]
    pub expiration_date: Timestamp,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminResponse {
    pub signature: AdminSignature,
    /// URL-path-encoded base64 of the [`AdminSignature`] JSON, sent back by clients as the
    /// `signature` query parameter of every mutating call.
    pub signature_encoded: String,
}
