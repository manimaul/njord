//! Sounding depth parts (`libgdal/src/nativeMain/kotlin/Soundings.kt`).

use njord_model::JsonObject;
use serde_json::Value;

use crate::kotlin::sign;

#[derive(Clone, Copy, Debug, PartialEq)]
pub struct DisplayDepths {
    pub meters: f64,
    pub meters_whole: i32,
    pub meters_tenths: i32,
    pub feet: i32,
    pub fathoms: i32,
    pub fathoms_feet: i32,
}

/// Every part is computed on the magnitude and then given the sounding's sign, so a drying height
/// (negative, S-57 convention) carries the same digits as the equivalent depth with every part
/// negative. S-52 SNDFRM04 draws those digits positive with the SOUNDSA1 underline; the style
/// applies `abs` and reads the sign from `METERS` (see `soundg`).
pub fn display_depths(meters: f64) -> DisplayDepths {
    let magnitude = meters.abs();
    let sgn = sign(meters) as i32;

    let feet = magnitude * 3.28084;
    let mut feet_whole = feet as i32;
    if feet - feet_whole as f64 >= 0.9 {
        feet_whole += 1;
    }
    let fathoms = magnitude * 0.546807;
    let fathoms_whole = fathoms as i32;
    let fathoms_feet = ((fathoms - fathoms_whole as f64) * 6.0) as i32;
    // S-57 encodes depths to one decimal, so split on the decimal tenths rather than the binary
    // double: 2.3 is 2.29999… and truncating (2.3 - 2) * 10 would read 2.2. The epsilon absorbs
    // that representation error; anything beyond the first decimal still truncates, so a label
    // never reads deeper than the sounding.
    let tenths_total = (magnitude * 10.0 + 1e-6).floor() as i32;
    DisplayDepths {
        meters,
        meters_whole: sgn * (tenths_total / 10),
        meters_tenths: sgn * (tenths_total % 10),
        feet: sgn * feet_whole,
        fathoms: sgn * fathoms_whole,
        fathoms_feet: sgn * fathoms_feet,
    }
}

/// `addSoundingConversions`: `METERS`, `METERS_W`, `METERS_T`, `FEET`, `FATHOMS`, `FATHOMS_FT`.
pub fn add_sounding_conversions(props: &mut JsonObject, meters: f64) {
    let d = display_depths(meters);
    props.insert("METERS".into(), Value::from(meters));
    props.insert("METERS_W".into(), d.meters_whole.into());
    props.insert("METERS_T".into(), d.meters_tenths.into());
    props.insert("FEET".into(), d.feet.into());
    props.insert("FATHOMS".into(), d.fathoms.into());
    props.insert("FATHOMS_FT".into(), d.fathoms_feet.into());
}
