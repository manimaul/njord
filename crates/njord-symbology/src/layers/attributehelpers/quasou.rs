//! `attributehelpers/Quasou.kt`.

crate::layers::attributehelpers::list_attribute! {
    /// <https://openenc.com/control/symbols/OBSTRN/QUASOU>
    /// List
    pub enum Quasou = "QUASOU" {
        DEPTH_KNOWN = 1,
        DEPTH_UNKNOWN = 2,
        DOUBTFUL_SOUNDING = 3,
        UNRELIABLE_SOUNDING = 4,
        NO_BOTTOM_FOUND_AT_VALUE_SHOWN = 5,
        LEAST_DEPTH_KNOWN = 6,
        LEAST_DEPTH_UNKNOWN_SAFE_CLEARANCE_AT_VALUE_SHOWN = 7,
        VALUE_REPORTED_NOT_SURVEYED = 8,
        VALUE_REPORTED_NOT_CONFIRMED = 9,
        MAINTAINED_DEPTH = 10,
        NOT_REGULARLY_MAINTAINED = 11,
    }
}
