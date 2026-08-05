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

Runs `enc_cron.kexe` out of the chart server image. It diffs NOAA's product catalog against
`GET /v1/chart_editions` and downloads only the cells whose revision changed, publishing them as
bundle zips into `/mnt/njord/charts/save` for the ingest ReplicaSet to pick up.

Tune it in the `njord-enc-cron-config` ConfigMap at the bottom of the manifest, which is mounted
over `enc_cron_resources/config/enc_cron.json` in the image. It holds the whole file rather than a
patch, and edits take effect on the next scheduled run - a CronJob builds a fresh pod each time, so
there is nothing to restart. To seed a cold catalog faster, raise `maxCellsPerRun` from its default
of 500 - the full catalog is ~7.2k cells / ~818 MB.

Deleting cells NOAA has withdrawn needs admin credentials, supplied separately so no password ends
up in the ConfigMap:

```shell
kubectl -n njord create secret generic njord-admin-basic \
  --from-literal=admin_user="${NJORD_ADMIN_USER}" --from-literal=admin_pass="${NJORD_ADMIN_PASS}"
```

Without it the job still reports withdrawn cells and deletes nothing.

```shell
kubectl apply -f ./noaa_enc_daily_cron.yaml
kubectl apply -f ./walk_tiles_cronjob.yaml
```

Preview what a run would fetch without downloading anything:
```shell
kubectl -n njord run enc-cron-dryrun --rm -it --restart=Never \
  --image=ghcr.io/manimaul/njord-chart-server:latest \
  --overrides='{"spec":{"imagePullSecrets":[{"name":"ghreg"}]}}' \
  --command -- /opt/njord/enc_cron.kexe /opt/njord/enc_cron_resources --dry-run
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
