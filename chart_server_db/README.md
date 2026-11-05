# Local Dev
* url = `postgresql://admin:mysecretpassword@localhost:6432/njord_rust`
* username = `admin`
* password = `mysecretpassword`

Rather than installing PostGIS on your development machine just use `podman-compose` to bring up the database running
in a container with port `6432` exposed to your host network. There is no pgbouncer: the server pools
connections itself with sqlx.
```shell
podman-compose up -d
```

The first start (an empty `pb/`, the gitignored data directory) creates the `njord_rust` database with the PostGIS
extension loaded; this is the database `crates/njord-server/resources/config/application.json` points at. The init
runs only on an empty data directory, so to start over, stop the container and delete `pb/`. The `njord-db` and
`njord-server` tests create and drop their own `njord_rust_*` databases on this server and never touch `njord_rust`.

## Connect Prod db to local dev
```shell
app=njord-postgis-svc
ns=njord
pod=$(kubectl -n "$ns" get pods -l app="$app" -o jsonpath='{.items[*].metadata.name}')
kubectl -n "$ns" port-forward "$pod" 5432:5432
```

## Issues on Debian & Podman

`podman info --format '{{.Host.NetworkBackend}}'` reported `cni` which led to DNS failing

I had to enable netavark dns and reset podman:

Step 1: (If /etc/containers/containers.conf does not exist)
```shell
sudo cp /usr/share/containers/containers.conf /etc/containers/
```

Step 2: Edit /etc/containers/containers.conf
```toml
[network]
network_backend = "netavark"
```

Step 3: Reset Podman (removes all managed data)
```shell
podman system reset
```