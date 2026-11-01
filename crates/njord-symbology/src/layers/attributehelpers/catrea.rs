//! S-57 `CATREA` (category of restricted area) values, a list attribute.

crate::layers::attributehelpers::list_attribute! {
    /// <https://openenc.com/control/symbols/RESARE/CATREA>
    /// List
    pub enum Catrea = "CATREA" {
        OFFSHORE_SAFETY_ZONE = 1,
        NATURE_RESERVE = 4,
        BIRD_SANCTUARY = 5,
        GAME_PRESERVE = 6,
        SEAL_SANCTUARY = 7,
        DEGAUSSING_RANGE = 8,
        MILITARY_AREA = 9,
        HISTORIC_WRECK_AREA = 10,
        NAVIGATIONAL_AID_SAFETY_ZONE = 12,
        MINEFIELD = 14,
        SWIMMING_AREA = 18,
        WAITING_AREA = 19,
        RESEARCH_AREA = 20,
        DREDGING_AREA = 21,
        FISH_SANCTUARY = 22,
        ECOLOGICAL_RESERVE = 23,
        NO_WAKE_AREA = 24,
        SWINGING_AREA = 25,
    }
}
