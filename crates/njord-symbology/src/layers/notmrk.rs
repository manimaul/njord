//! Inland ENC object NOTMRK: registered (InlandLayers), but draws nothing yet (`Notmrk.kt`).

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Notmrk;

impl Layerable for Notmrk {
    fn layers(&self, _b: &mut LayerBuilder) -> Vec<Layer> {
        Vec::new()
    }
}
