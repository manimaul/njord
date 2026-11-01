# Njord in the `mxmariner` namespace (Rust environment)

The Rust port's deployment: a complete, separate environment at `https://njord.mxmariner.com`,
described in [docs/RUST_PORT_PLAN.md §10](../../docs/RUST_PORT_PLAN.md). The Kotlin environment
(namespace `njord`, `openenc.com`) is deployed from the `master` branch's `k8s_deploy/` and
`./gradlew :deploy`, which were removed here with the rest of the Kotlin code; nothing here
modifies it, and `cargo xtask` refuses `NJORD_NAMESPACE=njord`.

> **Not applied yet.** The environment is stood up once, in Phase 7 (§10.4), with the Rust image
> for every workload.

| File | What |
|---|---|
| `../../Containerfile` | Rust image `ghcr.io/manimaul/njord:<version>` |
| `../../.containerignore` | Build-context whitelist for that image (keeps the Cargo `target/` out) |
| `application.json` | Config baked into the image (database `njord`); the cluster mounts `njord-config` over it |
| `chart_server.yaml` | PVC, HPA, Services, relay DaemonSet, `njord-chart-dep`, `njord-config`, Certificate, Ingress, `njord-ingest-rs` |
| `noaa_enc_daily_cron.yaml` | `njord-enc-download` CronJob (02:00 PT) + `njord-enc-cron-config` |
| `walk_tiles_cronjob.yaml` | `njord-walk-tiles` CronJob (03:00 PT) + script ConfigMap |
| `vacuum_job.yaml` | One-off `VACUUM FULL features`, by hand only |
| `db_setup.sql` | One-time role/database/PostGIS setup |

The manifests hold `${NJORD_NAMESPACE}`, `${NJORD_DB}`, `${NJORD_HOST}` and `${NJORD_IMAGE}`
placeholders, so they are never applied directly: `cargo xtask render <chart|cron|vacuum>` shows
the result and `cargo xtask --execute apply ...` applies it. The tasks live in
[`crates/xtask`](../../crates/xtask/src/main.rs) (the `xtask` alias is in `.cargo/config.toml`) and
replace the Gradle deploy tasks and `create_nfs.sh` for this environment.

## Workloads

Every workload runs the Rust image `ghcr.io/manimaul/njord:$NJORD_VERSION`.

| Workload | Binary |
|---|---|
| `njord-chart-dep` | `/opt/njord/njord-server /opt/njord/resources` |
| `njord-ingest-rs` | `/opt/njord/njord-ingest /opt/njord/resources` (ingest and region export workers, no HTTP) |
| `njord-enc-download` | `/opt/njord/njord-enc-cron /opt/njord/enc_cron_resources` |
| `njord-walk-tiles` | `python:3.12-slim` with the script ConfigMap |

## Environment

```shell
export NJORD_NAMESPACE=mxmariner
export NJORD_ADMIN_KEY=… NJORD_ADMIN_USER=… NJORD_ADMIN_PASS=…
export NJORD_DB_HOST=… NJORD_DB_PORT=… NJORD_DB=njord NJORD_DB_USER=njord NJORD_DB_PASS=…
# optional
export NJORD_KUBE_CONTEXT=<your LKE context>   # --execute refuses any other current context
export NJORD_VERSION=0.1.0                     # default: [workspace.package] version in Cargo.toml
```

`NJORD_DB` replaces the Kotlin environment's `NJORD_DB_NAME`, which is never read. A task
aborts on any unset or empty variable it needs, and on the literal string `undefined`. Admin
signatures are per host (`externalBaseUrl()` is part of the HMAC), so `NJORD_ADMIN_KEY` may be the
same as or different from the `njord` namespace's.

## cargo xtask

Dry run is the default: each task prints the commands (and the rendered manifests, secrets
masked) it would run. Add `--execute` to act.

```shell
cargo xtask help
cargo xtask apply chart              # prints only
cargo xtask --execute apply chart    # applies
```

| Task | Replaces | Does |
|---|---|---|
| `secrets` | `./gradlew :secret` | `njord-db-upstream`, `njord-db-url` and `admin-secret-json` (`pgConnectionInfo` `postgresql://$NJORD_DB_USER:…@njord-db-relay:6432/$NJORD_DB?sslmode=require`; no pgbouncer, the servers pool with sqlx), `njord-admin-basic` |
| `nfs` | `create_nfs.sh` | Helm release `njord-nfs`, `storageClass.name=njord-nfs`, `persistence.size=$NJORD_NFS_SIZE` (≥ 20Gi) |
| `build` | `./gradlew :makeImg` | `podman build -f Containerfile --ignorefile .containerignore --platform linux/amd64`, then checks the image architecture |
| `push` | `./gradlew :pubImg` | `podman push ghcr.io/manimaul/njord:$NJORD_VERSION` (needs `podman login ghcr.io`) |
| `apply [chart\|cron\|vacuum\|all]` | `./gradlew :k8sApply` | render + `kubectl --namespace $NJORD_NAMESPACE apply -f -` |
| `rollout` | `./gradlew :rolloutRestart` | `rollout restart` + `rollout status` of `njord-chart-dep` |
| `cycle-ingest` | `./gradlew :cycleIngestPod` | delete the `njord-ingest-rs` pod |
| `seed` | — | one-off Job from `cronjob/njord-enc-download` |
| `verify` | — | `kubectl get pods,pvc,certificate,ingress`, `curl /v1/about/version` |
| `deploy` | `./gradlew :deploy` | `build`, `push`, `apply chart`, `sleep 5`, `rollout` |
| `render <chart\|cron\|vacuum\|secrets>` | `./gradlew :showSecret` | print to stdout; secrets masked unless `--show-secrets` |

Refusals: `NJORD_NAMESPACE=njord`; a Rust image repository containing `njord-chart-server`;
`NJORD_DB=s57server`; `NJORD_DB_USER=akmadmin`; `NJORD_HOST=*openenc.com`; an NFS size under 20Gi.
Every rendered manifest is also rejected if it contains `namespace: njord`, `storageClassName: nfs`,
`TCP4-LISTEN:6432`, `openenc.com`, `s57server`, an unfilled placeholder, or a `njord-chart-server`
image.

## Bootstrap (§10.4)

Run from the repo root with the environment above exported.

```shell
# 0. Database and role, once, as the cluster admin (§10.2 item 4). Check free disk on the managed
#    plan first.
psql "postgresql://akmadmin@${NJORD_DB_HOST}:${NJORD_DB_PORT}/defaultdb?sslmode=require" \
  -v ON_ERROR_STOP=1 -v njord_pass="$NJORD_DB_PASS" -f deploy/mxmariner/db_setup.sql

# 1. Namespace and ghreg already exist in mxmariner (regatta uses both). Check:
kubectl get namespace "$NJORD_NAMESPACE"
kubectl -n "$NJORD_NAMESPACE" get secret ghreg

# 2. NFS: Helm release njord-nfs, StorageClass njord-nfs
cargo xtask nfs                      # review
cargo xtask --execute nfs
kubectl get storageclass njord-nfs   # provisioner cluster.local/njord-nfs-nfs-server-provisioner

# 3 + 4. Pooler/relay/admin secrets and njord-admin-basic
cargo xtask secrets                  # review (masked)
cargo xtask --execute secrets

# 5. Image, then chart_server.yaml (relay on 6433, PVC on njord-nfs, Ingress for njord.mxmariner.com)
podman login ghcr.io                 # once
cargo xtask deploy                   # review
cargo xtask --execute deploy         # build, push, apply chart, rollout

# 6. Certificate READY, then the version endpoint
kubectl -n "$NJORD_NAMESPACE" wait --for=condition=Ready certificate/njord-cert --timeout=10m
cargo xtask --execute verify

# 7. CronJobs, then one njord-enc-download Job by hand to start seeding
cargo xtask --execute apply cron
cargo xtask --execute seed
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
- Rotating `ghreg`: run `kubectl create secret docker-registry ghreg --namespace mxmariner` (the
  Kotlin `k8s_deploy/k8s_create_reg_sec.sh k8s_login` on `master` is hard-coded to `njord`).
