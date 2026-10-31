//! `attributehelpers/Catlmk.kt`.

crate::layers::attributehelpers::list_attribute! {
    /// <https://openenc.com/control/symbols/LNDMRK/CATLMK>
    /// Attribute: Category of landmark
    /// Acronym: CATLMK
    /// Code: 35
    /// ID	Meaning
    /// 1	cairn
    /// 2	cemetery
    /// 3	chimney
    /// 4	dish aerial
    /// 5	flagstaff (flagpole)
    /// 6	flare stack
    /// 7	mast
    /// 8	windsock
    /// 9	monument
    /// 10	column (pillar)
    /// 11	memorial plaque
    /// 12	obelisk
    /// 13	statue
    /// 14	cross
    /// 15	dome
    /// 16	radar scanner
    /// 17	tower
    /// 18	windmill
    /// 19	windmotor
    /// 20	spire/minaret
    /// Attribute type: L
    pub enum Catlmk = "CATLMK" {
        CAIRN = 1,
        CEMETERY = 2,
        CHIMNEY = 3,
        DISH_AERIAL = 4,
        FLAGSTAFF_FLAGPOLE = 5,
        FLARE_STACK = 6,
        MAST = 7,
        WINDSOCK = 8,
        MONUMENT = 9,
        COLUMN_PILLAR = 10,
        MEMORIAL_PLAQUE = 11,
        OBELISK = 12,
        STATUE = 13,
        CROSS = 14,
        DOME = 15,
        RADAR_SCANNER = 16,
        TOWER = 17,
        WINDMILL = 18,
        WINDMOTOR = 19,
        SPIRE_MINARET = 20,
    }
}
