# Developing on Linux


Build Gdal with Java Bindings
```
sudo apt install -y build-essential cmake swig libproj-dev \
                    libjson-c-dev openjdk-17-jdk-headless \
		    openjdk-17-source ant
export JAVA_HOME=$(dirname $(dirname $(readlink -e /usr/bin/javac))) 
export JAVADOC="${JAVA_HOME}/bin/javadoc"
export JAVAC="${JAVA_HOME}/bin/javac"
export JAVA="${JAVA_HOME}/bin/java"
export JAR="${JAVA_HOME}/bin/jar"
export JAVA_INCLUDE="-I${JAVA_HOME}/include -I${JAVA_HOME}/include/linux"

version=3.7.0
wget http://download.osgeo.org/gdal/$version/gdal-$version.tar.xz
tar -xf gdal-$version.tar.xz
cd gdal-$version
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
cp /opt/gdal/share/java/gdal-$version.jar $(pwd)/../../../chart_server/libs
```

# Developing on MacOS
Build GDAL on MacOS Sequoia with Java binding
```
cmake -S . -B build \
	-DCMAKE_BUILD_TYPE=Release \
	-DCMAKE_OSX_ARCHITECTURES=arm64 \
	-DBUILD_JAVA_BINDINGS=ON \
  	-DCMAKE_PREFIX_PATH=/opt/anaconda3/envs/geospatial \
	-DCMAKE_INSTALL_PREFIX=$CONDA_PREFIX \
	-DCMAKE_INSTALL_RPATH=$CONDA_PREFIX/lib \
	-DCMAKE_VERBOSE_MAKEFILE=ON \
	-DCMAKE_CXX_FLAGS="-I/opt/homebrew/opt/podofo/include/podofo" \
	-DCMAKE_FIND_FRAMEWORK=LAST \
	-DCMAKE_FIND_APPBUNDLE=LAST \
	-DCMAKE_DISABLE_FIND_PACKAGE_Arrow=ON \
	-DGDAL_USE_LIBKML=OFF \
	-DOGR_ENABLE_DRIVER_LIBKML=OFF \
	-DBUILD_TESTING=OFF \
	-DGDAL_USE_GEOTIFF_INTERNAL=ON \
	-DGDAL_USE_TIFF_INTERNAL=ON \
	-DGDAL_ENABLE_HDF5_GLOBAL_LOCK=NO \
	-DGDAL_USE_MRSID=OFF \
	-DHDF5_ROOT=$(brew --prefix hdf5) \
	-DNETCDF_ROOT=$(brew --prefix netcdf) \
	-DZSTD_ROOT=$(brew --prefix zstd) \
	-DJPEG_INCLUDE_DIR=$(brew --prefix jpeg-turbo)/include \
	-DJPEG_LIBRARY=$(brew --prefix jpeg-turbo)/lib/libjpeg.dylib \
	-DTIFF_INCLUDE_DIR=$(brew --prefix libtiff)/include \
	-DTIFF_LIBRARY=$(brew --prefix libtiff)/lib/libtiff.dylib \
	-DPNG_PNG_INCLUDE_DIR=$(brew --prefix libpng)/include \
	-DPNG_LIBRARY=$(brew --prefix libpng)/lib/libpng.dylib \
	-DPODOFO_INCLUDE_DIR=/opt/homebrew/opt/podofo/include/podofo \
	-DPODOFO_LIBRARY=/opt/homebrew/opt/podofo/lib/libpodofo.dylib \
	-Wno-dev

cmake --build build --parallel 6
cmake --install build --prefix $CONDA_PREFIX

```