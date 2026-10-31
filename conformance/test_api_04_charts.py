"""P-API-4: chart, catalog, editions, feature and GeoJSON reads (audit API-1, API-5).

These are data-dependent: compared exactly when the target holds the same chart editions as the
recording (local servers seeded with the fixture cells), by shape otherwise (production).
"""

import json
from pathlib import Path

import pytest

from lib.engine import Spec

G = "api_04_charts"
CELL = "US5WA22M.000"
LIGHT_LNAM = "022600019FEA0001"  # a LIGHTS feature of US5WA22M
PORT_BODIES = Path(__file__).parent / "port_bodies"


def test_chart(checker, sample):
    # shape_depth=1: `dsid_props` and `chart_txt` are keyed by S-57 attribute / sidecar file name,
    # so another chart has other keys; they compare as "object".
    checker.check(f"{G}/chart", Spec(f"/v1/chart?name={sample['chart']}", data=True, shape_depth=1))


def test_synthetic_chart(checker, synthetic_present):
    checker.check(f"{G}/chart_synthetic", Spec("/v1/chart?name=US4ZZ01M.000", data=True))


@pytest.mark.parametrize("path", ["/v1/chart?name=NOPE.000", "/v1/chart"])
def test_unknown_or_missing_chart_name_is_404(checker, path):
    checker.check(f"{G}/chart_404_{'missing' if path.endswith('chart') else 'unknown'}", Spec(path))


def test_chart_catalog(checker, seeded):
    checker.check(f"{G}/chart_catalog", Spec("/v1/chart_catalog", data=True, shape_depth=3))


def test_chart_catalog_cursor(checker, sample):
    # `name` is the lexicographic cursor: the page starts at that chart.
    checker.check(f"{G}/chart_catalog_cursor", Spec(f"/v1/chart_catalog?name={sample['chart']}", data=True, shape_depth=3))


def test_chart_editions(checker, seeded):
    checker.check(f"{G}/chart_editions", Spec("/v1/chart_editions", data=True, shape_depth=1))


def test_feature_layer_page(checker, seeded):
    # Row ids are database sequence values and differ between ingests; everything else is pinned.
    checker.check(f"{G}/feature_layer_BOYLAT", Spec(
        "/v1/feature/layer/BOYLAT?start_id=0", data=True, shape_depth=3, masks=("/lastId", "/items/*/id"),
    ))


def test_feature_layer_page_topmar_carries_platform_fields(checker, seeded):
    # TOPMAR rows get `_PTFM` / `_ASOC` from their associated buoy or beacon (audit DB-5).
    checker.check(f"{G}/feature_layer_TOPMAR", Spec(
        "/v1/feature/layer/TOPMAR?start_id=0", data=True, shape_depth=3, masks=("/lastId", "/items/*/id"),
    ))


def test_feature_layer_unknown_layer_is_an_empty_page(checker):
    checker.check(f"{G}/feature_layer_unknown", Spec("/v1/feature/layer/NOPE?start_id=0"))


def test_feature_by_lnam(checker, sample):
    if not sample["lnam"]:
        pytest.skip("no LIGHTS feature to look up on the target")
    # shape_depth=1: `props` holds that feature's S-57 attributes and compares as "object".
    checker.check(f"{G}/feature_lnam", Spec(f"/v1/feature/lnam/{sample['lnam']}", data=True, shape_depth=1, masks=("/id",)))


@pytest.mark.parametrize("path", ["/v1/feature/lnam/NOPE", "/v1/feature/bogus/x"])
def test_feature_404s(checker, path):
    checker.check(f"{G}/feature_404_{path.split('/')[3]}", Spec(path))


def test_geojson_missing_params_is_404(checker):
    checker.check(f"{G}/geojson_missing_params", Spec("/v1/geojson"))


def test_geojson_unknown_chart_is_an_empty_collection(checker):
    checker.check(f"{G}/geojson_unknown_chart", Spec("/v1/geojson?chart_name=NOPE.000&layer_name=BOYLAT", kind="geojson"))


def test_geojson_existing_layer(checker, sample):
    # D14: the Kotlin server answers 404 for every chart/layer that HAS features, while an unknown
    # chart (zero rows) gets an empty collection. `GeoJsonDao.fetchAsync` decodes its `row_to_json`
    # rows into `geojson.Feature`, whose `id` is `String?` while the row's `id` is a number and
    # whose serializer rejects the row's extra `layer` key; the DAO retries exhaust and the handler
    # sees null. The port serves the collection; its expected body (port_bodies/) was built from the
    # seeded database with Kotlin's own query, ids masked.
    port_body = json.loads((PORT_BODIES / "geojson_existing_layer.json").read_text())["body"]
    checker.check(f"{G}/geojson_existing_layer", Spec(
        f"/v1/geojson?chart_name={sample['lights_chart']}&layer_name=LIGHTS", kind="geojson", data=True,
        masks=("/features/*/id",),
        port={"decision": "D14", "status": 200, "content_type": "application/json", "body": port_body},
        note="Kotlin answers 404 for a layer that has features (DAO decode failure, D14)",
    ))
