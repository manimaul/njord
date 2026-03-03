#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# ---------------------------------------------------------------------------
# Read version; convert - to ~ for Debian pre-release ordering
# (e.g. 1.0-SNAPSHOT -> 1.0~SNAPSHOT sorts below 1.0 as intended)
# ---------------------------------------------------------------------------
UPSTREAM_VERSION=$(grep '^version=' gradle.properties | cut -d'=' -f2)
DEB_VERSION="${UPSTREAM_VERSION//-/\~}"

# Detect architecture
if command -v dpkg &>/dev/null; then
    ARCH=$(dpkg --print-architecture)
else
    case "$(uname -m)" in
        x86_64)  ARCH="amd64" ;;
        aarch64) ARCH="arm64" ;;
        *)       ARCH="$(uname -m)" ;;
    esac
fi

DEB_NAME="njord_${DEB_VERSION}_${ARCH}.deb"
PKG_DIR="build/deb/njord_${DEB_VERSION}_${ARCH}"

echo "Building ${DEB_NAME} ..."

# ---------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------
./gradlew :server:linkReleaseExecutableNative :web:jsBrowserDistribution --no-daemon

BINARY="server/build/bin/native/releaseExecutable/server.kexe"
strip --strip-all "$BINARY"

# ---------------------------------------------------------------------------
# Lay out package tree
# ---------------------------------------------------------------------------
rm -rf "$PKG_DIR"
mkdir -p \
    "$PKG_DIR/DEBIAN" \
    "$PKG_DIR/usr/bin" \
    "$PKG_DIR/usr/share/njord" \
    "$PKG_DIR/lib/systemd/system"

# Executable
install -m 755 "$BINARY" "$PKG_DIR/usr/bin/njord"

# Resources (json data files, config, committed www assets)
cp -r server/src/nativeMain/resources/. "$PKG_DIR/usr/share/njord/"

# Overwrite www with freshly built web frontend
rm -rf "$PKG_DIR/usr/share/njord/www"
cp -r web/build/dist/js/productionExecutable/. "$PKG_DIR/usr/share/njord/www/"

# ---------------------------------------------------------------------------
# Systemd unit
# ---------------------------------------------------------------------------
cat > "$PKG_DIR/lib/systemd/system/njord.service" <<'EOF'
[Unit]
Description=Njord Marine ENC Server
Documentation=https://openenc.com
After=network.target

[Service]
Type=simple
User=njord
ExecStart=/usr/bin/njord /usr/share/njord
Restart=on-failure
RestartSec=5
StandardOutput=journal
StandardError=journal
SyslogIdentifier=njord

[Install]
WantedBy=multi-user.target
EOF

# ---------------------------------------------------------------------------
# DEBIAN/control
# ---------------------------------------------------------------------------
cat > "$PKG_DIR/DEBIAN/control" <<EOF
Package: njord
Version: ${DEB_VERSION}
Architecture: ${ARCH}
Maintainer: Njord <njord@openenc.com>
Section: utils
Priority: optional
Depends: libgdal32, libpq5, libzip4, libssl3, libcurl4, libgd3
Description: Njord Marine ENC Server
 Ingests S-57 hydrographic chart files and serves them as
 Mapbox Vector Tiles (MVT).
EOF

# ---------------------------------------------------------------------------
# DEBIAN/postinst  – create user, symlink, enable service
# ---------------------------------------------------------------------------
cat > "$PKG_DIR/DEBIAN/postinst" <<'EOF'
#!/bin/bash
set -e

if ! id -u njord &>/dev/null; then
    useradd --system --no-create-home --shell /usr/sbin/nologin njord
fi

# /etc/njord -> /usr/share/njord/config
ln -snf /usr/share/njord/config /etc/njord

systemctl daemon-reload
systemctl enable njord.service
systemctl start njord.service || true
EOF
chmod 755 "$PKG_DIR/DEBIAN/postinst"

# ---------------------------------------------------------------------------
# DEBIAN/prerm  – stop and disable service before removal
# ---------------------------------------------------------------------------
cat > "$PKG_DIR/DEBIAN/prerm" <<'EOF'
#!/bin/bash
set -e

if systemctl is-active --quiet njord.service 2>/dev/null; then
    systemctl stop njord.service
fi
systemctl disable njord.service 2>/dev/null || true
EOF
chmod 755 "$PKG_DIR/DEBIAN/prerm"

# ---------------------------------------------------------------------------
# Build the .deb
# ---------------------------------------------------------------------------
dpkg-deb --build --root-owner-group "$PKG_DIR" "$DEB_NAME"

echo "Done: ${DEB_NAME}"
