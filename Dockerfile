FROM debian:bookworm AS builder

RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    git \
    openjdk-17-jdk-headless \
    libgdal-dev \
    libpq-dev \
    libssl-dev \
    libzip-dev \
    libcurl4-openssl-dev \
    && rm -rf /var/lib/apt/lists/*

ARG GH_USER
ARG GH_TOKEN

WORKDIR /build
COPY . .

RUN ./gradlew :web:jsBrowserDistribution --no-daemon
RUN ./gradlew :server:linkReleaseExecutableNative --no-daemon


FROM debian:bookworm-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
    libgdal32 \
    libpq5 \
    libzip4 \
    libssl3 \
    libcurl4 \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /build/server/build/bin/native/releaseExecutable/server.kexe /opt/njord/server.kexe
COPY --from=builder /build/server/src/nativeMain/resources /opt/njord/resources
COPY docker/application.json /opt/njord/resources/config/application.json
COPY --from=builder /build/web/build/dist/js/productionExecutable /opt/njord/resources/www

CMD ["/opt/njord/server.kexe", "/opt/njord/resources"]
