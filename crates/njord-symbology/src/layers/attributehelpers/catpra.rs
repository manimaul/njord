//! `attributehelpers/Catpra.kt`.

crate::layers::attributehelpers::single_attribute! {
    /// <https://openenc.com/control/symbols/PRDARE/CATPRA>
    /// Enum
    pub enum Catpra = "CATPRA" {
        QUARRY = 1,
        MINE = 2,
        STOCKPILE = 3,
        POWER_STATION_AREA = 4,
        REFINERY_AREA = 5,
        TIMBER_YARD = 6,
        FACTORY_AREA = 7,
        TANK_FARM = 8,
        WIND_FARM = 9,
    }
}
