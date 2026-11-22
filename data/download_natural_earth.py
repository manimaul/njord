#!/usr/bin/env python3

"""
Download the Natural Earth physical vector zips the Njord base map is built from, into
{output-dir}/ne/<scale>/, and repair the ones the server cannot ingest as shipped (see REPAIR).

A zip that is already there is not downloaded again. The repair needs GDAL's ogr2ogr on the PATH;
the script exits with status 1 when a repair fails, so upload_natural_earth.py does not go on to
upload the unrepaired zip.
"""

import argparse
import os
import shlex
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import zipfile
from pathlib import Path

BASE_URL = "https://naciscdn.org/naturalearth/{scale}/physical/{name}.zip"

FILES = {
    "10m": [
        "ne_10m_coastline",
        "ne_10m_land",
        "ne_10m_minor_islands",
        "ne_10m_ocean",
        "ne_10m_lakes",
        "ne_10m_rivers_lake_centerlines",
        "ne_10m_glaciated_areas",
        "ne_10m_reefs",
        "ne_10m_playas",
        "ne_10m_antarctic_ice_shelves_polys",
    ],
    "50m": [
        "ne_50m_coastline",
        "ne_50m_land",
        "ne_50m_ocean",
        "ne_50m_lakes",
        "ne_50m_rivers_lake_centerlines",
        "ne_50m_glaciated_areas",
        "ne_50m_antarctic_ice_shelves_polys",
    ],
    "110m": [
        "ne_110m_coastline",
        "ne_110m_land",
        "ne_110m_ocean",
        "ne_110m_lakes",
        "ne_110m_rivers_lake_centerlines",
        "ne_110m_glaciated_areas",
    ],
}

# Shapefiles whose geometry is repaired here, before upload, because the server cannot do it.
#
# ne_10m_ocean is one polygon of about 447,000 vertices with a self-intersecting ring. The Njord
# ingest repairs an invalid geometry itself (GDAL MakeValid), which for this polygon takes about
# 1.1 GiB of memory: more than the ingest pod's limit, so the pod is killed mid-ingest. A valid
# polygon is ingested as it is, in about 130 MiB.
REPAIR = {"ne_10m_ocean"}

# The parts of a shapefile. In a repaired zip these come from ogr2ogr; everything else in the
# original zip (the README and VERSION files) is carried over.
SHAPEFILE_PARTS = {".shp", ".shx", ".dbf", ".prj", ".cpg", ".sbn", ".sbx", ".qix"}


def repaired_marker(name):
    """The zip member that marks {name}.zip as repaired, so it is repaired only once."""
    return f"{name}.REPAIRED.txt"


def is_repaired(zip_path, name):
    with zipfile.ZipFile(zip_path) as archive:
        return repaired_marker(name) in archive.namelist()


def repair(zip_path, name):
    """Replace {name}.shp in zip_path with ogr2ogr's valid copy of it, keeping the zip's name.

    The zip is replaced only once the new one is complete. Raises when ogr2ogr is missing or
    fails, with zip_path untouched.
    """
    if shutil.which("ogr2ogr") is None:
        raise RuntimeError("ogr2ogr (GDAL) is not on the PATH")
    command = ["ogr2ogr", "-f", "ESRI Shapefile", f"repaired/{name}.shp", f"{name}.shp",
               "-makevalid", "-nlt", "MULTIPOLYGON"]
    with tempfile.TemporaryDirectory() as tmp:
        original = Path(tmp)
        repaired = original / "repaired"
        with zipfile.ZipFile(zip_path) as archive:
            archive.extractall(original)
        repaired.mkdir()
        result = subprocess.run(command, cwd=original, capture_output=True, text=True)
        if result.returncode != 0 or not (repaired / f"{name}.shp").exists():
            raise RuntimeError(f"ogr2ogr failed: {result.stderr.strip() or result.returncode}")
        marker = (
            f"{name}.shp in this zip is not the file Natural Earth ships. Its geometry was made\n"
            f"valid by data/download_natural_earth.py of the Njord repository with:\n\n"
            f"  {shlex.join(command)}\n"
        )
        kept = [p for p in sorted(original.iterdir())
                if p.is_file() and p.suffix.lower() not in SHAPEFILE_PARTS]
        new_zip = zip_path.with_name(zip_path.name + ".tmp")
        try:
            with zipfile.ZipFile(new_zip, "w", zipfile.ZIP_DEFLATED) as archive:
                for part in sorted(repaired.iterdir()) + kept:
                    archive.write(part, part.name)
                archive.writestr(repaired_marker(name), marker)
            os.replace(new_zip, zip_path)
        finally:
            new_zip.unlink(missing_ok=True)


def ensure_repaired(zip_path, name):
    """Repair zip_path unless it already is. Returns False when the repair failed."""
    if is_repaired(zip_path, name):
        print(f"  {name}.zip is already repaired.")
        return True
    print(f"  Repairing {name}.shp (ogr2ogr -makevalid) ...")
    try:
        repair(zip_path, name)
    except Exception as e:
        print(f"  REPAIR FAILED: {e}")
        print(f"  {zip_path} is the original and must not be uploaded: it kills the ingest.")
        return False
    print(f"  Done — {zip_path.stat().st_size / 1_048_576:.1f} MB")
    return True


def reporthook(count, block_size, total_size):
    downloaded = count * block_size
    if total_size > 0:
        pct = min(downloaded / total_size * 100, 100)
        mb = downloaded / 1_048_576
        total_mb = total_size / 1_048_576
        print(f"\r  {mb:.1f} / {total_mb:.1f} MB  ({pct:.0f}%)", end="", flush=True)

def main():
    parser = argparse.ArgumentParser(description="Download Natural Earth physical vector zips")
    parser.add_argument("--output-dir", default=".", help="Base directory; files go into {output-dir}/ne/<scale>/ (default: .)")
    parser.add_argument("--scales", nargs="+", choices=["10m", "50m", "110m"], default=["10m", "50m", "110m"],
                        help="Scales to download (default: all)")
    args = parser.parse_args()

    base = Path(args.output_dir)
    repairs_failed = False

    for scale in args.scales:
        out = base / "ne" / scale
        out.mkdir(parents=True, exist_ok=True)
        files = FILES[scale]
        print(f"\n[{scale}] {len(files)} file(s) → {out}")
        for name in files:
            url = BASE_URL.format(scale=scale, name=name)
            dest = out / f"{name}.zip"
            if dest.exists():
                print(f"  {name}.zip already exists, skipping.")
            else:
                print(f"  Downloading {name}.zip ...")
                try:
                    urllib.request.urlretrieve(url, dest, reporthook)
                    print(f"\n  Done — {dest.stat().st_size / 1_048_576:.1f} MB")
                except Exception as e:
                    print(f"\n  FAILED: {e}")
                    if dest.exists():
                        dest.unlink()
                    continue
            if name in REPAIR and not ensure_repaired(dest, name):
                repairs_failed = True

    if repairs_failed:
        sys.exit(1)

# python3 download_natural_earth.py --output-dir ./data
# python3 download_natural_earth.py --output-dir ./data --scales 50m 110m
if __name__ == "__main__":
    main()
