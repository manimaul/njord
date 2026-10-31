//! Kotlin standard-library behaviour the fixtures depend on: how a `Double` or `Float` prints and
//! how `kotlin.math.round` / `sign` behave.

use serde_json::Value;

/// `Double.toString()`: the shortest decimal that round-trips, always with a fraction (`10.0`),
/// switching to `1.0E7` notation outside `[1e-3, 1e7)`.
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

/// `kotlin.math.round`: to the nearest integer, ties to even (not half-up like [`f64::round`]).
pub fn round(x: f64) -> f64 {
    x.round_ties_even()
}

/// `kotlin.math.sign`: `0.0` for zero (where [`f64::signum`] answers `1.0`).
pub fn sign(x: f64) -> f64 {
    if x > 0.0 {
        1.0
    } else if x < 0.0 {
        -1.0
    } else {
        x
    }
}

/// A Kotlin `Float` as a JSON number. kotlinx writes a float by its shortest `Float` decimal
/// (`0.6f` is `0.6`), whereas widening the `f32` to `f64` first would give `0.6000000238418579`.
pub fn float_json(x: f32) -> Value {
    let shortest: f64 = format!("{x}").parse().unwrap();
    Value::from(shortest)
}
