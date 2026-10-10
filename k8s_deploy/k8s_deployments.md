# Kubernetes Deployments

Everything lives in the `njord` namespace. Run commands from the repo root unless noted otherwise.

| Workload | Kind | Manifest | Applied by |
|---|---|---|---|
| `njord-chart-dep` | Deployment (+ HPA, Service, Ingress, Certificate) | `chart_server.yaml` | `./gradlew :k8sApply` |
| `njord-ingest-rs` | bare ReplicaSet | `chart_server.yaml` | `./gradlew :k8sApply` |
| `njord-db-relay` | DaemonSet | `chart_server.yaml` | `./gradlew :k8sApply` |
| `njord-enc-download` | CronJob (+ `njord-enc-cron-config` ConfigMap) | `noaa_enc_daily_cron.yaml` | `kubectl apply` |
| `njord-walk-tiles` | CronJob (+ `walk-tiles-script` ConfigMap) | `walk_tiles_cronjob.yaml` | `kubectl apply` |
| `features-vacuum-full` | one-off Job | `vacuum_job.yaml` | `kubectl apply`, by hand only |

`k8sApply` applies **only** `chart_server.yaml`. It replaces `njord-chart-server:latest` with
`:<version>` from `gradle.properties` and writes the result to `k8s_deploy/.chart_server.yaml`.
The CronJob manifests are never applied by Gradle.

Most images are pulled with `imagePullPolicy: Always` on a floating tag (`1.4-SNAPSHOT`, `latest`).
So a pushed image only reaches a workload when that workload starts a new pod.

## Environment

These are used by `makeImg`/`pubImg` (registry), `secret` (cluster secrets) and
`k8s_create_reg_sec.sh`:

```shell
export GH_USER=... GH_TOKEN=... GH_EMAIL=...                       # ghcr.io push/pull
export NJORD_ADMIN_KEY=... NJORD_ADMIN_USER=... NJORD_ADMIN_PASS=...
export NJORD_DB_HOST=... NJORD_DB_PORT=... NJORD_DB_NAME=... NJORD_DB_USER=... NJORD_DB_PASS=...
```

`./gradlew :showSecret` prints the values Gradle will use. A variable that isn't set becomes the
literal string `undefined`, which won't stop the build, so check the output before running `secret`.

---

## Initial deployment

Cluster prerequisites, which aren't managed by this repo:
- an `haproxy` ingress class
- cert-manager with a `letsencrypt-prod` ClusterIssuer
- the `linode-block-storage-retain` StorageClass (LKE)
- `helm`
- DNS for `openenc.com` / `www.openenc.com` pointing at the ingress

```shell
# 1. Namespace
kubectl create namespace njord

# 2. Image pull secret (ghreg). The script deletes the secret first, so on a fresh cluster
#    expect a harmless "not found".
./k8s_deploy/k8s_create_reg_sec.sh k8s_login

# 3. NFS provisioner backing the RWX njord-shared-data PVC
./k8s_deploy/create_nfs.sh

# 4. Secrets: njord-pgbouncer-ini, njord-pgbouncer-userlist-txt, admin-secret-json, and the
#    njord-db-upstream ConfigMap. Must exist before chart_server.yaml is applied, or the relay
#    and server pods hang on missing mounts.
./gradlew :secret

# 5. Admin credentials for the NOAA cron's orphan deletes. Optional: without it the cron
#    reports orphans but deletes nothing.
./k8s_deploy/k8s_create_reg_sec.sh create_cron_secret

# 6. Build, push, apply chart_server.yaml, restart the chart Deployment
./gradlew :deploy

# 7. CronJobs
kubectl apply -f k8s_deploy/noaa_enc_daily_cron.yaml -f k8s_deploy/walk_tiles_cronjob.yaml
```

Check it:

```shell
kubectl -n njord get pods,cronjobs,certificate,ingress
kubectl -n njord logs deploy/njord-chart-dep -c njord-chart-svc
```

To seed charts without waiting for midnight, see [Run a CronJob now](#run-a-cronjob-now).

---

## Updates

### Application code (new image)

```shell
./gradlew :deploy           # makeImg → pubImg → k8sApply → rolloutRestart (chart Deployment)
./gradlew :cycleIngestPod   # the ingest ReplicaSet isn't touched by deploy
```

`njord-ingest-rs` is a bare ReplicaSet. It has no rollout, so neither `k8sApply` nor
`rolloutRestart` replaces its pod. Check that it picked up the new image:

```shell
kubectl -n njord get pods -o custom-columns='NAME:.metadata.name,START:.status.startTime,IMAGE:.status.containerStatuses[0].imageID'
```

CronJobs pick up the new image on their next scheduled run. Nothing needs restarting.

### `chart_server.yaml` changes (no new image)

```shell
./gradlew :k8sApply
```

What happens next depends on what changed:

| Change | Follow-up |
|---|---|
| Deployment pod template | none, it rolls automatically |
| `njord-config` ConfigMap | `./gradlew :rolloutRestart :cycleIngestPod`. It's mounted with `subPath`, so running pods never see the change. |
| ReplicaSet pod template | `./gradlew :cycleIngestPod` |
| DaemonSet pod template | none, it rolls automatically |
| Service / Ingress / HPA / Certificate | none |

### CronJob changes

```shell
kubectl apply -f k8s_deploy/noaa_enc_daily_cron.yaml -f k8s_deploy/walk_tiles_cronjob.yaml
```

Applies to the next run. That includes `njord-enc-cron-config` and `walk-tiles-script`, because
each run starts a fresh pod. A Job that's already running keeps the old spec.

### Secrets or database endpoint

```shell
./gradlew :showSecret      # check the values first
./gradlew :secret
kubectl -n njord rollout restart daemonset/njord-db-relay   # only if NJORD_DB_HOST/PORT changed
./gradlew :rolloutRestart :cycleIngestPod
```

Changing the Secrets or the `njord-db-upstream` ConfigMap doesn't restart any of their consumers.

Cron admin credentials (`njord-admin-basic`):

```shell
kubectl -n njord delete secret njord-admin-basic
./k8s_deploy/k8s_create_reg_sec.sh create_cron_secret
```

### Registry token rotation

```shell
./k8s_deploy/k8s_create_reg_sec.sh k8s_login
```

Running pods aren't affected. The new token is used on the next image pull.

### Version bump

After changing `version` in `gradle.properties`:

1. Update the hardcoded image tag in `k8s_deploy/noaa_enc_daily_cron.yaml`. `k8sApply` only
   rewrites the tag in `chart_server.yaml`.
2. Run the code update:
   ```shell
   ./gradlew :deploy :cycleIngestPod
   ```
3. Apply the CronJobs:
   ```shell
   kubectl apply -f k8s_deploy/noaa_enc_daily_cron.yaml
   ```

---

## One-off operations

### Run a CronJob now

```shell
kubectl -n njord create job noaadaily-$(date +%s) --from=cronjob/njord-enc-download
kubectl -n njord create job walk-$(date +%s) --from=cronjob/njord-walk-tiles
kubectl -n njord logs -f job/<name>
```

Jobs created from a CronJob inherit `ttlSecondsAfterFinished: 86400` and delete themselves a day
after they finish.

### Regenerate region archives

See [regenerate_region_archives.md](regenerate_region_archives.md).

### VACUUM FULL on `features`

This causes a chart server outage. Read the header of `vacuum_job.yaml` first.

```shell
kubectl apply -f k8s_deploy/vacuum_job.yaml
kubectl -n njord logs -f job/features-vacuum-full
```

The Job deletes itself 24h after it finishes. To re-run it sooner, delete it first:

```shell
kubectl -n njord delete job features-vacuum-full
```
