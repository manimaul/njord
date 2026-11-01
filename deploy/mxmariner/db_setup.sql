-- One-time setup of the mxmariner environment's database (docs/RUST_PORT_PLAN.md §10.2 item 4).
--
-- Nothing in the server creates the database or the PostGIS extension: DbMigrations assumes both
-- exist. Run this ONCE, by hand, as the managed cluster's admin user (akmadmin), against the
-- same managed Postgres cluster the njord namespace uses. It does not touch s57server.
--
--   psql "postgresql://akmadmin@<NJORD_DB_HOST>:<NJORD_DB_PORT>/defaultdb?sslmode=require" \
--     -v ON_ERROR_STOP=1 -v njord_pass="$NJORD_DB_PASS" -f deploy/mxmariner/db_setup.sql
--
-- The password is passed as a psql variable so it is not written into this file or the shell
-- history of the statement. Check the managed plan's free disk first: this database will grow to
-- about the size of s57server (full NOAA catalog, ~7.2k cells).

\set ON_ERROR_STOP on

-- A dedicated role rather than akmadmin, so a misconfigured pgConnectionInfo cannot reach
-- s57server's data.
CREATE ROLE njord LOGIN PASSWORD :'njord_pass';

-- If this fails with "must be member of role njord" (akmadmin is not a superuser on the managed
-- cluster), run `GRANT njord TO CURRENT_USER;` and retry.
CREATE DATABASE njord OWNER njord;

\c njord

CREATE EXTENSION IF NOT EXISTS postgis;

-- Verify.
SELECT current_database() AS database, pg_get_userbyid(datdba) AS owner
  FROM pg_database WHERE datname = current_database();
SELECT extname, extversion FROM pg_extension WHERE extname = 'postgis';
