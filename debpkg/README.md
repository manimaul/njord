# Debian Package

Targets Debian 13 (trixie) and Raspberry Pi OS based on it: the package depends on trixie's `libgdal36`.

## Build

On a Debian host (the package takes the host's architecture):

```shell
cargo build --release -p njord-server && \
(cd crates/njord-web && trunk build --release) && \
python3 debpkg/build_deb.py
```

Raspberry Pi 64-bit (ARM64), built in a `linux/arm64` container (native on Apple Silicon, QEMU on x86_64):

```shell
bash debpkg/build_deb_arm64.sh
```

Either way the package is written to `debpkg/njord_<version>_<arch>.deb`.

## Install

Use `apt`, not `dpkg -i`: `apt` also installs the dependencies (GDAL, PostgreSQL and PostGIS).

```shell
sudo apt install ./njord_<version>_arm64.deb
```

The service is enabled at boot but not started, because the database has to exist first:

1. Edit `/etc/njord/application.json`. At least change `adminUser`/`adminPass` and the password in
   `pgConnectionInfo` from the `admin`/`admin` defaults.
2. Create the database role, the database and the PostGIS extension from `pgConnectionInfo`:
   ```shell
   sudo /usr/share/njord/db_setup.sh
   ```
3. Start the server, then open `http://<host>:9000`:
   ```shell
   sudo systemctl start njord
   journalctl -u njord -f
   ```

## Layout

| Path | Contents |
|---|---|
| `/etc/njord/application.json` | Configuration (a conffile: kept on upgrade if you edited it) |
| `/etc/njord/njord.env` | This install's `adminKey`, generated at install time and passed as `CHART_SERVER_OPTS`, which overrides `application.json` |
| `/var/lib/njord` | `chartTempData`: uploads, region `.mbtiles` archives, tile cache |
| `/usr/lib/njord` | Server binary, resources and the web UI |
| `/usr/share/njord` | `db_setup.sh` and the chart download/upload scripts |

## Upgrade and removal

`sudo apt install ./njord_<newer version>_arm64.deb` upgrades in place and restarts the service if it was
running. `sudo apt remove njord` keeps the configuration and data; `sudo apt purge njord` also deletes
`/etc/njord`, `/var/lib/njord` and the `njord` user. The PostgreSQL database is never dropped
(`sudo -u postgres dropdb s57server` if it is no longer needed).
