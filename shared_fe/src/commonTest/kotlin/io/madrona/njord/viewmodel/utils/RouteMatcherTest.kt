package io.madrona.njord.viewmodel.utils

import io.madrona.njord.routing.Route
import io.madrona.njord.routing.RouteMatcher
import io.madrona.njord.routing.Routing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RouteMatcherTest {

    @Test
    fun routeArgMatch() {
        val path = "/control/tab1"
        val matcher = RouteMatcher.build("/control/:tab")
        assertTrue(matcher.matches(path))
        assertEquals("tab1", matcher.groups(path)["tab"])
    }

    @Test
    fun routeArgMatchAnySubpath() {
        val path = "/control/tab1"
        val matcher = RouteMatcher.build("/control/:tab/.*")
        assertTrue(matcher.matches(path))
        assertEquals(
            mapOf(
                "tab" to "tab1",
            ),
            matcher.groups(path)
        )
    }

    @Test
    fun routeMatch() {
        val path = "/control/tab1/sel1"
        val route = Routing.from(path)
        assertEquals(Route.ControlPanel, route.route)
        assertEquals("tab1", route.args?.get("tab"))
    }

    @Test
    fun matchesAny() {
        val matcher = RouteMatcher.build(".*")
        assertTrue(matcher.matches("/"))
        assertTrue(matcher.matches("/foo"))
        assertTrue(matcher.matches("/foo/bar"))
        assertTrue(matcher.matches("/foo/bar/baz"))
        assertTrue(matcher.matches("/foo/bar/baz?a=b"))
        assertTrue(matcher.groups("/").isEmpty())
        assertTrue(matcher.groups("").isEmpty())
        assertTrue(matcher.groups("?").isEmpty())
        assertTrue(matcher.groups("/?").isEmpty())
        assertTrue(matcher.groups("?/").isEmpty())
    }

    @Test
    fun wildCard() {
        val matcher = RouteMatcher.build("/foo/.*")
        assertFalse(matcher.matches("/"))
        assertTrue(matcher.matches("/foo"))
        assertTrue(matcher.matches("/foo#"))
        assertTrue(matcher.matches("/foo/"))
        assertTrue(matcher.matches("/foo/#"))
        assertTrue(matcher.matches("/foo/bar"))
        assertTrue(matcher.matches("/foo/bar/"))
        assertTrue(matcher.matches("/foo/bar/baz"))
        assertTrue(matcher.matches("/foo/bar/baz/"))
        assertTrue(matcher.matches("/foo/bar/baz?a=b"))
    }

    @Test
    fun namedWildCardControlPanel() {
        val matcher = RouteMatcher.build(Route.ControlPanel.pathPattern)
        assertTrue(matcher.matches("/control/sprites"))
        assertEquals(
            mapOf(
                "tab" to "sprites",
                "path" to ""
            ),
            matcher.groups("/control/sprites/")
        )
    }
    @Test
    fun namedWildCard() {
        val matcher = RouteMatcher.build("/foo/:*path")
        assertFalse(matcher.matches("/"))
        assertTrue(matcher.matches("/foo"))
        assertEquals(
            mapOf(
                "path" to ""
            ),
            matcher.groups("/foo")
        )
        assertEquals(
            mapOf(
                "path" to ""
            ),
            matcher.groups("/foo/")
        )
        assertTrue(matcher.matches("/foo/"))
        assertTrue(matcher.matches("/foo/bar"))
        assertTrue(matcher.matches("/foo/bar/"))
        assertTrue(matcher.matches("/foo/bar/baz"))
        assertTrue(matcher.matches("/foo/bar/baz/"))
        assertTrue(matcher.matches("/foo/bar/baz?a=b"))
        assertEquals(
            mapOf(
                "path" to "bar/baz"
            ),
            matcher.groups("/foo/bar/baz?a=b")
        )
        assertEquals(
            mapOf(
                "path" to "bar/baz"
            ),
            matcher.groups("/foo/bar/baz/?a=b")
        )
    }
}