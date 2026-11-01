//! S-57 `CATCAM` (category of cardinal mark) values, a single-valued attribute.

crate::layers::attributehelpers::single_attribute! {
    /// <https://njord.mxmariner.com/control/symbols/BCNCAR/CATCAM>
    /// Enum
    pub enum Catcam = "CATCAM" {
        NORTH_CARDINAL_MARK = 1,
        EAST_CARDINAL_MARK = 2,
        SOUTH_CARDINAL_MARK = 3,
        WEST_CARDINAL_MARK = 4,
    }
}
