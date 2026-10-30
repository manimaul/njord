"""Seeds a local server with the fixture cells through the public API (the P-API-8 path)."""

from __future__ import annotations

import io
import time
import zipfile
from pathlib import Path

from . import ws as wslib
from .client import Client

CELL = "US5WA22M"
SYNTHETIC = ("US4ZZ01M", "US5ZZ02M")
ALL_CHARTS = (f"{CELL}.000",) + tuple(f"{c}.000" for c in SYNTHETIC)


def fixtures_root() -> Path:
    """`testdata/` of the repo this suite lives in."""
    return Path(__file__).resolve().parents[2] / "testdata"


def zip_cells(cells: dict[str, Path]) -> bytes:
    """A NOAA-style zip: `ENC_ROOT/<CELL>/<files…>` so GDAL finds the `.00N` updates and `.TXT` sidecars."""
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as zf:
        for cell, directory in cells.items():
            for f in sorted(directory.iterdir()):
                if f.is_file() and f.name.upper().startswith(cell) and not f.name.endswith(".md"):
                    zf.write(f, f"ENC_ROOT/{cell}/{f.name}")
    return buf.getvalue()


def fixture_cell_zip() -> bytes:
    root = fixtures_root() / "fixtures"
    return zip_cells({CELL: root / CELL})


def synthetic_zip() -> bytes:
    root = fixtures_root() / "fixtures" / "synthetic"
    return zip_cells({c: root for c in SYNTHETIC})


def ingest(client: Client, signature: str, name: str, data: bytes, timeout: float = 900.0, settle: bool = True) -> list[dict]:
    """
    Uploads one zip, waits for the ingest to finish, resets the upload state. Returns the WsMsg list.

    With `settle` (default) it first waits for any region export in flight and afterwards for the
    exports the ingest triggers. The Kotlin server shares one lock between ingest and region export
    and clears it without checking ownership (TEST_COVERAGE_AUDIT.md D6): an upload during an
    export aborts the export, and the export's cleanup then aborts the ingest, which leaves the
    status file at its last progress message. Keeping the two apart makes the sequence deterministic.
    """
    if settle:
        wait_for_exports_to_settle(client)
    started = time.time()
    r = client.upload(f"/v1/enc_save?filename={name}", data, signature)
    r.raise_for_status()
    assert r.json() == {"zipFile": name}, r.text
    messages = wslib.collect_until_complete(client.ws_base, signature, timeout=timeout)
    client.request("DELETE", "/v1/enc_save", signature=signature).raise_for_status()
    if settle:
        wait_for_exports_after(client, started)
    return messages


def _created_at(entry: dict) -> float:
    value = entry.get("createdAt")
    if not value:
        return 0.0
    from datetime import datetime
    return datetime.fromisoformat(value.replace("Z", "+00:00")).timestamp()


def wait_for_exports_after(client: Client, since: float, timeout: float = 900.0) -> None:
    """Every configured region has an archive rendered after `since` (an ingest re-renders all of them)."""
    deadline = time.monotonic() + timeout
    while True:
        manifest = client.get("/v1/regions").json()
        if manifest and all(_created_at(e) >= since - 1.0 for e in manifest):
            return
        if time.monotonic() > deadline:
            raise TimeoutError(f"regions were not re-rendered after {since}: {manifest}")
        time.sleep(3)


def wait_for_exports_to_settle(client: Client, quiet_for: float = 12.0, timeout: float = 900.0) -> None:
    """No archive has changed for `quiet_for` seconds: the export worker is idle (a local render takes a few seconds)."""
    deadline = time.monotonic() + timeout
    last = None
    unchanged_since = time.monotonic()
    while time.monotonic() < deadline:
        snapshot = tuple(sorted((e["name"], e.get("archive")) for e in client.get("/v1/regions").json()))
        if snapshot != last:
            last, unchanged_since = snapshot, time.monotonic()
        elif time.monotonic() - unchanged_since >= quiet_for:
            return
        time.sleep(2)
    raise TimeoutError("region exports never settled")


def editions(client: Client) -> dict[str, str]:
    r = client.get("/v1/chart_editions")
    r.raise_for_status()
    return r.json()["editions"]


def ensure_seeded(client: Client, signature: str) -> None:
    have = editions(client)
    if f"{CELL}.000" not in have:
        ingest(client, signature, f"{CELL}.zip", fixture_cell_zip())
    if any(f"{c}.000" not in have for c in SYNTHETIC):
        ingest(client, signature, "synthetic.zip", synthetic_zip())


def wait_for_archives(client: Client, names: tuple[str, ...], timeout: float = 1500.0) -> list[dict]:
    """Polls `GET /v1/regions` until every named region has an archive."""
    deadline = time.monotonic() + timeout
    while True:
        r = client.get("/v1/regions")
        r.raise_for_status()
        manifest = r.json()
        by_name = {e["name"]: e for e in manifest}
        if all(by_name.get(n, {}).get("archive") for n in names):
            return manifest
        if time.monotonic() > deadline:
            raise TimeoutError(f"regions {names} not rendered within {timeout}s: {manifest}")
        time.sleep(5)
