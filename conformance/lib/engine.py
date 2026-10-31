"""Record / verify / diff engine: one `Spec` per request, one golden file per spec.

A golden (`golden/<group>/<case>.json`) stores the request, the masking list that was applied,
the comparison policy and the masked response as recorded against the Kotlin server. See
`conformance/README.md` for the policy; the short version:

- **exact**: status, content type, captured headers and the masked body must be equal;
- **shape**: status, content type and the structural skeleton (`mask.shape`) must be equal. Used
  for data-dependent routes when the target holds a different chart catalog than the recording,
  and always for `volatile` specs (counts, archive bytes, the SPA fallback page).

Tiles are compared decoded and canonicalised (`tiles.canonical`); raw bytes' sha256 and size are
recorded but never asserted. `comparable_body` applies the comparison-only normalisations (tile
geometry canonicalisation, `?info=true` rounding, GeoJSON `null` members) to both sides.
"""

from __future__ import annotations

import hashlib
import json
import re
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Any, Callable

import pytest

from . import mask as masklib
from . import tiles as tileslib
from .client import Client

COMPACT_THRESHOLD = 200_000  # bytes of pretty JSON above which a golden is written compact

KINDS = ("json", "style", "geojson", "info", "tile", "bytes", "text", "html")


@dataclass
class Spec:
    path: str
    method: str = "GET"
    headers: dict[str, str] = field(default_factory=dict)
    body: bytes | None = None
    json: Any = None
    signed: bool = False
    basic_auth: bool = False
    kind: str = "json"
    masks: tuple[str, ...] = ()
    capture_headers: tuple[str, ...] = ()
    shape_depth: int = 2
    data: bool = False
    volatile: bool = False
    port: dict | None = None
    raw_path: bool = False
    note: str = ""

    def request_view(self) -> dict:
        return {
            "method": self.method,
            "path": self.path,
            "headers": self.headers,
            "json": "<per-target>" if callable(self.json) else self.json,
            "body_bytes": len(self.body) if self.body is not None else None,
            "signed": self.signed,
            "basic_auth": self.basic_auth,
            "raw_path": self.raw_path,
            "kind": self.kind,
        }


@dataclass
class Target:
    client: Client
    signature: str | None
    admin: tuple[str, str] | None
    login: dict | None = None  # the full AdminResponse the signature came from


def _sorted_features(features: list, key_props: str = "properties") -> list:
    def key(f: dict) -> tuple:
        props = f.get(key_props) or {}
        return (str(f.get("layer", "")), str(props.get("LNAM", "")), json.dumps(props, sort_keys=True, default=str),
                json.dumps(f.get("geometry") or f.get("geom"), sort_keys=True, default=str))

    return sorted(features, key=key)


def capture(target: Target, spec: Spec) -> dict:
    """Performs the request and reduces the response to the comparable form for `spec.kind`."""
    client = target.client
    if spec.raw_path:
        path = spec.path
        if spec.signed:
            if target.signature is None:
                pytest.skip("needs an admin signature (--admin-user/--admin-pass)")
            path = client.with_signature(path, target.signature)
        raw = client.raw(spec.method, path, spec.headers)
        status, headers, content = raw.status_code, raw.headers, raw.content
        content_type = headers.get("content-type", "")
        header_get = headers.get
    else:
        if spec.signed and target.signature is None:
            pytest.skip("needs an admin signature (--admin-user/--admin-pass)")
        if spec.basic_auth and target.admin is None:
            pytest.skip("needs admin credentials (--admin-user/--admin-pass)")
        # A callable body is built per target (e.g. that server's own admin signature).
        body_json = spec.json(target) if callable(spec.json) else spec.json
        if callable(spec.json) and body_json is None:
            pytest.skip("needs admin credentials (--admin-user/--admin-pass)")
        resp = client.request(
            spec.method,
            spec.path,
            headers=spec.headers or None,
            data=spec.body,
            json=body_json,
            auth=target.admin if spec.basic_auth else None,
            signature=target.signature if spec.signed else None,
        )
        status, content, content_type = resp.status_code, resp.content, resp.headers.get("Content-Type", "")
        header_get = resp.headers.get

    captured: dict[str, Any] = {
        "status": status,
        "content_type": content_type,
        "headers": {h: header_get(h) for h in spec.capture_headers},
    }
    base = client.base_url
    kind = spec.kind
    if kind == "tile":
        captured["body"] = tileslib.summarize(content) if status == 200 else content.decode("utf-8", "replace")
    elif kind == "bytes":
        captured["body"] = {"size": len(content), "sha256": hashlib.sha256(content).hexdigest()}
    elif kind == "html":
        captured["body"] = {"size": len(content)}
    elif kind == "text":
        captured["body"] = masklib.normalize_base(content.decode("utf-8", "replace"), base)
    else:
        if content and "json" in content_type:
            body = json.loads(content)
            if kind == "geojson" and isinstance(body, dict) and isinstance(body.get("features"), list):
                body["features"] = _sorted_features(body["features"])
            elif kind == "info" and isinstance(body, list):
                body = _sorted_features(body, key_props="props")
            elif kind == "style":
                body = masklib.normalize_style(body)
            body = masklib.apply_masks(body, list(spec.masks))
            captured["body"] = masklib.normalize_base(body, base)
        else:
            captured["body"] = masklib.normalize_base(content.decode("utf-8", "replace"), base) if content else None
    for h, v in captured["headers"].items():
        if isinstance(v, str):
            captured["headers"][h] = v.replace(base, masklib.BASE)
    return captured


def expected_for(golden: dict, impl: str) -> dict:
    """The recorded Kotlin result, or the port's divergent expectation when verifying the Rust port."""
    expected = dict(golden["expected"])
    port = golden.get("port")
    if impl == "rust" and port:
        for k in ("status", "content_type", "headers", "body"):
            if k in port:
                expected[k] = port[k]
    return expected


def comparable_body(body: Any, kind: str) -> Any:
    """
    The body as it is compared, applied to both sides (goldens keep what was recorded):

    - `tile`: the decoded view re-canonicalised (`tiles.canonical_view`: ring start vertex and
      multi-geometry part order carry no meaning in MVT); `compare` then matches vertices within
      `tiles.PIXEL_TOLERANCE` (`tiles.tile_differences`);
    - `info`: every feature's WGS84 geometry with its rings oriented (`tiles.orient_rings`: ring
      direction is accepted either way), rounded to `tiles.INFO_DECIMALS` and canonicalised the
      same way, then the list re-sorted;
    - every JSON kind: `null` members of GeoJSON objects dropped (`mask.drop_geojson_nulls`),
      `geojson` features re-sorted afterwards.
    """
    if kind == "tile":
        if isinstance(body, dict) and isinstance(body.get("decoded"), dict):
            return {**body, "decoded": tileslib.canonical_view(body["decoded"])}
        return body
    if kind in ("json", "style", "geojson", "info"):
        body = masklib.drop_geojson_nulls(body)
    if kind == "geojson" and isinstance(body, dict) and isinstance(body.get("features"), list):
        body = {**body, "features": _sorted_features(body["features"])}
    if kind == "info" and isinstance(body, list):
        body = _sorted_features(
            [{**f, "geom": tileslib.canonical_geojson(tileslib.orient_rings(f.get("geom")), tileslib.INFO_DECIMALS)}
             if isinstance(f, dict) else f
             for f in body],
            key_props="props",
        )
    return body


def compare(expected: dict, actual: dict, spec_kind: str, exact: bool, shape_depth: int) -> list[str]:
    diffs: list[str] = []
    expected = {**expected, "body": comparable_body(expected.get("body"), spec_kind)}
    actual = {**actual, "body": comparable_body(actual.get("body"), spec_kind)}
    if expected["status"] != actual["status"]:
        diffs.append(f"status: expected {expected['status']}, got {actual['status']}")
    if _content_type(expected["content_type"]) != _content_type(actual["content_type"]):
        diffs.append(f"content-type: expected {expected['content_type']!r}, got {actual['content_type']!r}")
    for h, ev in expected.get("headers", {}).items():
        av = actual.get("headers", {}).get(h)
        if exact and ev != av:
            diffs.append(f"header {h}: expected {ev!r}, got {av!r}")
        elif not exact and (ev is None) != (av is None):
            diffs.append(f"header {h}: expected {'present' if ev is not None else 'absent'}, got {av!r}")
    eb, ab = expected.get("body"), actual.get("body")
    if spec_kind == "tile":
        if not isinstance(eb, dict) or not isinstance(ab, dict):
            pass  # an error status; the status comparison above already covers it
        elif exact:
            diffs += tileslib.tile_differences(eb.get("decoded"), ab.get("decoded"))
        elif not isinstance(ab.get("decoded"), dict):
            diffs.append("tile body does not decode")
    elif spec_kind == "bytes":
        if exact and eb != ab:
            diffs.append(f"bytes: expected {eb}, got {ab}")
    elif spec_kind == "html":
        pass
    elif exact:
        diffs += masklib.differences(eb, ab)
    else:
        diffs += masklib.shape_differences(masklib.shape(eb, shape_depth), masklib.shape(ab, shape_depth))
    return diffs


def _content_type(value: str | None) -> str:
    return re.sub(r"\s+", "", (value or "").lower())


class Checker:
    """Runs specs in the configured mode and fails the test on any difference."""

    def __init__(
        self,
        mode: str,
        golden_dir: Path,
        primary: Target,
        secondary: Target | None,
        impl: str,
        compare_policy: str,
        catalog_matches: Callable[[], bool],
        compare_impl: str | None = None,
    ) -> None:
        self.mode = mode
        self.golden_dir = golden_dir
        self.primary = primary
        self.secondary = secondary
        self.impl = impl
        self.compare_policy = compare_policy
        self.catalog_matches = catalog_matches
        self.compare_impl = compare_impl  # diff mode: implementation of the --compare-url server
        self.recorded: list[str] = []

    def golden_path(self, name: str) -> Path:
        return self.golden_dir / f"{name}.json"

    BUILD_FILE = "_recorded_build.json"

    def record_build(self, about: dict) -> None:
        """The unmasked `/v1/about/version` of the server the goldens were recorded from (informational)."""
        if self.mode == "record":
            self.golden_dir.mkdir(parents=True, exist_ok=True)
            (self.golden_dir / self.BUILD_FILE).write_text(json.dumps(about, indent=2) + "\n")

    def recorded_build(self) -> dict | None:
        path = self.golden_dir / self.BUILD_FILE
        return json.loads(path.read_text()) if path.exists() else None

    def exact(self, spec: Spec) -> bool:
        if spec.volatile or spec.kind == "html":
            return False
        if self.compare_policy == "exact":
            return True
        if self.compare_policy == "shape":
            return False
        return self.catalog_matches() if spec.data else True

    def check(self, name: str, spec: Spec) -> dict:
        assert spec.kind in KINDS, spec.kind
        actual = capture(self.primary, spec)
        exact = self.exact(spec)
        if self.mode == "record":
            self._write(name, spec, actual)
            return actual
        if self.mode == "diff":
            assert self.secondary is not None
            other = capture(self.secondary, spec)
            # A decided divergence (`port`) is the port's expectation, not a difference, when a
            # Kotlin --base-url is diffed against a Rust --compare-url.
            expected = actual
            if spec.port and self.impl == "kotlin" and self.compare_impl == "rust":
                expected = expected_for({"expected": actual, "port": spec.port}, "rust")
            diffs = compare(expected, other, spec.kind, exact, spec.shape_depth)
            self._report(name, spec, diffs, exact, f"{self.primary.client.base_url} vs {self.secondary.client.base_url}")
            return actual
        golden = self._read(name)
        expected = expected_for(golden, self.impl)
        diffs = compare(expected, actual, spec.kind, exact, spec.shape_depth)
        self._report(name, spec, diffs, exact, f"golden {self.golden_path(name).relative_to(self.golden_dir.parent)}")
        return actual

    def check_json(self, name: str, value: Any, masks: tuple[str, ...] = (), note: str = "") -> None:
        """
        Like `check`, for a value the test computed itself (e.g. the WebSocket message sequence of
        an ingest). Recorded and verified exactly; in diff mode there is nothing to compare against
        on the second server, so it only records nothing and passes.
        """
        actual = {"status": None, "content_type": "", "headers": {}, "body": masklib.apply_masks(value, list(masks))}
        if self.mode == "record":
            self._write(name, Spec(path=f"<computed:{name}>", method="-", masks=masks, note=note), actual)
            return
        if self.mode == "diff":
            return
        golden = self._read(name)
        diffs = masklib.differences(golden["expected"]["body"], actual["body"])
        if diffs:
            pytest.fail(f"{name} differs from golden:\n  " + "\n  ".join(diffs), pytrace=False)

    def _report(self, name: str, spec: Spec, diffs: list[str], exact: bool, against: str) -> None:
        if diffs:
            policy = "exact" if exact else "shape"
            pytest.fail(
                f"{spec.method} {spec.path} differs from {against} ({policy} comparison):\n  "
                + "\n  ".join(diffs)
                + ("\n  … (truncated)" if len(diffs) >= 20 else "")
                + "\nIf the new behaviour is intended, re-record with --record against the Kotlin server.",
                pytrace=False,
            )

    def _write(self, name: str, spec: Spec, actual: dict) -> None:
        golden = {
            "request": spec.request_view(),
            "note": spec.note,
            "masks": list(spec.masks),
            "capture_headers": list(spec.capture_headers),
            "shape_depth": spec.shape_depth,
            "data_dependent": spec.data,
            "volatile": spec.volatile,
            "port": spec.port,
            "expected": actual,
        }
        path = self.golden_path(name)
        path.parent.mkdir(parents=True, exist_ok=True)
        text = json.dumps(golden, indent=2, sort_keys=False, ensure_ascii=False)
        if len(text) > COMPACT_THRESHOLD:
            text = json.dumps(golden, separators=(",", ":"), ensure_ascii=False)
        path.write_text(text + "\n", encoding="utf-8")
        self.recorded.append(name)

    def _read(self, name: str) -> dict:
        path = self.golden_path(name)
        if not path.exists():
            pytest.fail(f"no golden at {path}; record it with `pytest conformance --base-url=<kotlin server> --record`", pytrace=False)
        return json.loads(path.read_text(encoding="utf-8"))
