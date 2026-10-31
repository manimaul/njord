//! `attributehelpers/Catwrk.kt`.

crate::layers::attributehelpers::single_attribute! {
    /// <https://openenc.com/control/symbols/WRECKS/CATWRK>
    /// Enum
    pub enum Catwrk = "CATWRK" {
        NON_DANGEROUS_WRECK = 1,
        DANGEROUS_WRECK = 2,
        DISTRIBUTED_REMAINS_OF_WRECK = 3,
        WRECK_SHOWING_MAST_MASTS = 4,
        WRECK_SHOWING_ANY_PORTION_OF_HULL_OR_SUPERSTRUCTURE = 5,
    }
}
