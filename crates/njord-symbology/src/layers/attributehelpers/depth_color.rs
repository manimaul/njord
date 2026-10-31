//! `attributehelpers/DepthColor.kt`: the depth-area colour bands.

use njord_model::Color;

#[allow(non_camel_case_types, clippy::upper_case_acronyms)]
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum DepthColor {
    DEEP_WATER,
    MEDIUM_DEPTH,
    SAFETY_DEPTH,
    VERY_SHALLOW,
    COVERS_UNCOVERS,
}

impl DepthColor {
    /// In declaration order.
    pub const ALL: &'static [DepthColor] = &[
        DepthColor::DEEP_WATER,
        DepthColor::MEDIUM_DEPTH,
        DepthColor::SAFETY_DEPTH,
        DepthColor::VERY_SHALLOW,
        DepthColor::COVERS_UNCOVERS,
    ];

    pub fn color(self) -> Color {
        match self {
            DepthColor::DEEP_WATER => Color::DEPDW,
            DepthColor::MEDIUM_DEPTH => Color::DEPMD,
            DepthColor::SAFETY_DEPTH => Color::DEPMS,
            DepthColor::VERY_SHALLOW => Color::DEPVS,
            DepthColor::COVERS_UNCOVERS => Color::DEPIT,
        }
    }
}
