//! The bearing label shared by NAVLNE, RADLNE, RECTRC, RCRTCL and DWRTCL: the
//! S-52 lookups' `TE('%03.0lf deg','ORIENT',...)`.
//!
//! Pre-rendered into the tile rather than composed by a style expression because the style spec
//! has no zero-padding, and a bearing has no unit preference to keep the tile agnostic of.

use crate::layers::prelude::*;

/// The feature prop carrying the bearing text.
pub const BEARING: &str = "_BRG";

/// `ChartFeature.bearingLabel()`: `_BRG` from ORIENT, when present.
pub fn bearing_label(feature: &mut ChartFeature) {
    if let Some(orient) = double_value(&feature.props, "ORIENT") {
        feature
            .props
            .insert(BEARING.into(), format_bearing(orient).into());
    }
}

/// Formats a bearing as `%03.0lf deg`: whole degrees, zero padded to three digits.
///
/// Rounds half up, and pads the integer's text to three characters (so `-5` would read `0-5`).
pub fn format_bearing(orient: f64) -> String {
    let whole = (orient + 0.5).floor() as i32;
    format!("{:0>3} deg", whole.to_string())
}

/// `Layerable.bearingLabelLayer(options)`.
pub fn bearing_label_layer(b: &mut LayerBuilder) -> Layer {
    b.line_layer_with_label(
        &Label::Property(BEARING.into()),
        LineLabelOpts {
            label_color: Color::CHBLK,
            highlight_color: Color::CHWHT,
        },
    )
}
