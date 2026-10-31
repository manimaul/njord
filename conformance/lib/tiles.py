"""MVT decoding and canonicalisation for tile comparisons.

Tiles are compared *decoded* (RUST_PORT_PLAN.md §5.5): two correct encoders may order layers,
keys and values differently, and feature ids are an encoder artefact (an autoincrement in the
Kotlin encoder). The raw bytes' sha256 and size are recorded for information only.

Geometry is canonicalised for what carries no meaning in MVT (or GeoJSON): the vertex a closed
ring or closed line starts at, the order of the parts of a multi-geometry and of a polygon's
holes. Tile features are then compared by `tile_differences`: layers, feature presence, types,
properties and the number of parts exactly, vertices within `PIXEL_TOLERANCE`. `?info=true`
bodies get the canonicalisation through `canonical_geojson`, after rounding their WGS84
coordinates to `INFO_DECIMALS` and orienting their rings (`orient_rings`).
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


INFO_DECIMALS = 7
"""
`?info=true` coordinates are compared rounded to 7 decimals (about 1 cm): the source S-57
coordinates have that precision (COMF 10^7), and vertices made by the clip's boolean operations
agree between GEOS (Kotlin) and `geo` (Rust) only to about 1e-11 degrees.
"""


def _canonical_ring(ring: list) -> list:
    """A closed ring (or closed line) rotated to start at its smallest vertex; open lines unchanged."""
    pts = [list(p) for p in ring]
    if len(pts) < 3 or pts[0] != pts[-1]:
        return pts
    body = pts[:-1]
    smallest = min(body)
    start = min((body[i:] + body[:i] for i, p in enumerate(body) if p == smallest))
    return start + [start[0]]


def _canonical_polygon(rings: list) -> list:
    if not rings:
        return []
    return [_canonical_ring(rings[0])] + sorted(_canonical_ring(r) for r in rings[1:])


def canonical_coordinates(kind: str | None, coords):
    """
    Canonical coordinates of a geometry of type `kind`: closed rings and closed lines start at
    their smallest vertex (direction kept), multi-geometry parts and polygon holes are sorted.
    """
    if not isinstance(coords, list):
        return coords
    if kind == "MultiPoint":
        return sorted(list(p) for p in coords)
    if kind == "LineString":
        return _canonical_ring(coords)
    if kind == "MultiLineString":
        return sorted(_canonical_ring(line) for line in coords)
    if kind == "Polygon":
        return _canonical_polygon(coords)
    if kind == "MultiPolygon":
        return sorted(_canonical_polygon(p) for p in coords)
    return coords


PIXEL_TOLERANCE = 0.5
"""
Tile pixels. Two drawings of a feature match when every vertex of each lies within this distance
of the other's polyline (`hausdorff`), with the same number of parts. GEOS (Kotlin) and `geo`
(Rust) simplify from different ring starts, so Douglas–Peucker keeps a vertex more or fewer
(seen up to 0.1 px on the fixture cells); the same 0.5 px rule is the P-TE-5 gate in
`crates/njord-server/tests/tiles.rs`. Decided 2026-10-30.
"""


def _paths(kind: str | None, coords) -> list[list[tuple[float, float]]]:
    """Every point sequence of a decoded tile geometry: one per point, line or ring."""
    if not isinstance(coords, list) or not coords:
        return []
    if kind == "Point":
        return [[tuple(coords)]]
    if kind == "MultiPoint":
        return [[tuple(p)] for p in coords]
    if kind == "LineString":
        return [[tuple(p) for p in coords]]
    if kind in ("MultiLineString", "Polygon"):
        return [[tuple(p) for p in part] for part in coords]
    if kind == "MultiPolygon":
        return [[tuple(p) for p in ring] for polygon in coords for ring in polygon]
    return []


def _to_segment(p, a, b) -> float:
    dx, dy = b[0] - a[0], b[1] - a[1]
    len2 = dx * dx + dy * dy
    t = 0.0 if len2 == 0 else max(0.0, min(1.0, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / len2))
    return math.hypot(p[0] - a[0] - t * dx, p[1] - a[1] - t * dy)


def hausdorff(a: list, b: list) -> float:
    """
    The largest distance from a vertex of either path set to the nearest segment (or lone vertex)
    of the other: how far apart the two drawings are, whatever vertices each keeps.
    """
    def segments(paths):
        out = []
        for p in paths:
            out += [(p[0], p[0])] if len(p) == 1 else list(zip(p, p[1:]))
        return out

    def one_way(x, y):
        segs = segments(y)
        return max((min(_to_segment(p, s0, s1) for s0, s1 in segs) for path in x for p in path), default=0.0)

    if not any(a) or not any(b):
        return math.inf
    return max(one_way(a, b), one_way(b, a))


def _feature_differences(e: dict, a: dict, path: str) -> list[str]:
    if e == a:
        return []
    if e.get("type") != a.get("type") or e.get("properties") != a.get("properties"):
        return [f"{path}: expected {e.get('type')} {e.get('properties')}, got {a.get('type')} {a.get('properties')}"]
    ep, ap = _paths(e.get("type"), e.get("geometry")), _paths(a.get("type"), a.get("geometry"))
    d = hausdorff(ep, ap)
    if len(ep) != len(ap) or d > PIXEL_TOLERANCE:
        return [f"{path}/geometry: {len(ep)} parts expected, got {len(ap)}, {d:.2f} px apart (tolerance {PIXEL_TOLERANCE})"]
    return []


def tile_differences(expected: dict, actual: dict, path: str = "/decoded") -> list[str]:
    """
    Differences between two canonical views (`canonical_view`). Layers and their `extent` /
    `version` must match exactly. Within a layer, features are paired by identity (LNAM, else the
    properties), the closest drawings first, so a missing or extra feature always fails; a pair
    matches when type, properties and part count are equal and the drawings lie within
    `PIXEL_TOLERANCE`.
    """
    if not isinstance(expected, dict) or not isinstance(actual, dict):
        return [] if expected == actual else [f"{path}: expected {expected!r}, got {actual!r}"]
    out: list[str] = []
    for name in sorted(set(expected) | set(actual)):
        lp = f"{path}/{name}"
        if name not in actual:
            out.append(f"{lp}: layer missing")
            continue
        if name not in expected:
            out.append(f"{lp}: unexpected layer")
            continue
        el, al = expected[name], actual[name]
        for k in ("extent", "version"):
            if el.get(k) != al.get(k):
                out.append(f"{lp}/{k}: expected {el.get(k)!r}, got {al.get(k)!r}")

        def identity(f):
            props = f.get("properties") or {}
            lnam = props.get("LNAM")
            return str(lnam) if lnam is not None else json.dumps(props, sort_keys=True, default=str)

        groups: dict[str, tuple[list, list]] = {}
        for f in el.get("features", []):
            groups.setdefault(identity(f), ([], []))[0].append(f)
        for f in al.get("features", []):
            groups.setdefault(identity(f), ([], []))[1].append(f)
        for ident in sorted(groups):
            es, as_ = groups[ident]
            fp = f"{lp}/features[{ident}]"
            if len(es) != len(as_):
                out.append(f"{fp}: {len(es)} features expected, got {len(as_)}")
            remaining = list(as_)
            for e in es:
                if not remaining:
                    break
                ep = _paths(e.get("type"), e.get("geometry"))
                best = min(remaining, key=lambda a: (a.get("type") != e.get("type"),
                                                    hausdorff(ep, _paths(a.get("type"), a.get("geometry")))))
                remaining.remove(best)
                out += _feature_differences(e, best, fp)
        if len(out) >= 20:
            return out[:20]
    return out


def _signed_area(ring: list) -> float:
    return sum(x0 * y1 - x1 * y0 for (x0, y0), (x1, y1) in zip(ring, ring[1:]))


def orient_rings(geometry):
    """
    A GeoJSON geometry with every polygon exterior counter-clockwise and every hole clockwise
    (RFC 7946 §3.1.6). Kotlin (GEOS) writes the `?info=true` exteriors clockwise and the port
    counter-clockwise, as RFC 7946 asks; ring direction is accepted either way (decided
    2026-10-30), so both sides are oriented before comparing.
    """
    if not isinstance(geometry, dict):
        return geometry
    kind, coords = geometry.get("type"), geometry.get("coordinates")

    def polygon(rings):
        out = []
        for i, ring in enumerate(rings):
            ccw = _signed_area(ring) > 0
            out.append(ring if ccw == (i == 0) else list(reversed(ring)))
        return out

    if kind == "Polygon" and isinstance(coords, list):
        return {**geometry, "coordinates": polygon(coords)}
    if kind == "MultiPolygon" and isinstance(coords, list):
        return {**geometry, "coordinates": [polygon(p) for p in coords]}
    if kind == "GeometryCollection":
        return {**geometry, "geometries": [orient_rings(g) for g in geometry.get("geometries", [])]}
    return geometry


def _round(coords, decimals: int):
    if isinstance(coords, list):
        return [_round(c, decimals) for c in coords]
    if isinstance(coords, float):
        return round(coords, decimals) + 0.0  # + 0.0 folds -0.0 into 0.0
    return coords


def canonical_geojson(geometry, decimals: int | None = None):
    """A GeoJSON geometry with canonical coordinates (`canonical_coordinates`), optionally rounded first."""
    if not isinstance(geometry, dict):
        return geometry
    out = dict(geometry)
    if geometry.get("type") == "GeometryCollection" and isinstance(geometry.get("geometries"), list):
        parts = [canonical_geojson(g, decimals) for g in geometry["geometries"]]
        out["geometries"] = sorted(parts, key=lambda g: json.dumps(g, sort_keys=True, default=str))
        return out
    if "coordinates" in geometry:
        coords = geometry["coordinates"]
        if decimals is not None:
            coords = _round(coords, decimals)
        out["coordinates"] = canonical_coordinates(geometry.get("type"), coords)
    return out


def _sort_key(feature: dict) -> tuple:
    props = feature.get("properties") or {}
    lnam = props.get("LNAM")
    return (
        str(lnam) if lnam is not None else "",
        json.dumps(props, sort_keys=True, default=str),
        str(feature.get("type")),
        json.dumps(feature.get("geometry"), sort_keys=True, default=str),
    )


def canonical_view(view: dict) -> dict:
    """
    Re-canonicalises a stored decoded view (`{layer: {extent, version, features: [{type,
    properties, geometry}]}}`), so goldens recorded before a canonicalisation rule existed compare
    the same as new ones. Idempotent.
    """
    if not isinstance(view, dict):
        return view
    out: dict = {}
    for name in sorted(view):
        layer = view[name]
        if not isinstance(layer, dict):
            out[name] = layer
            continue
        features = [
            {**f, "geometry": canonical_coordinates(f.get("type"), f.get("geometry"))}
            for f in layer.get("features", [])
        ]
        out[name] = {**layer, "features": sorted(features, key=_sort_key)}
    return out


def canonical(decoded: dict) -> dict:
    """
    Layers sorted by name; within a layer, features sorted by (LNAM, props, type, geometry) with
    the encoder-assigned `id` dropped and the geometry canonical (`canonical_coordinates`).
    Coordinates are left exactly as decoded (tile pixels, `mapbox_vector_tile` default
    orientation), so vertex-level differences are visible.
    """
    view = {
        name: {
            "extent": layer.get("extent"),
            "version": layer.get("version"),
            "features": [
                {
                    "type": f.get("geometry", {}).get("type"),
                    "properties": f.get("properties") or {},
                    "geometry": f.get("geometry", {}).get("coordinates"),
                }
                for f in layer.get("features", [])
            ],
        }
        for name, layer in decoded.items()
    }
    return canonical_view(view)


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
