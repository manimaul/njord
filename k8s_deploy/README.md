# Kubernetes Deployment Configs

------------------

## Chart Server

Deploy
```shell
./gradlew deploy
```

Logs
```shell
kubectl -n njord logs $(kubectl get pods -n njord -l app=njord-chart-svc -o jsonpath='{.items[*].metadata.name}')
```

------------------

## PostGIS

Publish init container image
```shell
cd chart_server_db/postres_init
docker build -t "ghcr.io/manimaul/njord-init-postgis:latest" .
docker push "ghcr.io/manimaul/njord-init-postgis:latest"
```

Add Github container registry secrets to njord namespace
```shell
cd k8s
./k8s_create_reg_sec.sh k8s_login
cd ..
./gradlew secret
```

Deploy postgis service
```shell
cd k8s
kubectl apply -f postgis_volume.yaml
kubectl apply -f postgis.yaml
# wait
kubectl apply -f postgis_init.yaml
```

Check postgis service
```shell
kubectl -n njord get pods --selector=job-name=njord-init-postgis

kubectl -n njord describe pod -l app=njord-postgis-svc 
kubectl -n njord logs $(kubectl get pods -n njord -l app=njord-postgis-svc -o jsonpath='{.items[*].metadata.name}')
kubectl -n njord describe deployment njord-postgis
```
