//! `attributehelpers/Catlnd.kt`.

crate::layers::attributehelpers::list_attribute! {
    /// <https://openenc.com/control/symbols/LNDRGN/CATLND>
    /// List
    pub enum Catlnd = "CATLND" {
        FEN = 1,
        MARSH = 2,
        MOOR_BOG = 3,
        HEATHLAND = 4,
        MOUNTAIN_RANGE = 5,
        LOWLANDS = 6,
        CANYON_LANDS = 7,
        PADDY_FIELD = 8,
        AGRICULTURAL_LAND = 9,
        SAVANNA_GRASSLAND = 10,
        PARKLAND = 11,
        SWAMP = 12,
        LANDSLIDE = 13,
        LAVA_FLOW = 14,
        SALT_PAN = 15,
        MORAINE = 16,
        CRATER = 17,
        CAVE = 18,
        ROCK_COLUMN_OR_PINNACLE = 19,
    }
}
