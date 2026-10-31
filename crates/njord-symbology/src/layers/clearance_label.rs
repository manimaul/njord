//! Vertical and horizontal clearance labels (`ClearanceLabel.kt`) — CHBLK on a CHWHT halo, per
//! the S-52 presentation library's `TE('clr %4.1lf','VERCLR',3,1,2,'15110',1,1,CHBLK,11)`.
//!
//! Split out of the object-class layerables (as `LndareLabel` is from `Lndare`) so the labels
//! can be registered separately, late in the draw order — clearance is text group 11 and
//! display-base in S-52, so it has to sit above the buoys, obstructions and area symbology that
//! the bridge itself is drawn among. One instance is registered per object class whose
//! presentation library lookup carries a `clr` instruction (BRIDGE, CBLOHD, CONVYR, CRANES,
//! GATCON), plus PIPOHD, under the key `<CLASS>_CLEARANCE`.
//!
//! The label text is composed by style expressions off the raw S-57 attributes, which reach the
//! tile as doubles, rather than being pre-rendered into the tile as strings. That keeps tiles
//! unit-agnostic — the same tile serves feet and metres — and keeps four strings per feature out
//! of every tile.
//!
//! Units follow the depth preference; fathoms are meaningless for a height, and fathom charts are
//! imperial anyway, so anything but metres renders feet.

use crate::layers::prelude::*;

/// Marks a BRIDGE whose CATBRG is an opening type, so the style knows to read VERCCL/VERCOP rather
/// than VERCLR. S-52 makes that choice through the presentation library's lookup table, which is
/// keyed on CATBRG; the style can't make it itself because CATBRG is a list attribute and GDAL
/// flattens it to the literal string `["5"]` on the way into the tile.
pub const OPENING_BRIDGE: &str = "_OPBR";

/// `ChartFeature.openingBridge()`.
pub fn opening_bridge(feature: &mut ChartFeature) {
    feature.props.insert(OPENING_BRIDGE.into(), true.into());
}

const FEET_PER_METER: f64 = 3.28084;

#[derive(Clone, Debug)]
pub struct ClearanceLabel {
    pub object_class: String,
}

impl ClearanceLabel {
    pub fn new(object_class: impl Into<String>) -> Self {
        ClearanceLabel {
            object_class: object_class.into(),
        }
    }

    /// The registration key, `<CLASS>_CLEARANCE`.
    pub fn key(&self) -> String {
        format!("{}_CLEARANCE", self.object_class)
    }
}

impl Layerable for ClearanceLabel {
    fn source_layer(&self) -> Option<&str> {
        Some(&self.object_class)
    }

    /// Deliberately unfiltered by geometry type and placed as a point, so a bridge encoded as a
    /// polygon, a line or a point all get one upright label at the geometry's centre. Line
    /// placement would rotate the text to follow the span, which is much harder to read and is not
    /// what the presentation library's screen-aligned `TE()` does.
    ///
    /// The two labels anchor away from each other vertically rather than using fixed offsets, so
    /// an opening bridge's two-line `clr cl` / `clr op` label grows upward without running into
    /// `hor clr` below it. The x offset mirrors the presentation library's `XOFFS=1` — one text
    /// body right of the symbol.
    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            label(
                b,
                format!("{}_vertical", b.key),
                vertical_text(b.depth),
                Anchor::BottomLeft,
                Offset::Coord(1.2, -0.3),
            ),
            label(
                b,
                format!("{}_horizontal", b.key),
                horizontal_text(b.depth),
                Anchor::TopLeft,
                Offset::Coord(1.2, 0.3),
            ),
        ]
    }
}

fn label(b: &LayerBuilder, id: String, text: Value, text_anchor: Anchor, offset: Offset) -> Layer {
    Layer {
        layout: Some(Layout {
            text_font: Some(vec![Font::RobotoBold]),
            text_anchor: Some(text_anchor),
            text_justify: Some(TextJustify::Left),
            text_field: Some(text),
            text_offset: Some(offset.property()),
            text_size: Some(14.0),
            // Zeroed so the default 2px collision buffer doesn't bridge the intentional gap
            // between the vertical and horizontal labels and drop one of the pair.
            text_padding: Some(0.0),
            symbol_placement: Some(Placement::Point),
            ..Default::default()
        }),
        paint: Some(b.label_paint(Color::SNDG2, Color::DEPDW)),
        ..b.layer(id, LayerType::Symbol)
    }
}

/// An opening bridge carries a closed and/or an open clearance; everything else carries a
/// VERCLR and/or a VERCSA (safe clearance, `TE('sf clr %4.1lf','VERCSA',...)` - S-52 has lookups
/// for it on CBLOHD, and lists it for BRIDGE in text group 11). An opening bridge encoding
/// neither (which happens in real NOAA data) falls through to the VERCLR/VERCSA branches.
fn vertical_text(depth: Depth) -> Value {
    let opening = json!(["has", OPENING_BRIDGE]);
    json!([
        "case",
        ["all", opening, present("VERCCL"), present("VERCOP")],
        [
            "concat",
            "clr cl ",
            clearance("VERCCL", depth),
            "\n",
            "clr op ",
            clearance("VERCOP", depth)
        ],
        ["all", opening, present("VERCCL")],
        ["concat", "clr cl ", clearance("VERCCL", depth)],
        ["all", opening, present("VERCOP")],
        ["concat", "clr op ", clearance("VERCOP", depth)],
        ["all", present("VERCLR"), present("VERCSA")],
        [
            "concat",
            "clr ",
            clearance("VERCLR", depth),
            "\n",
            "sf clr ",
            clearance("VERCSA", depth)
        ],
        present("VERCLR"),
        ["concat", "clr ", clearance("VERCLR", depth)],
        present("VERCSA"),
        ["concat", "sf clr ", clearance("VERCSA", depth)],
        "",
    ])
}

/// Not rendered by S-52 at all — horizontal clearance is a njord addition.
fn horizontal_text(depth: Depth) -> Value {
    json!([
        "case",
        present("HORCLR"),
        ["concat", "hor clr ", clearance("HORCLR", depth)],
        "",
    ])
}

/// `has` alone isn't enough — the tile encoder only drops a "0" for enumerated attributes, and
/// these are floats, so a zero clearance would reach the tile and render as `clr 0.0m`. `all`
/// short-circuits, so `>` never sees a missing property.
fn present(key: &str) -> Value {
    json!(["all", ["has", key], [">", ["get", key], 0]])
}

/// Metres keep S-52's `%4.1lf` — one fraction digit, so 17 reads as `17.0`, which needs
/// `number-format`'s min-fraction-digits. Feet are whole and use `round`/`to-string` instead,
/// specifically to dodge `number-format`'s digit grouping: horizontal clearances routinely run
/// past 1000 ft (the Tacoma Narrows is 2737) and `hor clr 2,737ft` is not wanted.
///
/// That leaves one asymmetry: a horizontal clearance of 1000 m or more would group, as
/// `hor clr 1,234.5m`. There is no way to turn grouping off in the style spec, and no span in
/// NOAA's data comes close, so it is left rather than hand-rolling decimal assembly out of
/// floor/%/concat.
fn clearance(key: &str, depth: Depth) -> Value {
    match depth {
        Depth::Meters => json!([
            "concat",
            [
                "number-format",
                ["get", key],
                {"locale": "en-US", "min-fraction-digits": 1, "max-fraction-digits": 1}
            ],
            "m",
        ]),
        Depth::Feet | Depth::Fathoms => json!([
            "concat",
            ["to-string", ["round", ["*", ["get", key], FEET_PER_METER]]],
            "ft",
        ]),
    }
}
