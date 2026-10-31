//! The style's background, NODTA (`Background.kt`).

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Background;

impl Layerable for Background {
    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![Layer {
            paint: Some(Paint {
                background_color: Some(b.color(Color::NODTA)),
                background_opacity: Some(1),
                ..Default::default()
            }),
            source: None,
            source_layer: None,
            ..b.layer("background", LayerType::Background)
        }]
    }
}
