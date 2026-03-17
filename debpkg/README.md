# Build Debian Package

```shell
./gradlew :server:linkReleaseExecutableArch && \
./gradlew :web:jsBrowserProductionWebpack && \
python3 ./build_deb.py
```

## Install
```shell
sudo dpkg -i ./njord<version>.deb
```