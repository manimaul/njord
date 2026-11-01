//! S-57 `CATBRG` (category of bridge) values, a list attribute.

crate::layers::attributehelpers::list_attribute! {
    /// <https://njord.mxmariner.com/control/symbols/BRIDGE/CATBRG>
    /// List
    pub enum Catbrg = "CATBRG" {
        FIXED_BRIDGE = 1,
        OPENING_BRIDGE = 2,
        SWING_BRIDGE = 3,
        LIFTING_BRIDGE = 4,
        BASCULE_BRIDGE = 5,
        PONTOON_BRIDGE = 6,
        DRAW_BRIDGE = 7,
        TRANSPORTER_BRIDGE = 8,
        FOOTBRIDGE = 9,
        VIADUCT = 10,
        AQUEDUCT = 11,
        SUSPENSION_BRIDGE = 12,
    }
}
