#!/usr/bin/env bash
set -e
JAVA_OPTS="-Dconfig.file=/etc/njord.conf -Dcharts.webStaticContent=/opt/chart_server/public -Djava.library.path=/opt/gdal/jni" \
/opt/chart_server/bin/chart_server
