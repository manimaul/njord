package io.madrona.njord.components

import io.madrona.njord.*
import io.madrona.njord.styles.AppRoutes
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.html.HTMLTag
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import react.*
import react.dom.*
import react.router.dom.Link
import react.router.useNavigate
import react.router.useParams
import kotlin.collections.Map

typealias S57ObjectMap = Map<String, S57Object>
typealias S57AttributeMap = Map<String?, S57Attribute?>
typealias S57ExpectedInputMap = Map<String, List<S57ExpectedInput>>

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

val ControlSymbols = fc<Props> { props ->
    val params = useParams()
    val symbol = params[AppRoutes.Params.symbol]
    val att = params[AppRoutes.Params.att]

    var objMap: S57ObjectMap? by useState(null)
    var attributes: S57AttributeMap? by useState(null)
    var input: S57ExpectedInputMap? by useState(null)
    useEffectOnce {
        mainScope.launch {
            objMap = fetchObjects()
            attributes = fetchAttributes()
            input = fetchExpectedInput()
        }
    }
    div(classes = "container") {
        div(classes = "row") {
            s57Objects(objMap, symbol)
            s57attribute(attributes?.let { it[att] }, input)
        }
    }
}

external interface S57ObjectProps : Props {
    var obj: S57Object
}

val S57ObjectComponent = fc<S57ObjectProps> { props ->
    div {
        br { }
        p {
            strong { +"Geometry Primitives: " }
            +props.obj.primitives.foldIndexed(StringBuilder()) { i, acc, ea ->
                acc.append(ea).also {
                    if (i < props.obj.primitives.size - 1) {
                        acc.append(", ")
                    }
                }
            }.toString()
        }
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
                selectedObject = props.obj.acronym
                desc = "(Attributes in this subset define the individual characteristics of the object.)"
                attributes = props.obj.attributeA
            }
        }
        AttributeSet {
            attrs.apply {
                name = "Attribute_B"
                selectedObject = props.obj.acronym
                desc =
                    "(Attributes in this subset provide information relevant to the use of the data, e.g. for presentation or for an information system.)"
                attributes = props.obj.attributeB
            }
        }
        AttributeSet {
            attrs.apply {
                name = "Attribute_C"
                selectedObject = props.obj.acronym
                desc =
                    "(Attributes in this subset provide administrative information about the object and data describing it.)"
                attributes = props.obj.attributeC
            }
        }
    }
}

external interface AttributeSetProps : Props {
    var selectedObject: String
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
        props.attributes.forEach { attName ->
            span {
                Link {
                    +attName
                    attrs.also {
                        it.to =
                            "${AppRoutes.control}/${ControlTab.Symbols.name.lowercase()}/${props.selectedObject}/$attName"
                    }
                }
                +" "
            }
        }
    }
}

fun RDOMBuilder<HTMLTag>.s57attribute(
    attribute: S57Attribute?,
    input: S57ExpectedInputMap?
) {
    val expectedInput = input?.get("${attribute?.code}")?.takeIf { it.isNotEmpty() }
    div(classes = "col") {
        h2 {
            +"S57 Attributes"
        }
        pathToA("/v1/about/s57attributes")
        br { }
        pathToA("/v1/about/expectedInput")
        br { }
        br { }
        input?.let {
            attribute?.let { att ->
                p {
                    strong { +"Attribute: " }
                    +att.attribute
                }
                p {
                    strong { +"Acronym: " }
                    +att.acronym
                }
                p {
                    strong { +"Code: " }
                    +"${att.code}"
                }

                expectedInput?.let { ei ->
                    table {
                        tr {
                            th { +"ID" }
                            th { +"Meaning" }
                        }
                        ei.forEach { each ->
                            tr {
                                td { +"${each.id}" }
                                td { +each.meaning }
                            }
                        }
                    }
                    br {}
                }

                p {
                    strong { +"Attribute type: " }
                    +att.attributeType
                }
                p {
                    strong { +"Attribute class: " }
                    +att.cls
                }
            } ?: +"Attribute not selected"
        } ?: Loading {}
    }
}

fun RDOMBuilder<HTMLTag>.s57Objects(
    objects: S57ObjectMap?,
    selectedObject: String?,
) {
    val navigate = useNavigate()
    div(classes = "col") {
        h2 {
            +"S57 Objects"
        }
        pathToA("/v1/about/s57objects")
        br { }
        br { }
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
                    it?.label?.let { navigate("${AppRoutes.control}/${ControlTab.Symbols.name.lowercase()}/$it") }
                }
            )
            if (selectedObject == null) {
                navigate("${AppRoutes.control}/${ControlTab.Symbols.name.lowercase()}/${objs.keys.first()}")
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
}
