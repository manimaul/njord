//! S-57 `CATTRK` (category of recommended track) values, a single-valued attribute.

crate::layers::attributehelpers::single_attribute! {
    /// <https://njord.mxmariner.com/control/symbols/RECTRC/CATTRK>
    /// Enum
    pub enum Cattrk = "CATTRK" {
        BASED_ON_A_SYSTEM_OF_MARKS = 1,
        NOT_BASED_ON_A_SYSTEM_OF_MARKS = 2,
    }
}
