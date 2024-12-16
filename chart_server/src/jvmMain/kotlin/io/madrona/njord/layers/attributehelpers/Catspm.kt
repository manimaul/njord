package io.madrona.njord.layers.attributehelpers

import io.madrona.njord.geo.symbols.intValues
import io.madrona.njord.model.ChartFeature

/**
 * https://openenc.com/control/symbols/BCNSPP/CATSPM
 *
 * Attribute: Category of special purpose mark
 *
 * Acronym: CATSPM
 *
 * Code: 66
 *
 * Enum
 */
enum class Catspm {
    FIRING_DANGER_AREA_MARK,
    TARGET_MARK,
    MARKER_SHIP_MARK,
    DEGAUSSING_RANGE_MARK,
    BARGE_MARK,
    CABLE_MARK,
    SPOIL_GROUND_MARK,
    OUTFALL_MARK,
    ODAS_OCEAN_DATA_ACQUISITION_SYSTEM,
    RECORDING_MARK,
    SEAPLANE_ANCHORAGE_MARK,
    RECREATION_ZONE_MARK,
    PRIVATE_MARK,
    MOORING_MARK,
    LANBY_LARGE_AUTOMATIC_NAVIGATIONAL_BUOY,
    LEADING_MARK,
    MEASURED_DISTANCE_MARK,
    NOTICE_MARK,
    TSS_MARK_TRAFFIC_SEPARATION_SCHEME,
    ANCHORING_PROHIBITED_MARK,
    BERTHING_PROHIBITED_MARK,
    OVERTAKING_PROHIBITED_MARK,
    TWO_WAY_TRAFFIC_PROHIBITED_MARK,
    REDUCED_WAKE_MARK,
    SPEED_LIMIT_MARK,
    STOP_MARK,
    GENERAL_WARNING_MARK,
    SOUND_SHIPS_SIREN_MARK,
    RESTRICTED_VERTICAL_CLEARENCE_MARK,
    MAXIMUM_VESSELS_DRAUGHT_MARK,
    RESTRICTED_HORIZONTAL_CLEARANCE_MARK,
    STRONG_CURRENT_WARNING_MARK,
    BERTHING_PERMITTED_MARK,
    OVERHEAD_POWER_CABLE_MARK,
    CHANNEL_EDGE_GRADIENT_MARK,
    TELEPHONE_MARK,
    FERRY_CROSSING_MARK,
    PIPLINE_MARK,
    ANCHORAGE_MARK,
    CLEARING_MARK,
    CONTROL_MARK,
    DIVING_MARK,
    REFUGE_BEACON,
    FOUL_GROUND_MARK,
    YACHTING_MARK,
    HELIPORT_MARK,
    GPS_MARK,
    SEAPLANE_LANDING_MARK,
    ENTRY_PROHIBITED_MARK,
    WORK_IN_PROGRESS_MARK,
    MARK_WITH_UNKNOWN_PURPOSE;

    companion object {

        fun ChartFeature.catspm(): List<Catspm> {
            return props.intValues("CATSPM").mapNotNull {
                when (it) {
                    1 -> FIRING_DANGER_AREA_MARK
                    2 -> TARGET_MARK
                    3 -> MARKER_SHIP_MARK
                    4 -> DEGAUSSING_RANGE_MARK
                    5 -> BARGE_MARK
                    6 -> CABLE_MARK
                    7 -> SPOIL_GROUND_MARK
                    8 -> OUTFALL_MARK
                    9 -> ODAS_OCEAN_DATA_ACQUISITION_SYSTEM
                    10 -> RECORDING_MARK
                    11 -> SEAPLANE_ANCHORAGE_MARK
                    12 -> RECREATION_ZONE_MARK
                    13 -> PRIVATE_MARK
                    14 -> MOORING_MARK
                    15 -> LANBY_LARGE_AUTOMATIC_NAVIGATIONAL_BUOY
                    16 -> LEADING_MARK
                    17 -> MEASURED_DISTANCE_MARK
                    18 -> NOTICE_MARK
                    19 -> TSS_MARK_TRAFFIC_SEPARATION_SCHEME
                    20 -> ANCHORING_PROHIBITED_MARK
                    21 -> BERTHING_PROHIBITED_MARK
                    22 -> OVERTAKING_PROHIBITED_MARK
                    23 -> TWO_WAY_TRAFFIC_PROHIBITED_MARK
                    24 -> REDUCED_WAKE_MARK
                    25 -> SPEED_LIMIT_MARK
                    26 -> STOP_MARK
                    27 -> GENERAL_WARNING_MARK
                    28 -> SOUND_SHIPS_SIREN_MARK
                    29 -> RESTRICTED_VERTICAL_CLEARENCE_MARK
                    30 -> MAXIMUM_VESSELS_DRAUGHT_MARK
                    31 -> RESTRICTED_HORIZONTAL_CLEARANCE_MARK
                    32 -> STRONG_CURRENT_WARNING_MARK
                    33 -> BERTHING_PERMITTED_MARK
                    34 -> OVERHEAD_POWER_CABLE_MARK
                    35 -> CHANNEL_EDGE_GRADIENT_MARK
                    36 -> TELEPHONE_MARK
                    37 -> FERRY_CROSSING_MARK
                    39 -> PIPLINE_MARK
                    40 -> ANCHORAGE_MARK
                    41 -> CLEARING_MARK
                    42 -> CONTROL_MARK
                    43 -> DIVING_MARK
                    44 -> REFUGE_BEACON
                    45 -> FOUL_GROUND_MARK
                    46 -> YACHTING_MARK
                    47 -> HELIPORT_MARK
                    48 -> GPS_MARK
                    49 -> SEAPLANE_LANDING_MARK
                    50 -> ENTRY_PROHIBITED_MARK
                    51 -> WORK_IN_PROGRESS_MARK
                    52 -> MARK_WITH_UNKNOWN_PURPOSE
                    else -> null
                }
            }
        }
    }
}
