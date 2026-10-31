"""P-API-3: `GET /v1/tile_json`, `GET /v1/tile/{z}/{x}/{y}`, `?info=true` (audit TE-7, API-3).

Tiles are compared decoded and canonicalised (lib/tiles.py). The tile list spans z4–z16 over the
fixture cell US5WA22M, the overlap of the two synthetic cells, a tile inside the outer synthetic
cell only, and tiles with no chart at all (base map only). The Kotlin unit fixture P-TE-5 pins the
same tiles byte-for-byte; the P-TE-5 coordinates under `testdata/geo/tiles/` are added when present.
"""

import json
from pathlib import Path

import pytest

from lib.engine import Spec
from lib.tiles import lon_lat_to_tile

G = "api_03_tiles"

CELL = (-122.4528, 47.278)          # centre of US5WA22M (M_COVR extent -122.552..-122.354, 47.231..47.325)
OVERLAP = (-123.0, 47.0)            # inside both synthetic cells (BOYLAT of the outer cell sits here)
OUTER_ONLY = (-123.15, 47.0)        # inside US4ZZ01M only
OCEAN = (-30.0, 0.0)                # no chart anywhere near

TILES = (
    [(z, *lon_lat_to_tile(*CELL, z)) for z in (4, 8, 12, 13, 14, 15, 16)]
    + [(z, *lon_lat_to_tile(*OVERLAP, z)) for z in (10, 12, 14, 15)]
    + [(14, *lon_lat_to_tile(*OUTER_ONLY, 14)), (11, *lon_lat_to_tile(*OUTER_ONLY, 11))]
    + [(3, *lon_lat_to_tile(*OCEAN, 3)), (6, 32, 31)]
)

# P-TE-5 golden tiles: testdata/geo/tiles/<z>_<x>_<y>.json (the `_info.json` sidecars are TE-7).
P_TE_5_DIR = Path(__file__).resolve().parents[1] / "testdata" / "geo" / "tiles"
for f in sorted(P_TE_5_DIR.glob("*.json")) if P_TE_5_DIR.is_dir() else []:
    if f.stem.endswith("_info"):
        continue
    try:
        coord = tuple(int(part) for part in f.stem.split("_"))
    except ValueError:
        continue
    if len(coord) == 3 and coord not in TILES:
        TILES.append(coord)


def test_tile_json(checker):
    checker.check(f"{G}/tile_json", Spec("/v1/tile_json"))


@pytest.mark.parametrize("z,x,y", TILES, ids=lambda v: str(v))
def test_tile(checker, seeded, z, x, y):
    checker.check(f"{G}/tile_{z}_{x}_{y}", Spec(f"/v1/tile/{z}/{x}/{y}", kind="tile", data=True))


def test_tile_info(checker, seeded):
    z, x, y = 14, *lon_lat_to_tile(*CELL, 14)
    # ChartFeatureInfo in WGS84, base map skipped; features sorted by (layer, LNAM) before comparing.
    checker.check(f"{G}/tile_info_{z}_{x}_{y}", Spec(f"/v1/tile/{z}/{x}/{y}?info=true", kind="info", data=True))


def test_non_integer_coordinates_fall_through_to_the_web_app(checker):
    # No handler answers, so Ktor's `unhandled` page serves the SPA index: 200 text/html, not 404.
    # The body is the deployed web app, so only status and content type are compared.
    checker.check(f"{G}/tile_non_integer", Spec("/v1/tile/x/0/0", kind="html", volatile=True,
                                                 note="falls through to the SPA index page"))


@pytest.mark.parametrize("z,x,y", [(5, -1, 0), (5, 32, 0), (25, 0, 0), (-1, 0, 0)])
def test_out_of_range_tile(checker, z, x, y):
    # D8: Kotlin encodes (and caches) any integer triple and answers an empty tile; the port must
    # refuse with 400 before touching the cache (TEST_COVERAGE_AUDIT.md §3.1).
    # data=True: because Kotlin does encode these, the body carries whatever base map the server
    # holds (production has Natural Earth, a fixture server has none).
    checker.check(f"{G}/out_of_range_{z}_{x}_{y}", Spec(
        f"/v1/tile/{z}/{x}/{y}", kind="tile", data=True,
        # The port's 400 has an empty body and no content type.
        port={"decision": "D8", "status": 400, "content_type": ""},
        note="D8: Kotlin 200 empty tile, port 400",
    ))
