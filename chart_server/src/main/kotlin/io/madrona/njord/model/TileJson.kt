package io.madrona.njord.model

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.ALWAYS)
data class TileJson(
        val bounds: List<Double> = listOf(
                -180.0, -85.05112877980659,
                180.0,
                85.0511287798066
        ),
        val format: String = "pbf",
        val maxzoom: Float = 30F,
        val minzoom: Float = 0F,
        val scheme: String = "xyz",
        val tilejson: String = "2.2.0",
        val tiles: List<String> = listOf(
                "http://localhost:9000/v1/tile/{z}/{x}/{y}"
        )
)

fun tileJson(vararg hosts: String) = TileJson(
        tiles = hosts.map {
            "https://${it}/v1/tile/{z}/{x}/{y}"
        }
)

