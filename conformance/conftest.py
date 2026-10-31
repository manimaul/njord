"""Njord HTTP conformance suite (RUST_PORT_PLAN.md §5.5).

    pytest conformance --base-url=$URL --record              # against the Kotlin server: rewrites golden/
    pytest conformance --base-url=$URL                       # verify against golden/
    pytest conformance --base-url=$A --compare-url=$B        # diff two live servers, goldens untouched

See README.md for the options, markers and comparison policy.
"""

from __future__ import annotations

import os
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent))

from lib import seed as seedlib  # noqa: E402
from lib.client import Client  # noqa: E402
from lib.engine import Checker, Target  # noqa: E402

HERE = Path(__file__).resolve().parent
LOCAL_ADMIN = ("admin", "admin", "changeme")  # server/src/nativeMain/resources/config/application.json


def pytest_addoption(parser: pytest.Parser) -> None:
    g = parser.getgroup("conformance")
    g.addoption("--base-url", default=os.environ.get("NJORD_BASE_URL"), help="server under test, e.g. http://localhost:9000")
    g.addoption("--compare-url", default=None, help="diff mode: second live server to compare against (no goldens)")
    g.addoption("--record", action="store_true", help="record mode: rewrite golden/ from --base-url (a Kotlin server)")
    g.addoption("--golden-dir", default=str(HERE / "golden"), help="golden directory (default conformance/golden)")
    g.addoption("--compare", default="auto", choices=("auto", "exact", "shape"),
                help="how data-dependent routes are compared; auto = exact when the chart catalog matches the recording")
    g.addoption("--admin-user", default=None, help="basic-auth user for GET /v1/admin (default: admin locally, $NJORD_ADMIN_USER otherwise)")
    g.addoption("--admin-pass", default=None, help="basic-auth password (default: admin locally, $NJORD_ADMIN_PASS otherwise)")
    g.addoption("--admin-key", default=None, help="server adminKey, lets the suite mint signatures for expiry tests (default: changeme locally)")
    g.addoption("--allow-mutating", action="store_true", help="run mutating tests against a non-local server (never openenc.com)")
    g.addoption("--server-impl", default="auto", choices=("auto", "kotlin", "rust"), help="skip port_only tests on kotlin")
    g.addoption("--trust-forwarded", action="store_true", help="run X-Forwarded-* tests against a non-local server")
    g.addoption("--skip-seed", action="store_true", help="do not ingest the fixture cells even if missing")


class Config:
    def __init__(self, pytestconfig: pytest.Config) -> None:
        opt = pytestconfig.getoption
        self.base_url: str | None = opt("--base-url")
        self.compare_url: str | None = opt("--compare-url")
        self.record: bool = opt("--record")
        self.golden_dir = Path(opt("--golden-dir"))
        self.compare_policy: str = opt("--compare")
        self.server_impl: str = opt("--server-impl")
        self.trust_forwarded: bool = opt("--trust-forwarded")
        self.skip_seed: bool = opt("--skip-seed")
        if not self.base_url:
            raise pytest.UsageError("--base-url (or NJORD_BASE_URL) is required")
        if self.record and self.compare_url:
            raise pytest.UsageError("--record and --compare-url are exclusive")
        self.mode = "record" if self.record else "diff" if self.compare_url else "verify"
        client = Client(self.base_url)
        local = client.is_local
        self.admin_user = opt("--admin-user") or (LOCAL_ADMIN[0] if local else os.environ.get("NJORD_ADMIN_USER"))
        self.admin_pass = opt("--admin-pass") or (LOCAL_ADMIN[1] if local else os.environ.get("NJORD_ADMIN_PASS"))
        self.admin_key = opt("--admin-key") or (LOCAL_ADMIN[2] if local else os.environ.get("NJORD_ADMIN_KEY"))
        # Diff mode compares two live servers that may share a database, so a mutation on one shows
        # up as a difference on the other: diff mode is read-only by design.
        self.allow_mutating = (local or opt("--allow-mutating")) and self.mode != "diff"
        if "openenc.com" in client.host and opt("--allow-mutating"):
            raise pytest.UsageError("refusing --allow-mutating against openenc.com")


@pytest.fixture(scope="session")
def config(pytestconfig: pytest.Config) -> Config:
    return Config(pytestconfig)


def _target(base_url: str, config: Config) -> Target:
    client = Client(base_url)
    admin = (config.admin_user, config.admin_pass) if config.admin_user and config.admin_pass else None
    signature = None
    login = None
    if admin:
        try:
            login = client.login(*admin)
            signature = login["signatureEncoded"]
        except Exception as e:  # noqa: BLE001 - creds wrong or server down; signed tests will skip
            print(f"\nadmin login at {base_url} failed: {e}", file=sys.stderr)
            admin = None
    return Target(client=client, signature=signature, admin=admin, login=login)


@pytest.fixture(scope="session")
def primary(config: Config) -> Target:
    return _target(config.base_url, config)


@pytest.fixture(scope="session")
def secondary(config: Config) -> Target | None:
    return _target(config.compare_url, config) if config.compare_url else None


@pytest.fixture(scope="session")
def client(primary: Target) -> Client:
    return primary.client


@pytest.fixture(scope="session")
def signature(primary: Target) -> str:
    if primary.signature is None:
        pytest.skip("needs an admin signature (--admin-user/--admin-pass)")
    return primary.signature


@pytest.fixture(scope="session")
def admin_auth(primary: Target) -> tuple[str, str]:
    if primary.admin is None:
        pytest.skip("needs admin credentials (--admin-user/--admin-pass)")
    return primary.admin


@pytest.fixture(scope="session")
def admin_key(config: Config) -> str:
    if not config.admin_key:
        pytest.skip("needs the server's admin key (--admin-key) to mint signatures")
    return config.admin_key


def detect_impl(client: Client) -> str:
    """Kotlin unless the about body says otherwise: an `implementation` field or `rust` in the version."""
    about = client.get("/v1/about/version").json()
    impl = str(about.get("implementation", "")).lower()
    if impl in ("kotlin", "rust"):
        return impl
    return "rust" if "rust" in str(about.get("version", "")).lower() else "kotlin"


_IMPL = pytest.StashKey[str]()


def impl_under_test(pytestconfig: pytest.Config) -> str:
    """`--server-impl`, or the `--base-url` server's detected implementation (cached per session)."""
    if _IMPL not in pytestconfig.stash:
        chosen = pytestconfig.getoption("--server-impl")
        pytestconfig.stash[_IMPL] = chosen if chosen != "auto" else detect_impl(Client(pytestconfig.getoption("--base-url")))
    return pytestconfig.stash[_IMPL]


@pytest.fixture(scope="session")
def server_impl(pytestconfig: pytest.Config) -> str:
    return impl_under_test(pytestconfig)


def pytest_runtest_setup(item: pytest.Item) -> None:
    """
    `port_pending(reason)`: the route is not ported yet (RUST_PORT_PLAN.md phases), so the test is
    skipped when the `--base-url` server is the Rust port. Checked here, before any fixture is set
    up, so a module fixture that waits for the missing feature (the region export worker) never runs.
    """
    marker = item.get_closest_marker("port_pending")
    if marker and not item.config.getoption("--record") and impl_under_test(item.config) == "rust":
        reason = marker.args[0] if marker.args else "not ported yet"
        pytest.skip(f"port_pending: {reason}")


@pytest.fixture(scope="session")
def seeded(config: Config, primary: Target) -> dict[str, str]:
    """The fixture cells are present (ingesting them through the API when allowed); their editions."""
    have = seedlib.editions(primary.client)
    missing = [c for c in seedlib.ALL_CHARTS if c not in have]
    if missing and config.allow_mutating and primary.signature and not config.skip_seed:
        seedlib.ensure_seeded(primary.client, primary.signature)
        have = seedlib.editions(primary.client)
        missing = [c for c in seedlib.ALL_CHARTS if c not in have]
    if not have:
        pytest.skip(f"{config.base_url} holds no charts and seeding is not possible here")
    return have


@pytest.fixture(scope="session")
def sample(seeded: dict[str, str], client: Client) -> dict[str, str | None]:
    """
    The chart and LNAM the data-route tests ask for: the fixture cell when the target holds it
    (exact comparison), otherwise the first chart of the target's own catalog and a LIGHTS LNAM
    from it (shape comparison - production no longer carries the fixture cell's edition).
    """
    cell = f"{seedlib.CELL}.000"
    if cell in seeded:
        return {"chart": cell, "lights_chart": cell, "lnam": "022600019FEA0001", "fixture": "yes"}
    chart = client.get("/v1/chart_catalog").json()["page"][0]["name"]
    page = client.get("/v1/feature/layer/LIGHTS?start_id=0").json()
    light = next((i for i in page.get("items", []) if i.get("props", {}).get("LNAM")), None)
    lnam = light["props"]["LNAM"] if light else None
    lights_chart = light["chartName"] if light else chart  # a chart that certainly has LIGHTS features
    print(f"\nfixture cell absent on {client.base_url}; data routes use {chart}, {lights_chart} / LNAM {lnam} by shape", file=sys.stderr)
    return {"chart": chart, "lights_chart": lights_chart, "lnam": lnam, "fixture": "no"}


@pytest.fixture(scope="session")
def synthetic_present(seeded: dict[str, str]) -> None:
    """The two synthetic cells (testdata/fixtures/synthetic) are ingested; production never has them."""
    missing = [f"{c}.000" for c in seedlib.SYNTHETIC if f"{c}.000" not in seeded]
    if missing:
        pytest.skip(f"synthetic fixture cells not ingested: {missing}")


@pytest.fixture(scope="session")
def checker(config: Config, primary: Target, secondary: Target | None, server_impl: str) -> Checker:
    state: dict[str, bool] = {}

    def catalog_matches() -> bool:
        """Exact comparison of data routes only when both sides hold the same chart editions."""
        if "value" not in state:
            mine = seedlib.editions(primary.client)
            if config.mode == "diff":
                theirs = seedlib.editions(secondary.client)  # type: ignore[union-attr]
            else:
                golden = config.golden_dir / "api_04_charts" / "chart_editions.json"
                theirs = None
                if golden.exists():
                    import json
                    theirs = json.loads(golden.read_text())["expected"]["body"].get("editions")
            state["value"] = mine == theirs
            print(f"\nchart catalog matches the comparison baseline: {state['value']}", file=sys.stderr)
        return state["value"]

    compare_impl = detect_impl(secondary.client) if secondary else None
    checker = Checker(config.mode, config.golden_dir, primary, secondary, server_impl, config.compare_policy, catalog_matches,
                      compare_impl)
    about = primary.client.get("/v1/about/version").json()
    checker.record_build(about)
    recorded = checker.recorded_build()
    if config.mode == "verify" and recorded and recorded.get("gitHash") != about.get("gitHash"):
        print(
            f"\nWARNING: goldens were recorded from build {recorded.get('gitHash')} ({recorded.get('gitBranch')}, "
            f"{recorded.get('buildDate')}) but {config.base_url} runs {about.get('gitHash')} ({about.get('gitBranch')}, "
            f"{about.get('buildDate')}); exact routes (style, sprites) may legitimately differ between builds.",
            file=sys.stderr,
        )
    return checker


def pytest_report_header(config: pytest.Config) -> list[str]:
    c = Config(config)
    return [f"njord conformance: mode={c.mode} base-url={c.base_url} compare-url={c.compare_url or '-'} "
            f"compare={c.compare_policy} golden={c.golden_dir} mutating={'yes' if c.allow_mutating else 'no'}"]


@pytest.fixture(autouse=True)
def _markers(request: pytest.FixtureRequest, config: Config) -> None:
    item = request.node
    if item.get_closest_marker("mutating") and not config.allow_mutating:
        pytest.skip("mutating test; target is not local (use --allow-mutating, never against openenc.com)")
    if item.get_closest_marker("port_only"):
        if config.mode == "record":
            pytest.skip("port_only: not recorded against the Kotlin server")
        impl = request.getfixturevalue("server_impl")
        if impl == "kotlin":
            pytest.skip("port_only: accepted Kotlin defect, fixed in the Rust port (TEST_COVERAGE_AUDIT.md §3.1)")
    if item.get_closest_marker("signed"):
        request.getfixturevalue("signature")
    if item.get_closest_marker("local_only") and not (Client(config.base_url).is_local or config.trust_forwarded):
        pytest.skip("depends on the server trusting client-sent headers (--trust-forwarded to force)")
