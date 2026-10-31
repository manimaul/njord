//! Inland ENC object LKBSPT: registered (InlandLayers), but draws nothing yet (`Lkbspt.kt`).

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Lkbspt;

impl Layerable for Lkbspt {
    fn layers(&self, _b: &mut LayerBuilder) -> Vec<Layer> {
        Vec::new()
    }
}
