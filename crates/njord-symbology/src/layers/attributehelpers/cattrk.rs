//! `attributehelpers/Cattrk.kt`.

crate::layers::attributehelpers::single_attribute! {
    /// <https://openenc.com/control/symbols/RECTRC/CATTRK>
    /// Enum
    pub enum Cattrk = "CATTRK" {
        BASED_ON_A_SYSTEM_OF_MARKS = 1,
        NOT_BASED_ON_A_SYSTEM_OF_MARKS = 2,
    }
}
