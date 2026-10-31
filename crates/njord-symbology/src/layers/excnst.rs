//! Inland ENC object EXCNST: registered (InlandLayers), but draws nothing yet (`Excnst.kt`).

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Excnst;

impl Layerable for Excnst {
    fn layers(&self, _b: &mut LayerBuilder) -> Vec<Layer> {
        Vec::new()
    }
}
