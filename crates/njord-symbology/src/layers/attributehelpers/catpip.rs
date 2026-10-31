//! `attributehelpers/Catpip.kt`.

crate::layers::attributehelpers::list_attribute! {
    /// <https://openenc.com/control/symbols/PIPARE/CATPIP>
    /// List
    pub enum Catpip = "CATPIP" {
        OUTFALL_PIPE = 2,
        INTAKE_PIPE = 3,
        SEWER = 4,
        BUBBLER_SYSTEM = 5,
        SUPPLY_PIPE = 6,
    }
}
