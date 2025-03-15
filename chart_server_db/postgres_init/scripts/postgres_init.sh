#!/usr/bin/env sh

set -e

until psql -c '\q'; do
  >&2 echo "Postgres is unavailable - sleeping"
  sleep 1
done

>&2 echo "Postgres is up - executing command"

if [[ $RDS == 1 ]]; then
  cat ./rds.sql | sed "s/admin/$PGUSER/g" > ./_rds.sql
  psql -a -q -f ./_rds.sql
fi

psql -a -q -f ./up.sql