//! S-57 `RESTRN` (restriction) values, a list attribute.

crate::layers::attributehelpers::list_attribute! {
    /// <https://openenc.com/control/symbols/RESARE/RESTRN>
    /// List
    pub enum Restrn = "RESTRN" {
        ANCHORING_PROHIBITED = 1,
        ANCHORING_RESTRICTED = 2,
        FISHING_PROHIBITED = 3,
        FISHING_RESTRICTED = 4,
        TRAWLING_PROHIBITED = 5,
        TRAWLING_RESTRICTED = 6,
        ENTRY_PROHIBITED = 7,
        ENTRY_RESTRICTED = 8,
        DREDGING_PROHIBITED = 9,
        DREDGING_RESTRICTED = 10,
        DIVING_PROHIBITED = 11,
        DIVING_RESTRICTED = 12,
        NO_WAKE = 13,
        AREA_TO_BE_AVOIDED = 14,
        CONSTRUCTION_PROHIBITED = 15,
    }
}
