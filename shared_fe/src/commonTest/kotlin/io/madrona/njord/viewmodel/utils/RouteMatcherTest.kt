package io.madrona.njord.viewmodel.utils

import io.madrona.njord.routing.Route
import io.madrona.njord.routing.Routing
import kotlin.test.Test
import kotlin.test.assertEquals

class RouteMatcherTest {

    @Test
    fun routeArgMatch() {
        val path = "/control/tab1/sel0"
        val route = Routing.from(path)
        assertEquals(Route.ControlPanel, route.route)
        assertEquals("tab1", route.args?.get("tab"))
        assertEquals("sel0", route.args?.get("selection"))
    }

    @Test
    fun routeMatch() {
        val path = "/control/tab1/sel1"
        val route = Routing.from(path)
        assertEquals(Route.ControlPanel, route.route)
        assertEquals("tab1", route.args?.get("tab"))
        assertEquals("sel1", route.args?.get("selection"))
    }
}