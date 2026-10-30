"""`/v1/ws/enc_process` client: the ordered `WsMsg` sequence of an ingest."""

from __future__ import annotations

import json
import time

from websockets.exceptions import ConnectionClosed
from websockets.sync.client import connect

TERMINAL = ("CompletionReport", "Error")


def msg_type(msg: dict) -> str:
    """`io.madrona.njord.model.ws.WsMsg.Info` -> `Info`. The Kotlin discriminator is the class name."""
    return str(msg.get("type", "Unknown")).rsplit(".", 1)[-1]


def connect_status(ws_base: str, signature: str | None, open_timeout: float = 10.0):
    path = "/v1/ws/enc_process"
    if signature is not None:
        path += f"?signature={signature}"
    return connect(ws_base + path, open_timeout=open_timeout, max_size=None)


def collect_until_complete(
    ws_base: str,
    signature: str,
    timeout: float = 900.0,
    stall_timeout: float = 180.0,
    stop_on_idle_after: float | None = None,
) -> list[dict]:
    """
    Connects and collects messages until a `CompletionReport` or `Error` arrives (or `timeout`).

    The server sends the current status on connect and then every change, so the first message is
    whatever the ingest is doing at that moment. `stall_timeout` bounds the silence between two
    messages: an ingest that was aborted (the Kotlin server leaves `status.json` at its last
    progress message in that case, see README) fails fast instead of waiting out `timeout`. With
    `stop_on_idle_after`, an `Idle` that persists for that many seconds also ends the collection.
    """
    messages: list[dict] = []
    deadline = time.monotonic() + timeout
    with connect_status(ws_base, signature) as ws:
        idle_since: float | None = None
        last_message = time.monotonic()
        while time.monotonic() < deadline:
            remaining = deadline - time.monotonic()
            try:
                text = ws.recv(timeout=min(remaining, 2.0))
            except TimeoutError:
                if stop_on_idle_after is not None and idle_since is not None \
                        and time.monotonic() - idle_since >= stop_on_idle_after:
                    return messages
                if time.monotonic() - last_message > stall_timeout:
                    raise TimeoutError(
                        f"no status change for {stall_timeout}s; saw {[msg_type(m) for m in messages]} "
                        "(an aborted ingest leaves the Kotlin status file at its last progress message)"
                    )
                continue
            except ConnectionClosed:
                return messages
            msg = json.loads(text)
            messages.append(msg)
            last_message = time.monotonic()
            kind = msg_type(msg)
            if kind in TERMINAL:
                return messages
            idle_since = time.monotonic() if kind == "Idle" else None
    raise TimeoutError(f"ingest did not finish within {timeout}s; saw {[msg_type(m) for m in messages]}")


def collapsed_types(messages: list[dict]) -> list[str]:
    """The message kinds with consecutive repeats removed: `Extracting, Info, CompletionReport`."""
    out: list[str] = []
    for m in messages:
        kind = msg_type(m)
        if not out or out[-1] != kind:
            out.append(kind)
    return out
