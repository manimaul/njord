//! Inland ENC object HRBBSN: registered (InlandLayers), but draws nothing yet (`Hrbbsn.kt`).

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Hrbbsn;

impl Layerable for Hrbbsn {
    fn layers(&self, _b: &mut LayerBuilder) -> Vec<Layer> {
        Vec::new()
    }
}
