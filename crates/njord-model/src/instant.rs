//! `kotlin.time.Instant` wire format.
//!
//! Kotlin writes ISO-8601 UTC with the fractional second padded to 0, 3, 6 or 9 digits
//! (`…:05Z`, `…:05.100Z`, `…:05.123456Z`, `…:05.123456789Z`). The admin signature HMAC covers
//! the serialised [`AdminSignature`](crate::AdminSignature), so the port must reproduce this
//! byte for byte (`testdata/admin/signature_vectors.json`).

use jiff::Timestamp;
use serde::{Deserialize, Deserializer, Serializer};

/// Formats `ts` exactly as Kotlin's `Instant.toString()`.
pub fn format(ts: Timestamp) -> String {
    let nanos = ts.subsec_nanosecond();
    let digits = if nanos == 0 {
        0
    } else if nanos % 1_000_000 == 0 {
        3
    } else if nanos % 1_000 == 0 {
        6
    } else {
        9
    };
    format!("{ts:.digits$}")
}

/// Parses an ISO-8601 instant as Kotlin's `Instant.parse` accepts it.
pub fn parse(s: &str) -> Result<Timestamp, jiff::Error> {
    s.parse()
}

pub fn serialize<S: Serializer>(ts: &Timestamp, serializer: S) -> Result<S::Ok, S::Error> {
    serializer.serialize_str(&format(*ts))
}

pub fn deserialize<'de, D: Deserializer<'de>>(deserializer: D) -> Result<Timestamp, D::Error> {
    let s = String::deserialize(deserializer)?;
    parse(&s).map_err(serde::de::Error::custom)
}

/// `Option<Instant>`: `null` when absent.
pub mod option {
    use jiff::Timestamp;
    use serde::{Deserialize, Deserializer, Serializer};

    pub fn serialize<S: Serializer>(
        ts: &Option<Timestamp>,
        serializer: S,
    ) -> Result<S::Ok, S::Error> {
        match ts {
            Some(ts) => super::serialize(ts, serializer),
            None => serializer.serialize_none(),
        }
    }

    pub fn deserialize<'de, D: Deserializer<'de>>(
        deserializer: D,
    ) -> Result<Option<Timestamp>, D::Error> {
        Option::<String>::deserialize(deserializer)?
            .map(|s| super::parse(&s).map_err(serde::de::Error::custom))
            .transpose()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn pads_fraction_like_kotlin() {
        for s in [
            "2026-01-02T03:04:05Z",
            "2026-01-02T03:04:05.100Z",
            "2026-01-02T03:04:05.123Z",
            "2026-01-02T03:04:05.120400Z",
            "2026-01-02T03:04:05.123456789Z",
            "2026-01-02T03:04:05.000000001Z",
        ] {
            assert_eq!(format(parse(s).unwrap()), s);
        }
    }
}
