//! Inland ENC object EXCNST: registered in
//! [`inland_layers`](crate::layer_factory::inland_layers), but draws nothing yet.

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Excnst;

impl Layerable for Excnst {
    fn layers(&self, _b: &mut LayerBuilder) -> Vec<Layer> {
        Vec::new()
    }
}
