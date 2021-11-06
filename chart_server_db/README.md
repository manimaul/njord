# Local Dev
url = `jdbc:postgresql://localhost:5432/s57server`
username = `admin`
password = `mysecretpassword`


# K8s

Publish init container image
```shell
cd postres_init
docker build -t "ghcr.io/manimaul/njord-init-postgis:latest" .
docker push "ghcr.io/manimaul/njord-init-postgis:latest"
```

Add Github container registry secrets to njord namespace
```shell
cd k8s
./k8s_create_reg_sec.sh
```

Deploy postgis service
```shell
cd k8s
kubectl apply -f volume.yaml
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