//! S-57 `CONRAD` (conspicuous, radar) values, a single-valued attribute.

crate::layers::attributehelpers::single_attribute! {
    /// <https://njord.mxmariner.com/control/symbols/CONVYR/CONRAD>
    /// Enum
    pub enum Conrad = "CONRAD" {
        RADAR_CONSPICUOUS = 1,
        NOT_RADAR_CONSPICUOUS = 2,
        RADAR_CONSPICUOUS_HAS_RADAR_REFLECTOR = 3,
    }
}
