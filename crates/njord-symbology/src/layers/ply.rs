//! The `PLY` source layer ([`extra_layers`](crate::layer_factory::extra_layers)): a CURSR outline
//! at zooms 0–9.

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Ply;

impl Layerable for Ply {
    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![Layer {
            filter: Some(json!([filters::ANY, filters::eq_type_line_string()])),
            paint: Some(Paint {
                line_color: Some(b.color_json(Color::CURSR)),
                line_width: Some(2.0),
                ..Default::default()
            }),
            min_zoom: Some(0),
            max_zoom_exclusive: Some(10),
            ..b.layer(format!("{}_line", b.key), LayerType::Line)
        }]
    }
}
