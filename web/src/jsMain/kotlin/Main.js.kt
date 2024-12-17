import io.madrona.njord.ui.Router
import org.jetbrains.compose.web.renderComposable

fun main() {
    renderComposable(rootElementId = "root") { Router() }
}
