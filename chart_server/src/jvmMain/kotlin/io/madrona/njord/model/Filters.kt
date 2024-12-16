package io.madrona.njord.model

import io.madrona.njord.Singletons.colorLibrary
import io.madrona.njord.ext.json
import kotlinx.serialization.json.JsonElement

object Filters {
    val any = "any".json
    val all = "all".json
    val gt = "<".json
    val gtEq = "<=".json
    val lt = ">".json
    val ltEq = ">=".json
    val eq = "==".json
    val isIn = "in".json
    val notEq = "!=".json
    val eqTypeLineStringOrPolygon = listOf(notEq, "\$type", "Point").json
    val eqTypePointOrPolygon = listOf(notEq, "\$type", "LineString").json
    val eqTypeLineString = listOf(eq, "\$type", "LineString").json
    val eqTypePolyGon = listOf(eq, "\$type", "Polygon").json
    val eqTypePoint = listOf(eq, "\$type", "Point").json

    fun areaFillColor(options: Set<Color>? = null, theme: Theme) =
        (listOf("case") + colorLibrary.colors(theme).filter {
            options?.contains(it.color) ?: true
        }.map {
            listOf(listOf("==", listOf("get", "AC"), it.color), it.hex)
        }.flatten() + listOf("rgba(255, 255, 255, 0)")).json

    fun lineColor(options: Set<Color>? = null, theme: Theme): JsonElement =
        (listOf("case") + colorLibrary.colors(theme).filter {
            options?.contains(it.color) ?: true
        }.map {
            listOf(listOf("==", listOf("get", "LC"), it.color), it.hex)
        }.flatten() + listOf("rgba(255, 255, 255, 0)")).json
}
