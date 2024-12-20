package io.madrona.njord.routing

enum class Route(
    val pathPattern: String,
    val title: String
) {
    About("/", "About"),
    Enc("/enc", "ENC"),
    ControlPanel("/control/:tab/:selection", "ControlPanel"),
    NotFound("/404", "Not Found");

    companion object {
        fun controlPanel(tab: String = "charts_catalog", selection: String = "none") =
            Routing.from("/control/$tab/$selection")
    }
}
