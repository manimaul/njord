package io.madrona.njord.routing

enum class NjordRoute(
    override val pathPattern: String,
    override val title: String,
) : Route {
    About("/", "About"),
    Enc("/enc", "ENC"),
    ControlPanel("/control/:tab/:*path", "Control Panel"),
    Chart("/chart/:id", "Chart"),
    Layer("/layer/:name", "Layer"),
    NotFound("/404", "Not Found");

    companion object {
        val registry = RouteRegistry(entries, NotFound)

        val navBarRoutes = listOf(
            Routing.from(About),
            Routing.from(Enc),
            controlPanel(),
        )

        fun controlPanel(tab: String = "charts_catalog", selection: String = "") =
            registry.from("/control/$tab/$selection")
    }
}
