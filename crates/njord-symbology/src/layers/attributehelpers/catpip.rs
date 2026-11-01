//! S-57 `CATPIP` (category of pipeline/pipe) values, a list attribute.

crate::layers::attributehelpers::list_attribute! {
    /// <https://njord.mxmariner.com/control/symbols/PIPARE/CATPIP>
    /// List
    pub enum Catpip = "CATPIP" {
        OUTFALL_PIPE = 2,
        INTAKE_PIPE = 3,
        SEWER = 4,
        BUBBLER_SYSTEM = 5,
        SUPPLY_PIPE = 6,
    }
}
