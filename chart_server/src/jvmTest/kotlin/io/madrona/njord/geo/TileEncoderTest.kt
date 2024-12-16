package io.madrona.njord.geo

import io.madrona.njord.ext.json
import io.madrona.njord.model.ChartFeature
import no.ecc.vectortile.VectorTileEncoder
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory

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
                "key0" to 1.j,
                "key1" to 1.0f.j,
                "key2" to 1.0.j,
                "key2" to "value".j,
                "key3" to listOf("a".j, "b".j, "c".j).j
                )
        )
        val geo =GeometryFactory().createPoint(Coordinate(0.0, 0.0))
        encoder.addFeature(feature.layer, feature.props, geo)
        encoder.encode()
    }
}