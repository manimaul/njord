#!/usr/bin/env bash
# Deploy the Rust Njord environment in the mxmariner namespace (docs/RUST_PORT_PLAN.md §10).
#
# Replaces the Gradle tasks secret, makeImg, pubImg, k8sApply, cycleIngestPod, rolloutRestart,
# holdOn and deploy, and the hard-coded k8s_deploy/create_nfs.sh (all removed with the Kotlin
# code in Phase 7), for the NEW environment only. The Kotlin `njord` namespace is deployed from
# the master branch's ./gradlew :deploy and k8s_deploy/, and is never touched from here.
#
# DRY RUN BY DEFAULT: every subcommand prints what it would do. Nothing touches the cluster, the
# registry or the local image store unless you pass --execute (or export DRY_RUN=0).
#
#   deploy/mxmariner/deploy.sh [--execute|--dry-run] <subcommand> [args]
#
# Subcommands (see usage() below): secrets, nfs, build, push, apply [chart|cron|vacuum|all],
# rollout, cycle-ingest, seed, verify, deploy, render <chart|cron|vacuum|secrets>.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

die() { echo "deploy.sh: ERROR: $*" >&2; exit 1; }
note() { echo "deploy.sh: $*" >&2; }

usage() {
  cat <<'USAGE'
usage: deploy/mxmariner/deploy.sh [--execute|--dry-run] <subcommand> [args]

Dry run is the default (DRY_RUN=1). Pass --execute or export DRY_RUN=0 to act.

  secrets            njord-pgbouncer-ini, njord-pgbouncer-userlist-txt, njord-db-upstream,
                     admin-secret-json, njord-admin-basic -> kubectl apply      (./gradlew :secret)
  nfs                Helm release njord-nfs, StorageClass njord-nfs, >= 20Gi     (create_nfs.sh)
  build              podman build -f Containerfile.rust -> $NJORD_IMAGE_REPO:$NJORD_VERSION
  push               podman push the Rust image                                  (./gradlew :pubImg)
  apply [what]       chart (default) | cron | vacuum | all (= chart + cron)     (./gradlew :k8sApply)
  rollout            kubectl rollout restart deployment/njord-chart-dep          (:rolloutRestart)
  cycle-ingest       delete the njord-ingest-rs pod so it pulls again            (:cycleIngestPod)
  seed               create a one-off Job from cronjob/njord-enc-download
  verify             kubectl get pods,pvc,certificate,ingress; curl /v1/about/version
  deploy             build, push, apply chart, wait 5s, rollout                  (./gradlew :deploy)
  render <what>      print a rendered manifest (chart|cron|vacuum|secrets) to stdout; secrets
                     are masked unless SHOW_SECRETS=1. Never touches anything.

Required environment (no defaults, the script aborts if any needed one is unset or empty):
  NJORD_NAMESPACE                                       (mxmariner; "njord" is refused)
  NJORD_DB                                              (njord; NJORD_DB_NAME is NOT read)
  NJORD_DB_HOST NJORD_DB_PORT NJORD_DB_USER NJORD_DB_PASS                    (secrets)
  NJORD_ADMIN_KEY NJORD_ADMIN_USER NJORD_ADMIN_PASS                          (secrets)

Optional, with defaults:
  NJORD_IMAGE_REPO=ghcr.io/manimaul/njord     Rust image repository (njord-chart-server refused)
  NJORD_VERSION=<workspace version in Cargo.toml>
  NJORD_HOST=njord.mxmariner.com
  NJORD_PLATFORM=linux/amd64                  podman build --platform
  NJORD_NFS_SIZE=20Gi                         NFS provisioner disk, at least 20Gi
  NJORD_KUBE_CONTEXT=                         if set, --execute refuses any other current context
USAGE
}

# ---------------------------------------------------------------------------------------------
# Arguments
# ---------------------------------------------------------------------------------------------
DRY_RUN="${DRY_RUN:-1}"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --execute) DRY_RUN=0; shift ;;
    --dry-run) DRY_RUN=1; shift ;;
    -h|--help|help) usage; exit 0 ;;
    --*) die "unknown option $1" ;;
    *) break ;;
  esac
done
[[ "${DRY_RUN}" == "0" || "${DRY_RUN}" == "1" ]] || die "DRY_RUN must be 0 or 1, got '${DRY_RUN}'"
[[ $# -ge 1 ]] || { usage >&2; exit 2; }
SUBCOMMAND="$1"; shift

# ---------------------------------------------------------------------------------------------
# Environment and safety checks
# ---------------------------------------------------------------------------------------------

# Abort on any unset, empty, or literal "undefined" variable. Secret.kt fell back to the string
# "undefined", so a shell exporting NJORD_DB_NAME but not NJORD_DB would have written
# dbname=undefined without complaint (§10.3).
require() {
  local v
  for v in "$@"; do
    if [[ -z "${!v:-}" ]]; then
      if [[ "${v}" == "NJORD_DB" && -n "${NJORD_DB_NAME:-}" ]]; then
        die "NJORD_DB is not set (NJORD_DB_NAME is, but that is the Kotlin environment's variable and is never read here)"
      fi
      die "${v} is not set"
    fi
    [[ "${!v}" != "undefined" ]] || die "${v} is the literal string 'undefined'"
  done
}

# Values that are substituted into YAML or pgbouncer.ini unquoted must be plain tokens.
require_token() {
  local v="$1" re="$2"
  require "${v}"
  [[ "${!v}" =~ ${re} ]] || die "${v}='${!v}' does not match ${re}"
}

workspace_version() {
  sed -n '/^\[workspace.package\]/,/^\[/{s/^version *= *"\(.*\)"/\1/p;}' "${REPO_ROOT}/Cargo.toml" | head -n1
}

NJORD_IMAGE_REPO="${NJORD_IMAGE_REPO:-ghcr.io/manimaul/njord}"
NJORD_VERSION="${NJORD_VERSION:-$(workspace_version)}"
NJORD_HOST="${NJORD_HOST:-njord.mxmariner.com}"
NJORD_PLATFORM="${NJORD_PLATFORM:-linux/amd64}"
NJORD_NFS_SIZE="${NJORD_NFS_SIZE:-20Gi}"
NJORD_KUBE_CONTEXT="${NJORD_KUBE_CONTEXT:-}"

check_namespace() {
  require_token NJORD_NAMESPACE '^[a-z0-9]([-a-z0-9]*[a-z0-9])?$'
  [[ "${NJORD_NAMESPACE}" != "njord" ]] \
    || die "NJORD_NAMESPACE=njord is the Kotlin environment; this script never touches it (§10)"
}

check_db_name() {
  require_token NJORD_DB '^[a-z_][a-z0-9_]*$'
  [[ "${NJORD_DB}" != "s57server" ]] \
    || die "NJORD_DB=s57server is the Kotlin environment's database (§10.1); use njord"
}

check_host() {
  require_token NJORD_HOST '^[a-z0-9.-]+$'
  [[ "${NJORD_HOST}" != *openenc.com ]] || die "NJORD_HOST=${NJORD_HOST} belongs to the njord namespace"
}

RUST_IMAGE=""
check_rust_image() {
  require_token NJORD_IMAGE_REPO '^[a-z0-9.:/_-]+$'
  require_token NJORD_VERSION '^[A-Za-z0-9_][A-Za-z0-9._-]*$'
  # §10.2 item 1: the njord namespace pulls njord-chart-server with imagePullPolicy: Always.
  [[ "${NJORD_IMAGE_REPO}" != *njord-chart-server* ]] \
    || die "NJORD_IMAGE_REPO=${NJORD_IMAGE_REPO}: the Rust image must never go to njord-chart-server (§10.2 item 1)"
  RUST_IMAGE="${NJORD_IMAGE_REPO}:${NJORD_VERSION}"
}

# Every workload (chart, ingest, enc-download) runs the one Rust image (§10.4).
NJORD_IMAGE=""
resolve_images() {
  check_rust_image
  NJORD_IMAGE="${RUST_IMAGE}"
}

# Run (or, in dry-run, print) a command.
run() {
  if [[ "${DRY_RUN}" == "1" ]]; then
    printf '[dry-run] +'; printf ' %q' "$@"; printf '\n'
  else
    printf '+'; printf ' %q' "$@"; printf '\n'
    "$@"
  fi
}

# Feed a rendered manifest to kubectl apply. In dry-run the (masked) manifest is printed.
#   apply_rendered <label> <real renderer> [<masked renderer>]
apply_rendered() {
  local label="$1" real="$2" masked="${3:-$2}"
  if [[ "${DRY_RUN}" == "1" ]]; then
    printf '[dry-run] + %s | kubectl --namespace %q apply -f -\n' "${label}" "${NJORD_NAMESPACE}"
    printf '[dry-run] ----- %s -----\n' "${label}"
    "${masked}"
    printf '[dry-run] ----- end %s -----\n' "${label}"
  else
    printf '+ %s | kubectl --namespace %q apply -f -\n' "${label}" "${NJORD_NAMESPACE}"
    "${real}" | kubectl --namespace "${NJORD_NAMESPACE}" apply -f -
  fi
}

# Before anything reaches the cluster, make sure kubectl/helm point where the user expects. Reads
# the local kubeconfig only.
KUBE_CHECKED=0
kube_preflight() {
  [[ "${DRY_RUN}" == "0" && "${KUBE_CHECKED}" == "0" ]] || return 0
  command -v kubectl >/dev/null || die "kubectl not found"
  local ctx
  ctx="$(kubectl config current-context)"
  note "kubectl context: ${ctx}, namespace: ${NJORD_NAMESPACE}"
  if [[ -n "${NJORD_KUBE_CONTEXT}" && "${ctx}" != "${NJORD_KUBE_CONTEXT}" ]]; then
    die "current context ${ctx} is not NJORD_KUBE_CONTEXT=${NJORD_KUBE_CONTEXT}"
  fi
  KUBE_CHECKED=1
}

# ---------------------------------------------------------------------------------------------
# Manifest rendering
# ---------------------------------------------------------------------------------------------

# Only these ${NAME} placeholders are substituted; anything else is left alone (shell in the
# vacuum Job, Python in walk_tiles.py) and a leftover ${NJORD_...} is an error.
substitute() {
  local file="$1" out v val
  out="$(cat "${file}")"
  for v in NJORD_NAMESPACE NJORD_DB NJORD_HOST NJORD_IMAGE; do
    val="${!v}"
    out="${out//\$\{${v}\}/${val}}"
  done
  printf '%s\n' "${out}"
}

# Last line of defence against the overlaps in §10.2, applied to every rendered manifest.
guard_rendered() {
  local label="$1" text="$2"
  local -a bad=(
    '^[[:space:]]*namespace:[[:space:]]*njord[[:space:]]*$'
    '^[[:space:]]*storageClassName:[[:space:]]*nfs[[:space:]]*$'
    'TCP4-LISTEN:6432'
    'openenc\.com'
    's57server'
    '\$\{NJORD_(NAMESPACE|DB|HOST|IMAGE)\}'
  )
  local re
  for re in "${bad[@]}"; do
    if grep -Eq -- "${re}" <<<"${text}"; then
      die "rendered ${label} matches forbidden pattern ${re}"
    fi
  done
  # No workload may run a Kotlin njord-chart-server image.
  if grep -Eq -- 'image:.*njord-chart-server' <<<"${text}"; then
    die "rendered ${label} uses a njord-chart-server image"
  fi
}

render_file() {
  local file="${SCRIPT_DIR}/$1" text
  [[ -f "${file}" ]] || die "missing ${file}"
  text="$(substitute "${file}")"
  guard_rendered "$1" "${text}"
  printf '%s\n' "${text}"
}

render_prereqs() { check_namespace; check_db_name; check_host; resolve_images; }
render_chart()  { render_file chart_server.yaml; }
render_cron()   { render_file noaa_enc_daily_cron.yaml; render_file walk_tiles_cronjob.yaml; }
render_vacuum() { render_file vacuum_job.yaml; }

# ---------------------------------------------------------------------------------------------
# Secrets (replacement for the Kotlin buildSrc Secret.kt and ./gradlew :secret)
# ---------------------------------------------------------------------------------------------

json_escape() {
  local s="$1"
  [[ "${s}" != *[[:cntrl:]]* ]] || die "control characters are not allowed in secret values"
  s="${s//\\/\\\\}"
  s="${s//\"/\\\"}"
  printf '%s' "${s}"
}

# A value placed verbatim in a YAML block scalar: anything but control characters (newlines).
raw_value() {
  [[ "$1" != *[[:cntrl:]]* ]] || die "control characters are not allowed in secret values"
  printf '%s' "$1"
}

# pgbouncer userlist.txt quotes with "..." and escapes " by doubling it.
userlist_escape() {
  local s="$1"
  [[ "${s}" != *[[:cntrl:]]* ]] || die "control characters are not allowed in secret values"
  printf '%s' "${s//\"/\"\"}"
}

secrets_prereqs() {
  check_namespace
  check_db_name
  require_token NJORD_DB_HOST '^[A-Za-z0-9.-]+$'
  require_token NJORD_DB_PORT '^[0-9]{1,5}$'
  require_token NJORD_DB_USER '^[a-z_][a-z0-9_]*$'
  require NJORD_DB_PASS NJORD_ADMIN_KEY NJORD_ADMIN_USER NJORD_ADMIN_PASS
  # Checked here because the escape helpers run inside $(...) in a heredoc, where die cannot
  # stop the script.
  local v
  for v in NJORD_DB_PASS NJORD_ADMIN_KEY NJORD_ADMIN_USER NJORD_ADMIN_PASS; do
    [[ "${!v}" != *[[:cntrl:]]* ]] || die "${v} contains control characters"
  done
  [[ "${NJORD_DB_USER}" != "akmadmin" ]] \
    || die "NJORD_DB_USER=akmadmin: use the dedicated njord role so this environment cannot touch s57server (§10.2 item 4)"
}

# $1 = 1 to mask secret values
secrets_yaml() {
  local mask="$1" db_pass="${NJORD_DB_PASS}" admin_key="${NJORD_ADMIN_KEY}" admin_pass="${NJORD_ADMIN_PASS}"
  if [[ "${mask}" == "1" ]]; then
    db_pass="<redacted>" admin_key="<redacted>" admin_pass="<redacted>"
  fi
  local ns="${NJORD_NAMESPACE}" db="${NJORD_DB}" user="${NJORD_DB_USER}"
  cat <<EOF
---
apiVersion: v1
kind: Secret
metadata:
  name: njord-pgbouncer-ini
  namespace: ${ns}
type: Opaque
stringData:
  pgbouncer.ini: |
    [databases]
    # Alias, dbname and the pgConnectionInfo path in admin-secret-json are all NJORD_DB, so they
    # cannot disagree (§10.3). Points at this namespace's njord-db-relay Service (port 6432 ->
    # host port 6433), not the database directly - see the DaemonSet in chart_server.yaml.
    ${db} = host=njord-db-relay port=6432 dbname=${db} user=${user}

    [pgbouncer]
    # Pod loopback only. The njord server shares this pod network namespace and connects over
    # localhost, so this costs nothing, and it keeps auth_type=trust from being reachable at
    # <podIP>:5432 by anything else in the cluster.
    listen_addr = 127.0.0.1
    listen_port = 5432
    auth_type = trust
    auth_file = /etc/pgbouncer/userlist.txt
    pool_mode = transaction
    max_client_conn = 100
    default_pool_size = 10
    server_reset_query =
---
# Not a secret: the database endpoint for the njord-db-relay DaemonSet, from the same
# NJORD_DB_HOST/NJORD_DB_PORT. Changing it does not restart the DaemonSet - cycle the relay pods.
apiVersion: v1
kind: ConfigMap
metadata:
  name: njord-db-upstream
  namespace: ${ns}
data:
  DB_HOST: "${NJORD_DB_HOST}"
  DB_PORT: "${NJORD_DB_PORT}"
---
apiVersion: v1
kind: Secret
metadata:
  name: njord-pgbouncer-userlist-txt
  namespace: ${ns}
type: Opaque
stringData:
  userlist.txt: |
    "$(userlist_escape "${user}")" "$(userlist_escape "${db_pass}")"
---
# CHART_SERVER_OPTS for njord-chart-dep and njord-ingest-rs.
apiVersion: v1
kind: Secret
metadata:
  name: admin-secret-json
  namespace: ${ns}
type: Opaque
stringData:
  chart_server_opts: |
    {
    "adminKey": "$(json_escape "${admin_key}")",
    "adminUser": "$(json_escape "${NJORD_ADMIN_USER}")",
    "adminPass": "$(json_escape "${admin_pass}")",
    "pgConnectionInfo": "postgresql://${user}@localhost:5432/${db}"
    }
---
# Basic credentials for njord-enc-download's orphan deletes (k8s_create_reg_sec.sh
# create_cron_secret).
apiVersion: v1
kind: Secret
metadata:
  name: njord-admin-basic
  namespace: ${ns}
type: Opaque
stringData:
  admin_user: |-
    $(raw_value "${NJORD_ADMIN_USER}")
  admin_pass: |-
    $(raw_value "${admin_pass}")
EOF
}

# Wrappers with no arguments, for apply_rendered.
secrets_real()   { local t; t="$(secrets_yaml 0)"; guard_rendered secrets "${t}"; printf '%s\n' "${t}"; }
secrets_masked() { local t; t="$(secrets_yaml 1)"; guard_rendered secrets "${t}"; printf '%s\n' "${t}"; }

# ---------------------------------------------------------------------------------------------
# Subcommands
# ---------------------------------------------------------------------------------------------

cmd_secrets() {
  secrets_prereqs
  kube_preflight
  apply_rendered "secrets (njord-pgbouncer-ini, njord-pgbouncer-userlist-txt, njord-db-upstream, admin-secret-json, njord-admin-basic)" \
    secrets_real secrets_masked
}

cmd_nfs() {
  check_namespace
  [[ "${NJORD_NFS_SIZE}" =~ ^([0-9]+)Gi$ ]] || die "NJORD_NFS_SIZE must be <n>Gi, got ${NJORD_NFS_SIZE}"
  (( BASH_REMATCH[1] >= 20 )) || die "NJORD_NFS_SIZE must be at least 20Gi (§10.2 item 2)"
  kube_preflight
  run helm repo add nfs-ganesha-server-and-external-provisioner \
    https://kubernetes-sigs.github.io/nfs-ganesha-server-and-external-provisioner/
  # Release njord-nfs (not nfs-server): a different provisioner name and a StorageClass that Helm
  # can own, instead of colliding with the njord namespace's nfs-server release and `nfs` class.
  # `helm install` (not upgrade --install) so an existing release is never modified.
  run helm install njord-nfs nfs-ganesha-server-and-external-provisioner/nfs-server-provisioner \
    --namespace "${NJORD_NAMESPACE}" \
    --set persistence.enabled=true \
    --set persistence.storageClass=linode-block-storage-retain \
    --set "persistence.size=${NJORD_NFS_SIZE}" \
    --set storageClass.name=njord-nfs
}

cmd_build() {
  check_rust_image
  local hash branch dirty
  hash="$(git -C "${REPO_ROOT}" rev-parse --short HEAD 2>/dev/null || echo unknown)"
  branch="$(git -C "${REPO_ROOT}" rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"
  dirty="$(git -C "${REPO_ROOT}" status --porcelain 2>/dev/null | wc -l | tr -d ' ')"
  run podman build --platform "${NJORD_PLATFORM}" \
    --ignorefile "${SCRIPT_DIR}/containerignore" \
    -f "${REPO_ROOT}/Containerfile.rust" \
    --build-arg "GIT_HASH=${hash}" \
    --build-arg "GIT_BRANCH=${branch}" \
    --build-arg "GIT_DIRTY=${dirty}" \
    -t "${RUST_IMAGE}" \
    "${REPO_ROOT}"
}

cmd_push() {
  check_rust_image
  run podman push "${RUST_IMAGE}"
}

cmd_apply() {
  local what="${1:-chart}"
  render_prereqs
  kube_preflight
  case "${what}" in
    chart)  apply_rendered chart_server.yaml render_chart ;;
    cron)   apply_rendered "noaa_enc_daily_cron.yaml + walk_tiles_cronjob.yaml" render_cron ;;
    vacuum) apply_rendered vacuum_job.yaml render_vacuum ;;
    all)    apply_rendered chart_server.yaml render_chart
            apply_rendered "noaa_enc_daily_cron.yaml + walk_tiles_cronjob.yaml" render_cron ;;
    *) die "apply: expected chart, cron, vacuum or all, got ${what}" ;;
  esac
}

cmd_rollout() {
  check_namespace
  kube_preflight
  run kubectl --namespace "${NJORD_NAMESPACE}" rollout restart deployment/njord-chart-dep
  run kubectl --namespace "${NJORD_NAMESPACE}" rollout status deployment/njord-chart-dep --timeout=10m
}

cmd_cycle_ingest() {
  check_namespace
  kube_preflight
  # A bare ReplicaSet has no rollout; deleting its pod makes it pull the image again.
  run kubectl --namespace "${NJORD_NAMESPACE}" delete pods -l app=njord-ingest
}

cmd_seed() {
  check_namespace
  kube_preflight
  local job="njord-enc-seed-$(date +%Y%m%d%H%M%S)"
  run kubectl --namespace "${NJORD_NAMESPACE}" create job "${job}" --from=cronjob/njord-enc-download
  echo "follow it with: kubectl -n ${NJORD_NAMESPACE} logs -f -l job-name=${job}"
}

cmd_verify() {
  check_namespace
  check_host
  kube_preflight
  run kubectl --namespace "${NJORD_NAMESPACE}" get pods,pvc,certificate,ingress
  run curl -fsS "https://${NJORD_HOST}/v1/about/version"
}

cmd_deploy() {
  # Check everything up front so a bad variable fails before the image is built.
  render_prereqs
  cmd_build
  cmd_push
  cmd_apply chart
  run sleep 5   # holdOn
  cmd_rollout
}

cmd_render() {
  local what="${1:-}"
  case "${what}" in
    chart)   render_prereqs; render_chart ;;
    cron)    render_prereqs; render_cron ;;
    vacuum)  render_prereqs; render_vacuum ;;
    secrets) secrets_prereqs
             if [[ "${SHOW_SECRETS:-0}" == "1" ]]; then secrets_real; else secrets_masked; fi ;;
    *) die "render: expected chart, cron, vacuum or secrets" ;;
  esac
}

if [[ "${DRY_RUN}" == "1" && "${SUBCOMMAND}" != "render" ]]; then
  note "DRY RUN: nothing will be executed. Re-run with --execute to act."
fi

case "${SUBCOMMAND}" in
  secrets)      cmd_secrets "$@" ;;
  nfs)          cmd_nfs "$@" ;;
  build)        cmd_build "$@" ;;
  push)         cmd_push "$@" ;;
  apply)        cmd_apply "$@" ;;
  rollout)      cmd_rollout "$@" ;;
  cycle-ingest) cmd_cycle_ingest "$@" ;;
  seed)         cmd_seed "$@" ;;
  verify)       cmd_verify "$@" ;;
  deploy)       cmd_deploy "$@" ;;
  render)       cmd_render "$@" ;;
  *) usage >&2; die "unknown subcommand ${SUBCOMMAND}" ;;
esac
