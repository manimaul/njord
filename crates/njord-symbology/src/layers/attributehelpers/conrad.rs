//! `attributehelpers/Conrad.kt`.

crate::layers::attributehelpers::single_attribute! {
    /// <https://openenc.com/control/symbols/CONVYR/CONRAD>
    /// Enum
    pub enum Conrad = "CONRAD" {
        RADAR_CONSPICUOUS = 1,
        NOT_RADAR_CONSPICUOUS = 2,
        RADAR_CONSPICUOUS_HAS_RADAR_REFLECTOR = 3,
    }
}
