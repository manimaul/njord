#!/usr/bin/env python3

"""
Trigger forced SQLite region export on a Njord chart server (POST /v1/regions).

The server reads region configs from application.json, but this script lets you
trigger an immediate regeneration for any region — including one not in the
server config — by posting the full region definition.

Usage:
    # Regenerate a region by reading its config from application.json:
    python3 generate_region.py --user admin --password secret --config /path/to/application.json --name REGION_15

    # Post a region defined inline:
    python3 generate_region.py --user admin --password secret \\
        --name REGION_15 \\
        --description "Pacific NorthWest" \\
        --coverage "POLYGON ((-120.5 43.2, ...))"

    # Against a remote server:
    python3 generate_region.py --server https://openenc.com --user admin --password secret \\
        --config /path/to/application.json --name REGION_15
"""

import argparse
import base64
import json
import sys
import time
import urllib.parse
import urllib.request
import urllib.error


# ── auth ──────────────────────────────────────────────────────────────────────

def login(base_url: str, username: str, password: str) -> str:
    """GET /v1/admin with Basic Auth. Returns signatureEncoded."""
    url = f"{base_url}/v1/admin"
    creds = base64.b64encode(f"{username}:{password}".encode()).decode()
    req = urllib.request.Request(url, headers={"Authorization": f"Basic {creds}"})
    with urllib.request.urlopen(req) as resp:
        data = json.loads(resp.read())
    return data["signatureEncoded"]


# ── region config ─────────────────────────────────────────────────────────────

def load_region_from_config(config_path: str, region_name: str) -> dict:
    """Read region config from an application.json and return the named region."""
    try:
        with open(config_path) as f:
            config = json.load(f)
    except FileNotFoundError:
        sys.exit(f"Config file not found: {config_path}")
    except json.JSONDecodeError as e:
        sys.exit(f"Invalid JSON in {config_path}: {e}")

    regions = config.get("regionExports", [])
    if not regions:
        sys.exit(f"No regionExports defined in {config_path}")

    match = next((r for r in regions if r["name"] == region_name), None)
    if match is None:
        names = [r["name"] for r in regions]
        sys.exit(
            f"Region '{region_name}' not found in {config_path}.\n"
            f"Available: {', '.join(names)}"
        )
    return match


# ── POST /v1/regions ──────────────────────────────────────────────────────────

def post_generate(base_url: str, signature: str, region: dict):
    """POST the region config to /v1/regions?signature=... Returns on 202 Accepted."""
    qs = urllib.parse.urlencode({"signature": signature})
    url = f"{base_url}/v1/regions?{qs}"
    body = json.dumps(region).encode()
    req = urllib.request.Request(
        url,
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    spinner = ["|", "/", "-", "\\"]
    import threading
    result_box = [None]
    error_box = [None]
    status_box = [None]

    def do_request():
        try:
            with urllib.request.urlopen(req) as resp:
                status_box[0] = resp.status
                result_box[0] = resp.read().decode(errors="replace")
        except urllib.error.HTTPError as e:
            error_box[0] = f"HTTP {e.code}: {e.read().decode(errors='replace')}"
        except Exception as e:
            error_box[0] = str(e)

    t = threading.Thread(target=do_request, daemon=True)
    t.start()
    i = 0
    while t.is_alive():
        print(f"\r  Posting region '{region['name']}' {spinner[i % 4]}", end="", flush=True)
        i += 1
        time.sleep(0.1)
    t.join()

    if error_box[0]:
        raise RuntimeError(error_box[0])

    print(f"\r  Server accepted region '{region['name']}' — export running in background.")


# ── main ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="Trigger forced SQLite region export on a Njord chart server",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=(
            "Examples:\n"
            "  From application.json:\n"
            "    python3 generate_region.py --user admin --password secret"
            " --config ../server/src/nativeMain/resources/config/application.json"
            " --name REGION_15\n\n"
            "  Inline region definition:\n"
            "    python3 generate_region.py --user admin --password secret\n"
            "      --name REGION_15 --description 'Pacific NW'\n"
            '      --coverage "POLYGON ((-120.5 43.2, ...))"\n\n'
            "  Against a remote server:\n"
            "    python3 generate_region.py --server https://openenc.com"
            " --user admin --password secret --config /path/to/application.json"
            " --name REGION_15\n"
        ),
    )
    parser.add_argument(
        "--server", default="http://localhost:9000",
        help="Base server URL (default: http://localhost:9000)",
    )
    parser.add_argument("--user", required=True, help="Admin username")
    parser.add_argument("--password", required=True, help="Admin password")

    source = parser.add_mutually_exclusive_group()
    source.add_argument(
        "--config",
        metavar="PATH",
        help="Path to application.json — use with --name to load the region definition",
    )

    parser.add_argument(
        "--name", required=True,
        help="Region name (e.g. REGION_15); used as a lookup key with --config, "
             "or as the name field when providing --description and --coverage",
    )
    parser.add_argument(
        "--description", default=None,
        help="Region description (required when not using --config)",
    )
    parser.add_argument(
        "--coverage", default=None,
        help="Coverage polygon in WKT (required when not using --config)",
    )

    args = parser.parse_args()

    # Build region dict from config file or inline args
    if args.config:
        region = load_region_from_config(args.config, args.name)
    else:
        if not args.description or not args.coverage:
            parser.error(
                "--description and --coverage are required when --config is not provided"
            )
        region = {
            "name": args.name,
            "description": args.description,
            "coverage": args.coverage,
        }

    print(f"Logging in to {args.server} as '{args.user}' ...")
    try:
        signature = login(args.server, args.user, args.password)
    except Exception as e:
        sys.exit(f"Login failed: {e}")
    print("  OK\n")

    print(f"Region: {region['name']}")
    print(f"  Description : {region['description']}")
    print(f"  Coverage    : {region['coverage'][:80]}{'...' if len(region['coverage']) > 80 else ''}")
    print()

    try:
        post_generate(args.server, signature, region)
    except Exception as e:
        sys.exit(f"Request failed: {e}")

    print(
        "\nThe server is generating the SQLite archive in the background.\n"
        f"Check GET {args.server}/v1/regions for the updated manifest once complete."
    )


if __name__ == "__main__":
    main()
