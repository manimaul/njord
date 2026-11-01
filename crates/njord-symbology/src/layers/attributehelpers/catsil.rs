//! S-57 `CATSIL` (category of silo/tank) values, a single-valued attribute.

crate::layers::attributehelpers::single_attribute! {
    /// <https://njord.mxmariner.com/control/symbols/SILTNK/CATSIL>
    /// Enum
    /// 1	silo in general
    /// 2	tank in general
    /// 3	grain elevator
    /// 4	water tower
    pub enum Catsil = "CATSIL" {
        SILO_IN_GENERAL = 1,
        TANK_IN_GENERAL = 2,
        GRAIN_ELEVATOR = 3,
        WATER_TOWER = 4,
    }
}
