//! `attributehelpers/Catfif.kt`.

crate::layers::attributehelpers::single_attribute! {
    /// <https://openenc.com/control/symbols/FSHFAC/CATFIF>
    /// Attribute: Category of fishing facility
    /// Acronym: CATFIF
    /// Code: 26
    /// Enum
    pub enum Catfif = "CATFIF" {
        FISHING_STAKE = 1,
        FISH_TRAP = 2,
        FISH_WEIR = 3,
        TUNNY_NET = 4,
    }
}
