FROM gradle:7.2.0-jdk11 as builder

WORKDIR /build
COPY . .
RUN gradle :chart_server:installDist

FROM osgeo/gdal:ubuntu-full-3.3.3

COPY --from=builder /build/chart_server/build/install /opt

ENV JAVA_OPTS="-Dconfig.file=/opt/chart_server/application.conf"

COPY chart_server/src/main/resources/application.conf /opt/chart_server/application.conf

CMD ["/opt/chart_server/bin/chart_server"]
