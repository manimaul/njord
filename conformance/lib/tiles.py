"""MVT decoding and canonicalisation for tile comparisons.

Tiles are compared *decoded* (RUST_PORT_PLAN.md §5.5): two correct encoders may order layers,
keys and values differently, and feature ids are an encoder artefact (an autoincrement in the
Kotlin encoder). The raw bytes' sha256 and size are recorded for information only.
"""

from __future__ import annotations

import gzip
import hashlib
import json
import math

import mapbox_vector_tile


def maybe_gunzip(data: bytes) -> bytes:
    return gzip.decompress(data) if data[:2] == b"\x1f\x8b" else data


def decode(data: bytes) -> dict:
    """`{layer: {extent, version, features: [...]}}`; an empty body decodes to `{}`."""
    data = maybe_gunzip(data)
    if not data:
        return {}
    return mapbox_vector_tile.decode(data)


def _sort_key(feature: dict) -> tuple:
    props = feature.get("properties") or {}
    lnam = props.get("LNAM")
    return (
        str(lnam) if lnam is not None else "",
        json.dumps(props, sort_keys=True, default=str),
        json.dumps(feature.get("geometry"), sort_keys=True, default=str),
    )


def canonical(decoded: dict) -> dict:
    """
    Layers sorted by name; within a layer, features sorted by (LNAM, props, geometry) with the
    encoder-assigned `id` dropped. Geometry coordinates are left exactly as decoded (tile pixels,
    `mapbox_vector_tile` default orientation), so vertex-level differences are visible.
    """
    out: dict = {}
    for name in sorted(decoded):
        layer = decoded[name]
        features = []
        for f in sorted(layer.get("features", []), key=_sort_key):
            features.append({
                "type": f.get("geometry", {}).get("type"),
                "properties": f.get("properties") or {},
                "geometry": f.get("geometry", {}).get("coordinates"),
            })
        out[name] = {"extent": layer.get("extent"), "version": layer.get("version"), "features": features}
    return out


def summarize(data: bytes) -> dict:
    """What a tile golden stores: size and sha256 (informational) plus the canonical decoded view."""
    return {
        "size": len(data),
        "sha256": hashlib.sha256(data).hexdigest(),
        "decoded": canonical(decode(data)),
    }


def lon_lat_to_tile(lon: float, lat: float, z: int) -> tuple[int, int]:
    """Web Mercator tile containing a WGS84 coordinate (XYZ scheme, y down)."""
    n = 2 ** z
    x = int((lon + 180.0) / 360.0 * n)
    lat_rad = math.radians(lat)
    y = int((1.0 - math.log(math.tan(lat_rad) + 1.0 / math.cos(lat_rad)) / math.pi) / 2.0 * n)
    return min(max(x, 0), n - 1), min(max(y, 0), n - 1)
