//! Debug tile overlay, registered only with `SymbologyConfig::debug_tile`.

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Debug;

impl Layerable for Debug {
    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            Layer {
                filter: Some(json!(["any", filters::eq_type_point()])),
                layout: Some(Layout {
                    text_font: Some(vec![Font::RobotoBold]),
                    text_anchor: Some(Anchor::Center),
                    text_justify: Some(TextJustify::Center),
                    text_field: Some(json!(["get", "DMSG"])),
                    text_allow_overlap: Some(true),
                    text_ignore_placement: Some(true),
                    text_size: Some(11.0),
                    symbol_placement: Some(Placement::Point),
                    ..Default::default()
                }),
                ..b.layer("debug-msg", LayerType::Symbol)
            },
            Layer {
                filter: Some(json!([filters::ALL, filters::eq_type_line_string()])),
                paint: Some(Paint {
                    line_color: Some(b.color_json(Color::CSTLN)),
                    line_width: Some(5.0),
                    ..Default::default()
                }),
                ..b.layer("debug-line", LayerType::Line)
            },
        ]
    }
}
