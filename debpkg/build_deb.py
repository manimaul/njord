#!/usr/bin/env python3
"""
Build a Debian package for the Njord Marine ENC Chart Server.

Usage:
    python3 build_deb.py [--version VERSION]

Prerequisites (run from the repo root first):
    ./gradlew :server:linkReleaseExecutableNative
    ./gradlew :web:jsBrowserDistribution

The script expects to be run from the debpkg/ directory or the repo root.
"""

import argparse
import os
import shutil
import subprocess
import sys
import textwrap
import secrets
import string
from pathlib import Path


PACKAGE_NAME = "njord"
MAINTAINER = "Njord Maintainers <noreply@openenc.com>"
DESCRIPTION = "Njord Marine ENC Chart Server (S-57 → MVT tiles)"

def _host_architecture() -> str:
    result = subprocess.run(["dpkg", "--print-architecture"], capture_output=True, text=True)
    if result.returncode == 0:
        return result.stdout.strip()
    # fallback: map uname machine to Debian arch names
    import platform
    machine = platform.machine()
    return {"x86_64": "amd64", "aarch64": "arm64"}.get(machine, machine)

ARCHITECTURE = _host_architecture()
DEPENDS = [
    "systemd",
    "ca-certificates",
    "libgdal30",
    "libpq5",
    "libzip4",
    "libssl3",
    "libcurl4",
    "libgd3",
    "postgis",
    "postgresql-client",
]

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent


def find_server_binary() -> Path:
    candidates = [
        REPO_ROOT / "server/build/bin/arch/releaseExecutable/server.kexe",
    ]
    for p in candidates:
        if p.exists():
            return p
    sys.exit(
        "ERROR: server binary not found. Run:\n"
        "  ./gradlew :server:linkReleaseExecutableNative"
    )


def find_web_dist() -> Path:
    p = REPO_ROOT / "web/build/dist/js/productionExecutable"
    if not p.is_dir():
        sys.exit(
            "ERROR: web dist not found. Run:\n"
            "  ./gradlew :web:jsBrowserDistribution"
        )
    return p


def read_version() -> str:
    props = REPO_ROOT / "gradle.properties"
    for line in props.read_text().splitlines():
        if line.startswith("version="):
            return line.split("=", 1)[1].strip()
    return "0.0.0"


def build_package(version: str) -> None:
    pkg_root = SCRIPT_DIR / f"{PACKAGE_NAME}_{version}_{ARCHITECTURE}"
    if pkg_root.exists():
        shutil.rmtree(pkg_root)

    # ── directory layout ────────────────────────────────────────────────────
    debian_dir   = pkg_root / "DEBIAN"
    lib_njord    = pkg_root / "usr/lib/njord"
    share_njord  = pkg_root / "usr/share/njord"
    etc_njord    = pkg_root / "etc/njord"
    systemd_dir  = pkg_root / "lib/systemd/system"

    for d in (debian_dir, lib_njord, share_njord, etc_njord, systemd_dir):
        d.mkdir(parents=True, exist_ok=True)

    # ── server binary ────────────────────────────────────────────────────────
    binary = find_server_binary()
    dest_binary = lib_njord / "server.kexe"
    shutil.copy2(binary, dest_binary)
    dest_binary.chmod(0o755)

    # ── server resources (fonts, sprites, JSON data) ─────────────────────────
    src_resources = REPO_ROOT / "server/src/nativeMain/resources"
    for item in src_resources.iterdir():
        # if item.name == "config":
        #     continue
        dst = lib_njord / item.name
        if item.is_dir():
            shutil.copytree(item, dst, dirs_exist_ok=True)
        else:
            shutil.copy2(item, dst)

    # ── application.json → debpkg/ (with webStaticContent patched) ──────────
    import json
    app_json_src = src_resources / "config/application.json"
    app_json_dst = lib_njord / "config/application.json"
    config = json.loads(app_json_src.read_text())
    config["webStaticContent"] = "/usr/lib/njord/js"
    config["useTileCache"] = True
    config["chartIngestWorkers"] = 2
    config["pgConnectionInfo"] = "postgresql://admin:admin@localhost:5432/s57server"
    chars = string.ascii_letters + string.digits
    config["adminKey"] = ''.join(secrets.choice(chars) for _ in range(32))
    app_json_dst.write_text(json.dumps(config, indent=4) + "\n")

    shutil.move(app_json_dst, etc_njord / "application.json")

    # ── web frontend ─────────────────────────────────────────────────────────
    web_dist = find_web_dist()
    shutil.copytree(web_dist,  lib_njord / "js", dirs_exist_ok=True)
    # Normalize permissions: dirs 755, files 644 (source files may have tighter perms)
    js_dir = lib_njord / "js"
    for item in js_dir.rglob("*"):
        if item.is_dir():
            item.chmod(0o755)
        else:
            item.chmod(0o644)

    # ── systemd unit ─────────────────────────────────────────────────────────
    shutil.copy2(SCRIPT_DIR / "njord.service", systemd_dir / "njord.service")

    # ── helper scripts ───────────────────────────────────────────────────────
    db_setup = share_njord / "db_setup.sh"
    shutil.copy2(SCRIPT_DIR / "db_setup.sh", db_setup)
    db_setup.chmod(0o755)

    # ── data scripts ─────────────────────────────────────────────────────────
    data_dir = REPO_ROOT / "data"
    for script in sorted(data_dir.glob("*.py")):
        dest = share_njord / script.name
        shutil.copy2(script, dest)
        dest.chmod(0o755)

    # ── DEBIAN/control ───────────────────────────────────────────────────────
    (debian_dir / "control").write_text(
        textwrap.dedent(f"""\
            Package: {PACKAGE_NAME}
            Version: {version}
            Architecture: {ARCHITECTURE}
            Maintainer: {MAINTAINER}
            Depends: {", ".join(DEPENDS)}
            Section: net
            Priority: optional
            Description: {DESCRIPTION}
        """)
    )

    # ── DEBIAN/conffiles ──────────────────────────────────────────────────────
    (debian_dir / "conffiles").write_text("/etc/njord/application.json\n")

    # ── DEBIAN/preinst, postinst, prerm ──────────────────────────────────────
    for script in ("preinst", "postinst", "prerm"):
        dest = debian_dir / script
        shutil.copy2(SCRIPT_DIR / script, dest)
        dest.chmod(0o755)

    # ── build the .deb ───────────────────────────────────────────────────────
    output_deb = SCRIPT_DIR / f"{PACKAGE_NAME}_{version}_{ARCHITECTURE}.deb"
    result = subprocess.run(
        ["dpkg-deb", "--build", "--root-owner-group", str(pkg_root), str(output_deb)],
        check=False,
    )
    if result.returncode != 0:
        sys.exit(f"ERROR: dpkg-deb failed with exit code {result.returncode}")

    # clean up staging tree
    shutil.rmtree(pkg_root)

    print(f"\nPackage built: {output_deb}")
    print(f"Install with:  sudo dpkg -i {output_deb.name}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Build Njord Debian package")
    parser.add_argument(
        "--version",
        default=None,
        help="Override package version (default: read from gradle.properties)",
    )
    args = parser.parse_args()

    version = args.version or read_version()
    print(f"Building {PACKAGE_NAME} {version} ({ARCHITECTURE}) …")
    build_package(version)


if __name__ == "__main__":
    main()
