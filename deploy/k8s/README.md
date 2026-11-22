# Deploying Njord

Njord runs as two environments on one Kubernetes cluster, deployed with `cargo xtask`
([`crates/xtask`](../../crates/xtask/src/main.rs); the `xtask` alias is in `.cargo/config.toml`).

| | Staging | Production |
|---|---|---|
| `--env` | `staging` (**the default**) | `production` |
| Namespace | `mxstaging` | `mxmariner` |
| Host | `staging-njord.mxmariner.com` | `njord.mxmariner.com` (plus the `openenc.com` mirror) |
| Database and role | `njord_staging` | `njord` |
| Relay host port | 6434 | 6433 |
| NFS Helm release and StorageClass | `njord-staging-nfs` | `njord-nfs` |
| Secret variables | `NJORD_STAGING_*` | `NJORD_PRODUCTION_*` |
| `njord-chart-dep` memory (request / limit) | 128Mi / 512Mi | 512Mi / 1536Mi |
| `njord-chart-dep` pods (autoscaler) | 1 | 1 to 5 |
| Ingest memory (request = limit) | 512Mi | 1Gi |
| `chartIngestWorkers` | 1 | 3 |
| `njord-enc-download` / `njord-walk-tiles` (Pacific time) | 04:00 / 05:00 | 02:00 / 03:00 |
| PriorityClass | `njord-staging` (negative, never preempts) | the default, 0 |
| ResourceQuota | memory: 1536Mi of requests, 3Gi of limits | none |

Both environments share the cluster's three nodes and one managed PostgreSQL server. The sizes
make staging lose every contest with production for memory, and the schedules keep the two
environments' nightly jobs apart on the database server. Each value is a field of `Environment`
in [`crates/xtask/src/main.rs`](../../crates/xtask/src/main.rs).

> **Staging is not stood up yet.** [docs/STAGING_ENVIRONMENT_PLAN.md](../../docs/STAGING_ENVIRONMENT_PLAN.md)
> is the plan and tracks what is done. Until its step 3, only production exists.

An image is built once, runs in staging, and that same image is promoted to production:
`deploy` builds and releases to staging, `promote` gives production the image staging runs, and no
other task changes which image an environment runs.

## Files

| File | What |
|---|---|
| `../../Containerfile` | The image `ghcr.io/manimaul/njord:<version>-<git hash>` |
| `../../.containerignore` | Build-context whitelist for that image (keeps the Cargo `target/` out) |
| `application.json` | Config baked into the image; the cluster mounts `njord-config` over it |
| `chart_server.yaml` | PVC, HPA, Services, relay DaemonSet, `njord-chart-dep`, `njord-config`, Certificate, Ingress, `njord-ingest-rs` |
| `noaa_enc_daily_cron.yaml` | `njord-enc-download` CronJob + `njord-enc-cron-config` |
| `walk_tiles_cronjob.yaml` | `njord-walk-tiles` CronJob + script ConfigMap |
| `staging.yaml` | Staging only: the `njord-staging` PriorityClass and the namespace's ResourceQuota |
| `production/openenc_mirror.yaml` | Production only: the `openenc.com` Certificate and Ingress. Applied by hand, not by the xtask |
| `db_setup.sql` | One-time role, database and PostGIS setup of an environment, run with `psql` |

The manifests hold a `${NJORD_...}` placeholder for everything in the table above and for the
image, so they are never applied directly. `cargo xtask render <chart|cron|quota>` shows the
result for an environment and `cargo xtask --execute apply ...` applies it. A value written in a
manifest as a literal is the same in both environments.

Every rendered manifest is checked before it is printed or applied: it is refused if it names
another environment's namespace, host, database, role, relay port, StorageClass, PriorityClass
or cron schedule, or still holds an unfilled placeholder. A value written into a manifest as a
literal where a placeholder belongs is caught there.

## Workloads

Every workload of an environment runs the same image.

| Workload | Binary |
|---|---|
| `njord-chart-dep` | `/opt/njord/njord-server /opt/njord/resources` |
| `njord-ingest-rs` | `/opt/njord/njord-ingest /opt/njord/resources` (ingest and region export workers, no HTTP) |
| `njord-enc-download` | `/opt/njord/njord-enc-cron /opt/njord/enc_cron_resources` |
| `njord-walk-tiles` | `python:3.12-slim` with the script ConfigMap |

## Variables

The environment comes from `--env` alone. No variable selects it, so a value left in a shell
cannot redirect a deploy.

Only the `secrets` task reads secrets, from the chosen environment's variables. There is no
fallback to an unprefixed name, so one environment's password is never written into the other:

```shell
export NJORD_STAGING_DB_HOST=… NJORD_STAGING_DB_PORT=… NJORD_STAGING_DB_PASS=…
export NJORD_STAGING_ADMIN_KEY=… NJORD_STAGING_ADMIN_USER=… NJORD_STAGING_ADMIN_PASS=…
# and the same six as NJORD_PRODUCTION_*

# optional
export NJORD_KUBE_CONTEXT=<your LKE context>   # kubectl must be on this context
export NJORD_IMAGE_REPO=ghcr.io/manimaul/njord
export NJORD_PLATFORM=linux/amd64
```

A task aborts on an unset or empty variable it needs, and on the literal string `undefined`. The
database name and role are the environment's and are not variables. Admin signatures are per host
(the request's base URL is part of the HMAC), so each environment has its own admin key.

`NJORD_NAMESPACE`, `NJORD_DB`, `NJORD_HOST`, `NJORD_VERSION`, `NJORD_NFS_SIZE` and the unprefixed
`NJORD_DB_*` and `NJORD_ADMIN_*` secrets are no longer read. The xtask says so when one of the
first five is set. The conformance suite still reads `NJORD_ADMIN_USER`, `NJORD_ADMIN_PASS` and
`NJORD_ADMIN_KEY` for a non-local server.

## cargo xtask

Dry run is the default: each task prints the commands (and the rendered manifests, secrets
masked) it would run. Add `--execute` to act. A dry run changes nothing, but it may read the
cluster to learn which image an environment runs.

```shell
cargo xtask help
cargo xtask deploy                               # staging, prints only
cargo xtask --execute deploy                     # staging
cargo xtask --env production promote             # production, prints only
cargo xtask --env production --execute promote   # production
```

| Task | Does |
|---|---|
| `secrets` | `njord-db-upstream`, `admin-secret-json` (`pgConnectionInfo` through `njord-db-relay:6432`; no pgbouncer, the servers pool with sqlx) and `njord-admin-basic` |
| `nfs` | The environment's NFS provisioner: Helm release, StorageClass and a 20Gi disk, with a memory request and limit (192Mi / 384Mi) and the environment's PriorityClass |
| `build` | `podman build` of `<repo>:<version>-<git hash>` for `linux/amd64`, then checks the image architecture and prints the image name |
| `push [--tag <tag>]` | `podman push` (needs `podman login ghcr.io`) |
| `deploy` | **Staging only.** `build`, `push`, then a release (below) of the new image |
| `promote [--tag <tag>]` | **Production only.** A release of the image staging runs, after checking that staging serves that build. `--tag` names another image already in the registry: a rollback |
| `apply [chart\|cron\|quota\|all]` | Render and `kubectl apply`, **with the image the environment already runs**. `quota` is `staging.yaml`; it names no image, so it works before anything is deployed, and it does nothing for production |
| `rollout` | `rollout restart` + `rollout status` of `njord-chart-dep` |
| `cycle-ingest` | `rollout restart` + `rollout status` of `njord-ingest-rs` |
| `seed` | A one-off Job from `cronjob/njord-enc-download` |
| `verify` | `kubectl get pods,pvc,certificate,ingress`, and check that `/v1/about/version` serves the git hash in the Deployment's image tag |
| `render <chart\|cron\|quota\|secrets> [--tag <tag>]` | Print to stdout; secrets masked unless `--show-secrets` |

A **release** applies `staging.yaml` (staging only) and `chart_server.yaml` with the image,
applies the CronJobs too if they are already installed, waits for the rollouts of
`njord-chart-dep` and `njord-ingest-rs`, and then waits until `/v1/about/version` reports the
image's git hash. The CronJobs are left out until `apply cron` has installed them, so the first
NOAA download starts when you decide.

A new image replaces the pods by itself. When the environment already runs the image, the release
restarts both Deployments instead, so the pods always start from what was just applied.
`njord-config` and the secrets are read at startup: after `apply chart` or `secrets` alone, run
`rollout` and `cycle-ingest` to make a change take effect.

A rollout waits for pods that work. `njord-chart-dep` has a readiness probe on
`/v1/about/version`, which the server answers once it has migrated the schema and is listening,
so a build that does not start leaves the old pod serving and the release fails after ten
minutes. `njord-ingest-rs` is a `Recreate` Deployment: its old pod is gone before the new one is
scheduled, so the ingest's memory is never reserved twice.

### Image tags

`build` tags an image `<workspace version>-<short git hash>`, for example `2.0.0-44dfc867`. A
working tree with uncommitted changes adds `-dirty-<UTC timestamp>`. A tag therefore names one
build and is never pushed again with different contents, which is what makes it safe for two
environments to pull from one repository, and why the pull policy is `IfNotPresent`. `promote`
refuses a `-dirty-` image.

Production still runs the bare tag `2.0.0`, pushed before tags carried a hash. Nothing pushes
that tag any more, and the first `promote` replaces it.

## Bootstrap of an environment

The order for a new environment. The plan's step 3 has staging's details; production was
bootstrapped this way on 2026-10-31.

```shell
ENV=staging NS=mxstaging           # or production / mxmariner

# 1. Namespace and the registry pull secret. GH_TOKEN is a GitHub token with read:packages
kubectl create namespace $NS
kubectl -n $NS create secret docker-registry ghreg --docker-server=ghcr.io \
  --docker-username=manimaul --docker-password="$GH_TOKEN"

# 2. Database and role, once, as the managed cluster's admin. Staging's:
psql "postgresql://akmadmin@$NJORD_STAGING_DB_HOST:$NJORD_STAGING_DB_PORT/defaultdb?sslmode=require" \
  -v db=njord_staging -v role=njord_staging -v pass="$NJORD_STAGING_DB_PASS" \
  -v connection_limit=25 -f deploy/k8s/db_setup.sql

# 3. Staging only: its PriorityClass and ResourceQuota, before its first pod
cargo xtask --execute apply quota

# 4. NFS provisioner and StorageClass
cargo xtask --env $ENV nfs                     # review
cargo xtask --env $ENV --execute nfs

# 5. Relay endpoint and secrets
cargo xtask --env $ENV secrets                 # review (masked)
cargo xtask --env $ENV --execute secrets

# 6. The first image: deploy for staging, promote for production
podman login ghcr.io                           # once
cargo xtask --execute deploy

# 7. Certificate, then the version endpoint
kubectl -n $NS wait --for=condition=Ready certificate/njord-cert --timeout=10m
cargo xtask --env $ENV --execute verify

# 8. CronJobs, then one njord-enc-download Job by hand to start seeding
cargo xtask --env $ENV --execute apply cron
cargo xtask --env $ENV --execute seed
```

`db_setup.sql` creates the role and the database it owns, revokes `CONNECT` from `PUBLIC` so
that no other role on the managed server can open the database, and loads PostGIS. Its header
lists the variables.

`*.mxmariner.com` is a wildcard record pointing at the ingress load balancer
(`172.232.176.134`), so a new host needs no DNS change before its certificate can be issued.

Seeding: `noaa_enc_daily_cron.yaml` sets `maxCellsPerRun` to 10000, above the full catalog (~7.2k
cells), so one `seed` fetches everything. The ingest pod picks the bundles up from
`/mnt/njord/charts/save`. The job stops early if `maxQueuedBundles` (50) bundles are waiting there,
which means ingest has fallen behind; run `seed` again once `save/` drains, or let the nightly job
fetch the rest.

## Verify

```shell
cargo xtask --env production --execute verify

kubectl -n mxmariner get pvc njord-shared-data          # Bound, StorageClass njord-nfs
kubectl -n mxmariner get ds njord-db-relay              # one per node, host port 6433
kubectl -n mxmariner logs deploy/njord-chart-dep -c njord-chart-svc
kubectl -n mxmariner logs -l app=njord-ingest -c njord-ingest -f

# the repository against what the cluster runs: no output means no difference
cargo xtask --env production render chart | kubectl -n mxmariner diff -f -
```

Compare staging with production before promoting (read-only):

```shell
cd conformance
.venv/bin/pytest . --base-url=https://staging-njord.mxmariner.com --compare-url=https://njord.mxmariner.com
```

## Notes

- **Production until its first `promote`** still runs what was applied before these manifests
  changed: no readiness probe, `imagePullPolicy: Always`, and `njord-ingest-rs` as a bare
  ReplicaSet. The first `promote` (or `apply chart`) deletes that ReplicaSet before it applies
  the Deployment of the same name, because a Deployment does not adopt it and two ingest pods
  would run. Until then `--env production cycle-ingest` finds no Deployment; delete the pod
  instead (`kubectl -n mxmariner delete pods -l app=njord-ingest`).
- PostgreSQL grants `CONNECT` to `PUBLIC` on every database. `db_setup.sql` revokes it for a new
  database; production's `njord` was created before it did, so every role on the managed server
  can still connect to it. As the `njord` role: `REVOKE CONNECT ON DATABASE njord FROM PUBLIC;`
- An ingest pod that is killed mid-ingest (`OOMKilled`, a lost node) leaves the ingest lock held.
  `njord-ingest` frees it when it starts again, drops the upload it was working on and reports
  that on the ingest status; upload it again. That is safe only because one ingest pod runs per
  environment, so never raise `njord-ingest-rs` above one replica or turn `enableIngestion` on
  in `njord-config`.
- `ne_10m_ocean.shp` cannot be ingested as Natural Earth ships it: repairing its one invalid
  polygon takes about 1.1 GiB, above the ingest limit of both environments, while a valid
  polygon ingests in about 130 MiB. `data/download_natural_earth.py` repairs it after the
  download with `ogr2ogr -makevalid` (GDAL must be on the PATH), and
  `data/upload_natural_earth.py` runs that script first. A zip downloaded before the script did
  this is repaired the next time it runs; a repaired zip holds `ne_10m_ocean.REPAIRED.txt`.
- A pod in `mxstaging` that leaves out its memory request or limit is refused by the quota. That
  includes pods the manifests do not create, such as a `kubectl run` or `kubectl debug` pod.
- Production's NFS provisioner was installed before `nfs` set memory values, and has none.
- There is no `VACUUM FULL` job. Autovacuum keeps up with the nightly NOAA updates (about 0.4% of
  `features` a day). After a migration that rewrites the whole table, reclaim the space with
  `pg_repack`, which the managed server offers and which does not lock the table, or with a
  `VACUUM FULL features` from `psql` in a quiet window.
- The `njord-db-url` Secret in `mxmariner` belonged to the removed vacuum Job and is unused:
  `kubectl -n mxmariner delete secret njord-db-url`.
- Rotating `ghreg`, once per namespace: the bootstrap command with
  `--dry-run=client -o yaml | kubectl apply -f -` appended, since `create` refuses a Secret that
  exists.
