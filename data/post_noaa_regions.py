#!/usr/bin/env python3

"""
Post NOAA ENC region download URLs to a Njord chart server (/v1/enc_url) and
monitor ingestion via WebSocket.

Usage:
    python3 post_noaa_regions.py --user admin --password secret
    python3 post_noaa_regions.py --server https://openenc.com --user admin --password secret
    python3 post_noaa_regions.py --server http://localhost:9000 --user admin --password secret --regions 02,07,14
    python3 post_noaa_regions.py --server http://localhost:9000 --user admin --password secret --all
"""

import argparse
import base64
import json
import os
import socket
import ssl
import struct
import sys
import time
import urllib.parse
import urllib.request

BASE_URL = "https://charts.noaa.gov/ENCs/{}Region_ENCs.zip"
REGIONS = ["02", "03", "04", "06", "07", "08", "10", "12", "13", "14",
           "15", "17", "22", "24", "26", "30", "32", "34", "36", "40"]


# ── progress helpers ──────────────────────────────────────────────────────────

def _bar(frac: float, width: int = 40) -> str:
    filled = int(width * min(frac, 1.0))
    return "=" * filled + "-" * (width - filled)


def _print_extract_progress(progress: float):
    print(f"\r  [{_bar(progress)}] {progress * 100:.0f}%", end="", flush=True)


def _print_ingest_progress(feature: int, total_features: int, chart: int, total_charts: int):
    frac = feature / total_features if total_features else 0
    print(
        f"\r  Chart {chart}/{total_charts}  feature {feature}/{total_features}"
        f"  [{_bar(frac)}] {frac * 100:.0f}%",
        end="", flush=True,
    )


# ── auth ──────────────────────────────────────────────────────────────────────

def login(base_url: str, username: str, password: str) -> str:
    """GET /v1/admin with Basic Auth. Returns signatureEncoded."""
    url = f"{base_url}/v1/admin"
    creds = base64.b64encode(f"{username}:{password}".encode()).decode()
    req = urllib.request.Request(url, headers={"Authorization": f"Basic {creds}"})
    with urllib.request.urlopen(req) as resp:
        data = json.loads(resp.read())
    return data["signatureEncoded"]


# ── URL post ──────────────────────────────────────────────────────────────────

def post_url(base_url: str, signature: str, region: str, url: str) -> dict:
    """POST {"url": url} to /v1/enc_url?signature=... Returns the 202 response body."""
    qs = urllib.parse.urlencode({"signature": signature})
    endpoint = f"{base_url}/v1/enc_url?{qs}"
    body = json.dumps({"url": url}).encode()
    req = urllib.request.Request(
        endpoint,
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    spinner = ["|", "/", "-", "\\"]
    # Animate while the request is in-flight (usually fast)
    import threading
    result_box = [None]
    error_box = [None]

    def do_request():
        try:
            with urllib.request.urlopen(req) as resp:
                result_box[0] = json.loads(resp.read())
        except urllib.error.HTTPError as e:
            error_box[0] = f"HTTP {e.code}: {e.read().decode(errors='replace')}"
        except Exception as e:
            error_box[0] = str(e)

    t = threading.Thread(target=do_request, daemon=True)
    t.start()
    i = 0
    while t.is_alive():
        print(f"\r  [{region}] Posting URL {spinner[i % 4]}", end="", flush=True)
        i += 1
        time.sleep(0.1)
    t.join()

    if error_box[0]:
        raise RuntimeError(error_box[0])
    print(f"\r  [{region}] Accepted — server will download {url.rsplit('/', 1)[-1]}")
    return result_box[0]


# ── WebSocket ─────────────────────────────────────────────────────────────────

def _ws_send_frame(sock: socket.socket, opcode: int, payload: bytes = b""):
    mask_key = os.urandom(4)
    masked = bytes(b ^ mask_key[i % 4] for i, b in enumerate(payload))
    length = len(payload)
    if length < 126:
        header = bytes([0x80 | opcode, 0x80 | length]) + mask_key
    elif length < 65536:
        header = bytes([0x80 | opcode, 0xFE]) + struct.pack(">H", length) + mask_key
    else:
        header = bytes([0x80 | opcode, 0xFF]) + struct.pack(">Q", length) + mask_key
    sock.sendall(header + masked)


def _recv_exact(sock: socket.socket, n: int) -> bytes:
    buf = b""
    while len(buf) < n:
        chunk = sock.recv(n - len(buf))
        if not chunk:
            raise ConnectionError("WebSocket connection closed unexpectedly")
        buf += chunk
    return buf


def ws_connect(base_url: str, signature: str) -> socket.socket:
    parsed = urllib.parse.urlparse(base_url)
    host = parsed.hostname
    use_ssl = parsed.scheme in ("https", "wss")
    port = parsed.port or (443 if use_ssl else 80)

    qs = urllib.parse.urlencode({"signature": signature})
    ws_path = f"/v1/ws/enc_process?{qs}"
    ws_key = base64.b64encode(os.urandom(16)).decode()

    raw = socket.create_connection((host, port), timeout=30)
    if use_ssl:
        ctx = ssl.create_default_context()
        sock = ctx.wrap_socket(raw, server_hostname=host)
    else:
        sock = raw

    handshake = (
        f"GET {ws_path} HTTP/1.1\r\n"
        f"Host: {host}:{port}\r\n"
        f"Upgrade: websocket\r\n"
        f"Connection: Upgrade\r\n"
        f"Sec-WebSocket-Key: {ws_key}\r\n"
        f"Sec-WebSocket-Version: 13\r\n"
        f"\r\n"
    )
    sock.sendall(handshake.encode())

    response = b""
    while b"\r\n\r\n" not in response:
        chunk = sock.recv(4096)
        if not chunk:
            raise ConnectionError("Server closed connection during WebSocket handshake")
        response += chunk

    status_line = response.decode(errors="replace").split("\r\n")[0]
    if "101" not in status_line:
        raise RuntimeError(f"WebSocket upgrade failed: {status_line}")

    sock.settimeout(300)
    return sock


def _ws_recv_frame(sock: socket.socket) -> str | None:
    header = _recv_exact(sock, 2)
    opcode = header[0] & 0x0F
    masked = (header[1] & 0x80) != 0
    payload_len = header[1] & 0x7F

    if payload_len == 126:
        payload_len = struct.unpack(">H", _recv_exact(sock, 2))[0]
    elif payload_len == 127:
        payload_len = struct.unpack(">Q", _recv_exact(sock, 8))[0]

    mask_key = _recv_exact(sock, 4) if masked else b""
    payload = _recv_exact(sock, payload_len)

    if masked:
        payload = bytes(b ^ mask_key[i % 4] for i, b in enumerate(payload))

    if opcode == 8:
        return None
    if opcode == 9:
        _ws_send_frame(sock, 0xA, payload)
        return ""
    if opcode == 1:
        return payload.decode("utf-8")
    return ""


def display_ingestion(sock: socket.socket) -> bool:
    """Read WebSocket messages and display ingestion progress. Returns True when complete."""
    last_type = None
    done = False
    try:
        while True:
            text = _ws_recv_frame(sock)
            if text is None:
                print("\n[WS] Server closed the connection.")
                break
            if not text:
                continue

            try:
                msg = json.loads(text)
            except json.JSONDecodeError:
                print(f"\n[WS] Unparseable message: {text}")
                continue

            msg_type = msg.get("type", "Unknown").rsplit(".", 1)[-1]

            if msg_type != last_type and last_type in ("Extracting", "Info"):
                print()

            if msg_type == "Idle":
                if last_type != "Idle":
                    print("  [Status] Idle — waiting for ingestion to start...")

            elif msg_type == "Extracting":
                if last_type != "Extracting":
                    print("  Extracting chart files...")
                _print_extract_progress(msg.get("progress", 0.0))

            elif msg_type == "Info":
                _print_ingest_progress(
                    msg.get("feature", 0),
                    msg.get("totalFeatures", 0),
                    msg.get("chart", 0),
                    msg.get("totalCharts", 0),
                )

            elif msg_type == "CompletionReport":
                print()
                charts = msg.get("totalChartCount", 0)
                features = msg.get("totalFeatureCount", 0)
                elapsed = msg.get("ms", 0) / 1000
                print(f"  [Done] {charts} charts, {features:,} features in {elapsed:.1f}s")
                for item in msg.get("items", []):
                    print(f"    {item['chartName']}: {item['featureCount']:,} features")
                failed = msg.get("failedCharts", [])
                if failed:
                    print(f"  [Failed charts] {', '.join(failed)}")
                done = True
                break

            elif msg_type == "Error":
                print(f"\n  [Error] {msg.get('message')} (fatal={msg.get('isFatal')})")
                if msg.get("isFatal"):
                    done = True
                    break

            else:
                print(f"\n  [WS] {text}")

            last_type = msg_type

    except (socket.timeout, TimeoutError):
        print("\n[WS] Timed out waiting for ingestion messages.")
    except (ConnectionError, OSError) as e:
        print(f"\n[WS] Connection error: {e}")
    finally:
        sock.close()

    return done


def monitor(base_url: str, username: str, password: str, signature: str):
    reconnect_delay = 3
    while True:
        print("\nConnecting to ingestion WebSocket ...")
        try:
            sock = ws_connect(base_url, signature)
        except Exception as e:
            print(f"  WebSocket connection failed: {e}")
            print(f"  Retrying in {reconnect_delay}s ...")
            time.sleep(reconnect_delay)
            try:
                signature = login(base_url, username, password)
            except Exception as login_err:
                print(f"  Re-login failed: {login_err}")
            continue

        print("  Connected — monitoring ingestion:\n")
        done = display_ingestion(sock)
        if done:
            break

        print(f"\n  Reconnecting in {reconnect_delay}s ...")
        time.sleep(reconnect_delay)
        try:
            signature = login(base_url, username, password)
            print("  Signature refreshed.\n")
        except Exception as e:
            print(f"  Re-login failed: {e}\n")


# ── region selection ──────────────────────────────────────────────────────────

def pick_regions(preselected: list[str] | None) -> list[str]:
    """Return a list of selected region codes via interactive prompt or --regions."""
    if preselected:
        invalid = [r for r in preselected if r not in REGIONS]
        if invalid:
            sys.exit(f"Unknown region(s): {', '.join(invalid)}. Valid: {', '.join(REGIONS)}")
        return preselected

    print("Available NOAA ENC regions:\n")
    for i, r in enumerate(REGIONS, 1):
        url = BASE_URL.format(r)
        print(f"  {i:3d}.  [{r}]  {url}")

    print()
    print("Enter region numbers to post (e.g. 1,3,5  or  1-5  or  all):")
    while True:
        try:
            raw = input("> ").strip()
        except (EOFError, KeyboardInterrupt):
            print()
            sys.exit(0)

        if raw.lower() == "all":
            return list(REGIONS)

        selected = []
        valid = True
        for part in raw.replace(" ", "").split(","):
            if "-" in part:
                bounds = part.split("-", 1)
                try:
                    lo, hi = int(bounds[0]) - 1, int(bounds[1]) - 1
                    if 0 <= lo <= hi < len(REGIONS):
                        selected.extend(REGIONS[lo:hi + 1])
                    else:
                        print(f"  Range out of bounds: {part}")
                        valid = False
                        break
                except ValueError:
                    print(f"  Invalid range: {part}")
                    valid = False
                    break
            else:
                try:
                    idx = int(part) - 1
                    if 0 <= idx < len(REGIONS):
                        selected.append(REGIONS[idx])
                    else:
                        print(f"  Number out of range: {part}")
                        valid = False
                        break
                except ValueError:
                    print(f"  Invalid input: {part}")
                    valid = False
                    break

        if valid and selected:
            # Deduplicate while preserving order
            seen = set()
            unique = [r for r in selected if not (r in seen or seen.add(r))]
            return unique
        if valid:
            print("  No regions selected — try again.")


# ── main ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="Post NOAA ENC region URLs to Njord /v1/enc_url and monitor ingestion",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=(
            "Examples:\n"
            "  Interactive region selection:\n"
            "    python3 post_noaa_regions.py --user admin --password secret\n\n"
            "  Specific regions:\n"
            "    python3 post_noaa_regions.py --user admin --password secret --regions 02,07,14\n\n"
            "  All regions:\n"
            "    python3 post_noaa_regions.py --server https://openenc.com --user admin --password secret --all\n"
        ),
    )
    parser.add_argument("--server", default="http://localhost:9000",
                        help="Base server URL (default: http://localhost:9000)")
    parser.add_argument("--user", required=True, help="Admin username")
    parser.add_argument("--password", required=True, help="Admin password")
    parser.add_argument("--regions", default=None,
                        help="Comma-separated region codes to post, e.g. 02,07,14")
    parser.add_argument("--all", action="store_true",
                        help="Post all regions without interactive selection")
    args = parser.parse_args()

    print(f"Logging in to {args.server} as '{args.user}' ...")
    try:
        signature = login(args.server, args.user, args.password)
    except Exception as e:
        sys.exit(f"Login failed: {e}")
    print("  OK\n")

    if args.all:
        selected = list(REGIONS)
    else:
        preselected = [r.strip() for r in args.regions.split(",")] if args.regions else None
        selected = pick_regions(preselected)

    print(f"\nPosting {len(selected)} region(s): {', '.join(selected)}\n")

    failed = []
    for i, region in enumerate(selected, 1):
        url = BASE_URL.format(region)
        print(f"[{i}/{len(selected)}] Region {region}")
        try:
            post_url(args.server, signature, region, url)
        except Exception as e:
            print(f"  [ERROR] {e}")
            failed.append(region)

    if failed:
        print(f"\nFailed to post: {', '.join(failed)}")
    else:
        print(f"\nAll {len(selected)} region URL(s) accepted.")

    monitor(args.server, args.user, args.password, signature)


if __name__ == "__main__":
    main()
