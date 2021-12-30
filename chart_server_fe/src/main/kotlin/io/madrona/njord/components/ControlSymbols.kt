package io.madrona.njord.components

import io.madrona.njord.*
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import react.*
import react.dom.*
import kotlin.collections.Map

typealias S57ObjectMap = Map<String, S57Object>
typealias S57AttributeMap = Map<String, S57Attribute>
typealias S57ExpectedInputMap = Map<String, S57ExpectedInput>

suspend fun fetchObjects(): S57ObjectMap {
    val response = window
        .fetch("/v1/about/s57objects")
        .await()
        .text()
        .await()
    return Json.decodeFromString(response)
}

suspend fun fetchAttributes(): S57AttributeMap {
    val response = window
        .fetch("/v1/about/s57attributes")
        .await()
        .text()
        .await()
    return Json.decodeFromString(response)
}

suspend fun fetchExpectedInput(): S57ExpectedInputMap {
    val response = window
        .fetch("/v1/about/expectedInput")
        .await()
        .text()
        .await()
    return Json.decodeFromString(response)
}

val ControlSymbols = fc<Props> {
    var objects: S57ObjectMap? by useState(null)
    var selectedObject: String? by useState(null)
    var attributes: S57AttributeMap? by useState(null)
    var selectedAttribute: String? by useState(null)
    var input: S57ExpectedInputMap? by useState(null)
    useEffectOnce {
        mainScope.launch {
            objects = fetchObjects()
            attributes = fetchAttributes()
            input = fetchExpectedInput()
        }
    }
    div(classes = "container") {
        div(classes = "row") {

            div(classes = "col") {
                h2 {
                    +"S57 Objects"
                }
                objects?.let { objs ->
                    dropdown(
                        items = objs.values.map {
                            DropdownLink(
                                label = it.acronym,
                                active = it.acronym == selectedObject
                            )
                        },
                        label = selectedObject ?: "",
                        enableFilter = true,
                        callback = {
                            selectedObject = it?.label
                        }
                    )
                    if (selectedObject == null) {
                        selectedObject = objs.keys.first()
                    }
                    objs[selectedObject]?.let {
                        S57ObjectComponent {
                            attrs {
                                this.obj = it
                            }
                        }
                    }
                } ?: Loading {}
            }

            div(classes = "col") {
                h2 {
                    +"S57 Attributes"
                }
                attributes?.let {
                    Loading {}
                }
            }
        }
    }
}

interface S57ObjectProps : Props {
    var obj: S57Object
}

val S57ObjectComponent = fc<S57ObjectProps> { props ->
    div {
        p {
            strong { +"Object: " }
            +props.obj.objectClass
        }
        p {
            strong { +"Acronym: " }
            +props.obj.acronym
        }
        p {
            strong { +"Code: " }
            +"${props.obj.code}"
        }
        AttributeSet {
            attrs.apply {
                name = "Attribute_A"
                desc = "(Attributes in this subset define the individual characteristics of the object.)"
                attributes = props.obj.attributeA
            }
        }
        AttributeSet {
            attrs.apply {
                name = "Attribute_B"
                desc =
                    "(Attributes in this subset provide information relevant to the use of the data, e.g. for presentation or for an information system.)"
                attributes = props.obj.attributeB
            }
        }
        AttributeSet {
            attrs.apply {
                name = "Attribute_C"
                desc =
                    "(Attributes in this subset provide administrative information about the object and data describing it.)"
                attributes = props.obj.attributeC
            }
        }
    }
}

interface AttributeSetProps : Props {
    var name: String
    var desc: String
    var attributes: List<String>
}

val AttributeSet = fc<AttributeSetProps> { props ->
    p {
        strong { +"${props.name} " }
        br { }
        i {
            +props.desc
        }
        br { }
        props.attributes.forEach {
            span {
                +"$it "
            }
        }
    }
}