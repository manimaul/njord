# Image for initializing the PostGIS Db
FROM debian:buster-slim

RUN apt-get update && apt-get install -y postgresql-client

WORKDIR /postgres_init
#COPY up.sql .
#COPY postgres_init.sh .

# https://www.postgresql.org/docs/current/libpq-envars.html
ENV PGUSER=admin
ENV PGPASSWORD=mysecretpassword
ENV PGHOST=localhost
ENV PGPORT=5432
ENV PGDATABASE=s57server
