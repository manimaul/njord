package io.madrona.njord.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.madrona.njord.geojson.Geometry
import io.madrona.njord.geojson.Point
import io.madrona.njord.model.Color
import io.madrona.njord.model.MapGeoJsonFeature
import io.madrona.njord.model.ThemeMode
import io.madrona.njord.viewmodel.ChartInfoViewModel
import io.madrona.njord.viewmodel.asyncComplete
import io.madrona.njord.viewmodel.chartObjectsViewModel
import io.madrona.njord.viewmodel.complete
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.compose.web.ExperimentalComposeWebSvgApi
import org.jetbrains.compose.web.dom.*
import org.jetbrains.compose.web.svg.*

private val skipKeys = setOf("SY", "AP", "AC", "LC", "LP")
private fun JsonElement.valueStr() = (this as? JsonPrimitive)?.content ?: toString()

@Composable
fun ChartQuery(
    content: List<MapGeoJsonFeature>,
) {
    val state by chartObjectsViewModel.flow.collectAsState()
    val colorState by chartObjectsViewModel.colorSelectionFlow.collectAsState()
    val charts =
        content.mapNotNull { it.properties["CID"]?.valueStr() }.distinct().map { ChartInfoViewModel(it) }
    println("num charts = ${charts.size}")
    asyncComplete(chartObjectsViewModel, state.s57Objects, colorState.themeColors) { obj, colors ->
        Accordion(charts, content, { builder ->
            builder.title = "Chart id:(${builder.item.id})"
            builder.body = { ChartInfo(builder.item) }
        }, { builder ->
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
                    B { Text("Properties:") }
                    feature.properties["SY"]?.valueStr()?.let { symbol ->
                        DisplaySymbol("SY", "Symbol", symbol)
                    }
                    feature.properties["AP"]?.valueStr()?.let { symbol ->
                        DisplaySymbol("AP", "Area Pattern", symbol)
                    }
                    feature.properties["AC"]?.valueStr()?.let { symbol ->
                        DisplayColor("AC", "Area Color", symbol, colorState.mode, colors)
                    }
                    feature.properties["LC"]?.valueStr()?.let { symbol ->
                        DisplayColor("LC", "Line Color", symbol, colorState.mode, colors)
                    }
                    feature.properties["LP"]?.valueStr()?.let { symbol ->
                        DisplaySymbol("LP", "Line Pattern", symbol)
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
        })
    }
}

@Composable
fun DisplaySymbol(
    key: String,
    desc: String,
    symbol: String,
) {
    Li {
        B { Text(key) }
        Text(" - $desc $symbol ")
        Img(src = "/v1/icon/${symbol}.png", alt = symbol)
    }
}

@Composable
fun DisplayColor(
    key: String,
    desc: String,
    symbol: String,
    mode: ThemeMode,
    colors: Map<Color, String>
) {
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
fun ChartInfo(
    viewModel: ChartInfoViewModel
) {
    val state by viewModel.flow.collectAsState()
    state.info.complete(viewModel) { chart ->
        Ol {
            Li {
                //todo: link to full chart information
                B { Text("id: ") }
                Text(chart.id.toString())
            }
            Li {
                B { Text("File name: ") }
                Text(chart.fileName)
            }
            Li {
                B { Text("Scale: ") }
                Text(chart.scale.toString())
            }
            Li {
                B { Text("Updated: ") }
                Text(chart.updated)
            }
            Li {
                B { Text("Issued: ") }
                Text(chart.issued)
            }
            Li {
                B { Text("Calculated zoom: ") }
                Text(chart.zoom.toString())
            }
            Li {
                B { Text("Feature count: ") }
                Text(chart.featureCount.toString())
            }
        }
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