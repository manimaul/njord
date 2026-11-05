"""P-API-13: `GET /v1/change_reports` and `GET /v1/change_reports/{date}` (docs/change_reports.md).

The change history has no golden: it is new to the Rust server, so every test here is `port_only`
and checks the live server alone. Local servers only, because the rows it asserts are the ones
the signed upload and delete in this module make.
"""

import datetime as dt

import pytest

from lib import seed

pytestmark = [pytest.mark.mutating, pytest.mark.signed, pytest.mark.port_only]

CHART = f"{seed.CELL}.000"


def _today() -> str:
    return dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%d")


def _report(client, signature, date: str) -> dict:
    r = client.get(f"/v1/change_reports/{date}", signature=signature)
    assert r.status_code == 200, (r.status_code, r.text)
    return r.json()


def test_routes_are_signed(client):
    assert client.get("/v1/change_reports").status_code == 401
    assert client.get("/v1/change_reports/2026-10-31").status_code == 401


def test_malformed_date_and_page_are_400(client, signature):
    assert client.get("/v1/change_reports/2026-13-01", signature=signature).status_code == 400
    assert client.get("/v1/change_reports/yesterday", signature=signature).status_code == 400
    assert client.get("/v1/change_reports?page=-1", signature=signature).status_code == 400
    assert client.get("/v1/change_reports?page=x", signature=signature).status_code == 400
    assert client.get(f"/v1/change_reports/{_today()}?page=-1", signature=signature).status_code == 400


def test_day_without_events_is_404(client, signature):
    assert client.get("/v1/change_reports/1999-01-01", signature=signature).status_code == 404


def test_upload_and_delete_are_recorded(client, signature):
    seed.ingest(client, signature, f"{seed.CELL}.zip", seed.fixture_cell_zip())
    today = _today()

    page = client.get("/v1/change_reports", signature=signature).json()
    assert page["pageIndex"] == 0 and page["pageSize"] == 25
    assert page["totalDays"] >= 1
    assert page["page"][0]["date"] == today, page
    assert page["page"][0]["actions"] >= 1

    report = _report(client, signature, today)
    assert report["date"] == today
    assert report["pageIndex"] == 0 and report["pageSize"] == 250
    assert len(report["items"]) <= 250
    mine = [i for i in report["items"] if i["name"] == CHART and i["outcome"] in ("new", "update")]
    assert mine, report["items"]
    item = mine[-1]
    assert item["kind"] == "chart"
    assert item["cause"] == "admin", "an upload through the API is the admin's"
    assert item["attempted"] is None and item["message"] is None
    assert item["installed"] is True
    assert item["occurredAt"].endswith("Z")
    assert isinstance(item["featureCountChange"], int)
    counts = report["counts"]
    assert set(counts) == {"new", "update", "delete", "nuke", "error"}
    assert sum(counts.values()) == report["totalItems"]
    assert len(report["items"]) == min(report["totalItems"], 250)

    r = client.request("DELETE", f"/v1/chart?name={CHART}&cause=noaa_enc_cron", signature=signature)
    assert r.status_code == 202, r.text
    report = _report(client, signature, today)
    deletes = [i for i in report["items"] if i["name"] == CHART and i["outcome"] == "delete"]
    assert deletes, report["items"]
    assert deletes[-1]["cause"] == "noaa_enc_cron"
    assert deletes[-1]["featureCountChange"] < 0
    assert deletes[-1]["installed"] is False, "the chart is gone, so nothing links to it"

    r = client.request("DELETE", f"/v1/chart?name={CHART}&cause=bogus", signature=signature)
    assert r.status_code == 400, "an unknown cause is refused, not counted as admin"

    # The errors come first, then the rest by name.
    outcomes = [i["outcome"] == "error" for i in report["items"]]
    assert outcomes == sorted(outcomes, reverse=True)

    seed.ensure_seeded(client, signature)
