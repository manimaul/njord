#!/usr/bin/env python3

"""
Download (if needed) and install Natural Earth physical zips into a Njord chart
server one file at a time, waiting for each CompletionReport before proceeding.

Usage:
    python3 upload_natural_earth.py --user admin --password secret
    python3 upload_natural_earth.py --server http://localhost:9000 --user admin --password secret
    python3 upload_natural_earth.py --server http://localhost:9000 --user admin --password secret --scales 50m 110m
    python3 upload_natural_earth.py --server http://localhost:9000 --user admin --password secret --skip-download
"""

import argparse
import base64
import http.client
import json
import os
import socket
import ssl
import struct
import subprocess
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

# Install order: coarsest first so higher-resolution data renders on top
SCALE_ORDER = ["110m", "50m", "10m"]


# ── progress helpers ──────────────────────────────────────────────────────────

def _bar(frac: float, width: int = 40) -> str:
    filled = int(width * min(frac, 1.0))
    return "=" * filled + "-" * (width - filled)


def _print_upload_progress(done: int, total: int):
    frac = done / total if total else 0
    print(
        f"\r  [{_bar(frac)}] {done / 1_048_576:.1f}/{total / 1_048_576:.1f} MB ({frac * 100:.0f}%)",
        end="", flush=True,
    )


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
    url = f"{base_url}/v1/admin"
    creds = base64.b64encode(f"{username}:{password}".encode()).decode()
    req = urllib.request.Request(url, headers={"Authorization": f"Basic {creds}"})
    with urllib.request.urlopen(req) as resp:
        data = json.loads(resp.read())
    return data["signatureEncoded"]


# ── upload ────────────────────────────────────────────────────────────────────

def upload(base_url: str, signature: str, zip_path: Path) -> dict:
    parsed = urllib.parse.urlparse(base_url)
    host = parsed.hostname
    port = parsed.port
    qs = urllib.parse.urlencode({"signature": signature, "filename": zip_path.name})
    path = f"/v1/enc_save?{qs}"
    total = zip_path.stat().st_size
    chunk_size = 64 * 1024

    if parsed.scheme == "https":
        conn = http.client.HTTPSConnection(host, port)
    else:
        conn = http.client.HTTPConnection(host, port)

    conn.putrequest("POST", path)
    conn.putheader("Content-Type", "application/octet-stream")
    conn.putheader("Content-Length", str(total))
    conn.endheaders()

    uploaded = 0
    with open(zip_path, "rb") as f:
        while True:
            chunk = f.read(chunk_size)
            if not chunk:
                break
            conn.send(chunk)
            uploaded += len(chunk)
            _print_upload_progress(uploaded, total)

    print()
    resp = conn.getresponse()
    body = resp.read()
    conn.close()

    if resp.status not in (200, 201, 202):
        raise RuntimeError(f"Upload failed: HTTP {resp.status} — {body.decode(errors='replace')}")
    return json.loads(body)


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


def wait_for_completion(base_url: str, signature: str) -> bool:
    """Connect to the ingestion WebSocket and block until CompletionReport. Returns True on success."""
    reconnect_delay = 3
    while True:
        try:
            sock = ws_connect(base_url, signature)
        except Exception as e:
            print(f"  WebSocket connection failed: {e}. Retrying in {reconnect_delay}s ...")
            time.sleep(reconnect_delay)
            try:
                signature = login_refresh(base_url, signature)
            except Exception:
                pass
            continue

        last_type = None
        done = False
        try:
            while True:
                text = _ws_recv_frame(sock)
                if text is None:
                    print("\n  [WS] Server closed the connection.")
                    break
                if not text:
                    continue

                try:
                    msg = json.loads(text)
                except json.JSONDecodeError:
                    continue

                msg_type = msg.get("type", "").rsplit(".", 1)[-1]

                if msg_type != last_type and last_type in ("Extracting", "Info"):
                    print()

                if msg_type == "Extracting":
                    if last_type != "Extracting":
                        print("  Extracting ...")
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
                    print(f"  [Done] {charts} chart(s), {features:,} features in {elapsed:.1f}s")
                    for item in msg.get("items", []):
                        print(f"    {item['chartName']}: {item['featureCount']:,} features")
                    failed = msg.get("failedCharts", [])
                    if failed:
                        print(f"  [Failed] {', '.join(failed)}")
                    done = True
                    break

                elif msg_type == "Error":
                    print(f"\n  [Error] {msg.get('message')} (fatal={msg.get('isFatal')})")
                    if msg.get("isFatal"):
                        done = True
                        break

                last_type = msg_type

        except (socket.timeout, TimeoutError):
            print("\n  [WS] Timed out.")
        except (ConnectionError, OSError) as e:
            print(f"\n  [WS] Connection error: {e}")
        finally:
            sock.close()

        if done:
            return True

        print(f"  Reconnecting in {reconnect_delay}s ...")
        time.sleep(reconnect_delay)


def login_refresh(base_url: str, signature: str) -> str:
    """Best-effort signature refresh — returns original on failure."""
    return signature


# ── main ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="Download (if needed) and install Natural Earth data into a Njord server",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("--server", default="http://localhost:9000",
                        help="Base server URL (default: http://localhost:9000)")
    parser.add_argument("--user", required=True, help="Admin username")
    parser.add_argument("--password", required=True, help="Admin password")
    parser.add_argument("--dir", default=".",
                        help="Base data directory containing ne/<scale>/ subdirs (default: .)")
    parser.add_argument("--scales", nargs="+", choices=["10m", "50m", "110m"],
                        default=["10m", "50m", "110m"], help="Scales to install (default: all)")
    parser.add_argument("--skip-download", action="store_true",
                        help="Skip running download_natural_earth.py")
    args = parser.parse_args()

    base_dir = Path(args.dir).resolve()
    scales = [s for s in SCALE_ORDER if s in args.scales]

    # ── Step 1: ensure all zips are present ───────────────────────────────────
    if not args.skip_download:
        downloader = Path(__file__).parent / "download_natural_earth.py"
        if not downloader.exists():
            sys.exit(f"Error: download_natural_earth.py not found at {downloader}")
        print("Ensuring Natural Earth zips are downloaded ...")
        cmd = [sys.executable, str(downloader), "--output-dir", str(base_dir),
               "--scales"] + scales
        result = subprocess.run(cmd)
        if result.returncode != 0:
            sys.exit("Download step failed — aborting.")
        print()

    # ── Step 2: login ─────────────────────────────────────────────────────────
    print(f"Logging in to {args.server} as '{args.user}' ...")
    try:
        signature = login(args.server, args.user, args.password)
    except Exception as e:
        sys.exit(f"Login failed: {e}")
    print("  OK\n")

    # ── Step 3: collect zips in install order ─────────────────────────────────
    zips = []
    for scale in scales:
        scale_dir = base_dir / "ne" / scale
        if not scale_dir.exists():
            print(f"[{scale}] directory not found, skipping: {scale_dir}")
            continue
        found = sorted(scale_dir.glob("*.zip"))
        if not found:
            print(f"[{scale}] no zip files found in {scale_dir}, skipping.")
            continue
        zips.extend(found)

    if not zips:
        sys.exit("No zip files found to upload.")

    total_files = len(zips)
    print(f"Found {total_files} zip file(s) to install.\n")

    # ── Step 4: upload one at a time and wait for completion ──────────────────
    for i, zip_path in enumerate(zips, 1):
        scale_tag = zip_path.parent.name
        size_mb = zip_path.stat().st_size / 1_048_576
        print(f"[{i}/{total_files}] [{scale_tag}] {zip_path.name}  ({size_mb:.1f} MB)")

        try:
            signature = login(args.server, args.user, args.password)
            upload(args.server, signature, zip_path)
        except Exception as e:
            print(f"  Upload failed: {e} — skipping.\n")
            continue

        wait_for_completion(args.server, signature)
        print()

    print("All done.")


if __name__ == "__main__":
    main()
