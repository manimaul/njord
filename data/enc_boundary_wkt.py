#!/usr/bin/env python3
"""
Output the union boundary of all S-57 charts in a directory as WKT.

The boundary of each chart is taken from the M_COVR layer geometry.
Charts are discovered by recursively searching for *.000 files.

Usage:
    python3 enc_boundary_wkt.py <directory>
"""

import argparse
import sys
from pathlib import Path

from osgeo import gdal, ogr

gdal.UseExceptions()


def get_chart_scale(ds) -> int | None:
    """Return the compilation scale (DSPM_CSCL) from the DSID layer, or None."""
    layer = ds.GetLayerByName("DSID")
    if layer is None:
        return None
    feature = layer.GetNextFeature()
    if feature is None:
        return None
    idx = feature.GetFieldIndex("DSPM_CSCL")
    if idx < 0:
        return None
    return feature.GetField(idx)


def get_mcovr_geometries(enc_path: str, max_scale: int) -> list:
    """Open an S-57 file and return all M_COVR geometries, skipping charts with scale >= max_scale."""
    ds = gdal.OpenEx(enc_path, gdal.OF_VECTOR)
    if ds is None:
        print(f"  WARNING: could not open {enc_path}", file=sys.stderr)
        return []

    scale = get_chart_scale(ds)
    print(f"  {enc_path} {scale}", file=sys.stderr)
    if scale is not None and scale >= max_scale:
        print(f"  SKIP (scale 1:{scale} >= 1:{max_scale}): {enc_path}", file=sys.stderr)
        return []

    layer = ds.GetLayerByName("M_COVR")
    if layer is None:
        return []

    geoms = []
    for feature in layer:
        geom = feature.GetGeometryRef()
        if geom is not None:
            geoms.append(geom.Clone())

    return geoms


def main():
    parser = argparse.ArgumentParser(
        description="Output the union of all S-57 chart M_COVR boundaries as WKT"
    )
    parser.add_argument("directory", help="Directory to search for S-57 .000 files")
    parser.add_argument(
        "--pretty", action="store_true", help="Print WKT with indentation (ISO WKT)"
    )
    parser.add_argument(
        "--geojson", action="store_true", help="Output as GeoJSON instead of WKT"
    )
    parser.add_argument(
        "--max-scale", type=int, default=900000,
        help="Exclude charts with compilation scale >= this value (default: 900000)"
    )
    args = parser.parse_args()

    root = Path(args.directory)
    if not root.is_dir():
        print(f"ERROR: {root} is not a directory", file=sys.stderr)
        sys.exit(1)

    enc_files = sorted(root.rglob("*.000"))
    if not enc_files:
        print(f"ERROR: no .000 files found under {root}", file=sys.stderr)
        sys.exit(1)

    print(f"Found {len(enc_files)} S-57 file(s) (excluding scale >= 1:{args.max_scale})", file=sys.stderr)

    union = None
    for enc_path in enc_files:
        # print(f"  {enc_path}", file=sys.stderr)
        for geom in get_mcovr_geometries(str(enc_path), args.max_scale):
            union = geom if union is None else union.Union(geom)

    if union is None:
        print("ERROR: no M_COVR geometries found", file=sys.stderr)
        sys.exit(1)

    if args.geojson:
        print(union.ExportToJson())
    elif args.pretty:
        print(union.ExportToIsoWkt())
    else:
        print(union.ExportToWkt())


if __name__ == "__main__":
    main()
