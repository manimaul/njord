"""P-API-9: `POST/DELETE /v1/chart`, `POST /v1/geojson`, `GET/DELETE /v1/cache` (audit API-4, API-7).

`DELETE /v1/nuke` is in test_api_99_nuke.py so it runs after everything that needs data.
Local servers only.
"""

import pytest

from lib.engine import Spec

G = "api_09_mutations"
pytestmark = [pytest.mark.mutating, pytest.mark.signed,
              pytest.mark.port_pending("Phase 4: chart/geojson POST, chart/cache DELETE")]

CHART = "ZZ_CONFORMANCE.000"
CHART_INSERT = {
    "name": CHART,
    "scale": 20000,
    "file_name": CHART,
    "updated": "20260101",
    "issued": "20260101",
    "zoom": 13,
    "covr": {"type": "Feature", "geometry": {"type": "Polygon", "coordinates": [[[10.0, 10.0], [10.1, 10.0], [10.1, 10.1], [10.0, 10.1], [10.0, 10.0]]]}, "properties": {}},
    "dsid_props": {"DSID_DSNM": CHART, "DSID_UPDN": "0", "DSID_EDTN": "1"},
    "chart_txt": {"ZZ_CONFORMANCE.TXT": "conformance suite chart"},
}
FEATURE = {
    "type": "Feature",
    "geometry": {"type": "Point", "coordinates": [10.05, 10.05]},
    "properties": {"LNAM": "ZZCONF00000001", "BOYSHP": 4, "CATLAM": 1, "COLOUR": ["4"], "OBJNAM": "Conformance Buoy", "SCAMIN": 29999},
}


def test_chart_insert_get_and_delete(checker, client, signature):
    checker.check(f"{G}/chart_post", Spec("/v1/chart", method="POST", signed=True, json=CHART_INSERT))
    checker.check(f"{G}/chart_get_inserted", Spec(f"/v1/chart?name={CHART}"))

    checker.check(f"{G}/geojson_post", Spec(f"/v1/geojson?chart_name={CHART}&layer_name=BOYLAT", method="POST", signed=True, json=FEATURE))
    checker.check(f"{G}/geojson_post_unknown_chart", Spec("/v1/geojson?chart_name=NOPE.000&layer_name=BOYLAT", method="POST", signed=True, json=FEATURE))
    checker.check(f"{G}/geojson_post_missing_params", Spec("/v1/geojson", method="POST", signed=True, json=FEATURE))
    checker.check(f"{G}/feature_lnam_inserted", Spec("/v1/feature/lnam/ZZCONF00000001", masks=("/id",)))
    checker.check(f"{G}/chart_get_with_feature", Spec(f"/v1/chart?name={CHART}"))

    checker.check(f"{G}/chart_delete", Spec(f"/v1/chart?name={CHART}", method="DELETE", signed=True))
    checker.check(f"{G}/chart_delete_again", Spec(f"/v1/chart?name={CHART}", method="DELETE", signed=True))
    checker.check(f"{G}/chart_get_deleted", Spec(f"/v1/chart?name={CHART}"))
    checker.check(f"{G}/feature_lnam_deleted", Spec("/v1/feature/lnam/ZZCONF00000001"))


def test_chart_delete_without_name_is_400(checker):
    checker.check(f"{G}/chart_delete_no_name", Spec("/v1/chart", method="DELETE", signed=True))


def test_chart_post_invalid_body_is_400(checker):
    # Ktor's receive() fails on a body that is not a ChartInsert; recorded as observed.
    checker.check(f"{G}/chart_post_invalid", Spec("/v1/chart", method="POST", signed=True, json={"not": "a chart"}, kind="text"))


def test_cache_counts_and_clear(checker, client):
    client.get("/v1/tile/4/2/5")  # make sure at least one tile is cached when the cache is on
    checker.check(f"{G}/cache_get", Spec("/v1/cache", volatile=True, note="per-zoom counts depend on request history"))
    checker.check(f"{G}/cache_delete", Spec("/v1/cache", method="DELETE", signed=True))
    checker.check(f"{G}/cache_get_after_clear", Spec("/v1/cache"))
