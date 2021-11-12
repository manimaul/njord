#!/usr/bin/env bash

set -eux

DIR="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
pushd "$DIR/.."
./gradlew :chart_server:installDist
popd
docker build -t "ghcr.io/manimaul/njord-chart-server:latest" -f "$DIR/Dockerfile" "$DIR/.."
