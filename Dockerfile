FROM debian:bookworm as builder

RUN apt update && apt install -y build-essential cmake swig libproj-dev libjson-c-dev openjdk-17-jdk-headless openjdk-17-source ant

ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64/
ENV JAVADOC=/usr/lib/jvm/java-17-openjdk-amd64/bin/javadoc
ENV JAVAC=/usr/lib/jvm/java-17-openjdk-amd64/bin/javac
ENV JAVA=/usr/lib/jvm/java-17-openjdk-amd64/bin/java
ENV JAR=/usr/lib/jvm/java-17-openjdk-amd64/bin/jar
ENV JAVA_INCLUDE="-I/usr/lib/jvm/java-17-openjdk-amd64/include -I/usr/lib/jvm/java-17-openjdk-amd64/include/linux"

WORKDIR /build
ENV GDAL_VERSION=3.10.0
ADD http://download.osgeo.org/gdal/$GDAL_VERSION/gdal-$GDAL_VERSION.tar.xz .
RUN tar -xf ./gdal-$GDAL_VERSION.tar.xz

WORKDIR /build/gdal-$GDAL_VERSION
RUN cmake -S . -B build \
	-DCMAKE_INSTALL_RPATH=/opt/gdal \
	-DBUILD_JAVA_BINDINGS=ON \
	-DCMAKE_INSTALL_PREFIX=/opt/gdal \
	-DCMAKE_INSTALL_LIBDIR=/opt/gdal \
	-DCMAKE_BUILD_TYPE=Release \
	-DCMAKE_VERBOSE_MAKEFILE=ON \
	-Wno-dev \
	-DBUILD_TESTING=OFF
RUN cmake --build build && \
	mkdir -p /opt/gdal && \
	cmake --install build


FROM debian:bookworm
COPY --from=builder /opt/gdal /opt/gdal

RUN apt update && apt install -y openjdk-17-jre-headless libcurl3-gnutls libdeflate0 libtiff6 libproj25 libjson-c5

COPY chart_server/build/install /opt
ADD https://github.com/prometheus/jmx_exporter/releases/download/1.1.0/jmx_prometheus_javaagent-1.1.0.jar /opt/chart_server/jmx-agent.jar

ENV JAVA_OPTS="-Dconfig.file=/opt/chart_server/application.conf -Dcharts.webStaticContent=/opt/chart_server/public -Djava.library.path=/opt/gdal/jni"

COPY chart_server/src/jvmMain/resources/application.conf /opt/chart_server/application.conf
COPY web/build/dist/js/productionExecutable /opt/chart_server/public

CMD ["/opt/chart_server/bin/chart_server"]
