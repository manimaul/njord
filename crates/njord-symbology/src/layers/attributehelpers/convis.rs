//! S-57 `CONVIS` (conspicuous, visually) values, a single-valued attribute.

crate::layers::attributehelpers::single_attribute! {
    /// <https://njord.mxmariner.com/control/symbols/SILTNK/CONVIS>
    /// Enum
    pub enum Convis = "CONVIS" {
        VISUAL_CONSPICUOUS = 1,
        NOT_VISUAL_CONSPICUOUS = 2,
    }
}
