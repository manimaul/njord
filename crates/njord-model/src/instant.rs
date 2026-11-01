//! The timestamp wire format, for use with `#[serde(with = "crate::instant")]`.
//!
//! Timestamps are ISO-8601 UTC with the fractional second padded to 0, 3, 6 or 9 digits
//! (`…:05Z`, `…:05.100Z`, `…:05.123456Z`, `…:05.123456789Z`). The admin signature HMAC covers
//! the serialised [`AdminSignature`](crate::AdminSignature), and clients hold signatures minted
//! earlier, so this format must be reproduced byte for byte
//! (`testdata/admin/signature_vectors.json`).

use jiff::Timestamp;
use serde::{Deserialize, Deserializer, Serializer};

/// Formats `ts` as ISO-8601 UTC with the shortest of 0, 3, 6 or 9 fraction digits that is exact.
///
/// ```
/// let ts: jiff::Timestamp = "2026-01-02T03:04:05.1Z".parse().unwrap();
/// assert_eq!(njord_model::instant::format(ts), "2026-01-02T03:04:05.100Z");
/// ```
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

/// Parses an ISO-8601 instant with any number of fraction digits.
///
/// # Errors
///
/// Returns the [`jiff::Error`] when `s` is not a valid ISO-8601 timestamp.
pub fn parse(s: &str) -> Result<Timestamp, jiff::Error> {
    s.parse()
}

/// Serialises `ts` as a string, with [`format()`].
///
/// # Errors
///
/// Returns the serializer's error.
pub fn serialize<S: Serializer>(ts: &Timestamp, serializer: S) -> Result<S::Ok, S::Error> {
    serializer.serialize_str(&format(*ts))
}

/// Deserialises a string with [`parse`].
///
/// # Errors
///
/// Fails when the value is not a string or not a valid timestamp.
pub fn deserialize<'de, D: Deserializer<'de>>(deserializer: D) -> Result<Timestamp, D::Error> {
    let s = String::deserialize(deserializer)?;
    parse(&s).map_err(serde::de::Error::custom)
}

/// The same format for `Option<Timestamp>`, with `null` for `None`.
pub mod option {
    use jiff::Timestamp;
    use serde::{Deserialize, Deserializer, Serializer};

    /// Serialises `Some` with [`format()`](super::format) and `None` as `null`.
    ///
    /// # Errors
    ///
    /// Returns the serializer's error.
    pub fn serialize<S: Serializer>(
        ts: &Option<Timestamp>,
        serializer: S,
    ) -> Result<S::Ok, S::Error> {
        match ts {
            Some(ts) => super::serialize(ts, serializer),
            None => serializer.serialize_none(),
        }
    }

    /// Deserialises `null` as `None` and a string with [`parse`](super::parse).
    ///
    /// # Errors
    ///
    /// Fails when the value is neither `null` nor a valid timestamp string.
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
    fn pads_fraction_to_0_3_6_or_9_digits() {
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
