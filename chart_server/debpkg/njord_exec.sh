#!/usr/bin/env bash
set -e

TOTAL_MEM=$(free -m | awk '/Mem:/ {print $2}')

JAVA_OPTS="-Dconfig.file=/etc/njord.conf
-Dcharts.webStaticContent=/opt/chart_server/public
-Djava.library.path=/opt/gdal/jni
-Xms$((TOTAL_MEM * 30 / 100 ))m
-Xmx$((TOTAL_MEM * 65 / 100 ))m" \
/opt/chart_server/bin/chart_server
