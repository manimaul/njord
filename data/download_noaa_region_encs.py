#!/usr/bin/env python3

import argparse
import urllib.request
from pathlib import Path

BASE_URL = "https://charts.noaa.gov/ENCs/{}Region_ENCs.zip"
REGIONS = ["02","03","04","06","07","08","10","12","13","14",
           "15","17","22","24","26","30","32","34","36","40"]

def reporthook(count, block_size, total_size):
    downloaded = count * block_size
    if total_size > 0:
        pct = min(downloaded / total_size * 100, 100)
        mb = downloaded / 1_048_576
        total_mb = total_size / 1_048_576
        print(f"\r  {mb:.1f} / {total_mb:.1f} MB  ({pct:.0f}%)", end="", flush=True)

def main():
    parser = argparse.ArgumentParser(description="Download NOAA ENC region zips")
    parser.add_argument("--output-dir", default=".", help="Directory to save zips (default: .)")
    args = parser.parse_args()

    out = Path(args.output_dir)
    out.mkdir(parents=True, exist_ok=True)

    for region in REGIONS:
        url = BASE_URL.format(region)
        filename = f"{region}Region_ENCs.zip"
        dest = out / filename
        if dest.exists():
            print(f"[{region}] {filename} already exists, skipping.")
            continue
        print(f"[{region}] Downloading {filename} ...")
        try:
            urllib.request.urlretrieve(url, dest, reporthook)
            print(f"\n[{region}] Done — {dest.stat().st_size / 1_048_576:.1f} MB")
        except Exception as e:
            print(f"\n[{region}] FAILED: {e}")
            if dest.exists():
                dest.unlink()

# python3 download_encs.py --output-dir ./data
if __name__ == "__main__":
    main()
