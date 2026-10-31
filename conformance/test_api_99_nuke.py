"""P-API-9 (continued): `DELETE /v1/nuke` wipes charts, features, base features, the tile cache and the
region archives. Runs last because everything else needs the data; re-seeds afterwards so the
next run starts populated. Local servers only.
"""

import pytest

from lib import seed
from lib.engine import Spec

G = "api_99_nuke"
pytestmark = [pytest.mark.mutating, pytest.mark.signed]


def test_nuke_empties_everything_then_reseed(checker, client, signature, seeded):
    checker.check(f"{G}/nuke", Spec("/v1/nuke", method="DELETE", signed=True))
    checker.check(f"{G}/chart_catalog_after", Spec("/v1/chart_catalog"))
    checker.check(f"{G}/chart_editions_after", Spec("/v1/chart_editions"))
    checker.check(f"{G}/cache_after", Spec("/v1/cache"))
    manifest = client.get("/v1/regions").json()
    assert all(e["archive"] is None for e in manifest), manifest
    assert client.get(f"/v1/chart?name={seed.CELL}.000").status_code == 404

    seed.ensure_seeded(client, signature)
    assert set(seed.ALL_CHARTS) <= set(seed.editions(client))
