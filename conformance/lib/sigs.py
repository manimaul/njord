"""Admin signatures minted locally, for the expiry and tamper cases of P-API-6.

Mirrors `AdminUtil.createSignature`: HMAC-SHA256 with the admin key over
`baseUrl ‖ date ‖ uuid`, where `date` is the RFC 3339 instant string that is also serialised
into the signature JSON. `signatureEncoded` is the base64 of the JSON, URL-path-encoded. Only
usable when the suite knows the server's admin key (`--admin-key`), i.e. locally.
"""

from __future__ import annotations

import base64
import hashlib
import hmac
import json
import urllib.parse
import uuid
from datetime import datetime, timedelta, timezone


def instant(dt: datetime) -> str:
    """`kotlinx.datetime` `Instant.toString()`: UTC, `Z`, fractional seconds trimmed in groups of three."""
    dt = dt.astimezone(timezone.utc)
    base = dt.strftime("%Y-%m-%dT%H:%M:%S")
    micros = dt.microsecond
    if micros == 0:
        return base + "Z"
    frac = f"{micros:06d}"
    if frac.endswith("000"):
        frac = frac[:3]
    return f"{base}.{frac}Z"


def mint(admin_key: str, base_url: str, date: datetime | None = None, expiration_seconds: int = 604800) -> dict:
    now = date or datetime.now(timezone.utc)
    date_str = instant(now)
    nonce = str(uuid.uuid4())
    mac = hmac.new(admin_key.encode(), digestmod=hashlib.sha256)
    mac.update(base_url.encode())
    mac.update(date_str.encode())
    mac.update(nonce.encode())
    signature = {
        "date": date_str,
        "signature": base64.b64encode(mac.digest()).decode(),
        "uuid": nonce,
        "expirationDate": instant(now + timedelta(seconds=expiration_seconds)),
    }
    return {"signature": signature, "signatureEncoded": encode(signature)}


def encode(signature: dict) -> str:
    """`signatureEncoded`: base64 of the compact JSON, then URL-path-encoded (slashes included)."""
    raw = json.dumps(signature, separators=(",", ":")).encode()
    return urllib.parse.quote(base64.b64encode(raw).decode(), safe="")
