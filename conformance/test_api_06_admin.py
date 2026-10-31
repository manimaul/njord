"""P-API-6: `GET /v1/admin` (basic auth) and `POST /v1/admin/verify` (audit SEC-1, SEC-6).

The signature values are random per call, so the goldens pin the key set; the behaviour is
asserted directly. Signatures are also minted locally (lib/sigs.py) when the admin key is known,
which doubles as a cross-implementation check of the HMAC input and encoding (P-SEC-1).
"""

import base64
from datetime import datetime, timedelta, timezone

import pytest

from lib import sigs
from lib.engine import Spec

G = "api_06_admin"
SIGNATURE_MASKS = ("/signature/date", "/signature/signature", "/signature/uuid", "/signature/expirationDate", "/signatureEncoded")


def test_admin_without_credentials_is_401(checker):
    checker.check(f"{G}/admin_no_auth", Spec("/v1/admin"))


def test_admin_with_wrong_credentials_is_401(checker):
    wrong = base64.b64encode(b"nobody:nothing").decode()
    checker.check(f"{G}/admin_wrong_auth", Spec("/v1/admin", headers={"Authorization": f"Basic {wrong}"}))


@pytest.mark.port_pending("Phase 4: GET /v1/admin signature minting")
def test_admin_mints_a_signature(checker, admin_auth):
    checker.check(f"{G}/admin_ok", Spec("/v1/admin", basic_auth=True, masks=SIGNATURE_MASKS))


def _own_signature(target):
    """Each server's own signature: it is bound to that server's external base URL (diff mode)."""
    return target.login["signature"] if target.login else None


@pytest.mark.port_pending("Phase 4: POST /v1/admin/verify")
def test_verify_refreshes_a_valid_signature(checker, admin_auth):
    checker.check(f"{G}/verify_ok", Spec("/v1/admin/verify", method="POST", json=_own_signature, masks=SIGNATURE_MASKS))


@pytest.mark.port_pending("Phase 4: POST /v1/admin/verify")
def test_verify_rejects_a_tampered_signature(checker, admin_auth):
    checker.check(f"{G}/verify_tampered", Spec(
        "/v1/admin/verify", method="POST",
        json=lambda t: dict(_own_signature(t), uuid="not-the-signed-uuid") if t.login else None,
    ))


@pytest.mark.port_pending("Phase 4: POST /v1/admin/verify")
def test_locally_minted_signature_verifies(client, admin_key):
    # Proves the suite's HMAC mirrors the server's: baseUrl ‖ date ‖ uuid, base64, URL-encoded.
    minted = sigs.mint(admin_key, client.base_url)
    r = client.request("POST", "/v1/admin/verify", json=minted["signature"])
    assert r.status_code == 200, r.text
    r = client.request("GET", "/v1/regions/nope.mbtiles", signature=minted["signatureEncoded"])
    assert r.status_code == 404, "a minted signatureEncoded must pass requireSignature (404 = past the auth check)"


@pytest.mark.port_pending("Phase 4: POST /v1/admin/verify")
def test_expired_signature_is_401(client, admin_key):
    expired = sigs.mint(admin_key, client.base_url, date=datetime.now(timezone.utc) - timedelta(days=8))
    assert client.request("POST", "/v1/admin/verify", json=expired["signature"]).status_code == 401


@pytest.mark.port_pending("Phase 4: POST /v1/admin/verify")
def test_future_dated_signature_passes(client, admin_key):
    # Documented in TEST_COVERAGE_AUDIT.md §3.1 D4: only `date.until(now) > adminExpirationSeconds`
    # rejects, so a future `date` verifies and `expirationDate` is informational.
    future = sigs.mint(admin_key, client.base_url, date=datetime.now(timezone.utc) + timedelta(days=1))
    assert client.request("POST", "/v1/admin/verify", json=future["signature"]).status_code == 200


@pytest.mark.port_pending("Phase 4: POST /v1/admin/verify")
def test_signature_for_another_base_url_is_401(client, admin_key):
    other = sigs.mint(admin_key, "https://somewhere.else.test")
    assert client.request("POST", "/v1/admin/verify", json=other["signature"]).status_code == 401


@pytest.mark.port_pending("Phase 4: POST /v1/admin/verify")
def test_wrong_admin_key_is_401(client, admin_key):
    bad = sigs.mint(admin_key + "x", client.base_url)
    assert client.request("POST", "/v1/admin/verify", json=bad["signature"]).status_code == 401
