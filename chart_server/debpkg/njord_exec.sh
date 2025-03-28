#!/usr/bin/env bash
set -e

TOTAL_MEM=$(free -m | awk '/Mem:/ {print $2}')

JAVA_OPTS="-Dconfig.file=/etc/njord/njord.conf
-Dcharts.webStaticContent=/opt/chart_server/public
-Djava.library.path=/opt/gdal/jni
-javaagent:/opt/chart_server/jmx_prometheus_javaagent-1.1.0.jar=5000:/etc/njord/jmx_exporter.yaml
-Xms$((TOTAL_MEM * 30 / 100 ))m
-Xmx$((TOTAL_MEM * 65 / 100 ))m" \
/opt/chart_server/bin/chart_server
