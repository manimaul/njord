"""P-API-8: `POST /v1/enc_save` → ingest worker → `/v1/ws/enc_process` → `DELETE /v1/enc_save`.

End-to-end ingest of the fixture cell through the API (audit IN-1, CC-5). Local servers only.
"""

import pytest

from lib import seed, ws as wslib
from lib.engine import Spec

G = "api_08_ingest"
pytestmark = [pytest.mark.mutating, pytest.mark.signed]


def test_upload_ingest_and_reset(checker, client, signature):
    # An export in flight would be aborted by the upload and would abort the ingest in turn (D6),
    # so wait for the worker to go quiet first.
    seed.wait_for_exports_to_settle(client)
    started = __import__("time").time()
    name = "US5WA22M.zip"
    r = client.upload(f"/v1/enc_save?filename={name}", seed.fixture_cell_zip(), signature)
    assert r.status_code == 200, r.text
    assert r.json() == {"zipFile": name}

    messages = wslib.collect_until_complete(client.ws_base, signature)
    kinds = wslib.collapsed_types(messages)
    # The upload writes Extracting(0) immediately; the worker (5 s poll) then streams Extracting
    # progress, Info progress and the report. Info is skipped on a cell this small when the
    # inserts finish between two status writes, so only the ends of the sequence are fixed.
    assert kinds[0] == "Extracting", kinds
    assert kinds[-1] == "CompletionReport", kinds
    assert set(kinds) <= {"Extracting", "Info", "CompletionReport"}, kinds

    report = dict(messages[-1])
    report["items"] = sorted(report["items"], key=lambda i: i["chartName"])
    assert report["failedCharts"] == []
    assert report["totalChartCount"] == 1
    assert report["items"][0]["chartName"] == "US5WA22M.000"
    checker.check_json(f"{G}/completion_report", report, masks=("/ms",),
                       note="CompletionReport of ingesting testdata/fixtures/US5WA22M through the API")

    checker.check(f"{G}/enc_save_delete", Spec("/v1/enc_save", method="DELETE", signed=True))
    assert "US5WA22M.000" in seed.editions(client)

    with wslib.connect_status(client.ws_base, signature) as ws:
        assert wslib.msg_type(__import__("json").loads(ws.recv(timeout=10))) == "Idle", "DELETE resets the status to Idle"
    # The ingest wakes the region export worker; every region re-renders.
    seed.wait_for_exports_after(client, started)


def test_delete_when_nothing_is_running_is_202(checker):
    # tryClearLock succeeds on an empty lock too, so DELETE always resets and answers 202.
    checker.check(f"{G}/enc_save_delete_idle", Spec("/v1/enc_save", method="DELETE", signed=True))


def test_upload_without_filename_is_400(checker):
    checker.check(f"{G}/enc_save_no_filename", Spec("/v1/enc_save", method="POST", signed=True, body=b"PK\x05\x06" + b"\0" * 18))


@pytest.mark.port_only
def test_traversal_filename_is_400(client, signature):
    # D2: Kotlin writes `File(chartDir, filename)` verbatim; the port accepts only a bare `.zip` name.
    for bad in ("../evil.zip", "sub/evil.zip", "evil.txt", "..", "evil.zip\x00"):
        r = client.upload(f"/v1/enc_save?filename={bad}", seed.synthetic_zip(), signature)
        assert r.status_code == 400, (bad, r.status_code)


@pytest.mark.port_only
def test_upload_during_ingest_does_not_abort_it(client, signature):
    # D6: on Kotlin a second upload clears the lock and aborts the running ingest; on the port the
    # second zip waits for the worker and both charts land.
    seed.wait_for_exports_to_settle(client)
    client.upload("/v1/enc_save?filename=first.zip", seed.fixture_cell_zip(), signature).raise_for_status()
    client.upload("/v1/enc_save?filename=second.zip", seed.synthetic_zip(), signature).raise_for_status()
    first = wslib.collect_until_complete(client.ws_base, signature)
    assert wslib.msg_type(first[-1]) == "CompletionReport" and first[-1]["failedCharts"] == []
    second = wslib.collect_until_complete(client.ws_base, signature)
    assert wslib.msg_type(second[-1]) == "CompletionReport" and second[-1]["failedCharts"] == []
    client.request("DELETE", "/v1/enc_save", signature=signature).raise_for_status()
    have = seed.editions(client)
    assert all(c in have for c in seed.ALL_CHARTS)
