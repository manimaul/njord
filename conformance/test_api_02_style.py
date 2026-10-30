"""P-API-2: `GET /v1/style/{depth}/{theme}[?c=]` — the full MapLibre style for every combination.

The style must be byte-equal after `{BASE}` normalisation (audit API-2, SY-13). The same bytes are
pinned by the Kotlin unit fixture P-SY-7 (`testdata/symbology/style/<depth>_<theme>.json`), which
is compared against the live style here when present.
"""

import json
from pathlib import Path

import pytest

from lib import mask as masklib
from lib.engine import Spec

G = "api_02_style"
DEPTHS = ("meters", "feet", "fathoms")
THEMES = ("day", "dusk", "night")
CUSTOM = (None, "sweden", "shom")  # server/src/nativeMain/resources/colors.json `custom` keys
FIXTURE_BASE = "https://njord.test"  # baseUrl the P-SY-7 fixtures are generated with
STYLE_FIXTURES = Path(__file__).resolve().parents[1] / "testdata" / "symbology" / "style"

COMBOS = [(d, t, c) for d in DEPTHS for t in THEMES for c in CUSTOM]


def style_path(depth, theme, custom):
    return f"/v1/style/{depth}/{theme}" + (f"?c={custom}" if custom else "")


def golden_name(depth, theme, custom):
    return f"{G}/{depth}_{theme}" + (f"_{custom}" if custom else "")


@pytest.mark.parametrize("depth,theme,custom", COMBOS, ids=lambda v: str(v))
def test_style_is_exact(checker, depth, theme, custom):
    # kind="style": generated layer ids are normalised (`DEPARE_fill_17` -> `DEPARE_fill#1`) because
    # the Kotlin server renumbers them on every request; see lib/mask.normalize_style.
    checker.check(golden_name(depth, theme, custom), Spec(style_path(depth, theme, custom), kind="style"))


def test_unknown_custom_theme_falls_back_to_the_base_theme(checker, client):
    checker.check(f"{G}/meters_day_unknown_custom", Spec("/v1/style/meters/day?c=nope", kind="style"))
    base = masklib.normalize_style(client.get("/v1/style/meters/day").json())
    assert masklib.normalize_style(client.get("/v1/style/meters/day?c=nope").json()) == base


@pytest.mark.parametrize("path", ["/v1/style/bogus/day", "/v1/style/meters/bogus"])
def test_unknown_depth_or_theme(checker, path):
    # Pinned as observed. The handler means to answer 404, but the Kotlin server answers 500 because
    # `call.respond(HttpStatusCode.NotFound, Any())` cannot serialise `Any` (candidate defect; no
    # port decision recorded yet).
    checker.check(f"{G}/unknown_{path.split('/')[3]}_{path.split('/')[4]}", Spec(path, note="Kotlin answers 500 where 404 was intended"))


def _fixture_style(depth, theme, custom):
    """The P-SY-7 fixture body, whichever of the plausible file names and layouts it uses."""
    candidates = [f"{depth}_{theme}.json"] if custom is None else [f"{depth}_{theme}_{custom}.json", f"{depth}_{theme}_c_{custom}.json"]
    for name in candidates:
        path = STYLE_FIXTURES / name
        if path.exists():
            doc = json.loads(path.read_text())
            if isinstance(doc, dict) and "cases" in doc:
                for case in doc["cases"]:
                    if custom is None or custom in json.dumps(case.get("input")):
                        return case["expected"]
                return doc["cases"][0]["expected"]
            if isinstance(doc, dict) and "expected" in doc and "version" not in doc:
                return doc["expected"]
            return doc
    return None


@pytest.mark.parametrize("depth,theme,custom", COMBOS, ids=lambda v: str(v))
def test_style_equals_the_P_SY_7_fixture(client, depth, theme, custom):
    expected = _fixture_style(depth, theme, custom)
    if expected is None:
        pytest.skip(f"no P-SY-7 fixture under {STYLE_FIXTURES} for {depth}/{theme}/{custom}")
    actual = client.get(style_path(depth, theme, custom)).json()
    expected = masklib.normalize_style(masklib.normalize_base(expected, FIXTURE_BASE))
    actual = masklib.normalize_style(masklib.normalize_base(actual, client.base_url))
    diffs = masklib.differences(expected, actual)
    assert not diffs, "\n".join(diffs)
