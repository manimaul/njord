# Developing on Linux


Build Gdal with Java Bindings
```
sudo apt install -y build-essential cmake swig libproj-dev libjson-c-dev openjdk-21-jdk-headless openjdk-21-source ant
export JAVA_HOME=$(dirname $(dirname $(readlink -e /usr/bin/javac))) 
export JAVADOC="${JAVA_HOME}/bin/javadoc"
export JAVAC="${JAVA_HOME}/bin/javac"
export JAVA="${JAVA_HOME}/bin/java"
export JAR="${JAVA_HOME}/bin/jar"
export JAVA_INCLUDE="-I${JAVA_HOME}/include -I${JAVA_HOME}/include/linux"

version=3.10.0
wget http://download.osgeo.org/gdal/$version/gdal-$version.tar.xz
tar -xf gdal-$version.tar.xz
cd gdal-$version
cmake -S . -B build \
	-DCMAKE_INSTALL_RPATH=/opt/gdal \
	-DBUILD_JAVA_BINDINGS=ON \
	-DBUILD_PYTHON_BINDINGS=OFF \
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

