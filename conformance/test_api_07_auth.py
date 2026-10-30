"""P-API-7: every mutating route × {no signature, well-formed wrong signature} → 401 (audit SEC-5).

Table-driven so a new handler without `requireSignature` is caught. A *malformed* signature is
the D4 divergence: Kotlin throws inside `verifySignature` and answers 500; the port answers 401.
"""

import base64
import json
import urllib.parse

import pytest
from websockets.exceptions import ConnectionClosed
from websockets.sync.client import connect

from lib import sigs
from lib.engine import Spec

G = "api_07_auth"

MUTATING = [
    ("POST", "/v1/chart"),
    ("DELETE", "/v1/chart?name=US5WA22M.000"),
    ("POST", "/v1/enc_save?filename=a.zip"),
    ("DELETE", "/v1/enc_save"),
    ("POST", "/v1/enc_url"),
    ("POST", "/v1/geojson?chart_name=a&layer_name=b"),
    ("DELETE", "/v1/nuke"),
    ("DELETE", "/v1/cache"),
    ("POST", "/v1/regions?name=WORLD"),
    ("GET", "/v1/regions/REGION_04_2026-01-01T00-00-00.mbtiles"),
]

# Well formed (base64 JSON with every field) but its HMAC is garbage: the clean 401 path.
WRONG_HMAC = sigs.encode({
    "date": "2026-10-30T00:00:00Z",
    "signature": base64.b64encode(b"\0" * 32).decode(),
    "uuid": "00000000-0000-0000-0000-000000000000",
    "expirationDate": "2026-11-06T00:00:00Z",
})


def _name(method, path):
    return f"{G}/{method}_{path.split('?')[0].removeprefix('/v1/').replace('/', '_')}"


@pytest.mark.parametrize("method,path", MUTATING, ids=lambda v: v if "/" not in v else v.split("?")[0])
def test_no_signature_is_401(checker, method, path):
    checker.check(_name(method, path) + "_nosig", Spec(path, method=method))


@pytest.mark.parametrize("method,path", MUTATING, ids=lambda v: v if "/" not in v else v.split("?")[0])
def test_wrong_signature_is_401(checker, method, path):
    sep = "&" if "?" in path else "?"
    checker.check(_name(method, path) + "_wrongsig", Spec(f"{path}{sep}signature={WRONG_HMAC}", method=method))


@pytest.mark.parametrize("signature", ["bogus", urllib.parse.quote(base64.b64encode(b'{"x":1}').decode(), safe="")],
                         ids=["not-base64", "json-missing-fields"])
def test_malformed_signature(checker, signature):
    # D4 (TEST_COVERAGE_AUDIT.md §3.1): Kotlin answers 500 with the exception text; the port 401.
    checker.check(f"{G}/malformed_{'b64' if signature == 'bogus' else 'json'}", Spec(
        f"/v1/cache?signature={signature}", method="DELETE", kind="text",
        port={"decision": "D4", "status": 401, "content_type": "", "body": None},
        note="D4: malformed signature is 500 on Kotlin, 401 on the port",
    ))


def test_websocket_without_signature_is_closed(client):
    with connect(client.ws_base + "/v1/ws/enc_process", open_timeout=10) as ws:
        try:
            msg = ws.recv(timeout=5)
        except ConnectionClosed:
            return
        pytest.fail(f"unsigned status socket delivered a message: {msg}")


def test_websocket_with_wrong_signature_is_closed(client):
    with connect(client.ws_base + f"/v1/ws/enc_process?signature={WRONG_HMAC}", open_timeout=10) as ws:
        try:
            msg = ws.recv(timeout=5)
        except ConnectionClosed:
            return
        pytest.fail(f"wrongly signed status socket delivered a message: {msg}")


@pytest.mark.signed
def test_websocket_with_a_signature_sends_the_current_status(client, signature):
    with connect(client.ws_base + f"/v1/ws/enc_process?signature={signature}", open_timeout=10) as ws:
        msg = json.loads(ws.recv(timeout=10))
    assert str(msg.get("type", "")).rsplit(".", 1)[-1] in ("Idle", "Extracting", "Info", "CompletionReport", "Error"), msg
