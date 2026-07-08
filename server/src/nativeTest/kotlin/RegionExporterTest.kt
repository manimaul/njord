import io.madrona.njord.ingest.RegionExporter
import kotlin.test.Test
import kotlin.test.assertEquals

class RegionExporterTest {

    @Test
    fun `xyzToTmsRow flips known values`() {
        assertEquals(0, RegionExporter.xyzToTmsRow(0, 0))
        assertEquals(15, RegionExporter.xyzToTmsRow(4, 0))
        assertEquals(0, RegionExporter.xyzToTmsRow(4, 15))
        assertEquals(7, RegionExporter.xyzToTmsRow(4, 8))
    }

    @Test
    fun `xyzToTmsRow is its own inverse`() {
        val z = 10
        val y = 137
        val tmsRow = RegionExporter.xyzToTmsRow(z, y)
        assertEquals(y, RegionExporter.xyzToTmsRow(z, tmsRow))
    }
}
