package io.madrona.njord.layers

import io.madrona.njord.ext.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.Json.Default.encodeToJsonElement

sealed interface Symbol {
    var property: JsonElement?

    class Sprite(sprite: io.madrona.njord.model.Sprite) : Symbol {
        override var property: JsonElement? = encodeToJsonElement(io.madrona.njord.model.Sprite.serializer(), sprite)
    }

    class Property(num: Int? = null) : Symbol {
        override var property: JsonElement? = listOf("get", num?.let { "SY$it" } ?: "SY").json
    }
}

sealed interface Label {
    var property: JsonElement?

    class Text(text: String) : Label {
        override var property: JsonElement? = text.json
    }

    class Property(name: String) : Label {
        override var property: JsonElement? = listOf("get", name).json
    }
}

sealed interface IconRot {
    val property: JsonElement?

    data class Degrees(val deg: Float) : IconRot {
        override val property = deg.json
    }

    class Property(name: String) : IconRot {
        override var property = listOf("get", name).json
    }
}
//sealed interface IconRotAlign {
//    val property: Any?
//
//    object Map : IconRotAlign {
//        override val property = IconRotationAlignment.MAP
//    }
//    object ViewPort: IconRotAlign {
//        override val property = IconRotationAlignment.VIEWPORT
//    }
//    object Auto: IconRotAlign {
//        override val property = IconRotationAlignment.AUTO
//    }
//    data class IfValueEq(
//        val key: String,
//        val value: String,
//        val eq: IconRotationAlignment,
//        val nEq: IconRotationAlignment,
//    ) : IconRotAlign {
//        override val property: Any
//            get() = listOf(
//                "case",
//                listOf("==", listOf("get", key), value),
//                listOf("literal", eq),
//                listOf("literal", nEq)
//            )
//    }
//}

sealed interface Offset {
    val property: JsonElement?

    data class Coord(val x: Float = 0f, val y: Float = 0f) : Offset {
        override val property = listOf(x, y).json
    }

    class EvalEq(key: String, value: String, eq: Coord, neq: Coord) : Offset {
        override val property = listOf(
            "case",
            listOf("==", listOf("get", key), value),
            listOf("literal", eq.property),
            listOf("literal", neq.property)
        ).json
    }
}

sealed interface LineStyle {
    var lineDashArray: List<Float>?

    object DashLine : LineStyle {
        override var lineDashArray: List<Float>? = listOf(1f, 2f)
    }

    object Solid : LineStyle {
        override var lineDashArray: List<Float>? = null
    }

    class CustomDash(width: Float, gap: Float) : LineStyle {
        override var lineDashArray: List<Float>? = listOf(width, gap)
    }
}

