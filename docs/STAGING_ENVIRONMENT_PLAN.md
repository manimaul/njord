# Staging environment plan

Status: **in progress**. Steps 0, 1 and 2 are done (2026-11-22); nothing has been applied to the
cluster yet, and staging does not exist.

## Goal

A second, complete Njord environment that every change reaches before production:

| | Production | Staging |
|---|---|---|
| Kubernetes namespace | `mxmariner` | `mxstaging` |
| Host | `njord.mxmariner.com` (plus the `openenc.com` mirror) | `staging-njord.mxmariner.com` |
| PostgreSQL database / role | `njord` / `njord` | `njord_staging` / `njord_staging` |
| `cargo xtask` target | only with `--env production` | **the default** |

The rule the plan is built around: **an image is built once, runs in staging, and that same image
is promoted to production.** Production never gets a build that staging has not run.

## Where things stand

The starting point: the repository and the cluster (context `lke275679-ctx`) as read on
2026-11-22, before step 1. The steps below record what has changed since.

- `cargo xtask` knows one environment. The namespace and database come from `NJORD_NAMESPACE` and
  `NJORD_DB`, the host defaults to `njord.mxmariner.com`, and the secrets come from unprefixed
  variables (`NJORD_DB_PASS`, `NJORD_ADMIN_KEY`, ...).
- Production runs `ghcr.io/manimaul/njord:2.0.0` with `imagePullPolicy: Always`, and `cargo xtask
  build` tags every build with the workspace version. **A staging deploy with today's xtask would
  push over `:2.0.0`, and production would start running it at its next pod start.** This is the
  hazard the `Containerfile` describes for the old Kotlin image, now between our own two
  environments. Fixing it is step 1, before anything is deployed to staging.
- Three things in `deploy/mxmariner/` (now `deploy/k8s/`) are per cluster or per node, not per namespace, so staging
  cannot reuse them:
  - the `njord-nfs` StorageClass (cluster-scoped) and its Helm release,
  - the relay DaemonSet's host port 6433 (`hostNetwork`, one listener per node),
  - `openenc_mirror.yaml`, which hard-codes `namespace: mxmariner` and is production-only.
- `*.mxmariner.com` is a wildcard record: `staging-njord.mxmariner.com` already resolves to the
  ingress load balancer (`172.232.176.134`). No DNS work is needed, and cert-manager's HTTP-01
  challenge will pass as soon as the Ingress exists.
- `deploy/mxmariner/README.md` was stale: it said the environment was not applied yet, described
  the retired `njord` namespace, and listed refusals (`NJORD_NAMESPACE=njord`, rejected strings in
  rendered manifests) that commit `88ddacee` removed from the xtask. Step 1 rewrote it.
- Node memory is the tight resource, and the scheduler's view of it is wrong; see
  [Nodes](#nodes) below.

## Measurements (step 0)

Taken on 2026-11-22, read-only, from the cluster and from the `njord` database as the `njord`
role.

### Database

The managed cluster runs **PostgreSQL 17.11 with PostGIS 3.6.4**.

| Database | Owner | Size | `CONNECT` for `PUBLIC` |
|---|---|---|---|
| `njord` | `njord` | 9298 MB | yes (default ACL) |
| `defaultdb` | `akmadmin` | 25 MB | yes |
| `s57server` | `akmadmin` | 25 MB (empty, left from the Kotlin environment) | yes |
| `mxmariner` | `mxmariner` | 7 MB | yes |
| `tartan37` | `akmadmin` | 7 MB | yes |
| `regatta` | `regatta_admin` | not readable | **revoked** |

- `njord` is almost entirely the `features` table: 9183 MB (5361 MB of heap, the rest indexes)
  for 7.45 million rows from 7266 charts.
- **What staging needs:** about 9.3 GB once seeded, and about 18.5 GB while a migration rewrites
  `features` (the old and new copies exist together). Production needs the same transient 9 GB
  for the same migration.
- **The plan's usable disk is 58 GB** (read from Cloud Manager; SQL cannot see it, and the
  `LINODE_TOKEN` in the shell is rejected by the Linode API). That is enough for a full catalog in
  both databases:

  | State | Disk used | Of 58 GB |
  |---|---|---|
  | Today | about 9.5 GB | 16% |
  | Both databases seeded | about 19 GB | 33% |
  | Staging rewriting `features`, production at rest (the release flow's order) | about 28 GB | 48% |
  | Both holding a rewritten, unvacuumed `features` | about 37 GB | 64% |

  WAL adds up to 2.9 GB (`max_wal_size`) to each row.
- **Autovacuum is enough for the nightly updates.** In the eleven days since the server last
  restarted, `features` had 355k rows deleted and 267k inserted, about 0.4% of the table a day.
  Autovacuum starts at 20% dead rows and the space is then reused, so the table stops growing
  without a `VACUUM FULL`. The Job that ran one, `vacuum_job.yaml`, was removed on 2026-11-22.
- **Memory is shared too.** One `features` table is already four times the instance's cache. A
  cold seed or a tile walk on staging pushes production's pages out of that cache, so production's
  uncached tile queries slow down while it runs. Production's tile cache hides most of this.
- Connections: `max_connections` is 100 (3 reserved) and 7 are open. The 25 planned for
  `njord_staging` fit.
- `PUBLIC` holds `CREATE` on the `public` schema of `njord`, so today any role on the cluster can
  connect to production's database and create tables in it. `regatta` has already revoked
  `CONNECT` from `PUBLIC`; D1 does the same for `njord` and `njord_staging`.

### Shared volume

`/mnt/njord` in production: 20G disk, **2.6G used (13%)**. The region archives are 2.5G, the tile
cache 57M, and `save/` is empty.

Staging gets the same 20Gi disk and 18Gi claim as production. A 10Gi disk would hold this, but it
saves about a dollar a month, the Kotlin environment's 10Gi disk ran full, and a cold seed queues
up to 50 bundles in `save/` on top of the archives.

### Nodes

Three nodes, 3821Mi allocatable each.

| Node | Requested | In use | Free by requests | Really free |
|---|---|---|---|---|
| `5spwr` | 1848Mi | 1538Mi (40%) | 1973Mi | 2283Mi |
| `s9zzd` | 1336Mi | 2348Mi (61%) | 2485Mi | 1473Mi |
| `stgp6` | 1098Mi | 2931Mi (76%) | 2723Mi | 890Mi |

The scheduler places pods by requests, so it sees `stgp6` as the emptiest node while it is the
fullest. A staging ingest pod (512Mi) would most likely land there and leave it under 400Mi. This
is the setup of the 2026-11-04 outage: an ingest pod growing into memory the node did not have.

The gap comes from a few pods that request far less than they use:

| Pod | Node | In use | Requested |
|---|---|---|---|
| `monitoring/prometheus-kps-prometheus-0` | `s9zzd` | 734Mi | 400Mi |
| `monitoring/kps-grafana` | `stgp6` | 632Mi | 128Mi |
| `emharbor/tsdbmysql` | `stgp6` | 529Mi | none |
| `mxmariner/mxmariner-web-dep` | `stgp6` | 272Mi | none |
| `kube-system/calico-node` (one per node, managed by LKE) | all | about 195Mi | none |
| `mxmariner/njord-nfs-nfs-server-provisioner-0` | `s9zzd` | 145Mi | none |

Production's Njord pods err the other way: the ingest uses 278Mi of its 1Gi request and
`njord-chart-dep` 91Mi of 512Mi.

**Staging fits on three nodes.** It will use about 0.5Gi idle and 0.75Gi while ingesting, against
4.6Gi really free across the cluster. It does not fit if a node is lost: the two remaining nodes
have about 0.8Gi to spare for what the third was running.

## Decisions

All four were decided on 2026-11-22.

### D1. Where the staging database lives

Staging gets its own database, `njord_staging`, owned by its own role, `njord_staging`, on the
managed PostgreSQL cluster production uses. The data and the credentials are separate; the CPU,
disk, I/O and connection limit are shared. Staging runs the same PostgreSQL and PostGIS versions
as production, so a migration rehearsal is faithful.

Three guards limit what staging can do to production's database:

- `REVOKE CONNECT ON DATABASE njord_staging FROM PUBLIC` and the same on `njord`, so neither role
  can open the other's database. PostgreSQL grants `CONNECT` to `PUBLIC` by default; the second
  revoke changes production's database and is one statement.
- `ALTER ROLE njord_staging CONNECTION LIMIT 25`: each process pools at most
  `MAX_DB_CONNECTIONS` = 10, so two processes plus a `psql` session fit and nothing more.
- Staging's own nightly jobs run at 04:00 and 05:00 PT, after production's at 02:00 and 03:00.

### D2. What data staging holds

Staging is its own environment with its own data. Nothing is copied from production, and nothing
in staging reads production's database or shared volume.

Staging's `njord-enc-download` job seeds it from NOAA's catalog and keeps it current, with the
same `njord-enc-cron-config` as production, and staging's ingest builds its own tile cache and
region archives. What follows from that:

- **The first seed is a cold seed** of the full catalog (about 7.2k cells) through an ingest
  capped at one worker and 512Mi. This is the load that took a node down on 2026-11-04, now under
  a limit the node can spare. How long it takes with one worker is not measured. The job stops
  when `maxQueuedBundles` (50) bundles are waiting in `save/`; each later run, by hand or the
  nightly one, fetches what is still missing, so the seed converges without supervision.
- **A schema migration is rehearsed on staging's data**, which is the same kind and, once seeded,
  the same scale as production's, though not the same rows. Migrations run at server startup and
  only go forward, so this rehearsal is the only test of one.
- **The diff-mode conformance run** (`--base-url` staging, `--compare-url` production) compares
  two servers that ingested the same NOAA catalog independently. It is a review aid, not a gate:
  a difference can come from the code or from one side having ingested a newer cell.
- **A destructive test costs a reseed.** There is no shortcut back to a full dataset: after the
  nuke route or the mutating conformance suite, staging is empty until the seed converges again.
- **Disk:** the managed plan holds a second database of production's size, with room for a
  migration that rewrites `features` (see [Database](#database)). Staging takes the full catalog,
  with no `scaleFilter`.

### D3. Capacity

Staging runs on the existing three nodes, under the caps in
[Capacity](#capacity-and-blast-radius). Before it is bootstrapped, the four pods that hide
`stgp6`'s and `s9zzd`'s real usage from the scheduler get honest memory requests (see
[Nodes](#nodes)). No node is added.

### D4. Who can reach staging

Staging is public, like production. The data is public NOAA charts, the admin routes are signed,
and a phone running a debug build must be able to reach it. Staging gets its own admin user,
password and key.

## Design

### One set of manifests, two environments

`deploy/mxmariner/` became `deploy/k8s/`, since it no longer describes one namespace. The xtask's
placeholder substitution stays: the dry run, the secret masking and the render checks are built on
it. Each environment is one value of an `Environment` type in `crates/xtask`, and the manifests
have a placeholder for everything that differs:

| Setting | Production | Staging |
|---|---|---|
| Namespace | `mxmariner` | `mxstaging` |
| Host | `njord.mxmariner.com` | `staging-njord.mxmariner.com` |
| Database, role | `njord`, `njord` | `njord_staging`, `njord_staging` |
| Relay host port (hex for the probe) | 6433 (`1921`) | 6434 (`1922`) |
| NFS Helm release and StorageClass | `njord-nfs` | `njord-staging-nfs` |
| NFS disk / shared PVC | 20Gi / 18Gi | 20Gi / 18Gi |
| `njord-chart-dep` memory (request / limit) | 512Mi / 1536Mi | 128Mi / 512Mi |
| `njord-chart-scaler` replicas | 1–5 | 1–1 |
| Ingest memory (request = limit) | 1Gi | 512Mi |
| `chartIngestWorkers` | 3 | 1 |
| `njord-enc-download` / `njord-walk-tiles` | 02:00 / 03:00 PT | 04:00 / 05:00 PT |
| PriorityClass | default (0) | `njord-staging` (negative) |
| ResourceQuota | none | yes |
| `openenc_mirror.yaml` | applied by hand | never |

`regionExports`, the depths and every other `njord-config` key are identical in both, so staging
builds the same region archives the shipped mobile apps download.

Staging keeps its own relay DaemonSet instead of dialing production's `njord-db-relay` Service.
It costs three 16Mi pods, and in exchange neither namespace depends on the other.

### Environment selection in `cargo xtask`

- A global `--env <staging|production>` flag, **default `staging`**. `cargo xtask --execute deploy`
  deploys to staging.
- `NJORD_NAMESPACE`, `NJORD_DB` and `NJORD_HOST` stop being inputs: the environment supplies them.
  This is deliberate. A shell that still exports `NJORD_NAMESPACE=mxmariner` from today's workflow
  must not be able to turn a default deploy into a production deploy. If one of them is set, the
  xtask says it is ignored. `NJORD_VERSION` and `NJORD_NFS_SIZE` go the same way: the tag comes
  from git and the disk size from the environment.
- Secrets are read per environment, with no fallback to the unprefixed names, so a staging
  `secrets` run can never write production's password:

  | Staging | Production |
  |---|---|
  | `NJORD_STAGING_DB_HOST`, `_DB_PORT`, `_DB_PASS` | `NJORD_PRODUCTION_DB_HOST`, ... |
  | `NJORD_STAGING_ADMIN_KEY`, `_ADMIN_USER`, `_ADMIN_PASS` | `NJORD_PRODUCTION_ADMIN_KEY`, ... |

  The database role is not a variable. It is the environment's, like the database name, so
  staging's secrets cannot be written with production's role.

- Every task prints the environment in its first line, in a dry run and with `--execute`.
- The render checks that `88ddacee` removed come back as environment isolation checks, run on
  every render and in a unit test that renders both environments: a staging manifest must not
  contain production's namespace, host, database, role, relay port or StorageClass, and the
  reverse. Values are compared whole, never as substrings: `staging-njord.mxmariner.com` contains
  `njord.mxmariner.com`, and `njord_staging` contains `njord`.

### Build once, promote

- **Immutable tags.** `build` tags the image `<workspace version>-<short git hash>`, for example
  `2.0.0-44dfc867`. A dirty tree adds `-dirty-` and a UTC timestamp. The bare `:<version>` tag is
  never pushed again. Since a tag now names one image, `imagePullPolicy` becomes `IfNotPresent`
  (step 2).
- **`deploy` is staging-only**: build, push, then a release. With `--env production` it refuses
  and points at `promote`.
- **`promote`** takes no build step. It reads the image `njord-chart-dep` runs in `mxstaging`,
  checks that `https://staging-njord.mxmariner.com/v1/about/version` reports the same git hash,
  refuses a `-dirty-` tag, and releases that image to production. `--tag <tag>` promotes or rolls
  back to a named image that is already in the registry. Without `--env production` it refuses.
- **A release**, the part `deploy` and `promote` share: apply `staging.yaml` (staging only) and
  `chart_server.yaml` with the image, wait for the rollouts of `njord-chart-dep` and
  `njord-ingest-rs`, and wait until `/v1/about/version` reports the image's git hash. A new image
  replaces the pods by itself; when the environment already runs the image, the release restarts
  both Deployments instead. It also applies the CronJobs, but only once they are installed, so
  the first NOAA download still starts when `apply cron` is run by hand.
- **`apply` and `render` never change the image.** They use the image the environment already
  runs, read from its Deployment. Otherwise `--env production apply chart` would put whatever
  commit is checked out onto production and bypass staging.
- **`verify`** compares the `gitHash` from `/v1/about/version` with the tag of the image the
  Deployment runs.

Two changes to the workloads make a rollout mean something, and staging is where they are tried
first:

- `njord-chart-dep` has a readiness probe on `/v1/about/version`. Without one, `rollout status`
  succeeds on a server that never started serving and the ingress routes to it. Production runs
  without it until its first `promote`.
- `njord-ingest-rs` is a Deployment with the `Recreate` strategy, so a new image replaces its pod
  and the old pod is gone before the new one is scheduled. Production runs it as a bare
  ReplicaSet, which does not replace its pod when the image changes, until its first `promote`.

### Capacity and blast radius

Staging must lose every contest with production for memory.

- **PriorityClass `njord-staging`**, negative value, `preemptionPolicy: Never`, on every staging
  pod. Everything else on the cluster runs at 0, so staging is what the scheduler preempts and
  what the kubelet evicts first, and production's pods need no change.
- **Limits below what a node can spare.** The ingest's request equals its limit (512Mi, one
  worker), so the scheduler reserves what the pod may use and the kernel kills the container, not
  the node, if it grows. If it is OOM-killed, the fix is still fewer workers before a higher limit.
- **ResourceQuota in `mxstaging`**: `requests.memory` 1.5Gi, `limits.memory` 3Gi as a starting
  point. Steady state requests are about 0.9Gi (chart 128Mi, ingest 512Mi, NFS provisioner 192Mi,
  three relays at 16Mi) and limits about 1.5Gi. The two nightly jobs add 320Mi of requests and
  1.2Gi of limits while they run. A quota on memory makes resources mandatory on every pod, so
  `njord-walk-tiles` and the NFS provisioner have explicit values. The limits leave no room for
  a second chart pod while both nightly jobs run: a rollout started then waits for a job to
  finish.
- **Honest requests where usage is hidden** (D3). Four changes, all outside this repository, make
  the scheduler see `stgp6` and `s9zzd` as they are:

  | Pod | Memory request | Where it is set |
  |---|---|---|
  | `kps-grafana` | 128Mi → 640Mi | `kps` Helm values |
  | `prometheus-kps-prometheus-0` | 400Mi → 768Mi | `kps` Helm values |
  | `emharbor/tsdbmysql` | none → 512Mi | its Deployment |
  | `mxmariner-web-dep` | none → 256Mi | the parent repository's `backend/k8s.yaml` |

  After them `stgp6` shows about 1.4Gi free by requests instead of 2.7Gi, and the staging ingest
  goes to a node that has the memory. Each change restarts the pod it touches.

If a node is lost, as on 2026-11-04, the remaining two cannot hold everything. With the
PriorityClass, staging is what stays Pending.

## Steps

### 0. Measure and decide

Done 2026-11-22. The results are in [Measurements](#measurements-step-0) and
[Decisions](#decisions).

- [x] The size of the `njord` database: 9298 MB.
- [x] The managed plan's usable disk, from Cloud Manager: 58 GB.
- [x] `df -h /mnt/njord` and `du -sh /mnt/njord/charts/*` in `njord-chart-dep`: 2.6G of 20G.
- [x] Node and pod memory, in use against requested.
- [x] D3 and D4 confirmed.

### 1. `cargo xtask`: environments, immutable tags, `promote`

Done 2026-11-22. No cluster change.

- [x] `Environment` type and the `--env` flag, default `staging`; per-environment secret
      variables; the legacy variables reported as ignored.
- [x] Image tag `<version>-<hash>`; `deploy` staging-only; `promote`; `verify` checks the hash.
- [x] The isolation checks, and unit tests that render every manifest for both environments and
      feed each environment the other's manifests.
- [x] `nfs` takes its release, StorageClass and size from the environment.
- [x] The four placeholders the isolation checks depend on, brought forward from step 2: the relay
      port and its hex form, the StorageClass, and the database role in `njord-config`.
- [x] The deploy README, the root `README.md` and `RELEASE_NOTES.md`, brought forward from step 6:
      `deploy` changed meaning, so the old text had become dangerous.
- [x] Acceptance: `cargo xtask --env production render <chart|cron> | kubectl -n mxmariner diff -f -`
      reports no difference, and the render differs from the old xtask's only in comments.
      Production's image stays `:2.0.0` until the first `promote`; the pull policy, the probe and
      the ingest's kind change in step 2.

### 2. Manifests

Done 2026-11-22. No cluster change: production keeps running what was applied before, until its
first `promote` (step 5).

- [x] `deploy/mxmariner/` moved to `deploy/k8s/`, with `Containerfile`, `.containerignore`,
      `README.md` and the xtask updated. `openenc_mirror.yaml` is in `deploy/k8s/production/`.
- [x] The remaining placeholders from the table in [Design](#one-set-of-manifests-two-environments):
      memory, replicas, `chartIngestWorkers`, the cron schedules and the PriorityClass, and
      `imagePullPolicy: IfNotPresent`. Production's pods get no `priorityClassName` line at all.
- [x] Readiness probe on `njord-chart-dep`; `njord-ingest-rs` as a `Recreate` Deployment. A
      release waits for both rollouts and no longer deletes the ingest pod. It restarts the two
      Deployments only when the environment already runs the image, since a new image replaces
      the pods by itself.
- [x] `staging.yaml`, rendered only for staging: the PriorityClass (value -100, never preempts)
      and the ResourceQuota. `cargo xtask apply quota` applies it, and so does every release.
- [x] `nfs` gives the provisioner a memory request and limit (192Mi / 384Mi) and the
      environment's PriorityClass, which the quota makes mandatory for staging.
- [x] `njord-walk-tiles` has a memory request and limit, in both environments.
- [x] `db_setup.sql` takes the database, role and password as `psql` variables, grants the admin
      user membership in the new role, and gained the `REVOKE CONNECT` and `CONNECTION LIMIT`
      statements. Run on a local PostgreSQL 17 as a non-superuser admin, and on PostGIS.
- [x] `conformance/conftest.py`: `--allow-mutating` is refused for production's hosts, compared
      whole: `njord.mxmariner.com` and the `openenc.com` mirror. Staging is no longer refused by
      accident. The mutating suite includes the nuke route, so running it against staging stays a
      deliberate act that costs a reseed (D2).
- [x] The isolation checks also refuse another environment's PriorityClass and cron schedules.
- [x] Acceptance: `cargo xtask --env production render <chart|cron> | kubectl -n mxmariner diff -f -`
      reports only the intended changes: the pull policy, the readiness probe, the new
      `njord-ingest-rs` Deployment and `njord-walk-tiles`' resources. Staging's render passes
      `kubectl apply --dry-run=client --validate=strict`, and its PriorityClass a server dry run.

Found while doing it, and handled in step 5: a Deployment does not adopt the bare
`njord-ingest-rs` ReplicaSet production runs today, because that ReplicaSet has no labels of its
own, so applying the new chart beside it would run two ingest pods. `apply chart` and a release
therefore delete the bare ReplicaSet first, when one exists.

### 3. Bootstrap `mxstaging`

- [ ] First, the four memory requests from [Capacity](#capacity-and-blast-radius) (D3). Check with
      `kubectl describe node` that `stgp6` no longer shows the most memory free by requests.

Every command is a dry run first, then `--execute`. `--env staging` is the default and is omitted.

```shell
# Namespace and the registry pull secret (ghreg exists only in mxmariner today). GH_TOKEN is a
# GitHub token that can read the ghcr.io/manimaul/njord package (scope read:packages).
kubectl create namespace mxstaging
kubectl -n mxstaging create secret docker-registry ghreg --docker-server=ghcr.io \
  --docker-username=manimaul --docker-password="$GH_TOKEN"

# Database and role, once, as the managed cluster's admin. The script revokes CONNECT on
# njord_staging from PUBLIC; the second command does the same for production's database (D1).
psql "postgresql://akmadmin@$NJORD_STAGING_DB_HOST:$NJORD_STAGING_DB_PORT/defaultdb?sslmode=require" \
  -v db=njord_staging -v role=njord_staging -v pass="$NJORD_STAGING_DB_PASS" \
  -v connection_limit=25 -f deploy/k8s/db_setup.sql
psql "postgresql://njord@$NJORD_PRODUCTION_DB_HOST:$NJORD_PRODUCTION_DB_PORT/njord?sslmode=require" \
  -c 'REVOKE CONNECT ON DATABASE njord FROM PUBLIC'

cargo xtask --execute apply quota   # PriorityClass njord-staging and the ResourceQuota, before any pod
cargo xtask --execute nfs           # Helm release and StorageClass njord-staging-nfs
cargo xtask --execute secrets       # njord-db-upstream, admin-secret-json, njord-admin-basic
cargo xtask --execute deploy        # build, push <version>-<hash>, apply, wait for the rollouts, verify
kubectl -n mxstaging wait --for=condition=Ready certificate/njord-cert --timeout=10m
```

The certificate is issued over HTTP-01, so cert-manager runs a short-lived solver pod in
`mxstaging`. The quota refuses a pod without a memory request and limit; cert-manager's solver
sets both by default. If the certificate stays pending, look for a quota refusal in
`kubectl -n mxstaging get events`.

- [x] Acceptance: `curl https://staging-njord.mxmariner.com/v1/about/version` reports the deployed
      hash; `kubectl -n mxstaging get ds njord-db-relay` is 3/3 on host port 6434; the PVC is
      Bound on `njord-staging-nfs`; `kubectl -n mxmariner get pods` shows no restarts.

### 4. Data

Staging seeds itself from NOAA (D2).

```shell
cargo xtask --execute apply cron   # njord-enc-download 04:00 PT, njord-walk-tiles 05:00 PT
cargo xtask --execute seed         # one njord-enc-download Job now, instead of waiting for 04:00
kubectl -n mxstaging logs -f deploy/njord-ingest-rs
```

- [ ] Watch the first hours: the ingest pod's memory against its 512Mi limit, its restart count,
      and `kubectl top nodes`. An OOM kill here is the limit doing its job; the response is the
      a smaller `bundleSizeCells`, not a higher limit.
- [ ] Record how long the seed takes to converge. It is the cost of every later reseed.
- [ ] Acceptance: `GET /v1/chart_editions` on staging lists about as many charts as production's;
      `GET /v1/regions` lists every region archive; a `njord-enc-download` run on a converged
      staging fetches only what NOAA changed that day.
- [ ] Review one diff-mode run, to learn what the two environments differ in before any code does:
      `pytest conformance --base-url=https://staging-njord.mxmariner.com --compare-url=https://njord.mxmariner.com`

### 5. Move production onto the new flow

- [ ] Export the `NJORD_PRODUCTION_*` variables; remove the unprefixed ones from the shell profile.
- [ ] `cargo xtask --env production promote` (dry run, review, then `--execute`). This is
      production's first immutable tag, and the first rollout gated by a readiness probe. It
      deletes the bare `njord-ingest-rs` ReplicaSet and creates the Deployment of the same name,
      so production has no ingest pod for the seconds in between; run it outside the 02:00 PT
      NOAA job.
- [ ] Afterwards, remove `retire_bare_ingest` and `BARE_INGEST` from `crates/xtask/src/main.rs`,
      and the "Production until its first `promote`" note from `deploy/k8s/README.md`: no
      environment has a bare ReplicaSet any more.
- [ ] Acceptance: production reports the same `gitHash` as staging, and both namespaces run the
      same image tag.

### 6. Documentation

- [x] Rewrite the deploy README for two environments (done in step 1; moved to `deploy/k8s/` and
      given staging's `db_setup.sql` invocation in step 2).
- [ ] Remove "staging is not stood up yet" from `deploy/k8s/README.md`, `README.md` and this
      document's status line, once step 3 is done.
- [x] `README.md`: the `xtask` row and the Container section.
- [x] `README.md`: the conformance example, which compared production with itself.
- [x] `crates/xtask/src/main.rs` module doc and `--help` text, to `docs/rustdoc_standards.md`.
- [x] `RELEASE_NOTES.md`.
- [x] The header comments of the manifests, which described the Kotlin copy and the port plan
      (done when the port plan documents were removed).
- [ ] In the parent repository, `docs/explorations_ideas/explore_backend_staging.md` names the
      host `njord-staging.mxmariner.com`; it should say `staging-njord.mxmariner.com`.
- [ ] In the parent repository, `docs/runbook/release_web.md` and
      `docs/explorations_implemented/backend_db.md` cite `njord/deploy/mxmariner/`; it is
      `njord/deploy/k8s/` once the parent's submodule pointer moves past the rename.

## The release flow afterwards

```shell
cargo xtask --execute deploy                     # staging: build, push, apply, rollout, verify
pytest conformance --base-url=https://staging-njord.mxmariner.com \
                   --compare-url=https://njord.mxmariner.com       # review every difference
cargo xtask --env production promote             # dry run: shows the image and the manifests
cargo xtask --env production --execute promote
cargo xtask --env production --execute verify
```

For a build with a schema migration, deploy it to a fully seeded staging, so the migration runs
against a database of production's scale, and let staging's nightly NOAA job, ingest and region
export run on the new schema before promoting. Rolling production back is
`promote --tag <previous tag>`; it does not undo a migration, which is why the rehearsal matters.

## Risks

| Risk | Mitigation |
|---|---|
| A staging build replaces production's image tag | Immutable tags, step 1, before staging exists |
| A stale `NJORD_NAMESPACE=mxmariner` sends a default deploy to production | The variable is no longer read; the environment comes only from `--env` |
| Staging exhausts a node's memory | Request = limit on the ingest, one worker, ResourceQuota, negative PriorityClass |
| Staging slows the shared database (D1): the instance caches about 2.3 GB and each `features` table is 9.2 GB | Connection limit; no heavy jobs in production's cron window; production's tile cache; start the cold seed at a quiet hour and watch production's tile latency |
| The managed plan's disk fills with a second database of production's size | 58 GB usable against about 37 GB in the worst measured case, both tables rewritten and neither reclaimed |
| A destructive test leaves staging empty while a release is waiting | The reseed time is recorded in step 4; run destructive tests when no release is pending |
| A second `hostNetwork` listener on 6433 crash-loops the relay | Port 6434 from the environment; isolation check on the rendered manifest |
| Staging drifts from production and stops predicting it | One set of manifests and one `njord-config`; the same nightly NOAA job on both |

## Out of scope

- A staging environment for mxmariner.com's backend and web app (`staging.mxmariner.com`), and a
  debug build of the mobile apps that points at staging. Both follow from this one; see
  `docs/explorations_ideas/explore_backend_staging.md` in the parent repository.
- CI. Deploys stay manual through `cargo xtask`.
- Leftovers of the retired `njord` namespace: the empty `s57server` database, two `Released` volumes (`pvc-59f48cea1a294fd3`, 10Gi,
  still billed, and `pvc-dbe695c6-…`) and the orphaned `nfs` StorageClass.
