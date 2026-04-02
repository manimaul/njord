#!/bin/bash
# db_setup.sh — Initialize the Njord PostgreSQL database.
#
# Reads /etc/njord/application.json and parses the pgConnectionInfo URL to
# determine the database name, role, and password, then creates the role and
# database (if they don't already exist)
# and enables the PostGIS extension.
#
# Must be run as root (uses sudo -u postgres internally).
#
# Usage:
#   sudo /usr/share/njord/db_setup.sh [/path/to/application.json]

set -euo pipefail

CONFIG="${1:-/etc/njord/application.json}"

if [ ! -f "$CONFIG" ]; then
    echo "ERROR: config file not found: $CONFIG" >&2
    exit 1
fi

# Parse pgConnectionInfo, adminUser, and adminPass from JSON
eval "$(python3 - "$CONFIG" <<'PYEOF'
import json, sys
from urllib.parse import urlparse

cfg = json.load(open(sys.argv[1]))
url = urlparse(cfg["pgConnectionInfo"])
print(f"DB_NAME={url.path.lstrip('/')}")
print(f"DB_USER={url.username}")
print(f"DB_PASS={url.password}")
PYEOF
)"

echo "Configuring PostgreSQL for Njord..."
echo "  Database : $DB_NAME"
echo "  Role     : $DB_USER"

# Create role and database (must connect directly to postgres, not pgbouncer)
sudo -u postgres psql -v ON_ERROR_STOP=1 <<SQL
DO \$\$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '${DB_USER}') THEN
        CREATE ROLE "${DB_USER}" WITH LOGIN PASSWORD '${DB_PASS}';
        RAISE NOTICE 'Role "${DB_USER}" created.';
    ELSE
        RAISE NOTICE 'Role "${DB_USER}" already exists.';
    END IF;
END
\$\$;

SELECT 'CREATE DATABASE "${DB_NAME}" OWNER "${DB_USER}"'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '${DB_NAME}')\gexec
SQL

sudo -u postgres psql -v ON_ERROR_STOP=1 -d "$DB_NAME" -c "CREATE EXTENSION IF NOT EXISTS postgis;"

echo "Done. Database '$DB_NAME' is ready."
echo "Run 'sudo systemctl start njord' to start the service."
