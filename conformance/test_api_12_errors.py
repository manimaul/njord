"""P-API-12: 405 for unimplemented verbs, unknown routes, CORS (audit API-8)."""

import pytest

from lib.engine import Spec

G = "api_12_errors"
CORS_HEADERS = ("Access-Control-Allow-Origin", "Access-Control-Allow-Methods", "Access-Control-Allow-Headers", "Access-Control-Max-Age")


@pytest.mark.parametrize("method,path", [
    ("PUT", "/v1/tile_json"),
    ("PATCH", "/v1/about/version"),
    ("POST", "/v1/about/version"),
    ("HEAD", "/v1/tile_json"),
    ("PUT", "/v1/chart"),
    ("DELETE", "/v1/style/meters/day"),
])
def test_unimplemented_verb_is_405(checker, method, path):
    checker.check(f"{G}/405_{method}_{path.removeprefix('/v1/').replace('/', '_')}", Spec(path, method=method))


@pytest.mark.parametrize("path", ["/v1/nope", "/v1/tile", "/nothing/here"])
def test_unknown_route_serves_the_web_app(checker, path):
    # There is no 404 for unknown routes: Ktor's `unhandled` page serves the SPA index so deep
    # links into the web app resolve. Only status and content type are compared (the body is the
    # deployed web app).
    checker.check(f"{G}/unknown_route_{path.strip('/').replace('/', '_')}", Spec(path, kind="html", volatile=True,
                                                                                    note="SPA fallback: 200 text/html"))


def test_cors_preflight(checker):
    checker.check(f"{G}/cors_preflight", Spec(
        "/v1/chart", method="OPTIONS", capture_headers=CORS_HEADERS,
        headers={"Origin": "http://example.test", "Access-Control-Request-Method": "POST", "Access-Control-Request-Headers": "content-type"},
    ))


def test_cors_simple_request_allows_any_origin(checker):
    checker.check(f"{G}/cors_get", Spec("/v1/tile_json", headers={"Origin": "http://example.test"}, capture_headers=("Access-Control-Allow-Origin",)))
