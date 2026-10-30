"""P-API-1: `GET /v1/about/*` — JSON shape of the version and the S-57 dictionaries (audit API-7)."""

import pytest

from lib.engine import Spec

G = "api_01_about"


def test_version(checker):
    # Every value is build-specific (version string, GDAL version, git hash, build date), so the
    # golden pins the key set only. The Rust port reports its own version and GDAL build here.
    checker.check(f"{G}/version", Spec(
        "/v1/about/version",
        masks=("/version", "/gdalVersion", "/gitHash", "/gitBranch", "/buildDate"),
        note="key set only: every value identifies the build",
    ))


@pytest.mark.parametrize("name", ["s57objects", "s57attributes", "expectedInput", "colors", "colors/custom"])
def test_dictionaries_are_exact(checker, name):
    # The S-57 object/attribute dictionaries and the colour tables are resources shipped with the
    # server; they must be byte-for-byte the same on every implementation.
    checker.check(f"{G}/{name.replace('/', '_')}", Spec(f"/v1/about/{name}"))


def test_unknown_about_path_is_404(checker):
    checker.check(f"{G}/unknown", Spec("/v1/about/nope"))
