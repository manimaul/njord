//! S-57 `FUNCTN` (function) values, a list attribute.

crate::layers::attributehelpers::list_attribute! {
    /// <https://njord.mxmariner.com/control/symbols/LNDMRK/FUNCTN>
    /// Attribute: Function
    /// Acronym: FUNCTN
    /// Code: 94
    /// ID	Meaning
    /// 2	harbour-master's office
    /// 3	custom office
    /// 4	health office
    /// 5	hospital
    /// 6	post office
    /// 7	hotel
    /// 8	railway station
    /// 9	police station
    /// 10	water-police station
    /// 11	pilot office
    /// 12	pilot lookout
    /// 13	bank office
    /// 14	headquarters for district control
    /// 15	transit shed/warehouse
    /// 16	factory
    /// 17	power station
    /// 18	administrative
    /// 19	educational facility
    /// 20	church
    /// 21	chapel
    /// 22	temple
    /// 23	pagoda
    /// 24	shinto shrine
    /// 25	buddhist temple
    /// 26	mosque
    /// 27	marabout
    /// 28	lookout
    /// 29	communication
    /// 30	television
    /// 31	radio
    /// 32	radar
    /// 33	light support
    /// 34	microwave
    /// 35	cooling
    /// 36	observation
    /// 37	timeball
    /// 38	clock
    /// 39	control
    /// 40	airship mooring
    /// 41	stadium
    /// 42	bus station
    /// Attribute type: L
    pub enum Functn = "FUNCTN" {
        HARBOUR_MASTERS_OFFICE = 2,
        CUSTOM_OFFICE = 3,
        HEALTH_OFFICE = 4,
        HOSPITAL = 5,
        POST_OFFICE = 6,
        HOTEL = 7,
        RAILWAY_STATION = 8,
        POLICE_STATION = 9,
        WATER_POLICE_STATION = 10,
        PILOT_OFFICE = 11,
        PILOT_LOOKOUT = 12,
        BANK_OFFICE = 13,
        HEADQUARTERS_FOR_DISTRICT_CONTROL = 14,
        TRANSIT_SHED_WAREHOUSE = 15,
        FACTORY = 16,
        POWER_STATION = 17,
        ADMINISTRATIVE = 18,
        EDUCATIONAL_FACILITY = 19,
        CHURCH = 20,
        CHAPEL = 21,
        TEMPLE = 22,
        PAGODA = 23,
        SHINTO_SHRINE = 24,
        BUDDHIST_TEMPLE = 25,
        MOSQUE = 26,
        MARABOUT = 27,
        LOOKOUT = 28,
        COMMUNICATION = 29,
        TELEVISION = 30,
        RADIO = 31,
        RADAR = 32,
        LIGHT_SUPPORT = 33,
        MICROWAVE = 34,
        COOLING = 35,
        OBSERVATION = 36,
        TIMEBALL = 37,
        CLOCK = 38,
        CONTROL = 39,
        AIRSHIP_MOORING = 40,
        STADIUM = 41,
        BUS_STATION = 42,
    }
}
