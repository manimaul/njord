import io.madrona.njord.testdata.FixtureHeader
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Header for a fixture whose expected values come out of GDAL. The GDAL release is part of the
 * header on purpose: the Mac dev boxes run Homebrew's GDAL while the container builds against
 * Debian 13's, and geometry repair, WKT formatting and the S-57 reader can all shift between
 * releases. A header mismatch in verify mode is the signal to regenerate under CI's GDAL and
 * review the diff rather than to trust whichever side happened to write the file last.
 */
fun gdalHeader(pins: String, decisions: List<String>, generator: String, source: String) = FixtureHeader(
    pins = pins,
    decisions = decisions,
    generator = generator,
    source = "$source; GDAL ${Gdal.gdalVersion()} (CI must regenerate under its own GDAL if the version differs)",
)

/** One feature as `ChartIngest.installChartFeatures` hands it to `FeatureDao.insertFeatureSync`. */
@Serializable
data class FeatureOut(val props: JsonObject, val wkt: String?)

/**
 * The `OgrS57Dataset` layers ChartIngest never inserts (`ChartIngest.exLayers`). Mirrored here
 * because the server module is not on this classpath; S57FixtureTest pins the set itself.
 */
val ingestExcludedLayers = listOf("DSID", "IsolatedNode", "ConnectedNode", "Edge", "Face")

/** Every inserted feature of this layer: GDAL-mapped props plus the WKT of the geometry after `makeValid()`. */
fun OgrLayer.insertedFeatures(): List<FeatureOut> = mapFeature { f ->
    FeatureOut(f.properties, f.geometry?.makeValid()?.wkt)
}
