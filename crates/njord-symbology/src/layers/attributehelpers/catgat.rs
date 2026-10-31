//! `attributehelpers/Catgat.kt`.

crate::layers::attributehelpers::single_attribute! {
    /// <https://openenc.com/control/symbols/GATCON/CATGAT>
    /// Enum
    pub enum Catgat = "CATGAT" {
        FLOOD_BARRAGE_GATE = 2,
        CAISSON = 3,
        LOCK_GATE = 4,
        DYKE_GATE = 5,
    }
}
