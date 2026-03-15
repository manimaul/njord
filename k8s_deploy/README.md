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
kubectl -n njord delete job noaadaily
kubectl -n njord create job noaadaily --from=cronjob/njord-enc-download
kubectl logs -n njord -l job-name=noaadaily -f
```

```shell
kubectl create job --from=cronjob/njord-enc-download njord-enc-download-test -n njord
```

Run Immediate one-off Walk Tiles
```shell
kubectl -n njord delete job walk
kubectl -n njord create job walk --from=cronjob/njord-walk-tiles
```
