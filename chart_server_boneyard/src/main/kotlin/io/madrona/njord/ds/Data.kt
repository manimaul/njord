package io.madrona.njord.ds


interface FileEntry {
    val id: Int?
    val name: String?
    val file: String?
    val type: Int?
    val checksum: String?
    val depths: String?
    val datum: String?
    val updated: String?
    val issue_date: String?
    val scale: Int?
    val z: Int?
    val min_x: Int?
    val max_x: Int?
    val min_y: Int?
    val max_y: Int?
    val outline_wkt: String?
    val full_eval: Boolean?
}

enum class FileType {
    S57,

    /*
    http://www.naturalearthdata.com/downloads/10m-physical-vectors/
     */
    SHP_NE_LAND,
    SHP_NE_ISLANDS,
    SHP_NE_REEFS,
    SHP_NE_OCEAN,
    SHP_NE_RIVERS,
    SHP_NE_LAKES_RESERVOIRS,
    SHP_NE_LAKES_LABELS,
    SHP_NE_LAKES_PLAYAS,
    SHP_NE_LAKES_ICE_SHELVES,
    SHP_NE_LAKES_GLACIATED,
}

data class FileRecord(
        override val id: Int? = null,
        override val name: String? = null,
        override val file: String? = null,
        override val type: Int? = null,
        override val checksum: String? = null,
        override val depths: String? = null,
        override val datum: String? = null,
        override val updated: String? = null,
        override val issue_date: String? = null,
        override val scale: Int? = null,
        override val z: Int? = null,
        override val min_x: Int? = null,
        override val max_x: Int? = null,
        override val min_y: Int? = null,
        override val max_y: Int? = null,
        override val outline_wkt: String? = null,
        override val full_eval: Boolean? = null
) : FileEntry