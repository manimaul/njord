# Developing on MacOS


Build Gdal with Java Bindings
```
version=3.6.2
wget http://download.osgeo.org/gdal/$version/gdal-$version.tar.xz
tar -xf gdal-$version.tar.xz
cd gdal
cmake -S . -B build \
	-DCMAKE_INSTALL_RPATH=/opt/gdal \
	-DBUILD_JAVA_BINDINGS=ON \
	-DCMAKE_INSTALL_PREFIX=/opt/gdal \
	-DCMAKE_INSTALL_LIBDIR=/opt/gdal \
	-DCMAKE_BUILD_TYPE=Release \
	-DCMAKE_VERBOSE_MAKEFILE=ON \
	-Wno-dev \
	-DBUILD_TESTING=OFF
cmake --build build
sudo mkdir -p /opt/gdal
sudo chown -R $(whoami) /opt/gdal
cmake --install build
cp /opt/gdal/share/java/gdal-$version.jar $(pwd)../../njord/chart_server/libs
```

