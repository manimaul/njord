//! The S-57 `COLOUR` attribute. A list attribute; the first colour is
//! the one most rules read.

crate::layers::attributehelpers::list_attribute! {
    /// COLOUR ID → meaning, per S-57 (INT 1 IP 11.1–11.8 for the light colours).
    pub enum Colour = "COLOUR" {
        White = 1,
        Black = 2,
        Red = 3,
        Green = 4,
        Blue = 5,
        Yellow = 6,
        Grey = 7,
        Brown = 8,
        Amber = 9,
        Violet = 10,
        Orange = 11,
        Magenta = 12,
        Pink = 13,
    }
}

impl Colour {
    /// The first known colour of the feature's `COLOUR` list, the one most rules read.
    pub fn first(props: &njord_model::JsonObject) -> Option<Colour> {
        Colour::of(props).into_iter().next()
    }
}
