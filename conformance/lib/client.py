"""HTTP client for one Njord server: base URL, admin login, signed calls, raw uploads."""

from __future__ import annotations

import http.client
import urllib.parse
from dataclasses import dataclass, field

import requests


@dataclass
class RawResponse:
    """A response fetched with `http.client`, for paths `requests` would normalise (`..`)."""

    status_code: int
    headers: dict[str, str]
    content: bytes


@dataclass
class Client:
    base_url: str
    timeout: float = 60.0
    session: requests.Session = field(default_factory=requests.Session)

    def __post_init__(self) -> None:
        self.base_url = self.base_url.rstrip("/")

    @property
    def host(self) -> str:
        return urllib.parse.urlsplit(self.base_url).hostname or ""

    @property
    def is_local(self) -> bool:
        return self.host in ("localhost", "127.0.0.1", "::1")

    @property
    def ws_base(self) -> str:
        parts = urllib.parse.urlsplit(self.base_url)
        scheme = "wss" if parts.scheme == "https" else "ws"
        return urllib.parse.urlunsplit((scheme, parts.netloc, "", "", ""))

    def url(self, path: str) -> str:
        return self.base_url + path

    # ---- admin ---------------------------------------------------------------------------------

    def login(self, user: str, password: str) -> dict:
        """`GET /v1/admin` with basic auth; the full `AdminResponse` body."""
        r = self.session.get(self.url("/v1/admin"), auth=(user, password), timeout=self.timeout)
        r.raise_for_status()
        return r.json()

    @staticmethod
    def with_signature(path: str, signature: str) -> str:
        """Appends the already URL-encoded `signatureEncoded` without re-encoding it."""
        sep = "&" if "?" in path else "?"
        return f"{path}{sep}signature={signature}"

    # ---- requests ------------------------------------------------------------------------------

    def request(
        self,
        method: str,
        path: str,
        *,
        headers: dict[str, str] | None = None,
        data: bytes | None = None,
        json: object | None = None,
        auth: tuple[str, str] | None = None,
        signature: str | None = None,
        allow_redirects: bool = False,
    ) -> requests.Response:
        if signature is not None:
            path = self.with_signature(path, signature)
        return self.session.request(
            method,
            self.url(path),
            headers=headers,
            data=data,
            json=json,
            auth=auth,
            timeout=self.timeout,
            allow_redirects=allow_redirects,
        )

    def get(self, path: str, **kw) -> requests.Response:
        return self.request("GET", path, **kw)

    def upload(self, path: str, body: bytes, signature: str) -> requests.Response:
        """`POST` a raw body (`application/octet-stream`), the way `data/upload_noaa_region_encs.py` does."""
        return self.request(
            "POST", path, data=body, signature=signature, headers={"Content-Type": "application/octet-stream"}
        )

    def raw(self, method: str, path: str, headers: dict[str, str] | None = None) -> RawResponse:
        """Sends `path` verbatim (no dot-segment normalisation), for traversal probes."""
        parts = urllib.parse.urlsplit(self.base_url)
        conn_cls = http.client.HTTPSConnection if parts.scheme == "https" else http.client.HTTPConnection
        conn = conn_cls(parts.hostname, parts.port, timeout=self.timeout)
        try:
            conn.putrequest(method, path, skip_accept_encoding=True)
            for k, v in (headers or {}).items():
                conn.putheader(k, v)
            conn.endheaders()
            resp = conn.getresponse()
            return RawResponse(resp.status, {k.lower(): v for k, v in resp.getheaders()}, resp.read())
        finally:
            conn.close()
