package io.madrona.njord.routing

enum class Route(
    val pathPattern: String,
    val title: String
) {
    About("/", "About"),
    Enc("/enc", "ENC"),
    ControlPanel("/control/:tab/:*path", "Control Panel"),
    Chart("/chart/:id", "Chart"),
    Layer("/layer/:name", "Layer"),
    NotFound("/404", "Not Found");

    companion object {
        fun controlPanel(tab: String = "charts_catalog", selection: String = "") =
            Routing.from("/control/$tab/$selection")
    }
}
