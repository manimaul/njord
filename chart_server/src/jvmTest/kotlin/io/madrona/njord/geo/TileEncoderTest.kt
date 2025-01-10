package io.madrona.njord.geo

import io.madrona.njord.ext.json
import io.madrona.njord.model.ChartFeature
import no.ecc.vectortile.VectorTileEncoder
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import kotlin.test.Test

class TileEncoderTest {

    private val encoder: VectorTileEncoder = VectorTileEncoder(4096, 8, false, true, 0.1)

    @Test
    fun addCharts() {
        val strlist = listOf("a", "b", "c").toString()
        val feature = ChartFeature(
            layer = "foo",
            geomWKB = null,
            props = mutableMapOf(
                "key" to true.json,
                "key0" to 1.json,
                "key1" to 1.0f.json,
                "key2" to 1.0.json,
                "key2" to "value".json,
                "key3" to listOf("a", "b", "c").json
                )
        )
        val geo =GeometryFactory().createPoint(Coordinate(0.0, 0.0))
        encoder.addFeature(feature.layer, feature.props, geo)
        encoder.encode()
    }
}