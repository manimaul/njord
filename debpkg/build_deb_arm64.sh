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

docker buildx build \
    --output "type=local,dest=$SCRIPT_DIR" \
    --target artifact \
    -f "$SCRIPT_DIR/Dockerfile.arm64" \
    "$REPO_ROOT"

echo ""
echo "Done:"
ls "$SCRIPT_DIR"/njord_*_arm64.deb 2>/dev/null || echo "  (no .deb found — check build output above)"
