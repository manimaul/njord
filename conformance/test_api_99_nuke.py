"""P-API-9 (continued): `DELETE /v1/nuke` wipes charts, features, base features, the tile cache and the
region archives, and records itself in the change history, which it keeps. Runs last because
everything else needs the data; re-seeds afterwards so the next run starts populated. Local
servers only.
"""

import datetime as dt

import pytest

from conftest import impl_under_test
from lib import seed
from lib.engine import Spec

G = "api_99_nuke"
pytestmark = [pytest.mark.mutating, pytest.mark.signed]


def test_nuke_empties_everything_then_reseed(checker, client, signature, seeded, pytestconfig):
    rust = impl_under_test(pytestconfig) == "rust"
    today = dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%d")
    before = client.get(f"/v1/change_reports/{today}", signature=signature).json()["items"] if rust else []
    checker.check(f"{G}/nuke", Spec("/v1/nuke", method="DELETE", signed=True))
    if rust:
        # The change history survives the nuke, and the nuke is its newest row.
        after = client.get(f"/v1/change_reports/{today}", signature=signature).json()["items"]
        assert len(after) == len(before) + 1, (before, after)
        nukes = [i for i in after if i["outcome"] == "nuke"]
        assert nukes and nukes[-1]["kind"] == "all" and nukes[-1]["name"] == "*"
        assert nukes[-1]["featureCountChange"] < 0 and nukes[-1]["cause"] == "admin"
    checker.check(f"{G}/chart_catalog_after", Spec("/v1/chart_catalog"))
    checker.check(f"{G}/chart_editions_after", Spec("/v1/chart_editions"))
    checker.check(f"{G}/cache_after", Spec("/v1/cache"))
    manifest = client.get("/v1/regions").json()
    assert all(e["archive"] is None for e in manifest), manifest
    assert client.get(f"/v1/chart?name={seed.CELL}.000").status_code == 404

    seed.ensure_seeded(client, signature)
    assert set(seed.ALL_CHARTS) <= set(seed.editions(client))
