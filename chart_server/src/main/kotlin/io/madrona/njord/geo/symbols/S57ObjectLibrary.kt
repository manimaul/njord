package io.madrona.njord.geo.symbols

import com.fasterxml.jackson.databind.MappingIterator
import com.fasterxml.jackson.dataformat.csv.CsvMapper
import com.fasterxml.jackson.dataformat.csv.CsvSchema
import io.madrona.njord.S57Attribute
import io.madrona.njord.S57ExpectedInput
import io.madrona.njord.S57Object
import io.madrona.njord.Singletons
import io.madrona.njord.util.resourceAsString


class S57ObjectLibrary(
    private val csvMapper: CsvMapper = Singletons.csvMapper,
) {

    val expectedInput: Map<String, List<S57ExpectedInput>> by lazy {
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
        list.groupBy { "${it.code}" }
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
