# Kubernetes Deployment Configs

------------------

## Chart Server

Build & Publish container image
```shell
docker build -t "ghcr.io/manimaul/njord-chart-server:latest" .
docker push "ghcr.io/manimaul/njord-chart-server:latest"
```

Deploy
```shell
kubectl apply -f .chart_server.yaml
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
./k8s_create_reg_sec.sh istio_inject 
```

Deploy postgis service
```shell
cd k8s
istioctl kube-inject -f postgis_volume.yaml | kubectl apply -f -
istioctl kube-inject -f postgis.yaml | kubectl apply -f -
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
