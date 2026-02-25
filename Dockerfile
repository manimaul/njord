FROM debian:bookworm-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
    libgdal32 \
    libpq5 \
    libzip4 \
    libssl3 \
    libcurl4 \
    libgd3 \
    && rm -rf /var/lib/apt/lists/*

COPY server/build/bin/native/releaseExecutable/server.kexe /opt/njord/server.kexe
COPY server/src/nativeMain/resources /opt/njord/resources
COPY docker/application.json /opt/njord/resources/config/application.json
COPY web/build/dist/js/productionExecutable /opt/njord/resources/www

CMD ["/opt/njord/server.kexe", "/opt/njord/resources"]
