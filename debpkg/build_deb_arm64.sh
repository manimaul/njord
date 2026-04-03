#!/usr/bin/env bash
# Build a Raspberry Pi 64-bit (ARM64) Debian package.
# Uses Kotlin/Native cross-compilation (x86_64 → linuxArm64) — no QEMU required.
# Run from the repo root or debpkg/ directory.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"

echo "==> Building web frontend on host (architecture-independent)"
cd "$REPO_ROOT"
./gradlew :web:jsBrowserDistribution
cd - > /dev/null

echo "==> Cross-compiling ARM64 .deb (x86_64 → linuxArm64, no QEMU)"
echo "    Output: $SCRIPT_DIR/njord_*_arm64.deb"

# Cross compiling Kotlin native is only available on Linux x86_64
# Even on MacOS (arm64) since we are building in a Linux container we need the container to be x86_64 in order to cross compiling to arm64
# This could probably be optimized using a arm64 sysroot and building on the host targeting arm64 using the sysroot
podman build \
    --platform linux/amd64 \
    --target artifact \
    -t njord-arm64-artifact-tmp \
    -f "$SCRIPT_DIR/Containerfile.arm64" \
    "$REPO_ROOT"
CID=$(podman create --platform linux/amd64 njord-arm64-artifact-tmp)
podman cp "${CID}:/" "$SCRIPT_DIR/"
podman rm "$CID"
podman rmi njord-arm64-artifact-tmp

echo ""
echo "Done:"
ls "$SCRIPT_DIR"/njord_*_arm64.deb 2>/dev/null || echo "  (no .deb found — check build output above)"
