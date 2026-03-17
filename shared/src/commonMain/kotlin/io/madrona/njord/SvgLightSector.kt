package io.madrona.njord

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin

fun buildSectorSvg(
    sectr1: Double,
    sectr2: Double,
    fillColor: String,
    lineColor: String,
    radius: Double = 80.0,
): String {
    //angles are reciprocal as the attribute has the angle from the perspective of the navigator to the light
    val sector1 = (sectr1 + 180.0) % 360.0
    val sector2 = (sectr2 + 180.0) % 360.0
    val cx = 100.0
    val cy = 100.0

    val a1 = sector1 * PI / 180.0
    val a2 = sector2 * PI / 180.0

    val x1 = cx + radius * sin(a1)
    val y1 = cy - radius * cos(a1)
    val x2 = cx + radius * sin(a2)
    val y2 = cy - radius * cos(a2)

    val span = (sector2 - sector1 + 360.0) % 360.0
    val arcElement = if (span == 0.0) {
        // full circle
        """<circle cx="$cx" cy="$cy" r="${radius.fmt()}" fill="none" stroke="$fillColor" stroke-width="4"/>"""
    } else {
        val largeArc = if (span > 180.0) 1 else 0
        """<path d="M ${x1.fmt()} ${y1.fmt()} A ${radius.fmt()} ${radius.fmt()} 0 $largeArc 1 ${x2.fmt()} ${y2.fmt()}"
      fill="none" stroke="$fillColor" stroke-width="4" stroke-linecap="butt"/>"""
    }

    val sectorLinesElement = if (span == 0.0) {
        ""
    } else {
        """  <line x1="$cx" y1="$cy" x2="${x1.fmt()}" y2="${y1.fmt()}"
        stroke="$lineColor" stroke-width="2"
        stroke-dasharray="4 6" stroke-linecap="butt"/>
  <line x1="$cx" y1="$cy" x2="${x2.fmt()}" y2="${y2.fmt()}"
        stroke="$lineColor" stroke-width="2"
        stroke-dasharray="4 6" stroke-linecap="butt"/>"""
    }

    return """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200">
  $arcElement
  $sectorLinesElement
</svg>"""
}

private fun Double.fmt(): String = round(this * 10).toLong().let { "${it / 10}.${abs(it % 10)}" }
