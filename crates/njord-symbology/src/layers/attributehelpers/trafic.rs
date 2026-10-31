//! `attributehelpers/Trafic.kt`.

crate::layers::attributehelpers::single_attribute! {
    /// <https://openenc.com/control/symbols/DWRTPT/TRAFIC>
    /// Attribute: Traffic flow
    /// Acronym: TRAFIC
    /// Code: 172
    /// ID	Meaning
    /// 1	inbound
    /// 2	outbound
    /// 3	one-way
    /// 4	two-way
    pub enum Trafic = "TRAFIC" {
        INBOUND = 1,
        OUTBOUND = 2,
        ONE_WAY = 3,
        TWO_WAY = 4,
    }
}
