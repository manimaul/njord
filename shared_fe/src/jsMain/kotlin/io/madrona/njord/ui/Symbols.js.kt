package io.madrona.njord.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import io.madrona.njord.model.S57Object
import io.madrona.njord.viewmodel.chartObjectsViewModel
import io.madrona.njord.viewmodel.complete
import io.madrona.njord.viewmodel.routeViewModel
import io.madrona.njord.viewmodel.symbolViewModel
import io.madrona.njord.viewmodel.utils.Uninitialized
import org.jetbrains.compose.web.dom.*

@Composable
fun Symbols() {
    Div(attrs = { classes("row") }) {
        Div(attrs = { classes("col") }) { SymbolObjects() }
        Div(attrs = { classes("col") }) { SymbolAttributes() }
    }
}

@Composable
fun SymbolAttributes() {
    val state by symbolViewModel.expectedInputFlow.collectAsState(Uninitialized)
    H2 { Text("S57 Attribute") }
    state.complete(
        viewModel = symbolViewModel,
        initial = {
            Span { Text("Attribute not selected") }
        },
        complete = { value ->
            P {
                B { Text("Attribute: ") }
                Text(value.att.attribute)
            }
            P {
                B { Text("Acronym: ") }
                Text(value.att.acronym)
            }
            P {
                B { Text("Code: ") }
                Text("${value.att.code}")
            }
            P {
                B { Text("Attribute Type: ") }
                Text(value.att.attributeType)
            }
            P {
                B { Text("Attribute Class: ") }
                Text(value.att.cls)
            }
            Ul {
                Li { Text("Attribute type: one-character code for the attribute type - there are six possible types:") }
                Li { Text("Enumerated (\"E\") - the expected input is a number selected from a list of predefined attribute values; exactly one value must be chosen.") }
                Li { Text("List (\"L\") - the expected input is a list of one or more numbers selected from a list of pre-defined attribute values.") }
                Li { Text("Float (\"F\") - the expected input is a floating point numeric value with defined range, resolution, units and format.") }
                Li { Text("Integer (\"I\") - the expected input is an integer numeric value with defined range, units and format.") }
                Li { Text("Coded String (\"A\") - the expected input is a string of ASCII characters in a predefined format; the information is encoded according to defined coding systems.") }
                Li { Text("Free Text (\"S\") - the expected input is a free-format alphanumeric string; it may be a file name which points to a text or graphic file.") }
            }
            value.inputs.takeIf { it.isNotEmpty() }?.let { inputs ->
                Table(attrs = { classes("table") }) {
                    Thead {
                        Tr {
                            Th { Text("ID") }
                            Th { Text("Meaning") }
                        }
                    }
                    Tbody {
                        inputs.forEach { input ->
                            Tr {
                                Td { Text("${input.id}") }
                                Td { Text(input.meaning) }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun SymbolObjects() {
    val chartObjects by chartObjectsViewModel.flow.collectAsState()
    val symbolState by symbolViewModel.flow.collectAsState()
    H2 { Text("S57 Object") }
    chartObjects.s57Objects.complete(chartObjectsViewModel) { objects ->
        symbolState.selectedObj.complete(symbolViewModel) { obj ->
            DropdownButton(obj.acronym, objects.keys.toList()) { key ->
                symbolViewModel.selectObj(key)
            }

            Br()
            Button(attrs = {
                classes("btn", "btn-primary")
                onClick {
                    routeViewModel.pushRoute("/layer/${obj.acronym}")
                }
            }) { Text("Locate on chart") }
            Br()
            Br()
            P {
                B { Text("Geometry Primitives: ") }
                Text(geometryPrimitives(obj))
            }
            P {
                B { Text("Object: ") }
                Text(obj.objectClass)
            }
            P {
                B { Text("Acronym: ") }
                Text(obj.acronym)
            }
            P {
                B { Text("Code: ") }
                Text("${obj.code}")
            }
            AttributeSet(obj)
        }
    }
}

@Composable
fun AttributeSet(obj: S57Object) {
    P {
        B { Text("Attribute A") }
        Br()
        Text("(Attributes in this subset define the individual characteristics of the object.)")
        Br()
        obj.attributeA.forEach { att ->
            Span {
                A(
                    href = "/control/symbols/${obj.acronym}/$att",
                    attrs = {
                        onClick {
                            it.preventDefault()
                            symbolViewModel.selectAtt(att)
                        }
                    }
                ) { Text(att) }
                Text(" ")
            }
        }
    }
    P {
        B { Text("Attribute B") }
        Br()
        Text("(Attributes in this subset provide information relevant to the use of the data, e.g. for presentation or for an information system.)")
        Br()
        obj.attributeB.forEach { att ->
            Span {
                A(
                    href = "/control/symbols/${obj.acronym}/$att",
                    attrs = {
                        onClick {
                            it.preventDefault()
                            symbolViewModel.selectAtt(att)
                        }
                    }
                ) { Text(att) }
                Text(" ")
            }
        }
    }
    P {
        B { Text("Attribute C") }
        Br()
        Text("(Attributes in this subset provide administrative information about the object and data describing it.)")
        Br()
        obj.attributeC.forEach { att ->
            Span {
                A(
                    href = "/control/symbols/${obj.acronym}/$att",
                    attrs = {
                        onClick {
                            it.preventDefault()
                            symbolViewModel.selectAtt(att)
                        }
                    }
                ) { Text(att) }
                Text(" ")
            }
        }
    }

}

private fun geometryPrimitives(obj: S57Object): String {
    return obj.primitives.reduce { a, b -> "$a, $b" }
}
