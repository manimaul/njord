package io.madrona.njord.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class S57Object(

    /**
     * Unique code for the object.
     */
    @SerialName("Code") val code: Int,

    /**
     * Human-readable description of the object.
     */
    @SerialName("ObjectClass") val objectClass: String,

    /**
     * Six character acronym key for the object.
     */
    @SerialName("Acronym") val acronym: String,

    /**
     * Attributes in this subset define the individual characteristics of the object.
     */
    @SerialName("Attribute_A") val attributeA: List<String>,

    /**
     * Attributes in this subset provide information relevant to the use of the data, e.g. for presentation or for an
     * information system.
     */
    @SerialName("Attribute_B") val attributeB: List<String>,

    /**
     * Attributes in this subset provide administrative information about the object and data describing it.
     */
    @SerialName("Attribute_C") val attributeC: List<String>,


    /**
     * todo: (what do these mean?) G= M= C= $= <empty>=
     */
    @SerialName("Class") val cls: String,

    /**
     * The geometric primitives allowed for the object are P=point L=line A=area N=none
     */
    @SerialName("Primitives") val primitives: List<String>
)

@Serializable
data class S57Attribute(
    /**
     * Unique code for the attribute.
     */
    @SerialName("Code") val code: Int,

    /**
     * Human-readable description of the attribute.
     */
    @SerialName("Attribute") val attribute: String,

    /**
     * Six character acronym key for the attribute.
     */
    @SerialName("Acronym") val acronym: String,

    /**
     * Attribute type: one-character code for the attribute type - there are six possible types:
     * Enumerated ("E") - the expected input is a number selected from a list of predefined attribute values; exactly one value must be chosen.
     * List ("L") - the expected input is a list of one or more numbers selected from a list of pre-defined attribute values.
     * Float ("F") - the expected input is a floating point numeric value with defined range, resolution, units and format.
     * Integer ("I") - the expected input is an integer numeric value with defined range, units and format.
     * Coded String ("A") - the expected input is a string of ASCII characters in a predefined format; the information is encoded according to defined coding systems.
     * Free Text ("S") - the expected input is a free-format alphanumeric string; it may be a file name which points to a text or graphic file.
     */
    @SerialName("Attributetype") val attributeType: String,

    /**
     * todo: (what do these mean?) F= $= N= S= ?=
     */
    @SerialName("Class") val cls: String,
)

@Serializable
data class S57ExpectedInput(
    /**
     * The corresponding [S57Attribute.code]
     */
    @SerialName("Code") val code: Int,

    /**
     * Value in the [S57Object]'s [S57Attribute]
     * eg A BOYSPP feature with an attribute: CATSPM: ["27"]
     * CATSPM has id 66 so the [S57ExpectedInput] with Code: 66 and ID: 27 has the [S57ExpectedInput.meaning]: "general warning mark"
    },
     */
    @SerialName("ID") val id: Int,

    /**
     * Human readable description
     */
    @SerialName("Meaning") val meaning: String,

    )
