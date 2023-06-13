package io.madrona.njord.layers.attributehelpers

import io.madrona.njord.geo.symbols.intValues
import io.madrona.njord.model.ChartFeature

/**
 * https://openenc.com/control/symbols/LNDMRK/FUNCTN
 *
 * Attribute: Function
 *
 * Acronym: FUNCTN
 *
 * Code: 94
 *
 * ID	Meaning
 * 2	harbour-master's office
 * 3	custom office
 * 4	health office
 * 5	hospital
 * 6	post office
 * 7	hotel
 * 8	railway station
 * 9	police station
 * 10	water-police station
 * 11	pilot office
 * 12	pilot lookout
 * 13	bank office
 * 14	headquarters for district control
 * 15	transit shed/warehouse
 * 16	factory
 * 17	power station
 * 18	administrative
 * 19	educational facility
 * 20	church
 * 21	chapel
 * 22	temple
 * 23	pagoda
 * 24	shinto shrine
 * 25	buddhist temple
 * 26	mosque
 * 27	marabout
 * 28	lookout
 * 29	communication
 * 30	television
 * 31	radio
 * 32	radar
 * 33	light support
 * 34	microwave
 * 35	cooling
 * 36	observation
 * 37	timeball
 * 38	clock
 * 39	control
 * 40	airship mooring
 * 41	stadium
 * 42	bus station
 *
 * Attribute type: L
 */
enum class Functn {
    HARBOUR_MASTERS_OFFICE,
    CUSTOM_OFFICE,
    HEALTH_OFFICE,
    HOSPITAL,
    POST_OFFICE,
    HOTEL,
    RAILWAY_STATION,
    POLICE_STATION,
    WATER_POLICE_STATION,
    PILOT_OFFICE,
    PILOT_LOOKOUT,
    BANK_OFFICE,
    HEADQUARTERS_FOR_DISTRICT_CONTROL,
    TRANSIT_SHED_WAREHOUSE,
    FACTORY,
    POWER_STATION,
    ADMINISTRATIVE,
    EDUCATIONAL_FACILITY,
    CHURCH,
    CHAPEL,
    TEMPLE,
    PAGODA,
    SHINTO_SHRINE,
    BUDDHIST_TEMPLE,
    MOSQUE,
    MARABOUT,
    LOOKOUT,
    COMMUNICATION,
    TELEVISION,
    RADIO,
    RADAR,
    LIGHT_SUPPORT,
    MICROWAVE,
    COOLING,
    OBSERVATION,
    TIMEBALL,
    CLOCK,
    CONTROL,
    AIRSHIP_MOORING,
    STADIUM,
    BUS_STATION;

    companion object {

        fun ChartFeature.functn(): List<Functn> {
            return props.intValues("FUNCTN").mapNotNull {
                when (it) {
                    2 -> HARBOUR_MASTERS_OFFICE
                    3 -> CUSTOM_OFFICE
                    4 -> HEALTH_OFFICE
                    5 -> HOSPITAL
                    6 -> POST_OFFICE
                    7 -> HOTEL
                    8 -> RAILWAY_STATION
                    9 -> POLICE_STATION
                    10 -> WATER_POLICE_STATION
                    11 -> PILOT_OFFICE
                    12 -> PILOT_LOOKOUT
                    13 -> BANK_OFFICE
                    14 -> HEADQUARTERS_FOR_DISTRICT_CONTROL
                    15 -> TRANSIT_SHED_WAREHOUSE
                    16 -> FACTORY
                    17 -> POWER_STATION
                    18 -> ADMINISTRATIVE
                    19 -> EDUCATIONAL_FACILITY
                    20 -> CHURCH
                    21 -> CHAPEL
                    22 -> TEMPLE
                    23 -> PAGODA
                    24 -> SHINTO_SHRINE
                    25 -> BUDDHIST_TEMPLE
                    26 -> MOSQUE
                    27 -> MARABOUT
                    28 -> LOOKOUT
                    29 -> COMMUNICATION
                    30 -> TELEVISION
                    31 -> RADIO
                    32 -> RADAR
                    33 -> LIGHT_SUPPORT
                    34 -> MICROWAVE
                    35 -> COOLING
                    36 -> OBSERVATION
                    37 -> TIMEBALL
                    38 -> CLOCK
                    39 -> CONTROL
                    40 -> AIRSHIP_MOORING
                    41 -> STADIUM
                    42 -> BUS_STATION
                    else -> null
                }
            }
        }
    }
}
