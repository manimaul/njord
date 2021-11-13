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
            .readerForMapOf(String::class.java) // NOTE: no wrapping needed
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
    @JsonProperty("Code") val code: Int,
    @JsonProperty("ObjectClass") val objectClass: String,
    @JsonProperty("Acronym") val acronym: String,
    @JsonProperty("Attribute_A") val attributeA: List<String>,
    @JsonProperty("Attribute_B") val attributeB: List<String>,
    @JsonProperty("Attribute_C") val attributeC: List<String>,
    @JsonProperty("Class") val cls: String,
    @JsonProperty("Primitives") val primitives: List<String>
)