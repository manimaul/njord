//! S-57 `CATLAM` (category of lateral mark) values, a single-valued attribute.

crate::layers::attributehelpers::single_attribute! {
    /// <https://njord.mxmariner.com/control/symbols/BOYLAT/CATLAM>
    /// Attribute: Category of lateral mark
    /// Acronym: CATLAM
    /// Code: 36
    /// ID	Meaning
    /// 1	port-hand lateral mark
    /// 2	starboard-hand lateral mark
    /// 3	preferred channel to starboard lateral mark
    /// 4	preferred channel to port lateral mark
    /// Attribute type: E
    pub enum Catlam = "CATLAM" {
        PORT_HAND_LATERAL_MARK = 1,
        STARBOARD_HAND_LATERAL_MARK = 2,
        PREFERRED_CHANNEL_TO_STARBOARD_LATERAL_MARK = 3,
        PREFERRED_CHANNEL_TO_PORT_LATERAL_MARK = 4,
    }
}
