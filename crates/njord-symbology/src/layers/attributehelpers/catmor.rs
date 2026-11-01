//! S-57 `CATMOR` (category of mooring/warping facility) values, a single-valued attribute.
//!
//! Also builds `==` / `!=` style filters on it ([`Catmor::filter_eq`], [`Catmor::filter_not_eq`]).

crate::layers::attributehelpers::single_attribute! {
    /// <https://njord.mxmariner.com/control/symbols/MORFAC/CATMOR>
    /// Enum
    pub enum Catmor = "CATMOR" {
        DOLPHIN = 1,
        DEVIATION_DOLPHIN = 2,
        BOLLARD = 3,
        TIE_UP_WALL = 4,
        POST_OR_PILE = 5,
        CHAIN_WIRE_CABLE = 6,
        MOORING_BUOY = 7,
    }
}
impl Catmor {
    /// `["==", "CATMOR", id]`.
    pub fn filter_eq(self) -> serde_json::Value {
        serde_json::json!([crate::filters::EQ, "CATMOR", self.id()])
    }

    /// `["!=", "CATMOR", id]`.
    pub fn filter_not_eq(self) -> serde_json::Value {
        serde_json::json!([crate::filters::NOT_EQ, "CATMOR", self.id()])
    }
}
