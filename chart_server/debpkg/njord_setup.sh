#!/usr/bin/env bash

set -e

until sudo -Hiu postgres psql -c '\q'; do
  >&2 echo "Postgres is unavailable - exiting"
  exit 1;
done

>&2 echo "Postgres is up - executing setup"


db_secret="$(cat /dev/urandom | tr -dc 'A-Za-z0-9' | head -c 32)"
admin_secret="$(cat /dev/urandom | tr -dc 'A-Za-z0-9' | head -c 16)"
admin_key="$(cat /dev/urandom | tr -dc 'A-Za-z0-9' | head -c 8)"

echo "updating /etc/njord.conf with random secrets"
sudo sed -i -e "s/.*adminKey.*/    adminKey = \"$admin_key\"/g" /etc/njord.conf
sudo sed -i -e "s/.*adminPass.*/    adminPass = \"$admin_secret\"/g" /etc/njord.conf
sudo sed -i -e "s/.*pgPassword.*/    pgPassword = \"$db_secret\"/g" /etc/njord.conf

if [ "$(sudo -Hiu postgres psql postgres -tXAc "SELECT 1 FROM pg_roles WHERE rolname='njord'")" != 1 ]
then
  echo "creating postgres user njord"
  sudo -Hiu postgres createuser njord
fi

if [ "$(sudo -Hiu postgres psql postgres -tXAc "SELECT 1 FROM pg_database WHERE datname='njord';")" != 1 ]
then
  echo "creating postgres database njord"
  sudo -Hiu postgres createdb njord
fi

echo "creating postgres database njord"
sudo -Hiu postgres psql -c "ALTER DATABASE njord OWNER TO njord;"
sudo -Hiu postgres psql -c "ALTER USER njord PASSWORD '${db_secret}';"
sudo -Hiu postgres psql -c "ALTER DATABASE njord SET search_path=public,postgis,contrib;"
sudo -Hiu postgres psql njord -c "CREATE EXTENSION IF NOT EXISTS postgis;"
sudo -Hiu postgres psql njord -c "CREATE EXTENSION IF NOT EXISTS postgis_topology;"
sudo -Hiu postgres psql njord -c "CREATE EXTENSION IF NOT EXISTS fuzzystrmatch;"
sudo -Hiu postgres psql njord -c "CREATE EXTENSION IF NOT EXISTS postgis_tiger_geocoder;"
#sudo -Hiu postgres psql njord -c "CREATE EXTENSION IF NOT EXISTS postgis_raster;"
#sudo -Hiu postgres psql njord -c "CREATE EXTENSION IF NOT EXISTS address_standardizer_data_us";
sudo -Hiu postgres psql njord -c "SELECT postgis_full_version();"

#sudo -Hiu postgres psql -c "ALTER SCHEMA tiger OWNER TO njord;"
#sudo -Hiu postgres psql -c "ALTER SCHEMA tiger_data OWNER TO njord;"
#sudo -Hiu postgres psql -c "ALTER SCHEMA topology OWNER TO njord;"

sudo systemctl restart postgresql
sudo systemctl restart memcached
sudo systemctl enable njord
sudo systemctl restart njord
echo "done - the njord admin username password is set in /etc/njord.conf"
