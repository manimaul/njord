FROM osgeo/gdal:ubuntu-full-3.3.3

COPY chart_server/build/install /opt

ENV JAVA_OPTS="-Dconfig.file=/opt/chart_server/application.conf -Dcharts.webStaticContent=/opt/chart_server/public"

COPY chart_server/src/main/resources/application.conf /opt/chart_server/application.conf
COPY chart_server_fe/public /opt/chart_server/public

CMD ["/opt/chart_server/bin/chart_server"]
