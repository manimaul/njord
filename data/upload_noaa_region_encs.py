#!/usr/bin/env python3

"""
Upload NOAA ENC zip files to a Njord chart server and monitor ingestion.

Usage:
    python3 upload_noaa_region_encs.py --user admin --password secret
    python3 upload_noaa_region_encs.py --server http://localhost:9000 --user admin --password secret
    python3 upload_noaa_region_encs.py --server http://localhost:9000 --user admin --password secret --dir /path/to/zips
    python3 upload_noaa_region_encs.py --server http://localhost:9000 --user admin --password secret --file 02Region_ENCs.zip
"""

import argparse
import base64
import http.client
import json
import os
import socket
import ssl
import struct
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path


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
    """GET /v1/admin with Basic Auth. Returns signatureEncoded."""
    url = f"{base_url}/v1/admin"
    creds = base64.b64encode(f"{username}:{password}".encode()).decode()
    req = urllib.request.Request(url, headers={"Authorization": f"Basic {creds}"})
    with urllib.request.urlopen(req) as resp:
        data = json.loads(resp.read())
    return data["signatureEncoded"]


# ── upload ────────────────────────────────────────────────────────────────────

def upload(base_url: str, signature: str, zip_path: Path) -> dict:
    """POST the zip to /v1/enc_save as raw binary with streaming progress bar."""
    parsed = urllib.parse.urlparse(base_url)
    host = parsed.hostname
    port = parsed.port

    qs = urllib.parse.urlencode({"signature": signature, "filename": zip_path.name})
    path = f"/v1/enc_save?{qs}"
    total = zip_path.stat().st_size
    chunk_size = 64 * 1024  # 64 KB

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

    print()  # newline after progress bar

    resp = conn.getresponse()
    body = resp.read()
    conn.close()

    if resp.status not in (200, 201, 202):
        raise RuntimeError(
            f"Upload failed: HTTP {resp.status} — {body.decode(errors='replace')}"
        )
    return json.loads(body)


# ── WebSocket ─────────────────────────────────────────────────────────────────

def _ws_send_frame(sock: socket.socket, opcode: int, payload: bytes = b""):
    """Send a masked WebSocket frame (client frames must be masked per RFC 6455)."""
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
    """Open a WebSocket to /v1/ws/enc_process. Returns the connected socket."""
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

    # Read HTTP response until the blank line separating headers from body
    response = b""
    while b"\r\n\r\n" not in response:
        chunk = sock.recv(4096)
        if not chunk:
            raise ConnectionError("Server closed connection during WebSocket handshake")
        response += chunk

    status_line = response.decode(errors="replace").split("\r\n")[0]
    if "101" not in status_line:
        raise RuntimeError(f"WebSocket upgrade failed: {status_line}")

    sock.settimeout(300)  # 5-minute timeout for long ingestion runs
    return sock


def _ws_recv_frame(sock: socket.socket) -> str | None:
    """
    Read one WebSocket frame.
    Returns the text payload (str), empty string for non-text/control frames,
    or None on a close frame.
    """
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

    if opcode == 8:   # close frame
        return None
    if opcode == 9:   # ping — reply with pong, same payload
        _ws_send_frame(sock, 0xA, payload)
        return ""
    if opcode == 1:   # text frame
        return payload.decode("utf-8")
    return ""         # pong / binary — ignore


def display_ingestion(sock: socket.socket) -> bool:
    """
    Read WebSocket messages and display ingestion progress.
    Returns True when ingestion is complete, False if the connection dropped.
    """
    last_type = None
    done = False
    try:
        while True:
            text = _ws_recv_frame(sock)
            if text is None:
                print("\n[WS] Server closed the connection.")
                break
            if not text:
                continue  # non-text control frame

            try:
                msg = json.loads(text)
            except json.JSONDecodeError:
                print(f"\n[WS] Unparseable message: {text}")
                continue

            msg_type = msg.get("type", "Unknown").rsplit(".", 1)[-1]

            # End any in-progress progress-bar line when the message type changes
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


# ── interactive zip selection ─────────────────────────────────────────────────

def pick_zip(directory: Path, specified: str | None) -> Path:
    """Return the zip to upload — from --file or interactive numbered list."""
    if specified:
        p = Path(specified)
        if not p.is_absolute():
            p = directory / p
        if not p.exists():
            sys.exit(f"Error: {p} not found")
        return p

    zips = sorted(directory.glob("*.zip"))
    if not zips:
        sys.exit(f"No .zip files found in {directory}")

    print(f"Zip files in {directory}:\n")
    for i, z in enumerate(zips, 1):
        size_mb = z.stat().st_size / 1_048_576
        print(f"  {i:3d}.  {z.name}  ({size_mb:.1f} MB)")

    print()
    while True:
        try:
            raw = input("Select a file by number: ").strip()
            idx = int(raw) - 1
            if 0 <= idx < len(zips):
                return zips[idx]
            print(f"  Please enter a number between 1 and {len(zips)}")
        except ValueError:
            print("  Invalid input — enter a number")
        except (EOFError, KeyboardInterrupt):
            print()
            sys.exit(0)


# ── main ──────────────────────────────────────────────────────────────────────

def main():
    desc = """Upload an ENC zip to a Njord chart server and monitor ingestion
\n
Usage Examples:\n
Interactive selection from current directory\n
python3 upload_noaa_region_encs.py --user admin --password secret\n
\n
From a specific directory\n
python3 upload_noaa_region_encs.py --server http://localhost:9000 --user admin --password secret --dir /path/to/zips\n
\n
Direct file (skip selection prompt)\n
python3 upload_noaa_region_encs.py --server https://openenc.com --user admin --password secret --file 02Region_ENCs.zip
\n
"""
    parser = argparse.ArgumentParser(
        description=desc,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "--server", default="http://localhost:9000",
        help="Base server URL (default: http://localhost:9000)",
    )
    parser.add_argument("--user", required=True, help="Admin username")
    parser.add_argument("--password", required=True, help="Admin password")
    parser.add_argument(
        "--dir", default=".",
        help="Directory containing zip files for the interactive list (default: .)",
    )
    parser.add_argument(
        "--file", default=None,
        help="Zip file to upload — skips interactive selection (name or full path)",
    )
    parser.add_argument(
        "--monitor", action="store_true",
        help="Skip upload and just connect to the WebSocket to monitor ingestion progress",
    )
    args = parser.parse_args()

    print(f"\nLogging in to {args.server} as '{args.user}' ...")
    try:
        signature = login(args.server, args.user, args.password)
    except Exception as e:
        sys.exit(f"Login failed: {e}")
    print("  Signature obtained.\n")

    if not args.monitor:
        directory = Path(args.dir).resolve()
        zip_path = pick_zip(directory, args.file)

        print(f"Uploading {zip_path.name}  ({zip_path.stat().st_size / 1_048_576:.1f} MB) ...")
        try:
            result = upload(args.server, signature, zip_path)
            print(f"  Server accepted: {result}\n")
        except Exception as e:
            sys.exit(f"Upload failed: {e}")

    reconnect_delay = 3
    while True:
        print("Connecting to ingestion WebSocket ...")
        try:
            sock = ws_connect(args.server, signature)
        except Exception as e:
            print(f"  WebSocket connection failed: {e}")
            print(f"  Retrying in {reconnect_delay}s ...")
            time.sleep(reconnect_delay)
            # Re-login to get a fresh signature before retrying
            try:
                signature = login(args.server, args.user, args.password)
            except Exception as login_err:
                print(f"  Re-login failed: {login_err}")
            continue

        print("  Connected — monitoring ingestion:\n")
        done = display_ingestion(sock)
        if done:
            break

        print(f"\n  Reconnecting in {reconnect_delay}s ...")
        time.sleep(reconnect_delay)
        # Re-login so the signature stays fresh across long ingestion runs
        try:
            signature = login(args.server, args.user, args.password)
            print("  Signature refreshed.\n")
        except Exception as e:
            print(f"  Re-login failed: {e}\n")


if __name__ == "__main__":
    main()
