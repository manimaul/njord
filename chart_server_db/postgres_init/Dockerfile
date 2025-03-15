# Image for initializing the PostGIS Db
FROM debian:buster-slim

RUN apt-get update && apt-get install -y postgresql-client

WORKDIR /postgres_init
COPY scripts/up.sql .
COPY scripts/rds.sql .
COPY scripts/postgres_init.sh .

# https://www.postgresql.org/docs/current/libpq-envars.html
ENV RDS=0
ENV PGUSER=admin
ENV PGPASSWORD=mysecretpassword
ENV PGHOST=localhost
ENV PGPORT=5432
ENV PGDATABASE=s57server
