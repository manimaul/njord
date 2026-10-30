"""P-API-5: static content, icons and light-sector SVGs — bytes, content types, traversal refusal."""

import pytest

from lib.engine import Spec

G = "api_05_content"


@pytest.mark.parametrize("path,kind", [
    ("/v1/content/fonts/Roboto%20Bold/0-255.pbf", "bytes"),
    ("/v1/content/fonts/Roboto%20Regular/0-255.pbf", "bytes"),
    ("/v1/content/sprites/day_simplified.json", "json"),
    ("/v1/content/sprites/day_simplified.png", "bytes"),
    ("/v1/content/sprites/dusk_simplified@2x.json", "json"),
    ("/v1/content/sprites/night_simplified@2x.png", "bytes"),
])
def test_static_content_is_exact(checker, path, kind):
    name = path.removeprefix("/v1/content/").replace("/", "_").replace("%20", "_").replace("@", "_at_")
    checker.check(f"{G}/content_{name}", Spec(path, kind=kind))


@pytest.mark.parametrize("path", [
    "/v1/content/sprites/nope.json",
    "/v1/content/fonts/Nope%20Bold/0-255.pbf",
    "/v1/content/sprites/day_simplified.exe",  # unknown extension: no content type, no file
])
def test_missing_content_is_404(checker, path):
    checker.check(f"{G}/content_404_{path.rsplit('/', 1)[1].replace('%20', '_')}", Spec(path))


@pytest.mark.parametrize("path", [
    "/v1/content/../config/application.json",
    "/v1/content/sprites/../../config/application.json",
    "/v1/content/%2e%2e/config/application.json",
    "/v1/content/sprites/./day_simplified.json",
])
def test_traversal_is_refused(checker, path):
    # Sent verbatim (requests would collapse the dot segments). `..` and `.` segments are refused
    # before the resource lookup even though `www/../config` is inside the resources directory.
    name = path.removeprefix("/v1/content/").replace("/", "_").replace("%", "pct")
    checker.check(f"{G}/traversal_{name}", Spec(path, raw_path=True))


@pytest.mark.parametrize("path", ["/v1/icon/BOYLAT13.png", "/v1/icon/BOYLAT13", "/v1/icon/LIGHTS11.png?theme=night", "/v1/icon/TOPMAR02.png?theme=dusk"])
def test_icon_png(checker, path):
    name = path.removeprefix("/v1/icon/").replace("?", "_").replace("=", "_")
    checker.check(f"{G}/icon_{name}", Spec(path, kind="bytes"))


def test_unknown_icon_is_404(checker):
    checker.check(f"{G}/icon_404", Spec("/v1/icon/NOPE.png"))


@pytest.mark.parametrize("path", [
    "/v1/sector_svg/DAY/0/250/LITRD",
    "/v1/sector_svg/NIGHT/90/270/LITGN",
    "/v1/sector_svg/DUSK/0/0/LITYW",      # full circle
    "/v1/sector_svg/DAY/350/10/CHBLK",    # wraps through north
])
def test_sector_svg_is_exact(checker, path):
    checker.check(f"{G}/sector_svg_{path.removeprefix('/v1/sector_svg/').replace('/', '_')}", Spec(path, kind="text"))


@pytest.mark.parametrize("path", ["/v1/sector_svg/DAY/x/250/LITRD", "/v1/sector_svg/DAY/0/250/NOPE"])
def test_sector_svg_bad_params_is_400(checker, path):
    checker.check(f"{G}/sector_svg_400_{path.removeprefix('/v1/sector_svg/').replace('/', '_')}", Spec(path, kind="text"))
