#!/usr/bin/env bash
# Build a Raspberry Pi 64-bit (ARM64) Debian package.
# Compiles natively in a linux/arm64 container (QEMU on an x86_64 host).
# Run from the repo root or debpkg/ directory.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"

echo "==> Building ARM64 .deb"
echo "    Output: $SCRIPT_DIR/njord_*_arm64.deb"

podman build \
    --platform linux/arm64 \
    --ignorefile "$SCRIPT_DIR/containerignore" \
    --target artifact \
    -t njord-arm64-artifact-tmp \
    -f "$SCRIPT_DIR/Containerfile.arm64" \
    "$REPO_ROOT"
CID=$(podman create --platform linux/arm64 njord-arm64-artifact-tmp true)
podman cp "${CID}:/" "$SCRIPT_DIR/"
podman rm "$CID"
podman rmi njord-arm64-artifact-tmp

echo ""
echo "Done:"
ls "$SCRIPT_DIR"/njord_*_arm64.deb 2>/dev/null || echo "  (no .deb found — check build output above)"
