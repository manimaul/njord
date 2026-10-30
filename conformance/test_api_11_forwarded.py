"""P-API-11: `X-Forwarded-Proto/Host/Port` → `externalBaseUrl()` in style and tile_json URLs and in the
signature (audit SEC-8). The signature is an HMAC over the external base URL, so one minted behind
one host name never verifies behind another.

Marked local_only: a production proxy overwrites or drops client-sent forwarded headers.
"""

import pytest

from lib.engine import Spec

G = "api_11_forwarded"
pytestmark = pytest.mark.local_only

FORWARDED = {"X-Forwarded-Proto": "https", "X-Forwarded-Host": "charts.example.org"}


@pytest.mark.parametrize("headers,case", [
    (FORWARDED, "https_host"),
    ({**FORWARDED, "X-Forwarded-Port": "8443"}, "https_host_port"),
    ({**FORWARDED, "X-Forwarded-Port": "443"}, "https_default_port_elided"),
    ({"X-Forwarded-Proto": "http", "X-Forwarded-Host": "charts.example.org", "X-Forwarded-Port": "80"}, "http_default_port_elided"),
    ({"Forwarded": "proto=https;host=fw.example.org"}, "rfc7239"),
])
def test_tile_json_reflects_forwarded_headers(checker, headers, case):
    checker.check(f"{G}/tile_json_{case}", Spec("/v1/tile_json", headers=headers))


def test_style_urls_reflect_forwarded_headers(client):
    style = client.get("/v1/style/meters/day", headers={**FORWARDED, "X-Forwarded-Port": "8443"}).json()
    assert style["glyphs"] == "https://charts.example.org:8443/v1/content/fonts/{fontstack}/{range}.pbf"
    assert style["sprite"] == "https://charts.example.org:8443/v1/content/sprites/day_simplified"
    assert style["sources"]["src_senc"]["url"] == "https://charts.example.org:8443/v1/tile_json"


def test_signature_is_bound_to_the_forwarded_host(client, admin_auth):
    behind_proxy = client.get("/v1/admin", auth=admin_auth, headers=FORWARDED).json()
    # Verifies only when presented under the same external base URL …
    r = client.request("POST", "/v1/admin/verify", json=behind_proxy["signature"], headers=FORWARDED)
    assert r.status_code == 200, r.text
    r = client.request("GET", "/v1/regions/nope.mbtiles", signature=behind_proxy["signatureEncoded"], headers=FORWARDED)
    assert r.status_code == 404
    # … and never under the server's own host.
    assert client.request("POST", "/v1/admin/verify", json=behind_proxy["signature"]).status_code == 401
    assert client.request("GET", "/v1/regions/nope.mbtiles", signature=behind_proxy["signatureEncoded"]).status_code == 401
