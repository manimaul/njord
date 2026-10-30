"""Volatile-field masking, base-URL normalisation and shape reduction of JSON bodies.

Masks are JSON pointers (RFC 6901) with one extension: a `*` segment matches every index of a
list or every key of an object. The empty pointer `""` masks the whole body. Every golden file
carries its own mask list, so a new volatile field is a visible decision, not a silent skip
(RUST_PORT_PLAN.md §5.5).
"""

from __future__ import annotations

import copy
from typing import Any

MASKED = "<masked>"
BASE = "{BASE}"


def _unescape(segment: str) -> str:
    return segment.replace("~1", "/").replace("~0", "~")


def _apply(node: Any, segments: list[str]) -> Any:
    if not segments:
        return MASKED
    head, rest = segments[0], segments[1:]
    if isinstance(node, list):
        if head == "*":
            return [_apply(item, rest) for item in node]
        try:
            idx = int(head)
        except ValueError:
            return node
        if 0 <= idx < len(node):
            node[idx] = _apply(node[idx], rest)
        return node
    if isinstance(node, dict):
        if head == "*":
            return {k: _apply(v, rest) for k, v in node.items()}
        if head in node:
            node[head] = _apply(node[head], rest)
        return node
    return node


def apply_masks(body: Any, pointers: list[str]) -> Any:
    """A deep copy of `body` with every pointer target replaced by `"<masked>"`; missing targets are ignored."""
    out = copy.deepcopy(body)
    for pointer in pointers:
        if pointer == "":
            return MASKED
        if not pointer.startswith("/"):
            raise ValueError(f"not a JSON pointer: {pointer!r}")
        segments = [_unescape(s) for s in pointer[1:].split("/")]
        out = _apply(out, segments)
    return out


def normalize_base(body: Any, base_url: str) -> Any:
    """Replaces the server's external base URL inside every string with `{BASE}`."""
    if isinstance(body, str):
        return body.replace(base_url, BASE)
    if isinstance(body, list):
        return [normalize_base(x, base_url) for x in body]
    if isinstance(body, dict):
        return {k: normalize_base(v, base_url) for k, v in body.items()}
    return body


_ID_COUNTER = __import__("re").compile(r"\d+$")


def normalize_style(style: Any) -> Any:
    """
    Replaces the per-process counter in generated layer ids (`DEPARE_fill_17`) with the layer's
    ordinal among same-named layers (`DEPARE_fill#1`).

    The Kotlin `Layerable.nextId` counter is never reset and `LayerFactory.layers` re-evaluates a
    lazy sequence on every call, so the numeric suffix depends on how many styles the process has
    served (candidate defect, see README). Everything else about a layer, including its position,
    is compared exactly; the port is free to number stably.
    """
    if not isinstance(style, dict) or not isinstance(style.get("layers"), list):
        return style
    seen: dict[str, int] = {}
    layers = []
    for layer in style["layers"]:
        if isinstance(layer, dict) and isinstance(layer.get("id"), str) and _ID_COUNTER.search(layer["id"]):
            stem = _ID_COUNTER.sub("", layer["id"])
            seen[stem] = seen.get(stem, 0) + 1
            layer = {**layer, "id": f"{stem}#{seen[stem]}"}
        layers.append(layer)
    return {**style, "layers": layers}


def type_name(value: Any) -> str:
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "bool"
    if isinstance(value, int):
        return "int"
    if isinstance(value, float):
        return "float"
    if isinstance(value, str):
        return "str"
    if isinstance(value, list):
        return "list"
    if isinstance(value, dict):
        return "object"
    return type(value).__name__


def shape(value: Any, depth: int) -> Any:
    """
    The structural skeleton of a JSON value down to `depth` levels: objects keep their keys, lists
    collapse to the shape of their first element, everything deeper is a type name. Used where two
    servers hold different data and only the contract (status, keys, types) can be compared.
    """
    if depth <= 0 or value is None or isinstance(value, (str, bool, int, float)):
        return type_name(value)
    if isinstance(value, list):
        return [shape(value[0], depth - 1)] if value else []
    if isinstance(value, dict):
        return {k: shape(v, depth - 1) for k, v in value.items()}
    return type_name(value)


def differences(expected: Any, actual: Any, path: str = "", out: list | None = None, limit: int = 20) -> list[str]:
    """Human-readable list of the first `limit` differences between two JSON values, by pointer."""
    if out is None:
        out = []
    if len(out) >= limit:
        return out
    if type(expected) is not type(actual) and not (
        isinstance(expected, (int, float)) and isinstance(actual, (int, float))
        and not isinstance(expected, bool) and not isinstance(actual, bool)
    ):
        out.append(f"{path or '/'}: expected {_short(expected)}, got {_short(actual)}")
        return out
    if isinstance(expected, dict):
        for k in sorted(set(expected) | set(actual)):
            if k not in actual:
                out.append(f"{path}/{k}: missing (expected {_short(expected[k])})")
            elif k not in expected:
                out.append(f"{path}/{k}: unexpected {_short(actual[k])}")
            else:
                differences(expected[k], actual[k], f"{path}/{k}", out, limit)
            if len(out) >= limit:
                return out
        return out
    if isinstance(expected, list):
        if len(expected) != len(actual):
            out.append(f"{path or '/'}: {len(expected)} items expected, got {len(actual)}")
        for i, (e, a) in enumerate(zip(expected, actual)):
            differences(e, a, f"{path}/{i}", out, limit)
            if len(out) >= limit:
                return out
        return out
    if expected != actual:
        out.append(f"{path or '/'}: expected {_short(expected)}, got {_short(actual)}")
    return out


def shape_differences(expected: Any, actual: Any, path: str = "", out: list | None = None, limit: int = 20) -> list[str]:
    """
    `differences` for two `shape()` skeletons: a nullable field (`"null"` on one side) matches any
    scalar type, and an empty list matches any list, since the shape of a value that happens to be
    absent or empty on one server says nothing about the contract.
    """
    if out is None:
        out = []
    if len(out) >= limit:
        return out
    if expected == "null" or actual == "null":
        if isinstance(expected, (dict, list)) or isinstance(actual, (dict, list)):
            pass  # an object/list versus null is a nullable structure: fine either way
        return out
    if isinstance(expected, dict) and isinstance(actual, dict):
        for k in sorted(set(expected) | set(actual)):
            if k not in actual:
                out.append(f"{path}/{k}: missing (expected {_short(expected[k])})")
            elif k not in expected:
                out.append(f"{path}/{k}: unexpected {_short(actual[k])}")
            else:
                shape_differences(expected[k], actual[k], f"{path}/{k}", out, limit)
            if len(out) >= limit:
                return out
        return out
    if isinstance(expected, list) and isinstance(actual, list):
        if expected and actual:
            shape_differences(expected[0], actual[0], f"{path}/0", out, limit)
        return out
    if expected != actual:
        out.append(f"{path or '/'}: expected {_short(expected)}, got {_short(actual)}")
    return out


def _short(value: Any, n: int = 120) -> str:
    text = repr(value)
    return text if len(text) <= n else text[: n - 1] + "…"
