package io.madrona.njord

import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.madrona.njord.model.ColorLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.gdal.osr.SpatialReference

object Singletons {

    val objectMapper = jsonMapper {
        addModule(kotlinModule())
    }

    val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val config = ChartsConfig()

    val colorLibrary: ColorLibrary = ColorLibrary()

    val wgs84SpatialRef = SpatialReference("""GEOGCS["WGS 84",DATUM["WGS_1984",SPHEROID["WGS 84",6378137,298.257223563,AUTHORITY["EPSG","7030"]],AUTHORITY["EPSG","6326"]],PRIMEM["Greenwich",0,AUTHORITY["EPSG","8901"]],UNIT["degree",0.0174532925199433,AUTHORITY["EPSG","9122"]],AXIS["Latitude",NORTH],AXIS["Longitude",EAST],AUTHORITY["EPSG","4326"]]""")
}