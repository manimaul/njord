package io.madrona.njord.gis

import mil.nga.sf.geojson.FeatureCollection
import mil.nga.sf.geojson.FeatureConverter
import org.gdal.gdal.Dataset
import org.gdal.ogr.Layer
import java.lang.IllegalStateException

open class S57Layer(
        private val dsLayer: Layer
) {
    val name: String
        get() = dsLayer.GetName()

    val features: List<S57Feature> by lazy {
        dsLayer.ResetReading()
        val retVal = mutableListOf<S57Feature>()
        do {
            val feature = dsLayer.GetNextFeature()
            feature?.let { retVal.add(S57Feature(it)) }
        } while (feature != null)
        retVal
    }

    val geoJson: FeatureCollection
        get() = FeatureCollection().also { collection ->
            collection.addFeatures(
                    features.mapNotNull { s57Feature ->
                        s57Feature.geoJson?.let { geoJsonStr ->
                            mil.nga.sf.geojson.Feature().also { feature ->
                                feature.geometry = FeatureConverter.toGeometry(geoJsonStr)
                                feature.properties = s57Feature.properties
                            }
                        }
                    }
            )
        }

    val geoJsonStr : String
        get() = FeatureConverter.toStringValue(geoJson)
}

//SOUNDG needs to be treated special

class S57LayerDSID(dsLayer: Layer) : S57Layer(dsLayer) {

    /**
     * 4.4 Units
     * Units to be used in an ENC are :
     * - Position : latitude and longitude in decimal degrees (converted into integer values, see below).
     * - Depth : metres.
     * - Height : metres.
     * - Positional accuracy: metres.
     * - Distance : nautical miles and decimal miles, or metres as defined in the IHO Object Catalogue (see
     * S-57, Appendix A ).
     */
    val depths: String? by lazy {
        singleFieldValue("DSPM_DUNI")
    }

    val updated: String? by lazy {
        singleFieldValue("DSID_UADT")
    }

    val issueDate: String? by lazy {
        singleFieldValue("DSID_ISDT")
    }

    val scale: String? by lazy {
        singleFieldValue("DSPM_CSCL")
    }

    private val singleFeature = features.let {
        if (it.size != 1) {
            throw IllegalStateException("We only expected a single feature for layer: $LAYER_NAME ")
        }
        it.first()
    }

    private fun singleFieldValue(name: String) : String? {
        return singleFeature.fields.filter { field ->
            field.name == name
        }.let {
            if (it.size != 1) {
                throw IllegalStateException("We only expected a single field named $name in layer: $LAYER_NAME ")
            }
            it.firstOrNull()?.valueString
        }
    }

    companion object {
        const val LAYER_NAME = "DSID"

        fun fromDs(ds: Dataset) : S57LayerDSID {
            return S57LayerDSID(ds.GetLayer(LAYER_NAME))
        }
    }
}