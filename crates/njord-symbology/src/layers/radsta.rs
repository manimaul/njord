//! Geometry Primitives: Point
//!
//! Object: Radar station
//!
//! Acronym: RADSTA
//!
//! Code: 104
//!
//! S-52 lookups: `SY(POSGEN01)`, or for a radar surveillance station (CATRAS 2)
//! `SY(RDOSTA02);TE('ch %s','COMCHA',3,1,2,'15110',0,0,CHBLK,11)`.

use crate::layers::prelude::*;

const SURVEILLANCE: &str = "_SURV";

#[derive(Clone, Copy, Debug, Default)]
pub struct Radsta;

impl Layerable for Radsta {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        match int_value(&feature.props, "CATRAS") {
            Some(2) => {
                feature.point_symbol(Sprite::RDOSTA02);
                feature.props.insert(SURVEILLANCE.into(), true.into());
            }
            _ => feature.point_symbol(Sprite::POSGEN01),
        }
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.point_layer_from_symbol(PointSymbolOpts::default()),
            b.text_layer(
                json!(["concat", "ch ", ["to-string", ["get", "COMCHA"]]]),
                TextLayerOpts {
                    text_anchor: Anchor::BottomLeft,
                    text_justify: TextJustify::Left,
                    filter: Some(json!([
                        filters::ALL,
                        ["has", SURVEILLANCE],
                        ["has", "COMCHA"]
                    ])),
                    ..Default::default()
                },
            ),
        ]
    }
}
