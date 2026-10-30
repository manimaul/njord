package io.madrona.njord.viewmodel.utils

import io.madrona.njord.routing.NjordRoute
import io.madrona.njord.routing.QueryParams
import io.madrona.njord.routing.RouteMatcher
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Phase 0 §3.10: the frontend route contract the Leptos port must keep (RUST_PORT_PLAN.md §7),
 * pinned as `testdata/frontend/routes.json` - every `NjordRoute` pattern and title, `RouteMatcher`
 * match/group behaviour (trailing slash, `#`, query strings, `:name` and `:*wild` segments) and
 * `RouteRegistry.from` incl. the NotFound fallback and query-param parsing.
 *
 * This module is Kotlin/JS run in a browser, so it cannot read `testdata/`. The fixture text is
 * embedded below between the `ROUTES_FIXTURE` markers and `testdata/tools/check_routes_fixture.py`
 * fails CI if the embedded copy and the committed file differ, which keeps one source of truth:
 * the inputs in this test. To regenerate, run the test, copy the JSON from the failure message into
 * both places.
 */
class RouteFixtureTest {

    @Serializable
    data class RouteOut(val name: String, val pathPattern: String, val title: String)

    @Serializable
    data class Input(
        val pattern: String? = null,
        val path: String? = null,
        val registry: String? = null,
        val query: String? = null,
    )

    @Serializable
    data class Expected(
        val matches: Boolean? = null,
        val groups: Map<String, String>? = null,
        val route: String? = null,
        val args: Map<String, String>? = null,
        val params: Map<String, String?>? = null,
        val pathAndParams: String? = null,
    )

    @Serializable
    data class Case(val input: Input, val expected: Expected)

    @Serializable
    data class Fixture(
        val pins: String,
        val decisions: List<String>,
        val generator: String,
        val source: String,
        val routes: List<RouteOut>,
        val cases: List<Case>,
    )

    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
        explicitNulls = false
    }

    private fun matcher(pattern: String, path: String) = Input(pattern = pattern, path = path)
    private fun registry(path: String, query: String? = null) = Input(registry = path, query = query)

    private val inputs = listOf(
        // RouteMatcherTest cases
        matcher("/control/:tab", "/control/tab1"),
        matcher("/control/:tab/.*", "/control/tab1"),
        matcher(".*", "/"),
        matcher(".*", "/foo/bar/baz?a=b"),
        matcher(".*", ""),
        matcher(".*", "?"),
        matcher(".*", "/?"),
        matcher(".*", "?/"),
        matcher("/foo/.*", "/"),
        matcher("/foo/.*", "/foo"),
        matcher("/foo/.*", "/foo#"),
        matcher("/foo/.*", "/foo/"),
        matcher("/foo/.*", "/foo/#"),
        matcher("/foo/.*", "/foo/bar/baz/"),
        matcher("/foo/.*", "/foo/bar/baz?a=b"),
        matcher("/foo/:*path", "/"),
        matcher("/foo/:*path", "/foo"),
        matcher("/foo/:*path", "/foo/"),
        matcher("/foo/:*path", "/foo/bar/baz"),
        matcher("/foo/:*path", "/foo/bar/baz/"),
        matcher("/foo/:*path", "/foo/bar/baz?a=b"),
        matcher("/foo/:*path", "/foo/bar/baz/?a=b"),
        // every NjordRoute pattern against representative paths
        matcher("/", "/"),
        matcher("/", "/enc"),
        matcher("/", "/?foo=bar"),
        matcher("/enc", "/enc"),
        matcher("/enc", "/enc/"),
        matcher("/enc", "/enc/extra"),
        matcher("/chart/:name", "/chart/US5WA22M.000"),
        matcher("/chart/:name", "/chart/US5WA22M.000?z=14"),
        matcher("/chart/:name", "/chart"),
        matcher("/chart/:name", "/chart/a/b"),
        matcher("/layer/:name", "/layer/SOUNDG"),
        matcher("/layer/:name", "/layer/SOUNDG/"),
        matcher("/control/:tab/:*path", "/control/sprites"),
        matcher("/control/:tab/:*path", "/control/sprites/"),
        matcher("/control/:tab/:*path", "/control/charts_catalog/US5WA22M.000"),
        matcher("/control/:tab/:*path", "/control/symbols/BOYSPP/CATSPM"),
        matcher("/control/:tab/:*path", "/control"),
        matcher("/404", "/404"),
        matcher("/404", "/nope"),
        // registry resolution incl. the NotFound fallback and query params
        registry("/"),
        registry("/enc"),
        registry("/enc/"),
        registry("/control/tab1/sel1"),
        registry("/control/charts_catalog/"),
        registry("/control/symbols/BOYSPP/CATSPM", "theme=day&c=shom"),
        registry("/chart/US5WA22M.000", "z=14&flag"),
        registry("/layer/SOUNDG"),
        registry("/nope"),
        registry("/chart"),
        registry("/control"),
        registry("/404"),
        registry("/enc/extra"),
    )

    private fun compute(input: Input): Expected = if (input.pattern != null) {
        val m = RouteMatcher.build(input.pattern)
        Expected(matches = m.matches(input.path!!), groups = m.groups(input.path))
    } else {
        val routing = NjordRoute.registry.from(input.registry!!, QueryParams(input.query))
        Expected(
            route = routing.route.name,
            args = routing.args,
            params = routing.params?.values,
            pathAndParams = routing.pathAndParams(),
        )
    }

    fun generate(): String = json.encodeToString(
        Fixture.serializer(),
        Fixture(
            pins = "NjordRoute patterns and titles; RouteMatcher.matches/groups (a trailing '#' and '/' are tolerated, groups ignore the query string and strip a trailing '/', ':name' matches one segment, ':*name' the rest incl. empty); RouteRegistry.from picks the first matching route in declaration order, falls back to NotFound with null args, and QueryParams splits 'k=v&flag' into values with null for a bare key",
            decisions = listOf("FE-1"),
            generator = "shared_fe RouteFixtureTest (Kotlin/JS); the JSON is embedded in the test and testdata/tools/check_routes_fixture.py keeps the two identical",
            source = "RouteMatcherTest cases plus every NjordRoute against representative paths",
            routes = NjordRoute.entries.map { RouteOut(it.name, it.pathPattern, it.title) },
            cases = inputs.map { Case(it, compute(it)) },
        ),
    )

    @Test
    fun `the embedded fixture is what the router produces`() {
        val generated = generate().trim()
        assertEquals(
            ROUTES_FIXTURE.trim(),
            generated,
            "testdata/frontend/routes.json is stale; replace it and ROUTES_FIXTURE with:\n$generated\n",
        )
    }

    @Test
    fun `every NjordRoute is listed`() {
        val routes = json.decodeFromString(Fixture.serializer(), ROUTES_FIXTURE).routes
        assertEquals(NjordRoute.entries.map { it.name }, routes.map { it.name })
    }
}

// ROUTES_FIXTURE_BEGIN testdata/frontend/routes.json
val ROUTES_FIXTURE = """
{
  "pins": "NjordRoute patterns and titles; RouteMatcher.matches/groups (a trailing '#' and '/' are tolerated, groups ignore the query string and strip a trailing '/', ':name' matches one segment, ':*name' the rest incl. empty); RouteRegistry.from picks the first matching route in declaration order, falls back to NotFound with null args, and QueryParams splits 'k=v&flag' into values with null for a bare key",
  "decisions": [
    "FE-1"
  ],
  "generator": "shared_fe RouteFixtureTest (Kotlin/JS); the JSON is embedded in the test and testdata/tools/check_routes_fixture.py keeps the two identical",
  "source": "RouteMatcherTest cases plus every NjordRoute against representative paths",
  "routes": [
    {
      "name": "About",
      "pathPattern": "/",
      "title": "About"
    },
    {
      "name": "Enc",
      "pathPattern": "/enc",
      "title": "ENC"
    },
    {
      "name": "ControlPanel",
      "pathPattern": "/control/:tab/:*path",
      "title": "Control Panel"
    },
    {
      "name": "Chart",
      "pathPattern": "/chart/:name",
      "title": "Chart"
    },
    {
      "name": "Layer",
      "pathPattern": "/layer/:name",
      "title": "Layer"
    },
    {
      "name": "NotFound",
      "pathPattern": "/404",
      "title": "Not Found"
    }
  ],
  "cases": [
    {
      "input": {
        "pattern": "/control/:tab",
        "path": "/control/tab1"
      },
      "expected": {
        "matches": true,
        "groups": {
          "tab": "tab1"
        }
      }
    },
    {
      "input": {
        "pattern": "/control/:tab/.*",
        "path": "/control/tab1"
      },
      "expected": {
        "matches": true,
        "groups": {
          "tab": "tab1"
        }
      }
    },
    {
      "input": {
        "pattern": ".*",
        "path": "/"
      },
      "expected": {
        "matches": true,
        "groups": {}
      }
    },
    {
      "input": {
        "pattern": ".*",
        "path": "/foo/bar/baz?a=b"
      },
      "expected": {
        "matches": true,
        "groups": {}
      }
    },
    {
      "input": {
        "pattern": ".*",
        "path": ""
      },
      "expected": {
        "matches": true,
        "groups": {}
      }
    },
    {
      "input": {
        "pattern": ".*",
        "path": "?"
      },
      "expected": {
        "matches": false,
        "groups": {}
      }
    },
    {
      "input": {
        "pattern": ".*",
        "path": "/?"
      },
      "expected": {
        "matches": true,
        "groups": {}
      }
    },
    {
      "input": {
        "pattern": ".*",
        "path": "?/"
      },
      "expected": {
        "matches": false,
        "groups": {}
      }
    },
    {
      "input": {
        "pattern": "/foo/.*",
        "path": "/"
      },
      "expected": {
        "matches": false,
        "groups": {}
      }
    },
    {
      "input": {
        "pattern": "/foo/.*",
        "path": "/foo"
      },
      "expected": {
        "matches": true,
        "groups": {}
      }
    },
    {
      "input": {
        "pattern": "/foo/.*",
        "path": "/foo#"
      },
      "expected": {
        "matches": true,
        "groups": {}
      }
    },
    {
      "input": {
        "pattern": "/foo/.*",
        "path": "/foo/"
      },
      "expected": {
        "matches": true,
        "groups": {}
      }
    },
    {
      "input": {
        "pattern": "/foo/.*",
        "path": "/foo/#"
      },
      "expected": {
        "matches": true,
        "groups": {}
      }
    },
    {
      "input": {
        "pattern": "/foo/.*",
        "path": "/foo/bar/baz/"
      },
      "expected": {
        "matches": true,
        "groups": {}
      }
    },
    {
      "input": {
        "pattern": "/foo/.*",
        "path": "/foo/bar/baz?a=b"
      },
      "expected": {
        "matches": true,
        "groups": {}
      }
    },
    {
      "input": {
        "pattern": "/foo/:*path",
        "path": "/"
      },
      "expected": {
        "matches": false,
        "groups": {}
      }
    },
    {
      "input": {
        "pattern": "/foo/:*path",
        "path": "/foo"
      },
      "expected": {
        "matches": true,
        "groups": {
          "path": ""
        }
      }
    },
    {
      "input": {
        "pattern": "/foo/:*path",
        "path": "/foo/"
      },
      "expected": {
        "matches": true,
        "groups": {
          "path": ""
        }
      }
    },
    {
      "input": {
        "pattern": "/foo/:*path",
        "path": "/foo/bar/baz"
      },
      "expected": {
        "matches": true,
        "groups": {
          "path": "bar/baz"
        }
      }
    },
    {
      "input": {
        "pattern": "/foo/:*path",
        "path": "/foo/bar/baz/"
      },
      "expected": {
        "matches": true,
        "groups": {
          "path": "bar/baz"
        }
      }
    },
    {
      "input": {
        "pattern": "/foo/:*path",
        "path": "/foo/bar/baz?a=b"
      },
      "expected": {
        "matches": true,
        "groups": {
          "path": "bar/baz"
        }
      }
    },
    {
      "input": {
        "pattern": "/foo/:*path",
        "path": "/foo/bar/baz/?a=b"
      },
      "expected": {
        "matches": true,
        "groups": {
          "path": "bar/baz"
        }
      }
    },
    {
      "input": {
        "pattern": "/",
        "path": "/"
      },
      "expected": {
        "matches": true,
        "groups": {}
      }
    },
    {
      "input": {
        "pattern": "/",
        "path": "/enc"
      },
      "expected": {
        "matches": false,
        "groups": {}
      }
    },
    {
      "input": {
        "pattern": "/",
        "path": "/?foo=bar"
      },
      "expected": {
        "matches": false,
        "groups": {}
      }
    },
    {
      "input": {
        "pattern": "/enc",
        "path": "/enc"
      },
      "expected": {
        "matches": true,
        "groups": {}
      }
    },
    {
      "input": {
        "pattern": "/enc",
        "path": "/enc/"
      },
      "expected": {
        "matches": true,
        "groups": {}
      }
    },
    {
      "input": {
        "pattern": "/enc",
        "path": "/enc/extra"
      },
      "expected": {
        "matches": false,
        "groups": {}
      }
    },
    {
      "input": {
        "pattern": "/chart/:name",
        "path": "/chart/US5WA22M.000"
      },
      "expected": {
        "matches": true,
        "groups": {
          "name": "US5WA22M.000"
        }
      }
    },
    {
      "input": {
        "pattern": "/chart/:name",
        "path": "/chart/US5WA22M.000?z=14"
      },
      "expected": {
        "matches": true,
        "groups": {
          "name": "US5WA22M.000"
        }
      }
    },
    {
      "input": {
        "pattern": "/chart/:name",
        "path": "/chart"
      },
      "expected": {
        "matches": true,
        "groups": {
          "name": ""
        }
      }
    },
    {
      "input": {
        "pattern": "/chart/:name",
        "path": "/chart/a/b"
      },
      "expected": {
        "matches": false,
        "groups": {
          "name": "a"
        }
      }
    },
    {
      "input": {
        "pattern": "/layer/:name",
        "path": "/layer/SOUNDG"
      },
      "expected": {
        "matches": true,
        "groups": {
          "name": "SOUNDG"
        }
      }
    },
    {
      "input": {
        "pattern": "/layer/:name",
        "path": "/layer/SOUNDG/"
      },
      "expected": {
        "matches": true,
        "groups": {
          "name": "SOUNDG"
        }
      }
    },
    {
      "input": {
        "pattern": "/control/:tab/:*path",
        "path": "/control/sprites"
      },
      "expected": {
        "matches": true,
        "groups": {
          "tab": "sprites",
          "path": ""
        }
      }
    },
    {
      "input": {
        "pattern": "/control/:tab/:*path",
        "path": "/control/sprites/"
      },
      "expected": {
        "matches": true,
        "groups": {
          "tab": "sprites",
          "path": ""
        }
      }
    },
    {
      "input": {
        "pattern": "/control/:tab/:*path",
        "path": "/control/charts_catalog/US5WA22M.000"
      },
      "expected": {
        "matches": true,
        "groups": {
          "tab": "charts_catalog",
          "path": "US5WA22M.000"
        }
      }
    },
    {
      "input": {
        "pattern": "/control/:tab/:*path",
        "path": "/control/symbols/BOYSPP/CATSPM"
      },
      "expected": {
        "matches": true,
        "groups": {
          "tab": "symbols",
          "path": "BOYSPP/CATSPM"
        }
      }
    },
    {
      "input": {
        "pattern": "/control/:tab/:*path",
        "path": "/control"
      },
      "expected": {
        "matches": false,
        "groups": {}
      }
    },
    {
      "input": {
        "pattern": "/404",
        "path": "/404"
      },
      "expected": {
        "matches": true,
        "groups": {}
      }
    },
    {
      "input": {
        "pattern": "/404",
        "path": "/nope"
      },
      "expected": {
        "matches": false,
        "groups": {}
      }
    },
    {
      "input": {
        "registry": "/"
      },
      "expected": {
        "route": "About",
        "args": {},
        "pathAndParams": "/"
      }
    },
    {
      "input": {
        "registry": "/enc"
      },
      "expected": {
        "route": "Enc",
        "args": {},
        "pathAndParams": "/enc"
      }
    },
    {
      "input": {
        "registry": "/enc/"
      },
      "expected": {
        "route": "Enc",
        "args": {},
        "pathAndParams": "/enc/"
      }
    },
    {
      "input": {
        "registry": "/control/tab1/sel1"
      },
      "expected": {
        "route": "ControlPanel",
        "args": {
          "tab": "tab1",
          "path": "sel1"
        },
        "pathAndParams": "/control/tab1/sel1"
      }
    },
    {
      "input": {
        "registry": "/control/charts_catalog/"
      },
      "expected": {
        "route": "ControlPanel",
        "args": {
          "tab": "charts_catalog",
          "path": ""
        },
        "pathAndParams": "/control/charts_catalog/"
      }
    },
    {
      "input": {
        "registry": "/control/symbols/BOYSPP/CATSPM",
        "query": "theme=day&c=shom"
      },
      "expected": {
        "route": "ControlPanel",
        "args": {
          "tab": "symbols",
          "path": "BOYSPP/CATSPM"
        },
        "params": {
          "theme": "day",
          "c": "shom"
        },
        "pathAndParams": "/control/symbols/BOYSPP/CATSPM?theme=day&c=shom"
      }
    },
    {
      "input": {
        "registry": "/chart/US5WA22M.000",
        "query": "z=14&flag"
      },
      "expected": {
        "route": "Chart",
        "args": {
          "name": "US5WA22M.000"
        },
        "params": {
          "z": "14",
          "flag": null
        },
        "pathAndParams": "/chart/US5WA22M.000?z=14&flag"
      }
    },
    {
      "input": {
        "registry": "/layer/SOUNDG"
      },
      "expected": {
        "route": "Layer",
        "args": {
          "name": "SOUNDG"
        },
        "pathAndParams": "/layer/SOUNDG"
      }
    },
    {
      "input": {
        "registry": "/nope"
      },
      "expected": {
        "route": "NotFound",
        "pathAndParams": "/nope"
      }
    },
    {
      "input": {
        "registry": "/chart"
      },
      "expected": {
        "route": "Chart",
        "args": {
          "name": ""
        },
        "pathAndParams": "/chart"
      }
    },
    {
      "input": {
        "registry": "/control"
      },
      "expected": {
        "route": "NotFound",
        "pathAndParams": "/control"
      }
    },
    {
      "input": {
        "registry": "/404"
      },
      "expected": {
        "route": "NotFound",
        "args": {},
        "pathAndParams": "/404"
      }
    },
    {
      "input": {
        "registry": "/enc/extra"
      },
      "expected": {
        "route": "NotFound",
        "pathAndParams": "/enc/extra"
      }
    }
  ]
}
"""
// ROUTES_FIXTURE_END
