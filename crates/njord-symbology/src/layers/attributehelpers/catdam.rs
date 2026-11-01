//! S-57 `CATDAM` (category of dam) values, a single-valued attribute.

crate::layers::attributehelpers::single_attribute! {
    pub enum Catdam = "CATDAM" {
        WEIR = 1,
        DAM = 2,
        FLOOD_BARRAGE = 3,
    }
}
