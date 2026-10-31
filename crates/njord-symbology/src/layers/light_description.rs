//! The light description text string — `Fl(2) R 6s7.6m5M` — per S-52 PresLib 4.0 Part I §10.6.3
//! "Light Description Text Strings" (the textual replacement for the old `LITDSN01` C function),
//! in `docs/reference_material/s52_s57_spec/TSMAD26_DIPWG5-8.1C_S-52_PresLib_v3.4_to_v4.0_Redlines.pdf`
//! (`LightDescription.kt`).
//!
//! Attributes are output in the spec's draw order: CATLIT, LITCHR, SIGGRP, COLOUR, SIGPER, HEIGHT,
//! VALNMR, STATUS.
//!
//! HEIGHT is deliberately *not* rendered here. Like `clearance_label`, the tile stays
//! unit-agnostic: the string is split into a `head` (everything up to and including the period)
//! and a `tail` (range and status), and the style splices the height between them in the viewer's
//! units. The spacing around that splice is already baked into `head` and `tail`.

use njord_model::JsonObject;

use crate::kotlin::{double_to_string, round};
use crate::props::{double_value, int_value, int_values, string_value};

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct LightDescription {
    pub head: String,
    pub tail: String,
}

impl LightDescription {
    /// `None` when the light has no characteristic — there's nothing worth describing, and S-52
    /// doesn't fall back to a bare height/range.
    pub fn from(props: &JsonObject) -> Option<LightDescription> {
        let characteristic = int_value(props, "LITCHR").and_then(litchr)?;

        let category = join(props, "CATLIT", catlit, " ");
        let group = string_value(props, "SIGGRP")
            .filter(|g| !is_default_group(g))
            .unwrap_or_default();
        let colour = join(props, "COLOUR", colour, "");
        let status = join(props, "STATUS", status, " ");

        let period = positive(props, "SIGPER")
            .map(|p| format!("{}s", format(p)))
            .unwrap_or_default();
        let range = positive(props, "VALNMR")
            .map(|r| format!("{}M", format(r)))
            .unwrap_or_default();
        let has_height = positive(props, "HEIGHT").is_some();
        let has_numbers = !period.is_empty() || has_height || !range.is_empty();

        let mut head = [category, format!("{characteristic}{group}"), colour]
            .into_iter()
            .filter(|s| !s.is_empty())
            .collect::<Vec<_>>()
            .join(" ");
        if has_numbers {
            head.push(' ');
        }
        head.push_str(&period);
        let tail = if status.is_empty() {
            range
        } else {
            format!("{range} {status}")
        };
        Some(LightDescription { head, tail })
    }
}

fn join(
    props: &JsonObject,
    key: &str,
    table: fn(i32) -> Option<&'static str>,
    sep: &str,
) -> String {
    int_values(props, key)
        .into_iter()
        .filter_map(table)
        .collect::<Vec<_>>()
        .join(sep)
}

fn positive(props: &JsonObject, key: &str) -> Option<f64> {
    double_value(props, key).filter(|v| *v > 0.0)
}

/// "When the signal group value is set to or include "()" and/or "(1)" there is no requirement
/// for this to be populated in the light description text ... this follows the paper chart
/// convention Mariners are used to seeing."
fn is_default_group(group: &str) -> bool {
    group.replace("()", "").replace("(1)", "").trim().is_empty()
}

/// "The default presentation for each numeric value ... is no decimals. If the value of the
/// attribute has non-zero decimal part then the value is displayed to one decimal place."
/// Rounds to the tenth ties-to-even, as `kotlin.math.round` does.
pub fn format(value: f64) -> String {
    let tenths = round(value * 10.0) / 10.0;
    if tenths == tenths.floor() {
        (tenths as i64).to_string()
    } else {
        double_to_string(tenths)
    }
}

/// CATLIT - only these three categories have an abbreviation in the spec.
fn catlit(id: i32) -> Option<&'static str> {
    Some(match id {
        1 => "Dir",
        5 => "Aero",
        7 => "Fog Det Lt",
        _ => return None,
    })
}

fn litchr(id: i32) -> Option<&'static str> {
    Some(match id {
        1 => "F",
        2 => "Fl",
        3 => "LFl",
        4 => "Q",
        5 => "VQ",
        6 => "UQ",
        7 => "Iso",
        8 => "Oc",
        9 => "IQ",
        10 => "IVQ",
        11 => "IUQ",
        12 => "Mo",
        13 => "FFl",
        14 => "Fl+LFl",
        15 => "OcFl",
        16 => "FLFl",
        17 => "AlOc",
        18 => "AlLFl",
        19 => "AlFl",
        20 => "Al",
        25 => "Q+LFl",
        26 => "VQ+LFl",
        27 => "UQ+LFl",
        28 => "Al",
        29 => "AlF Fl",
        _ => return None,
    })
}

/// COLOUR - the spec lists only W, R, G and Y; the rest of the light colours are the INT 1
/// (IP 11.4-11.8) abbreviations. Black, grey, brown, magenta and pink aren't light colours.
fn colour(id: i32) -> Option<&'static str> {
    Some(match id {
        1 => "W",
        3 => "R",
        4 => "G",
        5 => "Bu",
        6 => "Y",
        9 => "Am",
        10 => "Vi",
        11 => "Or",
        _ => return None,
    })
}

fn status(id: i32) -> Option<&'static str> {
    Some(match id {
        2 => "occas",
        7 => "temp",
        8 => "priv",
        11 => "exting",
        17 => "U",
        _ => return None,
    })
}
