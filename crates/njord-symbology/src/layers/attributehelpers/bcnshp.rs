//! S-57 `BCNSHP` (beacon shape) values, a single-valued attribute.

crate::layers::attributehelpers::single_attribute! {
    /// <https://openenc.com/control/symbols/BCNLAT/BCNSHP>
    /// Enum
    pub enum Bcnshp = "BCNSHP" {
        STAKE_POLE_PERCH_POST = 1,
        WHITY = 2,
        BEACON_TOWER = 3,
        LATTICE_BEACON = 4,
        PILE_BEACON = 5,
        CAIRN = 6,
        BUOYANT_BEACON = 7,
    }
}
