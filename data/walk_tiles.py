#!/usr/bin/env python3
"""
Walk the full tile tree and print each z/x/y coordinate string,
followed by a tile count summary at the end of each zoom level.

Optionally warms the tile server cache by requesting each tile.
"""

import argparse
import asyncio
import sys
import urllib.request
from urllib.error import URLError


def fetch_tile(server: str, z: int, x: int, y: int) -> tuple[int, int, int, int]:
    """Fetch a single tile. Returns (z, x, y, http_status)."""
    url = f"{server}/v1/tile/{z}/{x}/{y}"
    try:
        with urllib.request.urlopen(url, timeout=60) as resp:
            return z, x, y, resp.status
    except URLError as e:
        code = e.code if hasattr(e, "code") else 0
        return z, x, y, code


async def fetch_tile_async(
    executor,
    semaphore: asyncio.Semaphore,
    server: str,
    z: int,
    x: int,
    y: int,
) -> tuple[int, int, int, int]:
    async with semaphore:
        loop = asyncio.get_running_loop()
        return await loop.run_in_executor(executor, fetch_tile, server, z, x, y)


async def warm_zoom(server: str, z: int, concurrency: int) -> tuple[int, int]:
    """Request all tiles at zoom z. Returns (ok_count, error_count)."""
    dim = 1 << z
    semaphore = asyncio.Semaphore(concurrency)
    loop = asyncio.get_running_loop()

    tasks = [
        fetch_tile_async(None, semaphore, server, z, x, y)
        for x in range(dim)
        for y in range(dim)
    ]

    ok = errors = done = 0
    total = len(tasks)

    for coro in asyncio.as_completed(tasks):
        tz, tx, ty, status = await coro
        done += 1
        if 200 <= status < 300 or status == 204:
            ok += 1
        else:
            errors += 1
            print(f"\r  [{tz}/{tx}/{ty}] HTTP {status}                    ", flush=True)

        pct = done / total * 100
        print(f"\r  {done:,}/{total:,} ({pct:.0f}%)  ok={ok:,}  err={errors}", end="", flush=True)

    print()  # newline after progress
    return ok, errors


def main():
    parser = argparse.ArgumentParser(
        description="Walk the tile tree, printing z/x/y coordinates and zoom-level counts",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("--min-zoom", type=int, default=0, metavar="Z",
                        help="Starting zoom level (default: 0)")
    parser.add_argument("--max-zoom", type=int, default=14, metavar="Z",
                        help="Ending zoom level (default: 14)")
    parser.add_argument("--server", default=None, metavar="URL",
                        help="Tile server base URL — if set, each tile is requested to warm the cache "
                             "(e.g. http://localhost:9000)")
    parser.add_argument("--concurrency", type=int, default=5, metavar="N",
                        help="Max in-flight tile requests (default: 5, only used with --server)")
    args = parser.parse_args()

    if args.min_zoom < 0 or args.max_zoom > 32 or args.min_zoom > args.max_zoom:
        sys.exit("Error: zoom levels must satisfy 0 <= min-zoom <= max-zoom <= 32")

    grand_total = 0
    grand_ok = grand_err = 0

    for z in range(args.min_zoom, args.max_zoom + 1):
        dim = 1 << z  # 2^z tiles per axis
        count = dim * dim

        if args.server:
            print(f"zoom {z}: requesting {count:,} tiles ...")
            ok, err = asyncio.run(warm_zoom(args.server, z, args.concurrency))
            grand_ok += ok
            grand_err += err
            print(f"zoom {z}: {count:,} tiles  ok={ok:,}  err={err}", flush=True)
        else:
            for x in range(dim):
                for y in range(dim):
                    print(f"{z}/{x}/{y}")
            print(f"# zoom {z}: {count:,} tiles", flush=True)

        grand_total += count

    if args.server:
        print(f"\ntotal: {grand_total:,} tiles  ok={grand_ok:,}  err={grand_err}")
    else:
        print(f"# total: {grand_total:,} tiles")


if __name__ == "__main__":
    main()
