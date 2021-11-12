# Local Dev
* url = `jdbc:postgresql://localhost:5432/s57server`
* username = `admin`
* password = `mysecretpassword`


Rather than installing PostGIS on your development machine just use `docker-compose` to bring up the database running 
in a container with port `5432` exposed to your host network.
```shell
docker-compose up
```