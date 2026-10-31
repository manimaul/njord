# Njord in the `mxmariner` namespace (Rust environment)

The Rust port's deployment: a complete, separate environment at `https://njord.mxmariner.com`,
described in [docs/RUST_PORT_PLAN.md §10](../../docs/RUST_PORT_PLAN.md). The Kotlin environment
(namespace `njord`, `openenc.com`, `k8s_deploy/`, `./gradlew :deploy`) is not modified by anything
here, and `deploy.sh` refuses `NJORD_NAMESPACE=njord`.

> **Not applied until Phase 7** (decided 2026-10-30). Every phase is first tested locally; the
> environment is stood up once, in Phase 7, with the Rust image for every workload. These files
> were prepared in Phase 3 with ingest and the NOAA cron still on the Kotlin image (the "Phase 3
> layout" below); flip both to Rust, and set `deleteOrphans` back to `true`, before the first apply.

| File | What |
|---|---|
| `../../Containerfile.rust` | Rust image `ghcr.io/manimaul/njord:<version>` |
| `containerignore` | Build-context whitelist for that image (keeps the Cargo `target/` out) |
| `application.json` | Config baked into the image (database `njord`); the cluster mounts `njord-config` over it |
| `chart_server.yaml` | PVC, HPA, Services, relay DaemonSet, `njord-chart-dep`, `njord-config`, Certificate, Ingress, `njord-ingest-rs` |
| `noaa_enc_daily_cron.yaml` | `njord-enc-download` CronJob (02:00 PT) + `njord-enc-cron-config` |
| `walk_tiles_cronjob.yaml` | `njord-walk-tiles` CronJob (03:00 PT) + script ConfigMap |
| `vacuum_job.yaml` | One-off `VACUUM FULL features`, by hand only |
| `db_setup.sql` | One-time role/database/PostGIS setup |
| `deploy.sh` | Replaces the Gradle deploy tasks and `create_nfs.sh` for this environment |

The manifests hold `${NJORD_NAMESPACE}`, `${NJORD_DB}`, `${NJORD_HOST}` and per-workload image
placeholders, so they are never applied directly: `deploy.sh render <chart|cron|vacuum>` shows the
result and `deploy.sh --execute apply ...` applies it.

## Phase 3 layout: who runs which image

| Workload | Image | Binary |
|---|---|---|
| `njord-chart-dep` | **Rust** `ghcr.io/manimaul/njord:$NJORD_VERSION` | `/opt/njord/njord-server` |
| `njord-ingest-rs` | **Kotlin** `$NJORD_KOTLIN_IMAGE` (default `ghcr.io/manimaul/njord-chart-server:<gradle version>`) | `/opt/njord/ingest.kexe` |
| `njord-enc-download` | **Kotlin** `$NJORD_KOTLIN_IMAGE` | `/opt/njord/enc_cron.kexe` |

Kotlin fills the `njord` database and the PVC; Rust reads them (§10.4). The Kotlin image is only
pulled here, never pushed. It is a floating tag with `imagePullPolicy: Always`, so pin it
(`NJORD_KOTLIN_IMAGE=ghcr.io/manimaul/njord-chart-server:<tag>`) if a Kotlin push should not
reach this namespace on its next pod start.

- **Phase 4:** set `NJORD_INGEST_IMPL=rust`, change the `njord-ingest` `command` in
  `chart_server.yaml`, remove the guard line in `deploy.sh resolve_images`, and set
  `deleteOrphans: true` in `noaa_enc_daily_cron.yaml` once the Rust server has `/v1/admin` and
  `DELETE /v1/chart`.
- **Phase 5:** the same for `NJORD_ENC_CRON_IMPL=rust` and the `enc-download` command.

## Environment

```shell
export NJORD_NAMESPACE=mxmariner
export NJORD_ADMIN_KEY=… NJORD_ADMIN_USER=… NJORD_ADMIN_PASS=…
export NJORD_DB_HOST=… NJORD_DB_PORT=… NJORD_DB=njord NJORD_DB_USER=njord NJORD_DB_PASS=…
# optional
export NJORD_KUBE_CONTEXT=<your LKE context>   # --execute refuses any other current context
export NJORD_VERSION=0.1.0                     # default: [workspace.package] version in Cargo.toml
export NJORD_KOTLIN_IMAGE=ghcr.io/manimaul/njord-chart-server:1.4-SNAPSHOT
```

`NJORD_DB` replaces the Kotlin environment's `NJORD_DB_NAME`, which is never read. The script
aborts on any unset or empty variable it needs, and on the literal string `undefined`. Admin
signatures are per host (`externalBaseUrl()` is part of the HMAC), so `NJORD_ADMIN_KEY` may be the
same as or different from the `njord` namespace's.

## deploy.sh

Dry run is the default: each subcommand prints the commands (and the rendered manifests, secrets
masked) it would run. Add `--execute` to act.

```shell
deploy/mxmariner/deploy.sh help
deploy/mxmariner/deploy.sh apply chart              # prints only
deploy/mxmariner/deploy.sh --execute apply chart    # applies
```

| Subcommand | Replaces | Does |
|---|---|---|
| `secrets` | `./gradlew :secret` | `njord-pgbouncer-ini` (alias = `dbname` = `$NJORD_DB`, `host=njord-db-relay`), `njord-pgbouncer-userlist-txt`, `njord-db-upstream`, `admin-secret-json` (`pgConnectionInfo` `…/$NJORD_DB`), `njord-admin-basic` |
| `nfs` | `k8s_deploy/create_nfs.sh` | Helm release `njord-nfs`, `storageClass.name=njord-nfs`, `persistence.size=$NJORD_NFS_SIZE` (≥ 20Gi) |
| `build` | `./gradlew :makeImg` | `podman build -f Containerfile.rust --ignorefile deploy/mxmariner/containerignore --platform linux/amd64` |
| `push` | `./gradlew :pubImg` | `podman push ghcr.io/manimaul/njord:$NJORD_VERSION` (needs `podman login ghcr.io`) |
| `apply [chart\|cron\|vacuum\|all]` | `./gradlew :k8sApply` | render + `kubectl --namespace $NJORD_NAMESPACE apply -f -` |
| `rollout` | `./gradlew :rolloutRestart` | `rollout restart` + `rollout status` of `njord-chart-dep` |
| `cycle-ingest` | `./gradlew :cycleIngestPod` | delete the `njord-ingest-rs` pod |
| `seed` | — | one-off Job from `cronjob/njord-enc-download` |
| `verify` | — | `kubectl get pods,pvc,certificate,ingress`, `curl /v1/about/version` |
| `deploy` | `./gradlew :deploy` | `build`, `push`, `apply chart`, `sleep 5`, `rollout` |
| `render <chart\|cron\|vacuum\|secrets>` | `./gradlew :showSecret` | print to stdout; secrets masked unless `SHOW_SECRETS=1` |

Refusals: `NJORD_NAMESPACE=njord`; a Rust image repository containing `njord-chart-server`;
`NJORD_DB=s57server`; `NJORD_DB_USER=akmadmin`; `NJORD_HOST=*openenc.com`; an NFS size under 20Gi;
a `NJORD_KOTLIN_IMAGE` outside `ghcr.io/manimaul/njord-chart-server`. Every rendered manifest is
also rejected if it contains `namespace: njord`, `storageClassName: nfs`, `TCP4-LISTEN:6432`,
`openenc.com`, `s57server`, an unfilled placeholder, or a `njord-chart-server` image in
`njord-chart-dep`.

## Bootstrap (§10.4)

Run from the repo root with the environment above exported.

```shell
# 0. Database and role, once, as the cluster admin (§10.2 item 4). Check free disk on the managed
#    plan first.
psql "postgresql://akmadmin@${NJORD_DB_HOST}:${NJORD_DB_PORT}/postgres?sslmode=require" \
  -v ON_ERROR_STOP=1 -v njord_pass="$NJORD_DB_PASS" -f deploy/mxmariner/db_setup.sql

# 1. Namespace and ghreg already exist in mxmariner (regatta uses both). Check:
kubectl get namespace "$NJORD_NAMESPACE"
kubectl -n "$NJORD_NAMESPACE" get secret ghreg

# 2. NFS: Helm release njord-nfs, StorageClass njord-nfs
deploy/mxmariner/deploy.sh nfs                       # review
deploy/mxmariner/deploy.sh --execute nfs
kubectl get storageclass njord-nfs                   # provisioner cluster.local/njord-nfs-nfs-server-provisioner

# 3 + 4. Pooler/relay/admin secrets and njord-admin-basic
deploy/mxmariner/deploy.sh secrets                   # review (masked)
deploy/mxmariner/deploy.sh --execute secrets

# 5. Image, then chart_server.yaml (relay on 6433, PVC on njord-nfs, Ingress for njord.mxmariner.com)
./gradlew :web:jsBrowserDistribution                 # Kotlin/JS UI baked into the Rust image
podman login ghcr.io                                 # once
deploy/mxmariner/deploy.sh deploy                    # review
deploy/mxmariner/deploy.sh --execute deploy          # build, push, apply chart, rollout

# 6. Certificate READY, then the version endpoint
kubectl -n "$NJORD_NAMESPACE" wait --for=condition=Ready certificate/njord-cert --timeout=10m
deploy/mxmariner/deploy.sh --execute verify

# 7. CronJobs, then one njord-enc-download Job by hand to start seeding
deploy/mxmariner/deploy.sh --execute apply cron
deploy/mxmariner/deploy.sh --execute seed
```

## Verify

```shell
kubectl -n mxmariner get pods,pvc,certificate,ingress
curl https://njord.mxmariner.com/v1/about/version

kubectl -n mxmariner get pvc njord-shared-data          # Bound, StorageClass njord-nfs
kubectl -n mxmariner get ds njord-db-relay              # 3/3 ready (one per node, host port 6433)
kubectl -n mxmariner logs deploy/njord-chart-dep -c njord-chart-svc
kubectl -n mxmariner logs -l app=njord-ingest -c njord-ingest -f
kubectl -n njord get pods                               # old environment: unchanged
```

Before step 6, `dig +short njord.mxmariner.com` should return the ingress load balancer
(`172.232.176.134`, §10.1), or the HTTP-01 challenge cannot succeed.

Seeding: raise `maxCellsPerRun` in `noaa_enc_daily_cron.yaml` (the full catalog is ~7.2k cells),
`apply cron`, then `seed` again. The ingest pod picks the bundles up from `/mnt/njord/charts/save`.

Compare with the old environment (§10.5):

```shell
pytest conformance --base-url=https://openenc.com --compare-url=https://njord.mxmariner.com
```

## Notes

- Regatta's `pgbouncer.ini` still points at `njord-db-relay.njord.svc.cluster.local`. Moving it
  onto this namespace's relay is part of the cutover (§10.6), not of this bootstrap.
- PostgreSQL grants `CONNECT` to `PUBLIC` on every database, so the `njord` role can connect to
  `s57server` (it owns nothing there). Revoking that changes the old database, so it is left for
  you to decide; `db_setup.sql` does not do it.
- Rotating `ghreg`: `k8s_deploy/k8s_create_reg_sec.sh k8s_login` is hard-coded to `njord`;
  for this namespace run the same `kubectl create secret docker-registry ghreg` with
  `--namespace mxmariner`.
