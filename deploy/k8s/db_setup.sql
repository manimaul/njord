-- One-time setup of an environment's database and role on the managed PostgreSQL cluster.
--
-- Nothing in the server creates the database or the PostGIS extension: the schema migrations
-- assume both exist. Run this once per environment, by hand, as the managed cluster's admin user
-- (akmadmin). For staging:
--
--   psql "postgresql://akmadmin@$NJORD_STAGING_DB_HOST:$NJORD_STAGING_DB_PORT/defaultdb?sslmode=require" \
--     -v db=njord_staging -v role=njord_staging -v pass="$NJORD_STAGING_DB_PASS" \
--     -v connection_limit=25 -f deploy/k8s/db_setup.sql
--
--   db                 the database to create
--   role               the role to create: it owns the database, and the servers connect as it
--   pass               the role's password, passed as a variable so it is not written here
--   connection_limit   optional: the most connections the role may hold (default: no limit)
--
-- Staging's limit of 25 leaves at least 75 of the managed server's 100 connections to everything
-- else on it. Each process pools at most 10 (MAX_DB_CONNECTIONS), so the server, the ingest and a
-- psql session fit.
--
-- Check the managed plan's free disk first: a database holding the full NOAA catalog (about
-- 7.2k cells) grows to about 9.3 GB.
--
-- Production's database, `njord`, was created before this script revoked CONNECT. The one
-- statement that brings it in line, run as the `njord` role:
--   REVOKE CONNECT ON DATABASE njord FROM PUBLIC;

\set ON_ERROR_STOP on

\if :{?db}
\else
  \warn 'db is not set: pass -v db=<database>'
  DO $$ BEGIN RAISE EXCEPTION 'db_setup.sql: a required variable is not set'; END $$;
\endif
\if :{?role}
\else
  \warn 'role is not set: pass -v role=<role>'
  DO $$ BEGIN RAISE EXCEPTION 'db_setup.sql: a required variable is not set'; END $$;
\endif
\if :{?pass}
\else
  \warn 'pass is not set: pass -v pass=<password>'
  DO $$ BEGIN RAISE EXCEPTION 'db_setup.sql: a required variable is not set'; END $$;
\endif
\if :{?connection_limit}
\else
  \set connection_limit -1
\endif

-- A dedicated role per environment, so that neither environment's pgConnectionInfo can open the
-- other's database.
CREATE ROLE :"role" LOGIN PASSWORD :'pass' CONNECTION LIMIT :connection_limit;

-- The admin user is not a superuser on the managed cluster. Membership in the new role lets it
-- create a database that role owns, and connect to it once PUBLIC cannot.
GRANT :"role" TO CURRENT_USER;

CREATE DATABASE :"db" OWNER :"role";

-- PostgreSQL lets every role connect to every database unless this is revoked. Afterwards only
-- the owner, and the members of the owner role, can.
REVOKE CONNECT ON DATABASE :"db" FROM PUBLIC;

\c :"db"

CREATE EXTENSION IF NOT EXISTS postgis;

-- Verify. `access` must not hold an entry that starts with `=c` or `=Tc` (CONNECT for PUBLIC).
SELECT current_database() AS database, pg_get_userbyid(datdba) AS owner, datacl AS access
  FROM pg_database WHERE datname = current_database();
SELECT rolname AS role, rolconnlimit AS connection_limit FROM pg_roles WHERE rolname = :'role';
SELECT extname, extversion FROM pg_extension WHERE extname = 'postgis';
