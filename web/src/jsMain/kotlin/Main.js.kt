import io.madrona.njord.ui.Router
import org.jetbrains.compose.web.renderComposable

external fun require(module: String): dynamic

fun main() {
    require("maplibre-gl/dist/maplibre-gl.css")
    renderComposable(rootElementId = "root") { Router() }
}
