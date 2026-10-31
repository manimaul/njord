//! `attributehelpers/Catobs.kt`.

crate::layers::attributehelpers::single_attribute! {
    /// <https://openenc.com/control/symbols/OBSTRN/CATOBS>
    /// Enum
    pub enum Catobs = "CATOBS" {
        SNAG_STUMP = 1,
        WELLHEAD = 2,
        DIFFUSER = 3,
        CRIB = 4,
        FISH_HAVEN = 5,
        FOUL_AREA = 6,
        FOUL_GROUND = 7,
        ICE_BOOM = 8,
        GROUND_TACKLE = 9,
        BOOM = 10,
    }
}
