#!/bin/sh -e

TOTAL_MEM=$(free -m | awk '/Mem:/ {print $4}')
APP_MEM=$((TOTAL_MEM * 85 / 100 ))

export NNS_OPTS="$(printf %s \
"-Dconfig.file=/etc/njord.conf " \
"-Dlog_level=INFO " \
"-XX:+UseG1GC " \
"-XX:MaxGCPauseMillis=200 " \
"-XX:ParallelGCThreads=20 " \
"-XX:ConcGCThreads=5 " \
"-XX:InitiatingHeapOccupancyPercent=70 " \
"-Xmx${APP_MEM}m " \
"-Xms${APP_MEM}m " \
)"

/opt/njord/bin/njord
