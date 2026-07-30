package io.madrona.njord.model

import io.madrona.njord.geojson.GeoJsonObject
import io.madrona.njord.geojson.Point
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class RegionManifestEntry(
    val name: String,
    val description: String,
    val coverage: String,
    val coverageGeo: GeoJsonObject,
    val labelPoint: Point? = null,
    val archive: String?,
    val createdAt: Instant?,
)
