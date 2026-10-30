import io.madrona.njord.ingest.NaturalEarthIngest
import io.madrona.njord.model.ws.WsMsg
import io.madrona.njord.testdata.FixtureHeader
import io.madrona.njord.testdata.fixtures
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * P-IN-7 `WsMsg` wire format and the pure half of P-IN-6, the Natural Earth file-name tables.
 */
class WireFormatFixtureTest {

    @BeforeTest
    fun before() {
        TestEnv.init()
    }

    private val messages: List<Pair<String, WsMsg>> = listOf(
        "idle" to WsMsg.Idle,
        "extracting_start" to WsMsg.Extracting(0f),
        "extracting_half" to WsMsg.Extracting(0.5f),
        "extracting_done" to WsMsg.Extracting(1f),
        "info" to WsMsg.Info(feature = 1234, totalFeatures = 5164, chart = 0, totalCharts = 3),
        "error_fatal" to WsMsg.Error(isFatal = true, message = "cell zip for US5ZZ99M contained no ENC_ROOT/US5ZZ99M/ entries"),
        "error_warning" to WsMsg.Error(isFatal = false, message = ""),
        "completion_report" to WsMsg.CompletionReport(
            totalFeatureCount = 5164,
            totalChartCount = 2,
            items = listOf(WsMsg.InsertItem("US5WA22M.000", 5159), WsMsg.InsertItem("US4ZZ01M.000", 6)),
            failedCharts = listOf("US5ZZ02M.000 msg: dsid is missing"),
            ms = 12345,
        ),
        "completion_report_empty" to WsMsg.CompletionReport(0, 0, emptyList(), emptyList(), 0),
    )

    /**
     * Every subtype as the status file and `/v1/ws/enc_process` carry it: the kotlinx polymorphic
     * encoding of the sealed class, with the fully qualified class name as the `type`
     * discriminator. The frontend and the mobile status readers depend on that discriminator.
     */
    @Test
    fun wsMessages() {
        val json = Json
        fixtures.table(
            "ws/messages.json",
            FixtureHeader(
                pins = "Json.encodeToString(WsMsg.serializer(), msg) for every WsMsg subtype: class-name \"type\" discriminator, field names and float/long formatting",
                decisions = listOf("SH-1"),
                generator = "WireFormatFixtureTest.wsMessages",
            ),
            messages.map { it.first },
        ) { name ->
            val msg = messages.first { it.first == name }.second
            val text = json.encodeToString(WsMsg.serializer(), msg)
            assertEquals(msg, json.decodeFromString(WsMsg.serializer(), text), "round trip of $name")
            json.parseToJsonElement(text)
        }
    }

    @Serializable
    data class NeName(val layer: String, val scale: Int)

    /** `normalizeLayerName` strips the `ne_<res>m_` prefix and extension and collapses the bathymetry / lakes families; `scaleFromFilename` defaults to 10m. */
    @Test
    fun naturalEarthNames() {
        val names = listOf(
            "ne_10m_land.shp", "ne_50m_land.shp", "ne_110m_land.shp",
            "ne_10m_coastline.shp", "ne_110m_coastline.shp",
            "ne_10m_lakes.shp", "ne_10m_lakes_europe.shp", "ne_50m_lakes_historic.shp",
            "ne_10m_bathymetry_A_10000.shp", "ne_10m_bathymetry_L_0.shp", "ne_110m_bathymetry_K_200.shp",
            "ne_10m_minor_islands.shp", "ne_10m_reefs.shp", "ne_10m_glaciated_areas.shp", "ne_50m_antarctic_ice_shelves_polys.shp",
            "ne_10m_playas.shp", "ne_10m_rivers_lake_centerlines.shp", "ne_10m_ocean.shp",
            "ne_10m_land", "land.shp", "custom_50m_thing.shp", "ne_10m_land.tar.gz", "",
        )
        fixtures.table(
            "s57/natural_earth_names.json",
            FixtureHeader(
                pins = "NaturalEarthIngest.normalizeLayerName / scaleFromFilename: shapefile name -> base_features layer and scale (110m=110000000, 50m=50000000, else 10000000)",
                decisions = listOf("IN-8"),
                generator = "WireFormatFixtureTest.naturalEarthNames",
            ),
            names,
        ) { NeName(NaturalEarthIngest.normalizeLayerName(it), NaturalEarthIngest.scaleFromFilename(it)) }
    }
}
