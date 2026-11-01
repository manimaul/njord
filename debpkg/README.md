# Build Debian Package

```shell
cargo build --release -p njord-server && \
(cd crates/njord-web && trunk build --release) && \
python3 debpkg/build_deb.py
```

Raspberry Pi 64-bit (ARM64), built in a `linux/arm64` container:

```shell
bash debpkg/build_deb_arm64.sh
```

## Install
```shell
sudo dpkg -i ./njord<version>.deb
```
