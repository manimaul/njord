"""P-API-10: `GET /v1/regions`, `POST /v1/regions?name=`, `GET /v1/regions/{archive}` with and without `Range`.

The manifest and the archive bytes change with every render, so the manifest's archive fields are
masked and archive downloads are compared by status and headers (audit RE-7, RE-8).
"""

import pytest

from lib import seed
from lib.engine import Spec

G = "api_10_regions"
MANIFEST_MASKS = ("/*/archive", "/*/archiveSize", "/*/archiveSha256", "/*/createdAt")
ARCHIVE_HEADERS = ("Accept-Ranges", "Content-Disposition", "Content-Range", "Content-Length")


@pytest.fixture(scope="module")
def archive(client, config, seeded):
    """The newest archive of a rendered region (REGION_04 locally), waiting for the export worker locally."""
    manifest = client.get("/v1/regions").json()
    if config.allow_mutating:
        manifest = seed.wait_for_archives(client, ("REGION_04",))
    rendered = [e for e in manifest if e.get("archive")]
    if not rendered:
        pytest.skip("no rendered region archive on the target")
    preferred = [e for e in rendered if e["name"] == "REGION_04"] or rendered
    return preferred[0]["archive"]


def test_manifest(checker, seeded):
    # Region list and coverage come from the server config (identical on both local servers,
    # different in production); archive name, size, checksum and timestamp from the last render.
    checker.check(f"{G}/regions", Spec("/v1/regions", data=True, masks=MANIFEST_MASKS, shape_depth=3))


@pytest.mark.signed
def test_archive_download(checker, archive):
    checker.check(f"{G}/archive_full", Spec(f"/v1/regions/{archive}", kind="bytes", signed=True, volatile=True, capture_headers=ARCHIVE_HEADERS))


@pytest.mark.signed
@pytest.mark.parametrize("range_header,case", [("bytes=0-99", "first_100"), ("bytes=-100", "suffix_100"), ("bytes=0-9,20-29", "multi_merged")])
def test_archive_partial_content(checker, archive, range_header, case):
    checker.check(f"{G}/archive_range_{case}", Spec(
        f"/v1/regions/{archive}", kind="bytes", signed=True, volatile=True,
        headers={"Range": range_header}, capture_headers=ARCHIVE_HEADERS,
    ))


@pytest.mark.signed
def test_archive_range_beyond_eof_is_416(checker, archive):
    checker.check(f"{G}/archive_range_416", Spec(
        f"/v1/regions/{archive}", kind="bytes", signed=True, volatile=True,
        headers={"Range": "bytes=999999999999-"}, capture_headers=ARCHIVE_HEADERS,
    ))


@pytest.mark.signed
def test_unknown_archive_is_404(checker):
    checker.check(f"{G}/archive_404", Spec("/v1/regions/NOPE_2026-01-01T00-00-00.mbtiles", signed=True))


@pytest.mark.signed
@pytest.mark.parametrize("name", ["..%2Fx.mbtiles", "a%2Fb.mbtiles", "..", "%2e%2e%2fx"])
def test_archive_traversal_is_400(checker, name):
    # Sent verbatim so the percent-encoding reaches the server as written.
    checker.check(f"{G}/archive_traversal_{name.replace('%', 'pct').replace('.', '_')}", Spec(
        f"/v1/regions/{name}", raw_path=True, signed=True,
    ))


@pytest.mark.mutating
@pytest.mark.signed
def test_post_region_rerenders(checker, client, archive):
    checker.check(f"{G}/regions_post_missing_name", Spec("/v1/regions", method="POST", signed=True))
    checker.check(f"{G}/regions_post_unknown", Spec("/v1/regions?name=NOPE", method="POST", signed=True))
    checker.check(f"{G}/regions_post_ok", Spec("/v1/regions?name=REGION_04", method="POST", signed=True))
    # The worker re-renders asynchronously; the manifest eventually names a newer archive.
    import time
    deadline = time.monotonic() + 600
    while time.monotonic() < deadline:
        entry = [e for e in client.get("/v1/regions").json() if e["name"] == "REGION_04"][0]
        if entry["archive"] and entry["archive"] != archive:
            return
        time.sleep(5)
    pytest.fail("REGION_04 was not re-rendered within 600 s of POST /v1/regions")
