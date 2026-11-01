//! S-52 text group 21, "names for position reporting" - `TE('by %s','OBJNAM',...)`
//! on buoys, `TE('bn %s','OBJNAM',...)` on beacons and daymarks, `LtV`, `Prod` and `Plt` on light
//! vessels, offshore platforms and pilot boarding places, and a plain `TX(OBJNAM,...)` /
//! `TX(INFORM,...)` on bridges and distance marks.
//!
//! Split out of the object-class layerables (as `ClearanceLabel` is) so the names can be
//! registered once, after every mark symbol, rather than threading a label layer through each buoy
//! and beacon class and its subclasses. Registered under the key `<CLASS>_NAME`.
//!
//! Like `ClearanceLabel`, the text is composed by a style expression off the raw attribute, so
//! nothing is added to the tile, and the layer is unfiltered by geometry type and point placed, so
//! an area (OFSPLF, PILBOP) or line (BRIDGE) gets one upright label at its centre.

use crate::layers::prelude::*;

#[derive(Clone, Debug)]
pub struct NameLabel {
    pub object_class: String,
    /// The `TE` format's text before `%s`, or `None` for a bare `TX`.
    pub prefix: Option<String>,
    pub placement: NamePlacement,
    pub attribute: String,
}

impl NameLabel {
    pub fn new(object_class: &str, prefix: Option<&str>, placement: NamePlacement) -> Self {
        NameLabel {
            object_class: object_class.to_owned(),
            prefix: prefix.map(str::to_owned),
            placement,
            attribute: "OBJNAM".to_owned(),
        }
    }

    /// The registration key, `<CLASS>_NAME`.
    pub fn key(&self) -> String {
        format!("{}_NAME", self.object_class)
    }

    /// Registered after all of the mark symbols, see `StandardLayers`.
    pub fn all() -> Vec<NameLabel> {
        let mut labels = Vec::new();
        for class in [
            "BOYCAR", "BOYINB", "BOYISD", "BOYLAT", "BOYSAW", "BOYSPP", "LITFLT",
        ] {
            labels.push(NameLabel::new(class, Some("by"), NamePlacement::UpperLeft));
        }
        for class in ["BCNCAR", "BCNISD", "BCNLAT", "BCNSAW", "BCNSPP", "DAYMAR"] {
            labels.push(NameLabel::new(class, Some("bn"), NamePlacement::UpperLeft));
        }
        labels.push(NameLabel::new(
            "LITVES",
            Some("LtV"),
            NamePlacement::UpperLeft,
        ));
        labels.push(NameLabel::new(
            "OFSPLF",
            Some("Prod"),
            NamePlacement::UpperRight,
        ));
        labels.push(NameLabel::new(
            "PILBOP",
            Some("Plt"),
            NamePlacement::UpperRight,
        ));
        labels.push(NameLabel::new("BRIDGE", None, NamePlacement::Left));
        labels.push(NameLabel {
            attribute: "INFORM".to_owned(),
            ..NameLabel::new("DISMAR", None, NamePlacement::DistanceMark)
        });
        labels
    }

    fn text(&self) -> Value {
        let attribute = &self.attribute;
        match &self.prefix {
            None => json!(["get", attribute]),
            Some(prefix) => json!([
                "case",
                ["has", attribute],
                [
                    "concat",
                    format!("{prefix} "),
                    ["to-string", ["get", attribute]]
                ],
                "",
            ]),
        }
    }
}

impl Layerable for NameLabel {
    fn source_layer(&self) -> Option<&str> {
        Some(&self.object_class)
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        let (anchor, justify, offset) = self.placement.layout();
        vec![Layer {
            layout: Some(Layout {
                text_font: Some(vec![Font::RobotoBold]),
                text_anchor: Some(anchor),
                text_justify: Some(justify),
                text_field: Some(self.text()),
                text_offset: Some(offset.property()),
                text_size: Some(14.0),
                symbol_placement: Some(Placement::Point),
                ..Default::default()
            }),
            paint: Some(b.label_paint(Color::SNDG2, Color::DEPDW)),
            ..b.layer(b.key, LayerType::Symbol)
        }]
    }
}

/// The presentation library's `TE`/`TX` justification and offset, where offsets are in text body
/// units (positive y is down, as in MapLibre). One body is taken as 1.2 em, as in `ClearanceLabel`.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum NamePlacement {
    /// `HJUST=2 (right), VJUST=1 (bottom), XOFFS=-1, YOFFS=-1` - buoys, beacons, daymarks, LITFLT, LITVES.
    UpperLeft,
    /// `HJUST=3 (left), VJUST=1 (bottom), XOFFS=1, YOFFS=-1` - OFSPLF, PILBOP.
    UpperRight,
    /// BRIDGE's `TX(OBJNAM,3,1,2,'15110',1,0,...)` is the same spot as its `clr` text, which
    /// `ClearanceLabel` already occupies and grows upward from, so the name goes to the left of
    /// the bridge instead.
    Left,
    /// `HJUST=2 (right), VJUST=1 (bottom), XOFFS=2, YOFFS=0` - DISMAR.
    DistanceMark,
}

impl NamePlacement {
    fn layout(self) -> (Anchor, TextJustify, Offset) {
        match self {
            NamePlacement::UpperLeft => (
                Anchor::BottomRight,
                TextJustify::Right,
                Offset::Coord(-1.2, -1.2),
            ),
            NamePlacement::UpperRight => (
                Anchor::BottomLeft,
                TextJustify::Left,
                Offset::Coord(1.2, -1.2),
            ),
            NamePlacement::Left => (Anchor::Right, TextJustify::Right, Offset::Coord(-1.2, 0.0)),
            NamePlacement::DistanceMark => (
                Anchor::BottomRight,
                TextJustify::Right,
                Offset::Coord(2.4, 0.0),
            ),
        }
    }
}
