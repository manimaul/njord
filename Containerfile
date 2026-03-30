FROM debian:12.9-slim AS builder

RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    git \
    openjdk-17-jdk-headless \
    libgdal-dev \
    libpq-dev \
    libssl-dev \
    libzip-dev \
    libcurl4-openssl-dev \
    libgd-dev \
    && rm -rf /var/lib/apt/lists/*

ENV KONAN_DATA_DIR=/root/.konan

WORKDIR /build
COPY . .

# RUN ./gradlew :web:jsBrowserDistribution --no-daemon
RUN --mount=type=cache,target=/root/.konan \
    --mount=type=cache,target=/root/.gradle \
    ./gradlew :server:linkReleaseExecutableArch :server:linkIngestReleaseExecutableArch --no-daemon

FROM debian:12.9-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    libgdal32 \
    libpq5 \
    libzip4 \
    libssl3 \
    libcurl4 \
    libgd3 \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /build/server/build/bin/arch/releaseExecutable/server.kexe /opt/njord/server.kexe
COPY --from=builder /build/server/build/bin/arch/ingestReleaseExecutable/ingest.kexe /opt/njord/ingest.kexe
COPY --from=builder /build/server/src/nativeMain/resources /opt/njord/resources
COPY container/application.json /opt/njord/resources/config/application.json
COPY web/build/dist/js/productionExecutable /opt/njord/resources/www

CMD ["/opt/njord/server.kexe", "/opt/njord/resources"]
