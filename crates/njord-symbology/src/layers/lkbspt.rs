//! Inland ENC object LKBSPT: registered in
//! [`inland_layers`](crate::layer_factory::inland_layers), but draws nothing yet.

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Lkbspt;

impl Layerable for Lkbspt {
    fn layers(&self, _b: &mut LayerBuilder) -> Vec<Layer> {
        Vec::new()
    }
}
