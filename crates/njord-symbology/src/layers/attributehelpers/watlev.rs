//! S-57 `WATLEV` (water level effect) values, a single-valued attribute.

crate::layers::attributehelpers::single_attribute! {
    /// <https://njord.mxmariner.com/control/symbols/OBSTRN/WATLEV>
    /// Enum
    pub enum Watlev = "WATLEV" {
        PARTLY_SUBMERGED_AT_HIGH_WATER = 1,
        ALWAYS_DRY = 2,
        ALWAYS_UNDER_WATER_SUBMERGED = 3,
        COVERS_AND_UNCOVERS = 4,
        AWASH = 5,
        SUBJECT_TO_INUNDATION_OR_FLOODING = 6,
        FLOATING = 7,
    }
}
