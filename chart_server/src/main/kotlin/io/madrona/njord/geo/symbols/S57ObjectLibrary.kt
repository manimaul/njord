package io.madrona.njord.geo.symbols

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.MappingIterator
import com.fasterxml.jackson.dataformat.csv.CsvMapper
import com.fasterxml.jackson.dataformat.csv.CsvSchema
import io.madrona.njord.Singletons
import io.madrona.njord.util.resourceAsString


class S57ObjectLibrary(
    private val csvMapper: CsvMapper = Singletons.csvMapper,
) {

    val expectedInput: Map<String, S57ExpectedInput> by lazy {
        val schema = CsvSchema.builder()
            .addColumn("Code")
            .addColumn("ID")
            .addColumn("Meaning")
            .build()

        val itor: MappingIterator<Map<String, String>> = csvMapper
            .readerForMapOf(String::class.java)
            .with(schema)
            .readValues(resourceAsString("s57expectedinput.csv"))

        val list = mutableListOf<S57ExpectedInput>()
        while (itor.hasNext()) {
            val value = itor.next()
            value["Code"]?.toIntOrNull()?.let {
                list.add(
                    S57ExpectedInput(
                        code = it,
                        id = value["ID"]?.toIntOrNull() ?: -1,
                        meaning = value["Meaning"] ?: ""
                    )
                )
            }
        }
        list.associateBy { "${it.code}-${it.id}" }
    }

    val attributes: Map<String, S57Attribute> by lazy {
        val schema = CsvSchema.builder()
            .addColumn("Code")
            .addColumn("Attribute")
            .addColumn("Acronym")
            .addColumn("Attributetype")
            .addColumn("Class")
            .build()

        val itor: MappingIterator<Map<String, String>> = csvMapper
            .readerForMapOf(String::class.java)
            .with(schema)
            .readValues(resourceAsString("s57attributes.csv"))

        val list = mutableListOf<S57Attribute>()
        while (itor.hasNext()) {
            val value = itor.next()
            value["Code"]?.toIntOrNull()?.let {
                list.add(
                    S57Attribute(
                        code = it,
                        attribute = value["Attribute"] ?: "",
                        acronym = value["Acronym"] ?: "",
                        attributeType = value["Attributetype"] ?: "",
                        cls = value["Class"] ?: ""
                    )
                )
            }
        }
        list.associateBy { it.acronym }
    }

    val objects: Map<String, S57Object> by lazy {
        val schema = CsvSchema.builder()
            .addColumn("Code")
            .addColumn("ObjectClass")
            .addColumn("Acronym")
            .addColumn("Attribute_A")
            .addColumn("Attribute_B")
            .addColumn("Attribute_C")
            .addColumn("Class")
            .addColumn("Primitives")
            .build()

        val itor: MappingIterator<Map<String, String>> = csvMapper
            .readerForMapOf(String::class.java)
            .with(schema)
            .readValues(resourceAsString("s57objectclasses.csv"))

        val list = mutableListOf<S57Object>()
        while (itor.hasNext()) {
            val value = itor.next()
            value["Code"]?.toIntOrNull()?.let {
                list.add(
                    S57Object(
                        code = it,
                        objectClass = value["ObjectClass"] ?: "",
                        acronym = value["Acronym"] ?: "",
                        attributeA = value["Attribute_A"].parse(),
                        attributeB = value["Attribute_B"].parse(),
                        attributeC = value["Attribute_C"].parse(),
                        cls = value["Class"] ?: "",
                        primitives = value["Primitives"].parse(),
                    )
                )
            }
        }
        list.associateBy { it.acronym }
    }

    private fun String?.parse(): List<String> {
        return this?.let {
            split(";").filter {
                it.isNotBlank()
            }
        } ?: emptyList()
    }


}


data class S57Object(

    /**
     * Unique code for the object.
     */
    @JsonProperty("Code") val code: Int,

    /**
     * Human-readable description of the object.
     */
    @JsonProperty("ObjectClass") val objectClass: String,

    /**
     * Six character acronym key for the object.
     */
    @JsonProperty("Acronym") val acronym: String,

    /**
     * Attributes in this subset define the individual characteristics of the object.
     */
    @JsonProperty("Attribute_A") val attributeA: List<String>,

    /**
     * Attributes in this subset provide information relevant to the use of the data, e.g. for presentation or for an
     * information system.
     */
    @JsonProperty("Attribute_B") val attributeB: List<String>,

    /**
     * Attributes in this subset provide administrative information about the object and data describing it.
     */
    @JsonProperty("Attribute_C") val attributeC: List<String>,


    /**
     * todo: (what do these mean?) G= M= C= $= <empty>=
     */
    @JsonProperty("Class") val cls: String,

    /**
     * The geometric primitives allowed for the object are P=point L=line A=area N=none
     */
    @JsonProperty("Primitives") val primitives: List<String>
)

data class S57Attribute(
    /**
     * Unique code for the attribute.
     */
    @JsonProperty("Code") val code: Int,

    /**
     * Human-readable description of the attribute.
     */
    @JsonProperty("Attribute") val attribute: String,

    /**
     * Six character acronym key for the attribute.
     */
    @JsonProperty("Acronym") val acronym: String,

    /**
     * todo: (what do these mean?) A= E= F= S= L= I= N/A=
     */
    @JsonProperty("Attributetype") val attributeType: String,

    /**
     * todo: (what do these mean?) F= $= N= S= ?=
     */
    @JsonProperty("Class") val cls: String,
)

data class S57ExpectedInput(
    /**
     * The corresponding [S57Attribute.code]
     */
    @JsonProperty("Code") val code: Int,

    /**
     * Value in the [S57Object]'s [S57Attribute]
     * eg A BOYSPP feature with an attribute: CATSPM: ["27"]
     * CATSPM has id 66 so the [S57ExpectedInput] with Code: 66 and ID: 27 has the [S57ExpectedInput.meaning]: "general warning mark"
    },
     */
    @JsonProperty("ID") val id: Int,

    /**
     * Human readable description
     */
    @JsonProperty("Meaning") val meaning: String,

)