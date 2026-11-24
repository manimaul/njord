//! The `cargo xtask` tasks: the container image and the Kubernetes deploy of Njord's two
//! environments, staging and production.
//!
//! Run `cargo xtask help` for the tasks (`secrets`, `nfs`, `build`, `push`, `apply`, `rollout`,
//! `deploy`, `promote`, `render`, ...) and the variables they read; `deploy/k8s/README.md`
//! walks through a deploy and `docs/STAGING_ENVIRONMENT_PLAN.md` gives the design. The `xtask`
//! alias is in `.cargo/config.toml`, and every command runs from the repository root.
//!
//! STAGING BY DEFAULT: a task acts on the staging environment unless `--env production` is
//! passed. The environment comes from that flag alone, never from a variable, so a value left in
//! a shell cannot redirect a deploy.
//!
//! DRY RUN BY DEFAULT: every task prints what it would do. Nothing in the cluster, the registry
//! or the local image store is changed unless `--execute` is passed. A dry run may read from the
//! cluster (which image a Deployment runs) to show what it would apply.
//!
//! BUILD ONCE, PROMOTE: `deploy` builds an image tagged `<version>-<git hash>`, which names that
//! one build for good, and gives it to staging. `promote` gives production the image staging
//! runs. No other task changes which image an environment runs.
//!
//! The manifests in `deploy/k8s/` carry `${NJORD_...}` placeholders, one for every setting that
//! differs between the environments. They are filled from the chosen [`Environment`] and piped to
//! `kubectl apply -f -`, so a filled manifest never touches the disk.

use anyhow::{Context, Result, anyhow, bail};
use clap::{Parser, Subcommand, ValueEnum};
use std::cell::Cell;
use std::fmt;
use std::path::{Path, PathBuf};
use std::time::Duration;
use xshell::{Cmd, Shell, cmd};

const IMAGE_REPO: &str = "ghcr.io/manimaul/njord";
const PLATFORM: &str = "linux/amd64";
const DEPLOYMENT: &str = "deployment/njord-chart-dep";
const INGEST_DEPLOYMENT: &str = "deployment/njord-ingest-rs";
const ENC_CRONJOB: &str = "cronjob/njord-enc-download";
const MANIFEST_DIR: &str = "deploy/k8s";

/// The memory request and limit `nfs` gives an NFS provisioner. Explicit because staging's
/// ResourceQuota refuses a pod without them. The provisioner uses about 145Mi.
const NFS_MEMORY: (&str, &str) = ("192Mi", "384Mi");

/// How many times, five seconds apart, a release waits for the new pods to answer
/// `/v1/about/version` with the git hash of the image that was just applied.
const VERSION_ATTEMPTS: u32 = 12;

// ------------------------------------------------------------------------------------------------
// Environments
// ------------------------------------------------------------------------------------------------

/// One deployed copy of Njord, and every setting that differs between two copies.
///
/// Staging and production share one Kubernetes cluster and one managed PostgreSQL cluster. The
/// fields up to `nfs_size` are what would collide, or let one copy reach the other's data, if
/// both used the same value; [`check_isolation`] enforces those on every rendered manifest. The
/// rest size the copy: staging is capped so that it cannot take memory production needs.
#[derive(Debug)]
struct Environment {
    /// The value of `--env` that selects it.
    name: &'static str,
    /// The prefix of the variables its secrets are read from: `NJORD_STAGING` for
    /// `NJORD_STAGING_DB_PASS`.
    var_prefix: &'static str,
    /// The Kubernetes namespace holding every workload.
    namespace: &'static str,
    /// The public host name of the Ingress and the certificate.
    host: &'static str,
    /// The PostgreSQL database.
    db: &'static str,
    /// The PostgreSQL role that owns the database and that the servers connect as.
    db_role: &'static str,
    /// The node port the `njord-db-relay` DaemonSet listens on. The relay is `hostNetwork`, so
    /// the port is taken on every node, whatever the namespace.
    relay_port: u16,
    /// The Helm release of the NFS provisioner behind the shared chart volume.
    nfs_release: &'static str,
    /// The StorageClass that release provides. A StorageClass is cluster-scoped.
    storage_class: &'static str,
    /// The size of the provisioner's block volume.
    nfs_size: &'static str,
    /// The memory an `njord-chart-dep` pod requests.
    chart_memory_request: &'static str,
    /// The memory an `njord-chart-dep` pod may use.
    chart_memory_limit: &'static str,
    /// The most `njord-chart-dep` pods the autoscaler runs. The least is one everywhere.
    chart_max_replicas: u8,
    /// The memory the ingest pod requests, which is also its limit.
    ingest_memory: &'static str,
    /// How many chart cells the ingest reads at once (`chartIngestWorkers`). Each holds one GDAL
    /// cell in memory, so it is sized with `ingest_memory`.
    ingest_workers: u8,
    /// The cron schedule of `njord-enc-download`, in Pacific time.
    enc_schedule: &'static str,
    /// The cron schedule of `njord-walk-tiles`, in Pacific time.
    walk_schedule: &'static str,
    /// The PriorityClass every pod names, or `None` for the cluster's default priority (0).
    priority_class: Option<&'static str>,
    /// The manifest of the objects that cap the environment, its PriorityClass and
    /// ResourceQuota, or `None` for an environment that has neither.
    quota_manifest: Option<&'static str>,
}

const STAGING: Environment = Environment {
    name: "staging",
    var_prefix: "NJORD_STAGING",
    namespace: "mxstaging",
    host: "staging-njord.mxmariner.com",
    db: "njord_staging",
    db_role: "njord_staging",
    relay_port: 6434,
    nfs_release: "njord-staging-nfs",
    storage_class: "njord-staging-nfs",
    nfs_size: "20Gi",
    chart_memory_request: "128Mi",
    chart_memory_limit: "512Mi",
    chart_max_replicas: 1,
    ingest_memory: "512Mi",
    ingest_workers: 1,
    enc_schedule: "0 4 * * *",
    walk_schedule: "0 5 * * *",
    priority_class: Some("njord-staging"),
    quota_manifest: Some("staging.yaml"),
};

const PRODUCTION: Environment = Environment {
    name: "production",
    var_prefix: "NJORD_PRODUCTION",
    namespace: "mxmariner",
    host: "njord.mxmariner.com",
    db: "njord",
    db_role: "njord",
    relay_port: 6433,
    nfs_release: "njord-nfs",
    storage_class: "njord-nfs",
    nfs_size: "20Gi",
    chart_memory_request: "512Mi",
    chart_memory_limit: "1536Mi",
    chart_max_replicas: 5,
    ingest_memory: "1Gi",
    ingest_workers: 3,
    enc_schedule: "0 2 * * *",
    walk_schedule: "0 3 * * *",
    priority_class: None,
    quota_manifest: None,
};

const ENVIRONMENTS: [&Environment; 2] = [&STAGING, &PRODUCTION];

impl Environment {
    /// The relay port as `/proc/net/tcp` prints it, which is what the relay's probes grep for.
    fn relay_port_hex(&self) -> String {
        format!("{:04X}", self.relay_port)
    }

    fn version_url(&self) -> String {
        format!("https://{}/v1/about/version", self.host)
    }

    fn is(&self, other: &Environment) -> bool {
        self.name == other.name
    }

    /// The placeholders of the environment's own settings, and their values. A placeholder whose
    /// value is `None` stands for a line the environment's manifests do not have.
    fn placeholders(&self) -> Vec<(&'static str, Option<String>)> {
        let value = |v: &dyn fmt::Display| Some(v.to_string());
        vec![
            ("${NJORD_NAMESPACE}", value(&self.namespace)),
            ("${NJORD_DB}", value(&self.db)),
            ("${NJORD_DB_USER}", value(&self.db_role)),
            ("${NJORD_HOST}", value(&self.host)),
            ("${NJORD_RELAY_PORT}", value(&self.relay_port)),
            ("${NJORD_RELAY_PORT_HEX}", value(&self.relay_port_hex())),
            ("${NJORD_STORAGE_CLASS}", value(&self.storage_class)),
            (
                "${NJORD_CHART_MEMORY_REQUEST}",
                value(&self.chart_memory_request),
            ),
            (
                "${NJORD_CHART_MEMORY_LIMIT}",
                value(&self.chart_memory_limit),
            ),
            (
                "${NJORD_CHART_MAX_REPLICAS}",
                value(&self.chart_max_replicas),
            ),
            ("${NJORD_INGEST_MEMORY}", value(&self.ingest_memory)),
            ("${NJORD_INGEST_WORKERS}", value(&self.ingest_workers)),
            ("${NJORD_ENC_SCHEDULE}", value(&self.enc_schedule)),
            ("${NJORD_WALK_SCHEDULE}", value(&self.walk_schedule)),
            (
                "${NJORD_PRIORITY_CLASS}",
                self.priority_class.map(str::to_string),
            ),
        ]
    }

    /// The environment's rendered quota manifest, or `None` when it has none. It names no image,
    /// so it can be applied before anything is deployed.
    ///
    /// # Errors
    ///
    /// The file cannot be read, or the result fails [`check_isolation`].
    fn quota(&self) -> Result<Option<String>> {
        self.quota_manifest
            .map(|file| render(self, file, &self.placeholders()))
            .transpose()
    }
}

/// The `--env` values.
#[derive(Clone, Copy, ValueEnum)]
enum Env {
    Staging,
    Production,
}

impl Env {
    fn settings(self) -> &'static Environment {
        match self {
            Env::Staging => &STAGING,
            Env::Production => &PRODUCTION,
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Command line
// ------------------------------------------------------------------------------------------------

#[derive(Parser)]
#[command(
    name = "cargo xtask",
    bin_name = "cargo xtask",
    about = "njord image and deploy tasks (staging unless --env production; dry run unless --execute)",
    after_help = ENV_HELP
)]
struct Cli {
    /// Run the commands. Without it every task only prints what it would do (and the rendered
    /// manifests, secrets masked).
    #[arg(long, global = true)]
    execute: bool,
    /// The environment to act on.
    #[arg(long = "env", global = true, value_enum, default_value_t = Env::Staging)]
    environment: Env,
    #[command(subcommand)]
    task: Task,
}

const ENV_HELP: &str = "\
Environments (--env; no variable selects one):
  staging      namespace mxstaging, staging-njord.mxmariner.com, database njord_staging  (default)
  production   namespace mxmariner, njord.mxmariner.com, database njord

Secrets, read by the `secrets` task for the chosen environment (<ENV> is STAGING or PRODUCTION;
the task aborts if one is unset, empty or the literal 'undefined'):
  NJORD_<ENV>_DB_HOST NJORD_<ENV>_DB_PORT NJORD_<ENV>_DB_PASS
  NJORD_<ENV>_ADMIN_KEY NJORD_<ENV>_ADMIN_USER NJORD_<ENV>_ADMIN_PASS

Optional, with defaults:
  NJORD_IMAGE_REPO=ghcr.io/manimaul/njord     image repository
  NJORD_PLATFORM=linux/amd64                  podman build --platform
  NJORD_KUBE_CONTEXT=                         if set, kubectl must be on this context

No longer read: NJORD_NAMESPACE NJORD_DB NJORD_HOST NJORD_VERSION NJORD_NFS_SIZE, and the
unprefixed NJORD_DB_* and NJORD_ADMIN_* secrets.";

/// The variables that used to choose the target or the image tag. A value left in a shell from
/// before the environments existed is reported, so it is clear that it has no effect.
const IGNORED_VARS: [&str; 5] = [
    "NJORD_NAMESPACE",
    "NJORD_DB",
    "NJORD_HOST",
    "NJORD_VERSION",
    "NJORD_NFS_SIZE",
];

#[derive(Subcommand)]
enum Task {
    /// Apply njord-db-upstream, admin-secret-json and njord-admin-basic.
    Secrets,
    /// Install the environment's NFS provisioner (Helm) and its StorageClass.
    Nfs,
    /// Build the image <repo>:<version>-<git hash> from Containerfile and print its name.
    Build,
    /// Push the image built from this commit; needs `podman login ghcr.io`.
    Push {
        /// The tag to push, as `build` printed it. Needed when the working tree is dirty.
        #[arg(long)]
        tag: Option<String>,
    },
    /// Render manifests and `kubectl apply` them, with the image the environment already runs.
    /// `quota` needs no image, so it also works before anything is deployed.
    Apply {
        #[arg(value_enum, default_value_t = Apply::Chart)]
        what: Apply,
    },
    /// Restart deployment/njord-chart-dep and wait for the rollout.
    Rollout,
    /// Restart deployment/njord-ingest-rs and wait for the rollout.
    CycleIngest,
    /// Create a one-off Job from cronjob/njord-enc-download.
    Seed,
    /// `kubectl get pods,pvc,certificate,ingress`, and check that /v1/about/version serves the
    /// build the Deployment's image names.
    Verify,
    /// Staging only: build, push, apply, wait for both rollouts, verify.
    Deploy,
    /// Production only: apply the image staging runs, wait for both rollouts, verify.
    Promote {
        /// Use this tag instead of staging's image: a rollback, or an image staging no longer
        /// runs. It must already be in the registry.
        #[arg(long)]
        tag: Option<String>,
    },
    /// Print a rendered manifest to stdout. Changes nothing.
    Render {
        #[arg(value_enum)]
        what: Render,
        /// Print the secret values instead of masking them.
        #[arg(long)]
        show_secrets: bool,
        /// Render with this image tag instead of the image the environment runs.
        #[arg(long)]
        tag: Option<String>,
    },
}

#[derive(Clone, Copy, ValueEnum)]
enum Apply {
    /// chart_server.yaml
    Chart,
    /// noaa_enc_daily_cron.yaml and walk_tiles_cronjob.yaml
    Cron,
    /// staging.yaml: staging's PriorityClass and ResourceQuota. Production has neither.
    Quota,
    /// quota, chart and cron
    All,
}

#[derive(Clone, Copy, ValueEnum)]
enum Render {
    Chart,
    Cron,
    Quota,
    Secrets,
}

fn main() -> Result<()> {
    let cli = Cli::parse();
    let sh = Shell::new()?;
    sh.change_dir(repo_root());
    let x = Xtask {
        sh,
        execute: cli.execute,
        env: cli.environment.settings(),
        kube_checked: Cell::new(false),
    };
    x.announce(matches!(cli.task, Task::Render { .. }));
    match cli.task {
        Task::Secrets => x.secrets(),
        Task::Nfs => x.nfs(),
        Task::Build => {
            let image = x.head_image()?;
            x.build(&image)?;
            println!("image: {image}");
            Ok(())
        }
        Task::Push { tag } => x.push(&x.image_to_push(tag)?),
        Task::Apply { what } => x.apply(what),
        Task::Rollout => x.rollout(),
        Task::CycleIngest => x.cycle_ingest(),
        Task::Seed => x.seed(),
        Task::Verify => x.verify(),
        Task::Deploy => x.deploy(),
        Task::Promote { tag } => x.promote(tag),
        Task::Render {
            what,
            show_secrets,
            tag,
        } => {
            let manifests = || -> Result<Manifests> {
                let image = match &tag {
                    Some(tag) => Image::new(image_repo()?, tag.clone())?,
                    None => x.deployed_image()?,
                };
                Ok(Manifests::new(x.env, image))
            };
            let text = match what {
                Render::Chart => manifests()?.chart()?,
                Render::Cron => manifests()?.cron()?,
                Render::Quota => x.env.quota()?.unwrap_or_else(|| {
                    eprintln!("xtask: {} has no quota manifest", x.env.name);
                    String::new()
                }),
                Render::Secrets => Secrets::from_env(x.env)?.yaml(!show_secrets)?,
            };
            print!("{text}");
            Ok(())
        }
    }
}

fn repo_root() -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR"))
        .ancestors()
        .nth(2)
        .expect("crates/xtask sits two levels below the repository root")
        .to_path_buf()
}

struct Xtask {
    sh: Shell,
    execute: bool,
    env: &'static Environment,
    kube_checked: Cell<bool>,
}

impl Xtask {
    /// Says on stderr which environment the task acts on and whether it will act, then names any
    /// set variable that no longer has an effect. `quiet` leaves out the dry-run notice, for
    /// `render`, which never acts.
    fn announce(&self, quiet: bool) {
        let Environment {
            name,
            namespace,
            host,
            ..
        } = self.env;
        let mode = match (quiet, self.execute) {
            (true, _) => "",
            (false, true) => ": EXECUTING",
            (false, false) => ": DRY RUN, nothing will be changed. Re-run with --execute to act.",
        };
        eprintln!("xtask: {name} (namespace {namespace}, {host}){mode}");
        let ignored: Vec<&str> = IGNORED_VARS
            .into_iter()
            .filter(|name| !optional(name).is_empty())
            .collect();
        if !ignored.is_empty() {
            eprintln!(
                "xtask: ignoring {}: the target comes from --env and the image tag from git",
                ignored.join(", ")
            );
        }
    }

    /// Runs a command, or in a dry run prints it.
    fn run(&self, cmd: Cmd<'_>) -> Result<()> {
        if self.execute {
            cmd.run()?;
        } else {
            println!("[dry-run] + {cmd}");
        }
        Ok(())
    }

    /// Feeds a rendered manifest to `kubectl apply` in the environment's namespace. A dry run
    /// prints the masked manifest instead.
    fn kubectl_apply(&self, label: &str, real: &str, masked: &str) -> Result<()> {
        let sh = &self.sh;
        let namespace = self.env.namespace;
        if self.execute {
            println!("+ {label} | kubectl --namespace {namespace} apply -f -");
            cmd!(sh, "kubectl --namespace {namespace} apply -f -")
                .stdin(real)
                .run()?;
        } else {
            println!("[dry-run] + {label} | kubectl --namespace {namespace} apply -f -");
            println!("[dry-run] ----- {label} -----");
            print!("{masked}");
            println!("[dry-run] ----- end {label} -----");
        }
        Ok(())
    }

    /// Makes sure kubectl points at the cluster the user expects, once per run. Reads the local
    /// kubeconfig only.
    ///
    /// # Errors
    ///
    /// `NJORD_KUBE_CONTEXT` is set and is not the current context, or kubectl has none.
    fn kube_preflight(&self) -> Result<()> {
        if self.kube_checked.get() {
            return Ok(());
        }
        let sh = &self.sh;
        let context = cmd!(sh, "kubectl config current-context")
            .quiet()
            .read()
            .context("kubectl config current-context")?;
        if self.execute {
            let namespace = self.env.namespace;
            eprintln!("xtask: kubectl context: {context}, namespace: {namespace}");
        }
        let expected = optional("NJORD_KUBE_CONTEXT");
        if !expected.is_empty() && context != expected {
            bail!("current context {context} is not NJORD_KUBE_CONTEXT={expected}");
        }
        self.kube_checked.set(true);
        Ok(())
    }

    /// [`kube_preflight`](Self::kube_preflight) before a command that changes the cluster. A dry
    /// run sends nothing, so it needs no cluster and skips the check.
    fn before_change(&self) -> Result<()> {
        if self.execute {
            self.kube_preflight()?;
        }
        Ok(())
    }

    /// The image `environment`'s `njord-chart-dep` Deployment is set to run, or `None` when the
    /// Deployment does not exist. Reads the cluster, in a dry run too.
    fn running_image(&self, environment: &Environment) -> Result<Option<Image>> {
        self.kube_preflight()?;
        let sh = &self.sh;
        let namespace = environment.namespace;
        let jsonpath = "jsonpath={.spec.template.spec.containers[0].image}";
        let reference = cmd!(
            sh,
            "kubectl --namespace {namespace} get {DEPLOYMENT} --ignore-not-found -o {jsonpath}"
        )
        .quiet()
        .read()
        .with_context(|| format!("reading the image of {DEPLOYMENT} in {namespace}"))?;
        if reference.is_empty() {
            return Ok(None);
        }
        Image::parse(&reference).map(Some)
    }

    /// The image this environment runs. Tasks that apply manifests without releasing use it, so
    /// that only `deploy` and `promote` ever change an environment's image.
    ///
    /// # Errors
    ///
    /// Nothing is deployed in the environment yet.
    fn deployed_image(&self) -> Result<Image> {
        let Environment {
            name, namespace, ..
        } = self.env;
        self.running_image(self.env)?.ok_or_else(|| {
            let first = if self.env.is(&STAGING) {
                "cargo xtask deploy"
            } else {
                "cargo xtask --env production promote"
            };
            anyhow!(
                "{DEPLOYMENT} does not exist in {namespace}, so {name} has no image yet: \
                 release one with `{first}`, or pass --tag to `render`"
            )
        })
    }

    /// Whether the environment's CronJobs have been applied. A release leaves them alone until
    /// they are, so that the first NOAA download starts when the operator applies them.
    fn cron_installed(&self) -> Result<bool> {
        self.kube_preflight()?;
        let sh = &self.sh;
        let namespace = self.env.namespace;
        let found = cmd!(
            sh,
            "kubectl --namespace {namespace} get {ENC_CRONJOB} --ignore-not-found -o name"
        )
        .quiet()
        .read()
        .with_context(|| format!("looking for {ENC_CRONJOB} in {namespace}"))?;
        Ok(!found.is_empty())
    }

    fn secrets(&self) -> Result<()> {
        let secrets = Secrets::from_env(self.env)?;
        self.before_change()?;
        self.kubectl_apply(
            "secrets (njord-db-upstream, admin-secret-json, njord-admin-basic)",
            &secrets.yaml(false)?,
            &secrets.yaml(true)?,
        )
    }

    fn nfs(&self) -> Result<()> {
        let Environment {
            namespace,
            nfs_release,
            storage_class,
            nfs_size,
            priority_class,
            ..
        } = self.env;
        self.before_change()?;
        let sh = &self.sh;
        self.run(cmd!(
            sh,
            "helm repo add nfs-ganesha-server-and-external-provisioner https://kubernetes-sigs.github.io/nfs-ganesha-server-and-external-provisioner/"
        ))?;
        // The release and the StorageClass are named per environment: a StorageClass is
        // cluster-scoped and the provisioner is named after its release, so two environments
        // cannot share either name. `helm install` (not upgrade --install) so an existing release
        // is never modified.
        let (request, limit) = NFS_MEMORY;
        let mut values = vec![
            format!("persistence.size={nfs_size}"),
            format!("storageClass.name={storage_class}"),
            format!("resources.requests.memory={request}"),
            format!("resources.limits.memory={limit}"),
        ];
        if let Some(class) = priority_class {
            values.push(format!("priorityClass.name={class}"));
        }
        let set_args = values.iter().flat_map(|value| ["--set", value.as_str()]);
        self.run(cmd!(
            sh,
            "helm install {nfs_release} nfs-ganesha-server-and-external-provisioner/nfs-server-provisioner
                --namespace {namespace}
                --set persistence.enabled=true
                --set persistence.storageClass=linode-block-storage-retain
                {set_args...}"
        ))
    }

    /// The image name for a build of the checked-out commit: `<version>-<git hash>`, and for a
    /// working tree with uncommitted changes also `-dirty-<UTC timestamp>`, so that two builds of
    /// different sources never share a tag.
    ///
    /// # Errors
    ///
    /// git cannot name the commit.
    fn head_image(&self) -> Result<Image> {
        let sh = &self.sh;
        let hash = cmd!(sh, "git rev-parse --short HEAD")
            .quiet()
            .ignore_stderr()
            .read()
            .context(
                "an image is named after its commit, and `git rev-parse --short HEAD` failed",
            )?;
        let dirty = !cmd!(sh, "git status --porcelain")
            .quiet()
            .read()?
            .is_empty();
        let mut tag = format!("{}-{hash}", env!("CARGO_PKG_VERSION"));
        if dirty {
            let stamp = cmd!(sh, "date -u +%Y%m%d%H%M%S").quiet().read()?;
            tag.push_str(&format!("{DIRTY_MARK}{stamp}"));
        }
        Image::new(image_repo()?, tag)
    }

    /// The image `push` sends: the named tag, or the one `build` gives this commit.
    ///
    /// # Errors
    ///
    /// No tag is named and the working tree is dirty: its tag carries the time of the build, which
    /// a later `push` cannot know.
    fn image_to_push(&self, tag: Option<String>) -> Result<Image> {
        if let Some(tag) = tag {
            return Image::new(image_repo()?, tag);
        }
        let image = self.head_image()?;
        if image.is_dirty() {
            bail!(
                "the working tree has uncommitted changes, so the image's tag carries its build \
                 time: pass the tag `build` printed with --tag, or use `cargo xtask deploy`"
            );
        }
        Ok(image)
    }

    /// Builds `image` and, when executing, checks that it really is for `NJORD_PLATFORM`.
    ///
    /// The build stage has no `.git`, so the git hash, branch and dirty count go in as the `GIT_*`
    /// build arguments (`unknown` when git cannot tell). The server reports them at
    /// `/v1/about/version`.
    fn build(&self, image: &Image) -> Result<()> {
        let platform = var("NJORD_PLATFORM", Some(PLATFORM))?;
        let sh = &self.sh;
        let git = |args: &[&str]| -> Option<String> {
            cmd!(sh, "git {args...}")
                .quiet()
                .ignore_stderr()
                .read()
                .ok()
        };
        let unknown = || "unknown".to_string();
        let hash = git(&["rev-parse", "--short", "HEAD"]).unwrap_or_else(unknown);
        let branch = git(&["rev-parse", "--abbrev-ref", "HEAD"]).unwrap_or_else(unknown);
        let dirty = git(&["status", "--porcelain"])
            .map(|s| s.lines().count().to_string())
            .unwrap_or_else(unknown);
        let build_args = [
            "--build-arg".to_string(),
            format!("GIT_HASH={hash}"),
            "--build-arg".to_string(),
            format!("GIT_BRANCH={branch}"),
            "--build-arg".to_string(),
            format!("GIT_DIRTY={dirty}"),
        ];
        let image = image.to_string();
        self.run(cmd!(
            sh,
            "podman build --platform {platform} --ignorefile .containerignore -f Containerfile {build_args...} -t {image} ."
        ))?;
        if self.execute {
            let expected = platform.split('/').nth(1).unwrap_or(&platform);
            let format = "{{.Architecture}}";
            let arch = cmd!(sh, "podman image inspect --format {format} {image}").read()?;
            if arch.trim() != expected {
                bail!("{image} was built for {arch}, expected {expected}");
            }
        }
        Ok(())
    }

    fn push(&self, image: &Image) -> Result<()> {
        let sh = &self.sh;
        let image = image.to_string();
        self.run(cmd!(sh, "podman push {image}"))
    }

    /// The `apply` task: renders what `what` names and applies it. The chart and the CronJobs keep
    /// the image the environment already runs.
    ///
    /// # Errors
    ///
    /// `what` includes the chart or the CronJobs and nothing is deployed in the environment yet.
    fn apply(&self, what: Apply) -> Result<()> {
        if matches!(what, Apply::Quota) {
            if !self.apply_quota()? {
                println!("xtask: {} has no quota manifest", self.env.name);
            }
            return Ok(());
        }
        let manifests = Manifests::new(self.env, self.deployed_image()?);
        match what {
            Apply::Chart => self.apply_chart(&manifests),
            Apply::Cron => self.apply_cron(&manifests),
            Apply::Quota | Apply::All => {
                // Render everything first, so a manifest that fails its checks stops the task
                // before anything is applied.
                manifests.chart()?;
                manifests.cron()?;
                self.apply_quota()?;
                self.apply_chart(&manifests)?;
                self.apply_cron(&manifests)
            }
        }
    }

    /// Applies the environment's quota manifest. Returns whether it has one.
    fn apply_quota(&self) -> Result<bool> {
        let Some(text) = self.env.quota()? else {
            return Ok(false);
        };
        self.before_change()?;
        let label = self.env.quota_manifest.unwrap_or_default();
        self.kubectl_apply(label, &text, &text)?;
        Ok(true)
    }

    fn apply_chart(&self, manifests: &Manifests) -> Result<()> {
        let text = manifests.chart()?;
        self.before_change()?;
        self.kubectl_apply("chart_server.yaml", &text, &text)
    }

    fn apply_cron(&self, manifests: &Manifests) -> Result<()> {
        let text = manifests.cron()?;
        self.before_change()?;
        self.kubectl_apply(
            "noaa_enc_daily_cron.yaml + walk_tiles_cronjob.yaml",
            &text,
            &text,
        )
    }

    /// Replaces the pods of `deployment` with new ones from its current template. This is what
    /// makes a changed `njord-config` or secret reach a process: both are read at startup.
    fn restart(&self, deployment: &str) -> Result<()> {
        let namespace = self.env.namespace;
        self.before_change()?;
        let sh = &self.sh;
        self.run(cmd!(
            sh,
            "kubectl --namespace {namespace} rollout restart {deployment}"
        ))
    }

    /// Waits until `deployment`'s pods all come from its current template and are available.
    ///
    /// # Errors
    ///
    /// The rollout is not done after ten minutes. The rollout itself goes on.
    fn await_rollout(&self, deployment: &str) -> Result<()> {
        let namespace = self.env.namespace;
        self.before_change()?;
        let sh = &self.sh;
        self.run(cmd!(
            sh,
            "kubectl --namespace {namespace} rollout status {deployment} --timeout=10m"
        ))
    }

    fn rollout(&self) -> Result<()> {
        self.restart(DEPLOYMENT)?;
        self.await_rollout(DEPLOYMENT)
    }

    fn cycle_ingest(&self) -> Result<()> {
        self.restart(INGEST_DEPLOYMENT)?;
        self.await_rollout(INGEST_DEPLOYMENT)
    }

    fn seed(&self) -> Result<()> {
        let namespace = self.env.namespace;
        self.before_change()?;
        let sh = &self.sh;
        let stamp = cmd!(sh, "date +%Y%m%d%H%M%S").quiet().read()?;
        let job = format!("njord-enc-seed-{stamp}");
        self.run(cmd!(
            sh,
            "kubectl --namespace {namespace} create job {job} --from={ENC_CRONJOB}"
        ))?;
        println!("follow it with: kubectl -n {namespace} logs -f -l job-name={job}");
        Ok(())
    }

    fn verify(&self) -> Result<()> {
        let namespace = self.env.namespace;
        self.before_change()?;
        let sh = &self.sh;
        self.run(cmd!(
            sh,
            "kubectl --namespace {namespace} get pods,pvc,certificate,ingress"
        ))?;
        if !self.execute {
            let url = self.env.version_url();
            println!(
                "[dry-run] + curl -fsS {url}   # gitHash must match the image of {DEPLOYMENT}"
            );
            return Ok(());
        }
        let image = self.deployed_image()?;
        self.expect_served_hash(self.env, &image, 1)
    }

    /// Checks that `environment` answers `/v1/about/version` with the git hash in `image`'s tag,
    /// trying up to `attempts` times five seconds apart. This is the proof that the pods serving
    /// the host run that build. An image whose tag names no commit has nothing to compare, so its
    /// answer is only printed.
    ///
    /// # Errors
    ///
    /// The host never answered, or never with that git hash.
    fn expect_served_hash(
        &self,
        environment: &Environment,
        image: &Image,
        attempts: u32,
    ) -> Result<()> {
        let sh = &self.sh;
        let url = environment.version_url();
        let Some(expected) = image.git_hash() else {
            let body = cmd!(sh, "curl -fsS --max-time 15 {url}").read()?;
            println!("{body}");
            println!("xtask: {image} names no commit, so the served version is not compared");
            return Ok(());
        };
        let mut last = String::new();
        for attempt in 1..=attempts {
            let answer = cmd!(sh, "curl -fsS --max-time 15 {url}")
                .quiet()
                .ignore_stderr()
                .read();
            match answer {
                Ok(body) if served_hash(&body).as_deref() == Some(expected) => {
                    println!("{url} serves git hash {expected}");
                    return Ok(());
                }
                Ok(body) => last = body,
                Err(error) => last = error.to_string(),
            }
            if attempt < attempts {
                std::thread::sleep(Duration::from_secs(5));
            }
        }
        bail!("{url} does not serve git hash {expected} of {image}; last answer: {last}")
    }

    /// Gives the environment `manifests`' image: applies the quota manifest if there is one, the
    /// chart manifest and, if they are installed, the CronJobs, waits until the chart server and
    /// the ingest run the new pods, and checks the served version. The steps `deploy` and
    /// `promote` share.
    ///
    /// Applying a new image replaces the pods by itself. When the environment already runs the
    /// image, both Deployments are restarted instead, so a release always ends with pods that
    /// started from what was just applied.
    fn release(&self, manifests: &Manifests) -> Result<()> {
        // Render everything first, so a manifest that fails its checks stops the release before
        // anything is applied.
        manifests.chart()?;
        manifests.cron()?;
        self.env.quota()?;
        // Read before the apply, which makes this true whatever ran before.
        let same_image = self.running_image(self.env)?.as_ref() == Some(&manifests.image);
        self.apply_quota()?;
        self.apply_chart(manifests)?;
        let namespace = self.env.namespace;
        if self.cron_installed()? {
            self.apply_cron(manifests)?;
        } else {
            println!(
                "xtask: {ENC_CRONJOB} is not installed in {namespace}; leaving the CronJobs out \
                 (install them with `apply cron`)"
            );
        }
        for deployment in [DEPLOYMENT, INGEST_DEPLOYMENT] {
            if same_image {
                self.restart(deployment)?;
            }
            self.await_rollout(deployment)?;
        }
        if self.execute {
            self.expect_served_hash(self.env, &manifests.image, VERSION_ATTEMPTS)
        } else {
            let url = self.env.version_url();
            let hash = manifests.image.git_hash().unwrap_or("?");
            println!("[dry-run] + curl -fsS {url}   # until it serves git hash {hash}");
            Ok(())
        }
    }

    /// Builds an image from the checked-out commit and releases it to staging.
    ///
    /// # Errors
    ///
    /// The environment is production, which only takes an image through
    /// [`promote`](Self::promote).
    fn deploy(&self) -> Result<()> {
        if !self.env.is(&STAGING) {
            bail!(
                "deploy builds a new image, and only staging takes one. Give production the \
                 image staging runs with `cargo xtask --env production promote`"
            );
        }
        let manifests = Manifests::new(self.env, self.head_image()?);
        // Check everything up front so a bad manifest fails before the image is built.
        manifests.chart()?;
        manifests.cron()?;
        self.env.quota()?;
        self.build(&manifests.image)?;
        self.push(&manifests.image)?;
        self.release(&manifests)
    }

    /// Releases to production the image staging runs, or the image `tag` names.
    ///
    /// # Errors
    ///
    /// The environment is not production; nothing is deployed in staging; staging's host does not
    /// serve the build its Deployment names; or the image was built from a dirty working tree.
    fn promote(&self, tag: Option<String>) -> Result<()> {
        if !self.env.is(&PRODUCTION) {
            bail!(
                "promote gives production the image staging runs: pass --env production \
                 (staging takes a new image with `cargo xtask deploy`)"
            );
        }
        let image = match tag {
            Some(tag) => Image::new(image_repo()?, tag)?,
            None => {
                let staging = STAGING.namespace;
                let image = self.running_image(&STAGING)?.ok_or_else(|| {
                    anyhow!("{DEPLOYMENT} does not exist in {staging}: there is nothing to promote")
                })?;
                if image.git_hash().is_none() {
                    bail!(
                        "staging runs {image}, whose tag names no commit: deploy to staging first"
                    );
                }
                self.expect_served_hash(&STAGING, &image, 1)?;
                image
            }
        };
        if image.is_dirty() {
            bail!(
                "{image} was built from a working tree with uncommitted changes: commit, deploy to staging, then promote"
            );
        }
        println!("xtask: promoting {image} to production");
        self.release(&Manifests::new(self.env, image))
    }
}

// ------------------------------------------------------------------------------------------------
// Variables
// ------------------------------------------------------------------------------------------------

/// A non-empty variable, or `default` when it is unset or empty. Without a default an unset or
/// empty variable is an error, and so is the literal string "undefined", so a value that was never
/// really set cannot end up in a manifest as `dbname=undefined`.
fn var(name: &str, default: Option<&str>) -> Result<String> {
    let value = match (optional(name), default) {
        (v, Some(default)) if v.is_empty() => default.to_string(),
        (v, None) if v.is_empty() => {
            bail!("{name} is not set");
        }
        (v, _) => v,
    };
    if value == "undefined" {
        bail!("{name} is the literal string 'undefined'");
    }
    Ok(value)
}

fn optional(name: &str) -> String {
    std::env::var(name).unwrap_or_default()
}

/// The environment's secret variable `<prefix>_<name>`, e.g. `NJORD_STAGING_DB_PASS`.
///
/// # Errors
///
/// It is unset, empty or `undefined`. There is no fallback to the unprefixed `NJORD_<name>`, so
/// one environment's secret is never written into the other; when that variable is set, the error
/// says it is not read.
fn env_var(environment: &Environment, name: &str) -> Result<String> {
    let prefixed = format!("{}_{name}", environment.var_prefix);
    var(&prefixed, None).map_err(|error| {
        let legacy = format!("NJORD_{name}");
        if optional(&legacy).is_empty() {
            error
        } else {
            anyhow!("{error} ({legacy} is set, but it is no longer read)")
        }
    })
}

/// `s` is non-empty, its first byte satisfies `first` and the others `rest`.
fn shaped(s: &str, first: fn(u8) -> bool, rest: fn(u8) -> bool) -> bool {
    let mut bytes = s.bytes();
    bytes.next().is_some_and(first) && bytes.all(rest)
}

fn lower_alnum(b: u8) -> bool {
    b.is_ascii_lowercase() || b.is_ascii_digit()
}

/// `s` with every byte outside RFC 3986 unreserved percent-encoded, for the password in a URL.
fn percent_encode(s: &str) -> String {
    s.bytes()
        .map(|b| match b {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'.' | b'_' | b'~' => {
                char::from(b).to_string()
            }
            _ => format!("%{b:02X}"),
        })
        .collect()
}

// ------------------------------------------------------------------------------------------------
// Images
// ------------------------------------------------------------------------------------------------

/// What a tag carries between the git hash and the build time when the image was built from a
/// working tree with uncommitted changes.
const DIRTY_MARK: &str = "-dirty-";

/// A container image reference, `<repo>:<tag>`. Every workload (chart, ingest, enc-download) of an
/// environment runs the same one.
///
/// A tag built by `cargo xtask` is `<version>-<git hash>`, optionally followed by
/// `-dirty-<UTC timestamp>`, and is never pushed twice with different contents.
#[derive(Clone, Debug, PartialEq)]
struct Image {
    repo: String,
    tag: String,
}

impl Image {
    /// # Errors
    ///
    /// `tag` is not a valid image tag. It is substituted into YAML unquoted.
    fn new(repo: String, tag: String) -> Result<Self> {
        let valid = shaped(
            &tag,
            |b| b.is_ascii_alphanumeric() || b == b'_',
            |b| b.is_ascii_alphanumeric() || b"._-".contains(&b),
        );
        if !valid || tag.len() > 128 {
            bail!("image tag '{tag}' does not match ^[A-Za-z0-9_][A-Za-z0-9._-]{{0,127}}$");
        }
        Ok(Self { repo, tag })
    }

    /// Splits `<repo>:<tag>` as a Deployment holds it.
    ///
    /// # Errors
    ///
    /// The reference has no tag, or names a digest.
    fn parse(reference: &str) -> Result<Self> {
        let name_start = reference.rfind('/').map_or(0, |slash| slash + 1);
        match reference[name_start..].rfind(':') {
            Some(colon) if !reference.contains('@') => {
                let colon = name_start + colon;
                Self::new(
                    reference[..colon].to_string(),
                    reference[colon + 1..].to_string(),
                )
            }
            _ => bail!("image '{reference}' is not <repo>:<tag>"),
        }
    }

    /// The short git hash the tag was built from, or `None` for a tag that names no commit (the
    /// bare `<version>` tags pushed before tags carried one).
    fn git_hash(&self) -> Option<&str> {
        let committed = self.tag.split(DIRTY_MARK).next()?;
        let (_, hash) = committed.rsplit_once('-')?;
        let hex = (7..=40).contains(&hash.len())
            && hash
                .bytes()
                .all(|b| b.is_ascii_digit() || (b'a'..=b'f').contains(&b));
        hex.then_some(hash)
    }

    /// Whether the image was built from a working tree with uncommitted changes, so that no
    /// commit reproduces it.
    fn is_dirty(&self) -> bool {
        self.tag.contains(DIRTY_MARK)
    }
}

impl fmt::Display for Image {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}:{}", self.repo, self.tag)
    }
}

/// The image repository, `NJORD_IMAGE_REPO` or `ghcr.io/manimaul/njord`.
fn image_repo() -> Result<String> {
    let repo = var("NJORD_IMAGE_REPO", Some(IMAGE_REPO))?;
    if repo.is_empty()
        || !repo
            .bytes()
            .all(|b| lower_alnum(b) || b".:/_-".contains(&b))
    {
        bail!("NJORD_IMAGE_REPO='{repo}' does not match ^[a-z0-9.:/_-]+$");
    }
    Ok(repo)
}

/// The `gitHash` field of a `/v1/about/version` response body.
fn served_hash(body: &str) -> Option<String> {
    let version: serde_json::Value = serde_json::from_str(body).ok()?;
    version.get("gitHash")?.as_str().map(str::to_owned)
}

// ------------------------------------------------------------------------------------------------
// Manifest rendering
// ------------------------------------------------------------------------------------------------

/// The manifests of one environment, rendered with one image.
struct Manifests {
    env: &'static Environment,
    image: Image,
}

impl Manifests {
    fn new(env: &'static Environment, image: Image) -> Self {
        Self { env, image }
    }

    /// The environment's placeholders and the image's, with their values.
    fn placeholders(&self) -> Vec<(&'static str, Option<String>)> {
        let mut placeholders = self.env.placeholders();
        placeholders.push(("${NJORD_IMAGE}", Some(self.image.to_string())));
        placeholders
    }

    /// `chart_server.yaml`, rendered.
    ///
    /// # Errors
    ///
    /// As [`render`].
    fn chart(&self) -> Result<String> {
        render(self.env, "chart_server.yaml", &self.placeholders())
    }

    /// `noaa_enc_daily_cron.yaml` followed by `walk_tiles_cronjob.yaml`, rendered.
    ///
    /// # Errors
    ///
    /// As [`render`].
    fn cron(&self) -> Result<String> {
        let placeholders = self.placeholders();
        Ok(render(self.env, "noaa_enc_daily_cron.yaml", &placeholders)?
            + &render(self.env, "walk_tiles_cronjob.yaml", &placeholders)?)
    }
}

/// The manifest `file` of `deploy/k8s/` for `environment`, with `placeholders` filled.
///
/// # Errors
///
/// The file cannot be read, or the result fails [`check_isolation`].
fn render(
    environment: &Environment,
    file: &str,
    placeholders: &[(&'static str, Option<String>)],
) -> Result<String> {
    let text = fill(file, placeholders)?;
    check_isolation(environment, &text)
        .with_context(|| format!("{file} for {}", environment.name))?;
    Ok(text)
}

/// The manifest `file` with each placeholder replaced by its value. A placeholder without a
/// value takes its whole line with it: that is how production's pods come to name no
/// PriorityClass. Nothing else is substituted (the relay's `$(HOST_IP)`, the Python in
/// `walk_tiles_cronjob.yaml`).
fn fill(file: &str, placeholders: &[(&'static str, Option<String>)]) -> Result<String> {
    let path = Path::new(MANIFEST_DIR).join(file);
    let mut text = std::fs::read_to_string(repo_root().join(&path))
        .with_context(|| format!("reading {}", path.display()))?;
    for (placeholder, value) in placeholders {
        text = match value {
            Some(value) => text.replace(placeholder, value),
            None => text
                .split_inclusive('\n')
                .filter(|line| !line.contains(placeholder))
                .collect(),
        };
    }
    if !text.ends_with('\n') {
        text.push('\n');
    }
    Ok(text)
}

/// Refuses a rendered manifest that would let `environment` collide with, or reach into, another
/// environment: one that names another environment's namespace, host, database, database role,
/// relay port, StorageClass, PriorityClass or cron schedule, or that still holds an unfilled
/// `${NJORD_...}` placeholder.
///
/// This is what catches a value written into a manifest as a literal where a placeholder belongs.
/// Values are compared whole, because `staging-njord.mxmariner.com` contains
/// `njord.mxmariner.com` and `njord_staging` contains `njord`.
///
/// # Errors
///
/// Names the first thing found that belongs to another environment.
fn check_isolation(environment: &Environment, text: &str) -> Result<()> {
    if let Some(start) = text.find("${NJORD_") {
        let rest = &text[start..];
        let end = rest.find('}').map_or(rest.len(), |brace| brace + 1);
        bail!("unfilled placeholder {}", &rest[..end]);
    }
    for other in ENVIRONMENTS.into_iter().filter(|o| !o.is(environment)) {
        let (port, hex) = (other.relay_port, other.relay_port_hex());
        let (role, name) = (other.db_role, other.name);
        let leaks = [
            ("namespace", line_is(text, "namespace", other.namespace)),
            ("host", names_host(text, other.host)),
            ("database", text.contains(&format!("/{}?", other.db))),
            (
                "database role",
                [
                    format!("postgresql://{role}@"),
                    format!("postgresql://{role}:"),
                ]
                .iter()
                .any(|url| text.contains(url.as_str())),
            ),
            (
                "relay port",
                text.contains(&format!("TCP4-LISTEN:{port},"))
                    || text.contains(&format!("':{hex}'"))
                    || line_is(text, "targetPort", &port.to_string())
                    || line_is(text, "- containerPort", &port.to_string()),
            ),
            (
                "StorageClass",
                line_is(text, "storageClassName", other.storage_class),
            ),
            (
                "PriorityClass",
                other
                    .priority_class
                    .is_some_and(|class| line_is(text, "priorityClassName", class)),
            ),
            (
                // The two environments' nightly jobs must not load the shared database server
                // at the same time.
                "cron schedule",
                [other.enc_schedule, other.walk_schedule]
                    .iter()
                    .any(|schedule| line_is(text, "schedule", &format!("\"{schedule}\""))),
            ),
        ];
        if let Some((what, _)) = leaks.into_iter().find(|(_, leaked)| *leaked) {
            bail!("the manifest names {name}'s {what}");
        }
    }
    Ok(())
}

/// Whether some line of `text` is exactly `<key>: <value>`, ignoring indentation.
fn line_is(text: &str, key: &str, value: &str) -> bool {
    text.lines()
        .filter_map(|line| line.trim().strip_prefix(key))
        .filter_map(|rest| rest.strip_prefix(": "))
        .any(|found| found == value)
}

/// Whether `text` names `host` as a whole host name, not as part of a longer one.
fn names_host(text: &str, host: &str) -> bool {
    let part_of_host = |c: char| c.is_ascii_alphanumeric() || c == '.' || c == '-';
    text.match_indices(host).any(|(start, _)| {
        let before = text[..start].chars().next_back();
        let after = text[start + host.len()..].chars().next();
        !before.is_some_and(part_of_host) && !after.is_some_and(part_of_host)
    })
}

// ------------------------------------------------------------------------------------------------
// Secrets
// ------------------------------------------------------------------------------------------------

/// The values of an environment's secrets manifest, read from its `<prefix>_*` variables.
struct Secrets {
    env: &'static Environment,
    db_host: String,
    db_port: String,
    db_pass: String,
    admin_key: String,
    admin_user: String,
    admin_pass: String,
}

impl Secrets {
    fn from_env(env: &'static Environment) -> Result<Self> {
        let secret = |name: &str| -> Result<String> {
            let value = env_var(env, name)?;
            if value.chars().any(char::is_control) {
                bail!("{}_{name} contains control characters", env.var_prefix);
            }
            Ok(value)
        };
        // These two are substituted into YAML unquoted, so they must be plain tokens.
        let token = |name: &str, pattern: &str, valid: fn(&str) -> bool| -> Result<String> {
            let value = env_var(env, name)?;
            if !valid(&value) {
                bail!(
                    "{}_{name}='{value}' does not match {pattern}",
                    env.var_prefix
                );
            }
            Ok(value)
        };
        Ok(Self {
            env,
            db_host: token("DB_HOST", "^[A-Za-z0-9.-]+$", |s| {
                !s.is_empty()
                    && s.bytes()
                        .all(|b| b.is_ascii_alphanumeric() || b == b'.' || b == b'-')
            })?,
            db_port: token("DB_PORT", "^[0-9]{1,5}$", |s| {
                (1..=5).contains(&s.len()) && s.bytes().all(|b| b.is_ascii_digit())
            })?,
            db_pass: secret("DB_PASS")?,
            admin_key: secret("ADMIN_KEY")?,
            admin_user: secret("ADMIN_USER")?,
            admin_pass: secret("ADMIN_PASS")?,
        })
    }

    /// The secrets manifest, with the secret values replaced by `<redacted>` when `mask` is set.
    fn yaml(&self, mask: bool) -> Result<String> {
        let masked = |value: &str| {
            if mask {
                "<redacted>".to_string()
            } else {
                value.to_string()
            }
        };
        let (db_pass, admin_key, admin_pass) = (
            masked(&percent_encode(&self.db_pass)),
            masked(&self.admin_key),
            masked(&self.admin_pass),
        );
        let Environment {
            namespace: ns,
            db,
            db_role: user,
            ..
        } = self.env;
        let Self {
            db_host,
            db_port,
            admin_user,
            ..
        } = self;
        let json = |s: &str| s.replace('\\', "\\\\").replace('"', "\\\"");
        let (json_key, json_user, json_pass) =
            (json(&admin_key), json(admin_user), json(&admin_pass));
        // The pods connect to their own namespace's njord-db-relay Service (port 6432, whatever
        // the relay's host port), not the database directly - see the DaemonSet in
        // chart_server.yaml. sqlx pools in each process (MAX_DB_CONNECTIONS), so there is no
        // pgbouncer sidecar. The database and the role are the environment's, not variables.
        let url = format!("postgresql://{user}:{db_pass}@njord-db-relay:6432/{db}?sslmode=require");
        let text = format!(
            r#"---
# Not a secret: the database endpoint for the njord-db-relay DaemonSet, from the same
# DB_HOST/DB_PORT variables. Changing it does not restart the DaemonSet - cycle the relay pods.
apiVersion: v1
kind: ConfigMap
metadata:
  name: njord-db-upstream
  namespace: {ns}
data:
  DB_HOST: "{db_host}"
  DB_PORT: "{db_port}"
---
# CHART_SERVER_OPTS for njord-chart-dep and njord-ingest-rs.
apiVersion: v1
kind: Secret
metadata:
  name: admin-secret-json
  namespace: {ns}
type: Opaque
stringData:
  chart_server_opts: |
    {{
    "adminKey": "{json_key}",
    "adminUser": "{json_user}",
    "adminPass": "{json_pass}",
    "pgConnectionInfo": "{url}"
    }}
---
# Basic credentials for njord-enc-download's orphan deletes.
apiVersion: v1
kind: Secret
metadata:
  name: njord-admin-basic
  namespace: {ns}
type: Opaque
stringData:
  admin_user: |-
    {admin_user}
  admin_pass: |-
    {admin_pass}
"#
        );
        Ok(text)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn image(tag: &str) -> Image {
        Image::new(IMAGE_REPO.into(), tag.into()).unwrap()
    }

    fn manifests(env: &'static Environment) -> Manifests {
        Manifests::new(env, image("2.0.0-44dfc867"))
    }

    /// Every manifest of `env`, with its placeholders filled and no isolation check.
    fn filled(env: &'static Environment) -> Vec<(&'static str, String)> {
        let placeholders = manifests(env).placeholders();
        [
            "chart_server.yaml",
            "noaa_enc_daily_cron.yaml",
            "walk_tiles_cronjob.yaml",
        ]
        .into_iter()
        .chain(env.quota_manifest)
        .map(|file| (file, fill(file, &placeholders).unwrap()))
        .collect()
    }

    /// How many lines of `text` are exactly `<key>: <value>`, ignoring indentation.
    fn count_lines(text: &str, key: &str, value: &str) -> usize {
        let line = format!("{key}: {value}");
        text.lines().filter(|l| l.trim() == line).count()
    }

    #[test]
    fn every_manifest_renders_for_every_environment() {
        for env in ENVIRONMENTS {
            let m = manifests(env);
            let chart = m.chart().unwrap();
            m.cron().unwrap();
            env.quota().unwrap();
            assert!(chart.contains("image: ghcr.io/manimaul/njord:2.0.0-44dfc867\n"));
            assert!(line_is(&chart, "namespace", env.namespace));
            assert!(line_is(&chart, "storageClassName", env.storage_class));
            assert!(names_host(&chart, env.host));
        }
    }

    #[test]
    fn staging_is_capped_and_production_is_not() {
        let chart = manifests(&STAGING).chart().unwrap();
        let cron = manifests(&STAGING).cron().unwrap();
        // Request = limit on the ingest, one worker, one chart pod.
        assert_eq!(count_lines(&chart, "memory", "512Mi"), 3);
        assert_eq!(count_lines(&chart, "memory", "128Mi"), 1);
        assert!(chart.contains("\"chartIngestWorkers\": 1,\n"));
        assert!(line_is(&chart, "maxReplicas", "1"));
        // The relay, the chart server and the ingest; the two CronJobs.
        assert_eq!(count_lines(&chart, "priorityClassName", "njord-staging"), 3);
        assert_eq!(count_lines(&cron, "priorityClassName", "njord-staging"), 2);
        assert!(line_is(&cron, "schedule", "\"0 4 * * *\""));
        assert!(line_is(&cron, "schedule", "\"0 5 * * *\""));
        let quota = STAGING.quota().unwrap().unwrap();
        assert!(quota.contains("kind: PriorityClass\nmetadata:\n  name: njord-staging\n"));
        assert!(quota.contains("kind: ResourceQuota\n"));
        assert!(line_is(&quota, "namespace", "mxstaging"));

        let chart = manifests(&PRODUCTION).chart().unwrap();
        let cron = manifests(&PRODUCTION).cron().unwrap();
        assert_eq!(count_lines(&chart, "memory", "1Gi"), 2);
        assert!(line_is(&chart, "memory", "1536Mi") && line_is(&chart, "memory", "512Mi"));
        assert!(chart.contains("\"chartIngestWorkers\": 3,\n"));
        assert!(line_is(&chart, "maxReplicas", "5"));
        assert!(!chart.contains("priorityClassName") && !cron.contains("priorityClassName"));
        assert!(line_is(&cron, "schedule", "\"0 2 * * *\""));
        assert!(line_is(&cron, "schedule", "\"0 3 * * *\""));
        assert!(PRODUCTION.quota().unwrap().is_none());
    }

    #[test]
    fn a_rollout_waits_for_pods_that_work() {
        for env in ENVIRONMENTS {
            let chart = manifests(env).chart().unwrap();
            let cron = manifests(env).cron().unwrap();
            // A tag names one build, so nothing pulls an image a node already holds.
            assert_eq!(count_lines(&chart, "imagePullPolicy", "IfNotPresent"), 2);
            assert!(line_is(&cron, "imagePullPolicy", "IfNotPresent"));
            assert!(!chart.contains("Always") && !cron.contains("Always"));
            // The ingest is a Deployment that never runs two pods, and the chart server is
            // routed to only once it answers.
            assert!(!chart.contains("kind: ReplicaSet"));
            assert!(chart.contains("kind: Deployment\nmetadata:\n  name: njord-ingest-rs\n"));
            assert!(line_is(&chart, "type", "Recreate"));
            assert!(chart.contains(
                "readinessProbe:\n            httpGet:\n              path: /v1/about/version\n"
            ));
        }
    }

    #[test]
    fn each_environment_has_its_own_relay_port_database_and_volume() {
        let production = manifests(&PRODUCTION).chart().unwrap();
        assert!(production.contains("- TCP4-LISTEN:6433,bind=$(HOST_IP),fork,reuseaddr\n"));
        assert!(production.contains("targetPort: 6433\n"));
        assert!(production.contains("grep -q ':1921' /proc/net/tcp"));
        assert!(production.contains("postgresql://njord@njord-db-relay:6432/njord?sslmode"));
        assert!(production.contains("storageClassName: njord-nfs\n"));

        let staging = manifests(&STAGING).chart().unwrap();
        assert!(staging.contains("- TCP4-LISTEN:6434,bind=$(HOST_IP),fork,reuseaddr\n"));
        assert!(staging.contains("targetPort: 6434\n"));
        assert!(staging.contains("grep -q ':1922' /proc/net/tcp"));
        assert!(
            staging
                .contains("postgresql://njord_staging@njord-db-relay:6432/njord_staging?sslmode")
        );
        assert!(staging.contains("storageClassName: njord-staging-nfs\n"));
    }

    #[test]
    fn no_two_environments_share_a_setting() {
        let (a, b) = (&STAGING, &PRODUCTION);
        assert_ne!(a.name, b.name);
        assert_ne!(a.var_prefix, b.var_prefix);
        assert_ne!(a.namespace, b.namespace);
        assert_ne!(a.host, b.host);
        assert_ne!(a.db, b.db);
        assert_ne!(a.db_role, b.db_role);
        assert_ne!(a.relay_port, b.relay_port);
        assert_ne!(a.nfs_release, b.nfs_release);
        assert_ne!(a.storage_class, b.storage_class);
        assert_ne!(a.priority_class, b.priority_class);
        let schedules = [
            a.enc_schedule,
            a.walk_schedule,
            b.enc_schedule,
            b.walk_schedule,
        ];
        for (i, schedule) in schedules.iter().enumerate() {
            assert!(!schedules[..i].contains(schedule), "{schedule}");
        }
    }

    #[test]
    fn a_manifest_of_one_environment_is_refused_for_the_other() {
        for (env, other) in [(&STAGING, &PRODUCTION), (&PRODUCTION, &STAGING)] {
            for (file, text) in filled(other) {
                assert!(check_isolation(other, &text).is_ok(), "{file}");
                assert!(
                    check_isolation(env, &text).is_err(),
                    "{file} of {}",
                    other.name
                );
            }
        }
    }

    #[test]
    fn each_leaked_setting_is_refused_by_name() {
        let refused =
            |env: &Environment, text: &str| check_isolation(env, text).unwrap_err().to_string();
        // Production's values in a staging manifest, and the reverse.
        for (env, other) in [(&STAGING, &PRODUCTION), (&PRODUCTION, &STAGING)] {
            let (port, hex) = (other.relay_port, other.relay_port_hex());
            let cases = [
                (format!("  namespace: {}\n", other.namespace), "namespace"),
                (format!("    - host: {}\n", other.host), "host"),
                (
                    format!("njord-db-relay:6432/{}?sslmode=require", other.db),
                    "database",
                ),
                (
                    format!("postgresql://{}@njord-db-relay", other.db_role),
                    "database role",
                ),
                (format!("- TCP4-LISTEN:{port},bind=x\n"), "relay port"),
                (format!("      targetPort: {port}\n"), "relay port"),
                (
                    format!("            - containerPort: {port}\n"),
                    "relay port",
                ),
                (format!("grep -q ':{hex}' /proc/net/tcp"), "relay port"),
                (
                    format!("  storageClassName: {}\n", other.storage_class),
                    "StorageClass",
                ),
                (
                    format!("  schedule: \"{}\"\n", other.enc_schedule),
                    "cron schedule",
                ),
                (
                    format!("  schedule: \"{}\"\n", other.walk_schedule),
                    "cron schedule",
                ),
            ];
            for (text, what) in cases {
                let expected = format!("the manifest names {}'s {what}", other.name);
                assert_eq!(refused(env, &text), expected, "{text}");
            }
        }
        assert_eq!(
            refused(&PRODUCTION, "      priorityClassName: njord-staging\n"),
            "the manifest names staging's PriorityClass"
        );
        assert_eq!(
            refused(&STAGING, "host: ${NJORD_HOSTNAME}\n"),
            "unfilled placeholder ${NJORD_HOSTNAME}"
        );
    }

    #[test]
    fn a_value_inside_a_longer_one_is_not_a_leak() {
        // staging-njord.mxmariner.com contains production's host, and njord_staging its database.
        let staging = "host: staging-njord.mxmariner.com\n\
                       url: postgresql://njord_staging@njord-db-relay:6432/njord_staging?sslmode\n\
                       storageClassName: njord-staging-nfs\n";
        check_isolation(&STAGING, staging).unwrap();
        assert!(!names_host("x.njord.mxmariner.com", "njord.mxmariner.com"));
        assert!(!names_host(
            "njord.mxmariner.com.example",
            "njord.mxmariner.com"
        ));
        assert!(names_host(
            "https://njord.mxmariner.com/v1",
            "njord.mxmariner.com"
        ));
        assert!(line_is(
            "  namespace: mxmariner\n",
            "namespace",
            "mxmariner"
        ));
        assert!(!line_is(
            "  namespace: mxmariner2\n",
            "namespace",
            "mxmariner"
        ));
    }

    #[test]
    fn an_image_tag_names_its_commit() {
        let built = image("2.0.0-44dfc867");
        assert_eq!(built.to_string(), "ghcr.io/manimaul/njord:2.0.0-44dfc867");
        assert_eq!(built.git_hash(), Some("44dfc867"));
        assert!(!built.is_dirty());

        let dirty = image("2.0.0-44dfc867-dirty-20261122101500");
        assert_eq!(dirty.git_hash(), Some("44dfc867"));
        assert!(dirty.is_dirty());

        // A pre-release version keeps its own hyphen; the hash is the last part.
        assert_eq!(image("2.1.0-rc.1-0a1b2c3").git_hash(), Some("0a1b2c3"));
        // The tags pushed before tags carried a hash.
        assert_eq!(image("2.0.0").git_hash(), None);
        assert_eq!(image("2.0.0-rust").git_hash(), None);
    }

    #[test]
    fn an_image_reference_is_split_at_its_tag() {
        assert_eq!(
            Image::parse("ghcr.io/manimaul/njord:2.0.0").unwrap(),
            image("2.0.0")
        );
        let with_port = Image::parse("localhost:5000/njord:2.0.0-44dfc867").unwrap();
        assert_eq!(with_port.repo, "localhost:5000/njord");
        assert_eq!(with_port.tag, "2.0.0-44dfc867");
        assert!(Image::parse("localhost:5000/njord").is_err());
        assert!(Image::parse("ghcr.io/manimaul/njord@sha256:abc").is_err());
        assert!(Image::new(IMAGE_REPO.into(), "bad tag".into()).is_err());
    }

    #[test]
    fn the_served_hash_is_read_from_the_version_response() {
        let body = r#"{"version":"2.0.0-rust","gdalVersion":"3.10.3","gitHash":"44dfc867","gitBranch":"master"}"#;
        assert_eq!(served_hash(body).as_deref(), Some("44dfc867"));
        assert_eq!(served_hash(r#"{"version":"2.0.0"}"#), None);
        assert_eq!(served_hash("<html>503</html>"), None);
    }

    #[test]
    fn secrets_are_escaped_and_masked() {
        let secrets = Secrets {
            env: &STAGING,
            db_host: "db.example".into(),
            db_port: "5432".into(),
            db_pass: r#"p"w"#.into(),
            admin_key: r#"k\"#.into(),
            admin_user: "admin".into(),
            admin_pass: "secret".into(),
        };
        let real = secrets.yaml(false).unwrap();
        let url =
            "postgresql://njord_staging:p%22w@njord-db-relay:6432/njord_staging?sslmode=require";
        assert!(real.contains(&format!(r#""pgConnectionInfo": "{url}""#)));
        assert!(real.contains(r#""adminKey": "k\\","#));
        assert!(real.contains("  namespace: mxstaging\n") && !real.contains("mxmariner"));
        let masked = secrets.yaml(true).unwrap();
        assert!(!masked.contains("secret\n") && !masked.contains("p%22w"));
        assert!(masked.contains("postgresql://njord_staging:<redacted>@njord-db-relay"));
        assert!(masked.contains(r#""adminPass": "<redacted>""#));
    }
}
