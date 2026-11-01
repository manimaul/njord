//! Number formatting and rounding the style JSON and the golden fixtures expect.
//!
//! Numbers print as the shortest decimal that round-trips, always with a fraction (`10.0`), and
//! in `E` notation outside `[1e-3, 1e7)`; [`round`] breaks ties to even; [`sign`] of zero is zero.
//! The style JSON, the label text the symbology writes into feature props and the fixtures in
//! `testdata/` all depend on these exact forms.

use serde_json::Value;

/// Formats `x` as the shortest decimal that round-trips, always with a fraction digit.
///
/// `10.0` rather than `10`; magnitudes outside `[1e-3, 1e7)` use `E` notation (`1.0E7`,
/// `1.5E-4`). Non-finite values print as `NaN`, `Infinity` and `-Infinity`.
pub fn double_to_string(x: f64) -> String {
    if !x.is_finite() {
        return if x.is_nan() {
            "NaN".to_owned()
        } else if x > 0.0 {
            "Infinity".to_owned()
        } else {
            "-Infinity".to_owned()
        };
    }
    let magnitude = x.abs();
    if x == 0.0 || (1e-3..1e7).contains(&magnitude) {
        let s = format!("{x}");
        if s.contains('.') { s } else { format!("{s}.0") }
    } else {
        let s = format!("{x:e}");
        let (mantissa, exponent) = s.split_once('e').unwrap();
        let mantissa = if mantissa.contains('.') {
            mantissa.to_owned()
        } else {
            format!("{mantissa}.0")
        };
        format!("{mantissa}E{exponent}")
    }
}

/// Rounds to the nearest integer, ties to even (not half away from zero like [`f64::round`]).
pub fn round(x: f64) -> f64 {
    x.round_ties_even()
}

/// The sign of `x` as `-1.0`, `1.0` or, for zero, `x` itself (where [`f64::signum`] answers `1.0`).
///
/// NaN answers NaN.
pub fn sign(x: f64) -> f64 {
    if x > 0.0 {
        1.0
    } else if x < 0.0 {
        -1.0
    } else {
        x
    }
}

/// An `f32` as a JSON number, written as its shortest `f32` decimal.
///
/// `0.6f32` becomes `0.6`, whereas widening the `f32` to `f64` first would give
/// `0.6000000238418579`; the style JSON and its goldens carry the short form.
pub fn float_json(x: f32) -> Value {
    let shortest: f64 = format!("{x}").parse().unwrap();
    Value::from(shortest)
}
