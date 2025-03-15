# Build Gdal with Java Bindings

##  [Corretto](https://docs.aws.amazon.com/corretto/latest/corretto-17-ug/generic-linux-install.html)
Note: Raspberry pi openjdk-17-source debian package seems broken 
```shell
wget -O - https://apt.corretto.aws/corretto.key | sudo gpg --dearmor -o /usr/share/keyrings/corretto-keyring.gpg && \
echo "deb [signed-by=/usr/share/keyrings/corretto-keyring.gpg] https://apt.corretto.aws stable main" | sudo tee /etc/apt/sources.list.d/corretto.list

sudo apt update
sudo apt install -y build-essential cmake swig libproj-dev libjson-c-dev java-17-amazon-corretto-jdk ant checkinstall
```

## OpenJDK 
Node: Debian, Ubuntu, PopOS
```shell
sudo apt install -y build-essential cmake swig libproj-dev libjson-c-dev openjdk-21-jdk-headless openjdk-21-source ant checkinstall
```

```shell
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
fakeroot checkinstall --install=no -D cmake --install build
sudo apt install "./gdal_$version-1_$(arch).deb"
/opt/gdal/bin/gdalinfo --version
```

