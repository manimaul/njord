# Local Dev
* url = `jdbc:postgresql://localhost:5432/s57server`
* username = `admin`
* password = `mysecretpassword`


Rather than installing PostGIS on your development machine just use `docker-compose` to bring up the database running 
in a container with port `5432` exposed to your host network.
```shell
docker-compose up
```
or

```shell
app=njord-postgis-svc
ns=njord
pod=$(kubectl -n "$ns" get pods -l app="$app" -o jsonpath='{.items[*].metadata.name}')
kubectl -n "$ns" port-forward "$pod" 5432:5432
docker run -p 11211:11211 memcached:1.6 memcached -I 128m -m 512
```

```shell
./gradlew :chart_server:run
```

