#!/usr/bin/env python3

import argparse
import urllib.request
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
        "ne_10m_geographic_lines",
        "ne_10m_bathymetry_all",
    ],
    "50m": [
        "ne_50m_coastline",
        "ne_50m_land",
        "ne_50m_ocean",
        "ne_50m_lakes",
        "ne_50m_rivers_lake_centerlines",
        "ne_50m_glaciated_areas",
        "ne_50m_antarctic_ice_shelves_polys",
        "ne_50m_geographic_lines",
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
                continue
            print(f"  Downloading {name}.zip ...")
            try:
                urllib.request.urlretrieve(url, dest, reporthook)
                print(f"\n  Done — {dest.stat().st_size / 1_048_576:.1f} MB")
            except Exception as e:
                print(f"\n  FAILED: {e}")
                if dest.exists():
                    dest.unlink()

# python3 download_natural_earth.py --output-dir ./data
# python3 download_natural_earth.py --output-dir ./data --scales 50m 110m
if __name__ == "__main__":
    main()
