package io.madrona.njord.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.madrona.njord.geojson.Geometry
import io.madrona.njord.geojson.Point
import io.madrona.njord.model.Color
import io.madrona.njord.model.MapGeoJsonFeature
import io.madrona.njord.model.ThemeMode
import io.madrona.njord.viewmodel.asyncComplete
import io.madrona.njord.viewmodel.chartObjectsViewModel
import io.madrona.njord.viewmodel.complete
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.compose.web.ExperimentalComposeWebSvgApi
import org.jetbrains.compose.web.dom.*
import org.jetbrains.compose.web.svg.*

private val skipKeys = setOf("SY", "AP", "AC", "LC", "CID")
private fun JsonElement.valueStr() = (this as? JsonPrimitive)?.content ?: toString()

@Composable
fun ChartQuery(
    content: List<MapGeoJsonFeature>,
) {
    val state by chartObjectsViewModel.flow.collectAsState()
    val colorState by chartObjectsViewModel.colorSelectionFlow.collectAsState()
    asyncComplete(chartObjectsViewModel, state.s57Objects, colorState.themeColors) { obj, colors ->
        Accordion(content) { builder ->
            val feature = builder.item
            builder.title =
                obj[feature.sourceLayer]?.objectClass?.let { "${feature.sourceLayer} - ($it)" } ?: feature.sourceLayer
            builder.body = {
                Div {
                    P {
                        B {
                            Text("Geometry: ")
                        }
                        Text("${feature.geometry?.type ?: "?"}")
                    }
                    LatLng(feature.geometry)
                    feature.properties["SORIND"]?.valueStr()?.let { symbol ->
                        P {
                            B { Text("Chart: ") }
                            Text(symbol)
                        }
                    }

                    feature.properties["SY"]?.valueStr()?.let { symbol ->
                        P {
                            B { Text("Symbol: ") }
                            Text("SY $symbol ")
                            Img(src = "/v1/icon/${symbol}.png", alt = symbol)
                        }
                    }
                    feature.properties["AP"]?.valueStr()?.let { symbol ->
                        P {
                            B { Text("Area Pattern: ") }
                            Text("AP $symbol ")
                            Img(src = "/v1/icon/${symbol}.png", alt = symbol)
                        }
                    }
                    feature.properties["AC"]?.valueStr()?.let { symbol ->
                        DisplayColor("AC", "Area Color", symbol, colorState.mode, colors)
                    }
                    feature.properties["LC"]?.valueStr()?.let { symbol ->
                        DisplayColor("LC", "Line Color", symbol, colorState.mode, colors)
                    }
                    feature.properties["LP"]?.valueStr()?.let { symbol ->
                        P {
                            B { Text("Line Pattern: ") }
                            Text("LP $symbol ")
                            Img(src = "/v1/icon/${symbol}.png", alt = symbol)
                        }
                    }
                }

                state.attributes.complete(chartObjectsViewModel) { att ->
                    feature.properties.keys.filter { !skipKeys.contains(it) }.forEach { key ->
                        att[key]?.let {
                            Li {
                                B { Text(key) }
                                Text(" - ${it.attribute}")
                                Text(": ")
                                I { Text("${feature.properties[key]}") }
                            }

                        } ?: Li {
                            Text(key)
                            Text(":")
                            Text("${feature.properties[key]}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DisplayColor(
    key: String,
    desc: String,
    symbol: String,
    mode: ThemeMode,
    colors: Map<Color, String>) {
    colors[Color.valueOf(symbol)]?.let { hex ->
        Li {
            B { Text(key) }
            Text(" - $desc $symbol ($mode) $hex ")
            SvgCircle(hex)
        }
    } ?: Li {
        B { Text(key) }
        Text(" ERROR! finding $desc $symbol")
    }
}

@Composable
fun LatLng(geo: Geometry?) {
    geo?.let { it as? Point }?.let {
        P {
            B {
                Text("Position: ")
            }
            Text("${it.position.latitude}, ${it.position.longitude}")
        }
    }
}

@OptIn(ExperimentalComposeWebSvgApi::class)
@Composable
fun SvgCircle(color: String) {
    Svg(attrs = {
        width(20)
        height(20)
    }) {
        Circle(cx = 10, cy = 10, r = 10, attrs = { fill(color) })
    }
}