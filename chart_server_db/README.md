# Local Dev
* url = `jdbc:postgresql://localhost:5432/s57server`
* username = `admin`
* password = `mysecretpassword`


Rather than installing PostGIS on your development machine just use `podman-compose` to bring up the database running
in a container with port `5432` exposed to your host network.
```shell
podman-compose up
```
or

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