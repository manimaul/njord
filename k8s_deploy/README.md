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

## Noaa Daily Update Cron

```shell
kubectl apply -f ./noaa_enc_daily_cron.yaml
kubectl apply -f ./walk_tiles_cronjob.yaml
```

Run Immediate one-off NOAA Daily
```shell
kubectl create job daily --from=cronjob/njord-enc-download njord-enc-download-manual -n njord
```

Run Immediate one-off Walk Tiles
```shell
kubectl create job walk --from=cronjob/njord-walk-tiles -n njord
```
