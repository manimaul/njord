#!/usr/bin/env python3
"""Keep testdata/frontend/routes.json and the copy embedded in RouteFixtureTest identical.

The shared_fe module is Kotlin/JS run in a browser, so its test cannot read testdata/. The fixture
text lives in the test between the `ROUTES_FIXTURE_BEGIN` / `ROUTES_FIXTURE_END` markers and is
mirrored into testdata/frontend/routes.json for the Rust port. This script exits non-zero when the
two differ (CI), or rewrites the JSON file from the test with `--write`. Run from the repo root.
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
TEST = ROOT / "shared_fe/src/commonTest/kotlin/io/madrona/njord/viewmodel/utils/RouteFixtureTest.kt"
FIXTURE = ROOT / "testdata/frontend/routes.json"


def embedded() -> str:
    text = TEST.read_text()
    match = re.search(r'ROUTES_FIXTURE_BEGIN.*?"""\n(.*?)"""\n// ROUTES_FIXTURE_END', text, re.S)
    if not match:
        sys.exit(f"no ROUTES_FIXTURE markers in {TEST}")
    return match.group(1).strip() + "\n"


def main() -> int:
    text = embedded()
    json.loads(text)  # must at least be JSON
    if "--write" in sys.argv:
        FIXTURE.parent.mkdir(parents=True, exist_ok=True)
        FIXTURE.write_text(text)
        print(f"wrote {FIXTURE.relative_to(ROOT)}")
        return 0
    if not FIXTURE.exists():
        print(f"{FIXTURE.relative_to(ROOT)} is missing; run with --write")
        return 1
    if FIXTURE.read_text() != text:
        print(f"{FIXTURE.relative_to(ROOT)} differs from the ROUTES_FIXTURE embedded in {TEST.relative_to(ROOT)}; run with --write")
        return 1
    print("routes fixture in sync")
    return 0


if __name__ == "__main__":
    sys.exit(main())
