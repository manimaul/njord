//! The `cargo xtask` tasks: the container image and the Kubernetes deploy of the Rust environment in
//! the `mxmariner` namespace (docs/RUST_PORT_PLAN.md §10, deploy/mxmariner/README.md).
//!
//! The `xtask` alias is in `.cargo/config.toml`, and every command runs from the repository root.
//! Only the NEW environment is deployed from here. The Kotlin `njord` namespace is deployed from the
//! master branch's `./gradlew :deploy` and `k8s_deploy/`, and is never touched.
//!
//! DRY RUN BY DEFAULT: every task prints what it would do. Nothing touches the cluster, the registry
//! or the local image store unless `--execute` is passed.
//!
//! The manifests in `deploy/mxmariner/` carry `${NJORD_NAMESPACE}`, `${NJORD_DB}`, `${NJORD_HOST}`
//! and `${NJORD_IMAGE}` placeholders. They are filled from the environment and piped to
//! `kubectl apply -f -`, so a filled manifest never touches the disk.

use anyhow::{Context, Result, bail};
use clap::{Parser, Subcommand, ValueEnum};
use std::cell::Cell;
use std::path::{Path, PathBuf};
use xshell::{Cmd, Shell, cmd};

const IMAGE_REPO: &str = "ghcr.io/manimaul/njord";
const HOST: &str = "njord.mxmariner.com";
const PLATFORM: &str = "linux/amd64";
const NFS_SIZE: &str = "20Gi";
const DEPLOYMENT: &str = "deployment/njord-chart-dep";
const MANIFEST_DIR: &str = "deploy/mxmariner";

#[derive(Parser)]
#[command(
    name = "cargo xtask",
    bin_name = "cargo xtask",
    about = "njord image and deploy tasks (dry run unless --execute)",
    after_help = ENV_HELP
)]
struct Cli {
    /// Run the commands. Without it every task only prints what it would do (and the rendered
    /// manifests, secrets masked).
    #[arg(long, global = true)]
    execute: bool,
    #[command(subcommand)]
    task: Task,
}

const ENV_HELP: &str = "\
Required environment (the task aborts if any it needs is unset, empty or the literal 'undefined'):
  NJORD_NAMESPACE                                       (mxmariner; \"njord\" is refused)
  NJORD_DB                                              (njord; NJORD_DB_NAME is NOT read)
  NJORD_DB_HOST NJORD_DB_PORT NJORD_DB_USER NJORD_DB_PASS                    (secrets)
  NJORD_ADMIN_KEY NJORD_ADMIN_USER NJORD_ADMIN_PASS                          (secrets)

Optional, with defaults:
  NJORD_IMAGE_REPO=ghcr.io/manimaul/njord     Rust image repository (njord-chart-server refused)
  NJORD_VERSION=<workspace version in Cargo.toml>
  NJORD_HOST=njord.mxmariner.com
  NJORD_PLATFORM=linux/amd64                  podman build --platform
  NJORD_NFS_SIZE=20Gi                         NFS provisioner disk, at least 20Gi
  NJORD_KUBE_CONTEXT=                         if set, --execute refuses any other current context";

#[derive(Subcommand)]
enum Task {
    /// Apply njord-db-upstream, njord-db-url, admin-secret-json and njord-admin-basic
    /// (./gradlew :secret).
    Secrets,
    /// Install the Helm release njord-nfs with the StorageClass njord-nfs (create_nfs.sh).
    Nfs,
    /// Build the container image $NJORD_IMAGE_REPO:$NJORD_VERSION from Containerfile (./gradlew :makeImg).
    Build,
    /// Push the image; needs `podman login ghcr.io` (./gradlew :pubImg).
    Push,
    /// Render manifests and `kubectl apply` them (./gradlew :k8sApply).
    Apply {
        #[arg(value_enum, default_value_t = Apply::Chart)]
        what: Apply,
    },
    /// Restart deployment/njord-chart-dep and wait for the rollout (./gradlew :rolloutRestart).
    Rollout,
    /// Delete the njord-ingest-rs pod so it pulls the image again (./gradlew :cycleIngestPod).
    CycleIngest,
    /// Create a one-off Job from cronjob/njord-enc-download.
    Seed,
    /// `kubectl get pods,pvc,certificate,ingress` and `curl /v1/about/version`.
    Verify,
    /// build, push, apply chart, wait 5s, rollout (./gradlew :deploy).
    Deploy,
    /// Print a rendered manifest to stdout. Never touches anything (./gradlew :showSecret).
    Render {
        #[arg(value_enum)]
        what: Render,
        /// Print the secret values instead of masking them.
        #[arg(long)]
        show_secrets: bool,
    },
}

#[derive(Clone, Copy, ValueEnum)]
enum Apply {
    /// chart_server.yaml
    Chart,
    /// noaa_enc_daily_cron.yaml and walk_tiles_cronjob.yaml
    Cron,
    /// vacuum_job.yaml
    Vacuum,
    /// chart and cron
    All,
}

#[derive(Clone, Copy, ValueEnum)]
enum Render {
    Chart,
    Cron,
    Vacuum,
    Secrets,
}

fn main() -> Result<()> {
    let cli = Cli::parse();
    let sh = Shell::new()?;
    sh.change_dir(repo_root());
    let x = Xtask {
        sh,
        execute: cli.execute,
        kube_checked: Cell::new(false),
    };
    if !x.execute && !matches!(cli.task, Task::Render { .. }) {
        eprintln!("xtask: DRY RUN: nothing will be executed. Re-run with --execute to act.");
    }
    match cli.task {
        Task::Secrets => x.secrets(),
        Task::Nfs => x.nfs(),
        Task::Build => x.build(),
        Task::Push => x.push(),
        Task::Apply { what } => x.apply(&Manifests::from_env()?, what),
        Task::Rollout => x.rollout(),
        Task::CycleIngest => x.cycle_ingest(),
        Task::Seed => x.seed(),
        Task::Verify => x.verify(),
        Task::Deploy => x.deploy(),
        Task::Render { what, show_secrets } => {
            let text = match what {
                Render::Chart => Manifests::from_env()?.chart()?,
                Render::Cron => Manifests::from_env()?.cron()?,
                Render::Vacuum => Manifests::from_env()?.vacuum()?,
                Render::Secrets => Secrets::from_env()?.yaml(!show_secrets)?,
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
    kube_checked: Cell<bool>,
}

impl Xtask {
    /// Runs a command, or in a dry run prints it.
    fn run(&self, cmd: Cmd<'_>) -> Result<()> {
        if self.execute {
            cmd.run()?;
        } else {
            println!("[dry-run] + {cmd}");
        }
        Ok(())
    }

    /// Feeds a rendered manifest to `kubectl apply`. A dry run prints the masked manifest instead.
    fn kubectl_apply(&self, namespace: &str, label: &str, real: &str, masked: &str) -> Result<()> {
        let sh = &self.sh;
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

    /// Before anything reaches the cluster, makes sure kubectl and helm point where the user
    /// expects. Reads the local kubeconfig only.
    fn kube_preflight(&self, namespace: &str) -> Result<()> {
        if !self.execute || self.kube_checked.get() {
            return Ok(());
        }
        let sh = &self.sh;
        let context = cmd!(sh, "kubectl config current-context")
            .quiet()
            .read()
            .context("kubectl config current-context")?;
        eprintln!("xtask: kubectl context: {context}, namespace: {namespace}");
        let expected = optional("NJORD_KUBE_CONTEXT");
        if !expected.is_empty() && context != expected {
            bail!("current context {context} is not NJORD_KUBE_CONTEXT={expected}");
        }
        self.kube_checked.set(true);
        Ok(())
    }

    fn secrets(&self) -> Result<()> {
        let secrets = Secrets::from_env()?;
        self.kube_preflight(&secrets.namespace)?;
        self.kubectl_apply(
            &secrets.namespace,
            "secrets (njord-db-upstream, njord-db-url, admin-secret-json, njord-admin-basic)",
            &secrets.yaml(false)?,
            &secrets.yaml(true)?,
        )
    }

    fn nfs(&self) -> Result<()> {
        let namespace = namespace()?;
        let size = var("NJORD_NFS_SIZE", Some(NFS_SIZE))?;
        let gib: u32 = size
            .strip_suffix("Gi")
            .and_then(|n| n.parse().ok())
            .with_context(|| format!("NJORD_NFS_SIZE must be <n>Gi, got {size}"))?;
        if gib < 20 {
            bail!("NJORD_NFS_SIZE must be at least 20Gi (§10.2 item 2)");
        }
        self.kube_preflight(&namespace)?;
        let sh = &self.sh;
        self.run(cmd!(
            sh,
            "helm repo add nfs-ganesha-server-and-external-provisioner https://kubernetes-sigs.github.io/nfs-ganesha-server-and-external-provisioner/"
        ))?;
        // Release njord-nfs (not nfs-server): a different provisioner name and a StorageClass that
        // Helm can own, instead of colliding with the njord namespace's nfs-server release and `nfs`
        // class. `helm install` (not upgrade --install) so an existing release is never modified.
        let size_arg = format!("persistence.size={size}");
        self.run(cmd!(
            sh,
            "helm install njord-nfs nfs-ganesha-server-and-external-provisioner/nfs-server-provisioner
                --namespace {namespace}
                --set persistence.enabled=true
                --set persistence.storageClass=linode-block-storage-retain
                --set {size_arg}
                --set storageClass.name=njord-nfs"
        ))
    }

    /// Builds the image and, when executing, checks that it really is for `NJORD_PLATFORM`.
    ///
    /// The build stage has no `.git`, so the git hash, branch and dirty count go in as the `GIT_*`
    /// build arguments (`unknown` when git cannot tell).
    fn build(&self) -> Result<()> {
        let image = image()?;
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

    fn push(&self) -> Result<()> {
        let image = image()?;
        let sh = &self.sh;
        self.run(cmd!(sh, "podman push {image}"))
    }

    fn apply(&self, manifests: &Manifests, what: Apply) -> Result<()> {
        self.kube_preflight(&manifests.namespace)?;
        let apply = |label: &str, text: String| {
            self.kubectl_apply(&manifests.namespace, label, &text, &text)
        };
        const CRON: &str = "noaa_enc_daily_cron.yaml + walk_tiles_cronjob.yaml";
        match what {
            Apply::Chart => apply("chart_server.yaml", manifests.chart()?),
            Apply::Cron => apply(CRON, manifests.cron()?),
            Apply::Vacuum => apply("vacuum_job.yaml", manifests.vacuum()?),
            Apply::All => {
                apply("chart_server.yaml", manifests.chart()?)?;
                apply(CRON, manifests.cron()?)
            }
        }
    }

    fn rollout(&self) -> Result<()> {
        let namespace = namespace()?;
        self.kube_preflight(&namespace)?;
        let sh = &self.sh;
        self.run(cmd!(
            sh,
            "kubectl --namespace {namespace} rollout restart {DEPLOYMENT}"
        ))?;
        self.run(cmd!(
            sh,
            "kubectl --namespace {namespace} rollout status {DEPLOYMENT} --timeout=10m"
        ))
    }

    fn cycle_ingest(&self) -> Result<()> {
        let namespace = namespace()?;
        self.kube_preflight(&namespace)?;
        let sh = &self.sh;
        // A bare ReplicaSet has no rollout; deleting its pod makes it pull the image again.
        self.run(cmd!(
            sh,
            "kubectl --namespace {namespace} delete pods -l app=njord-ingest"
        ))
    }

    fn seed(&self) -> Result<()> {
        let namespace = namespace()?;
        self.kube_preflight(&namespace)?;
        let sh = &self.sh;
        let stamp = cmd!(sh, "date +%Y%m%d%H%M%S").quiet().read()?;
        let job = format!("njord-enc-seed-{stamp}");
        self.run(cmd!(
            sh,
            "kubectl --namespace {namespace} create job {job} --from=cronjob/njord-enc-download"
        ))?;
        println!("follow it with: kubectl -n {namespace} logs -f -l job-name={job}");
        Ok(())
    }

    fn verify(&self) -> Result<()> {
        let namespace = namespace()?;
        let url = format!("https://{}/v1/about/version", host()?);
        self.kube_preflight(&namespace)?;
        let sh = &self.sh;
        self.run(cmd!(
            sh,
            "kubectl --namespace {namespace} get pods,pvc,certificate,ingress"
        ))?;
        self.run(cmd!(sh, "curl -fsS {url}"))
    }

    fn deploy(&self) -> Result<()> {
        // Check everything up front so a bad variable fails before the image is built.
        let manifests = Manifests::from_env()?;
        self.build()?;
        self.push()?;
        self.apply(&manifests, Apply::Chart)?;
        let sh = &self.sh;
        self.run(cmd!(sh, "sleep 5"))?; // holdOn
        self.rollout()
    }
}

// ------------------------------------------------------------------------------------------------
// Environment and safety checks
// ------------------------------------------------------------------------------------------------

/// A non-empty variable, or `default` when it is unset or empty. Without a default an unset or
/// empty variable is an error, and so is the literal string "undefined": Secret.kt fell back to it,
/// so a shell exporting NJORD_DB_NAME but not NJORD_DB would have written dbname=undefined without
/// complaint (§10.3).
fn var(name: &str, default: Option<&str>) -> Result<String> {
    let value = match (optional(name), default) {
        (v, Some(default)) if v.is_empty() => default.to_string(),
        (v, None) if v.is_empty() => {
            if name == "NJORD_DB" && !optional("NJORD_DB_NAME").is_empty() {
                bail!(
                    "NJORD_DB is not set (NJORD_DB_NAME is, but that is the Kotlin environment's variable and is never read here)"
                );
            }
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

/// A variable that is substituted into YAML or a connection URL unquoted, so it must be a plain token.
fn token(
    name: &str,
    default: Option<&str>,
    pattern: &str,
    valid: fn(&str) -> bool,
) -> Result<String> {
    let value = var(name, default)?;
    if !valid(&value) {
        bail!("{name}='{value}' does not match {pattern}");
    }
    Ok(value)
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

fn namespace() -> Result<String> {
    let namespace = token(
        "NJORD_NAMESPACE",
        None,
        "^[a-z0-9]([-a-z0-9]*[a-z0-9])?$",
        |s| shaped(s, lower_alnum, |b| lower_alnum(b) || b == b'-') && !s.ends_with('-'),
    )?;
    if namespace == "njord" {
        bail!("NJORD_NAMESPACE=njord is the Kotlin environment; xtask never touches it (§10)");
    }
    Ok(namespace)
}

/// `^[a-z_][a-z0-9_]*$`: a database or role name.
fn identifier(s: &str) -> bool {
    shaped(
        s,
        |b| b.is_ascii_lowercase() || b == b'_',
        |b| lower_alnum(b) || b == b'_',
    )
}

fn db() -> Result<String> {
    let db = token("NJORD_DB", None, "^[a-z_][a-z0-9_]*$", identifier)?;
    if db == "s57server" {
        bail!("NJORD_DB=s57server is the Kotlin environment's database (§10.1); use njord");
    }
    Ok(db)
}

fn host() -> Result<String> {
    let host = token("NJORD_HOST", Some(HOST), "^[a-z0-9.-]+$", |s| {
        !s.is_empty() && s.bytes().all(|b| lower_alnum(b) || b == b'.' || b == b'-')
    })?;
    if host.ends_with("openenc.com") {
        bail!("NJORD_HOST={host} belongs to the njord namespace");
    }
    Ok(host)
}

/// The Rust image `$NJORD_IMAGE_REPO:$NJORD_VERSION`. Every workload (chart, ingest, enc-download)
/// runs it (§10.4).
fn image() -> Result<String> {
    let repo = token(
        "NJORD_IMAGE_REPO",
        Some(IMAGE_REPO),
        "^[a-z0-9.:/_-]+$",
        |s| !s.is_empty() && s.bytes().all(|b| lower_alnum(b) || b".:/_-".contains(&b)),
    )?;
    let version = token(
        "NJORD_VERSION",
        Some(env!("CARGO_PKG_VERSION")),
        "^[A-Za-z0-9_][A-Za-z0-9._-]*$",
        |s| {
            shaped(
                s,
                |b| b.is_ascii_alphanumeric() || b == b'_',
                |b| b.is_ascii_alphanumeric() || b"._-".contains(&b),
            )
        },
    )?;
    // §10.2 item 1: the njord namespace pulls njord-chart-server with imagePullPolicy: Always.
    if repo.contains("njord-chart-server") {
        bail!(
            "NJORD_IMAGE_REPO={repo}: the Rust image must never go to njord-chart-server (§10.2 item 1)"
        );
    }
    Ok(format!("{repo}:{version}"))
}

/// The last line of defence against the overlaps in §10.2, applied to every rendered manifest.
fn guard(label: &str, text: &str) -> Result<()> {
    let key_is = |line: &str, key: &str, value: &str| {
        line.trim_start()
            .strip_prefix(key)
            .is_some_and(|rest| rest.trim() == value)
    };
    for line in text.lines() {
        let forbidden = if key_is(line, "namespace:", "njord") {
            Some("namespace: njord")
        } else if key_is(line, "storageClassName:", "nfs") {
            Some("storageClassName: nfs")
        } else if line
            .find("image:")
            .is_some_and(|i| line[i..].contains("njord-chart-server"))
        {
            // No workload may run a Kotlin njord-chart-server image.
            Some("a njord-chart-server image")
        } else {
            ["TCP4-LISTEN:6432", "openenc.com", "s57server"]
                .into_iter()
                .chain(PLACEHOLDERS.map(|(p, _)| p))
                .find(|s| line.contains(s))
        };
        if let Some(forbidden) = forbidden {
            bail!("rendered {label} contains {forbidden}: {}", line.trim());
        }
    }
    Ok(())
}

// ------------------------------------------------------------------------------------------------
// Manifest rendering
// ------------------------------------------------------------------------------------------------

/// The placeholders and the `Manifests` field each one is filled from. Only these are substituted;
/// anything else is left alone (shell in the vacuum Job, Python in walk_tiles.py).
const PLACEHOLDERS: [(&str, Field); 4] = [
    ("${NJORD_NAMESPACE}", |m| &m.namespace),
    ("${NJORD_DB}", |m| &m.db),
    ("${NJORD_HOST}", |m| &m.host),
    ("${NJORD_IMAGE}", |m| &m.image),
];

type Field = fn(&Manifests) -> &str;

struct Manifests {
    namespace: String,
    db: String,
    host: String,
    image: String,
}

impl Manifests {
    fn from_env() -> Result<Self> {
        Ok(Self {
            namespace: namespace()?,
            db: db()?,
            host: host()?,
            image: image()?,
        })
    }

    fn chart(&self) -> Result<String> {
        self.render("chart_server.yaml")
    }

    fn cron(&self) -> Result<String> {
        Ok(self.render("noaa_enc_daily_cron.yaml")? + &self.render("walk_tiles_cronjob.yaml")?)
    }

    fn vacuum(&self) -> Result<String> {
        self.render("vacuum_job.yaml")
    }

    fn render(&self, file: &str) -> Result<String> {
        let path = Path::new(MANIFEST_DIR).join(file);
        let mut text = std::fs::read_to_string(repo_root().join(&path))
            .with_context(|| format!("reading {}", path.display()))?;
        for (placeholder, value) in PLACEHOLDERS {
            text = text.replace(placeholder, value(self));
        }
        guard(file, &text)?;
        if !text.ends_with('\n') {
            text.push('\n');
        }
        Ok(text)
    }
}

// ------------------------------------------------------------------------------------------------
// Secrets (replacement for the Kotlin buildSrc Secret.kt and ./gradlew :secret)
// ------------------------------------------------------------------------------------------------

struct Secrets {
    namespace: String,
    db: String,
    db_host: String,
    db_port: String,
    db_user: String,
    db_pass: String,
    admin_key: String,
    admin_user: String,
    admin_pass: String,
}

impl Secrets {
    fn from_env() -> Result<Self> {
        let secret = |name: &str| -> Result<String> {
            let value = var(name, None)?;
            if value.chars().any(char::is_control) {
                bail!("{name} contains control characters");
            }
            Ok(value)
        };
        let secrets = Self {
            namespace: namespace()?,
            db: db()?,
            db_host: token("NJORD_DB_HOST", None, "^[A-Za-z0-9.-]+$", |s| {
                !s.is_empty()
                    && s.bytes()
                        .all(|b| b.is_ascii_alphanumeric() || b == b'.' || b == b'-')
            })?,
            db_port: token("NJORD_DB_PORT", None, "^[0-9]{1,5}$", |s| {
                (1..=5).contains(&s.len()) && s.bytes().all(|b| b.is_ascii_digit())
            })?,
            db_user: token("NJORD_DB_USER", None, "^[a-z_][a-z0-9_]*$", identifier)?,
            db_pass: secret("NJORD_DB_PASS")?,
            admin_key: secret("NJORD_ADMIN_KEY")?,
            admin_user: secret("NJORD_ADMIN_USER")?,
            admin_pass: secret("NJORD_ADMIN_PASS")?,
        };
        if secrets.db_user == "akmadmin" {
            bail!(
                "NJORD_DB_USER=akmadmin: use the dedicated njord role so this environment cannot touch s57server (§10.2 item 4)"
            );
        }
        Ok(secrets)
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
        let Self {
            namespace: ns,
            db,
            db_host,
            db_port,
            db_user: user,
            admin_user,
            ..
        } = self;
        let json = |s: &str| s.replace('\\', "\\\\").replace('"', "\\\"");
        let (json_key, json_user, json_pass) =
            (json(&admin_key), json(admin_user), json(&admin_pass));
        // The pods connect to this namespace's njord-db-relay Service (port 6432 -> host port
        // 6433), not the database directly - see the DaemonSet in chart_server.yaml. sqlx pools
        // in each process (MAX_DB_CONNECTIONS), so there is no pgbouncer sidecar. The path is
        // NJORD_DB in both secrets, so they cannot disagree (§10.3).
        let url = format!("postgresql://{user}:{db_pass}@njord-db-relay:6432/{db}?sslmode=require");
        let text = format!(
            r#"---
# Not a secret: the database endpoint for the njord-db-relay DaemonSet, from the same
# NJORD_DB_HOST/NJORD_DB_PORT. Changing it does not restart the DaemonSet - cycle the relay pods.
apiVersion: v1
kind: ConfigMap
metadata:
  name: njord-db-upstream
  namespace: {ns}
data:
  DB_HOST: "{db_host}"
  DB_PORT: "{db_port}"
---
# The same URL as pgConnectionInfo, for psql in features-vacuum-full.
apiVersion: v1
kind: Secret
metadata:
  name: njord-db-url
  namespace: {ns}
type: Opaque
stringData:
  url: |-
    {url}
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
# Basic credentials for njord-enc-download's orphan deletes (k8s_create_reg_sec.sh
# create_cron_secret).
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
        guard("secrets", &text)?;
        Ok(text)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn manifests() -> Manifests {
        Manifests {
            namespace: "mxmariner".into(),
            db: "njord".into(),
            host: HOST.into(),
            image: format!("{IMAGE_REPO}:test"),
        }
    }

    #[test]
    fn every_manifest_renders() {
        let m = manifests();
        for text in [m.chart().unwrap(), m.cron().unwrap(), m.vacuum().unwrap()] {
            assert!(guard("test", &text).is_ok());
        }
        assert!(
            m.chart()
                .unwrap()
                .contains("image: ghcr.io/manimaul/njord:test")
        );
    }

    #[test]
    fn guard_refuses_the_kotlin_environment() {
        for bad in [
            "  namespace: njord",
            "storageClassName:   nfs ",
            "  image: ghcr.io/manimaul/njord-chart-server:latest",
            "host: openenc.com",
            "dbname=s57server",
            "TCP4-LISTEN:6432,fork",
            "namespace: ${NJORD_NAMESPACE}",
        ] {
            assert!(guard("test", bad).is_err(), "{bad}");
        }
        for ok in [
            "namespace: njord-x",
            "storageClassName: njord-nfs",
            "image: ghcr.io/manimaul/njord:1",
        ] {
            assert!(guard("test", ok).is_ok(), "{ok}");
        }
    }

    #[test]
    fn secrets_are_escaped_and_masked() {
        let secrets = Secrets {
            namespace: "mxmariner".into(),
            db: "njord".into(),
            db_host: "db.example".into(),
            db_port: "5432".into(),
            db_user: "njord".into(),
            db_pass: r#"p"w"#.into(),
            admin_key: r#"k\"#.into(),
            admin_user: "admin".into(),
            admin_pass: "secret".into(),
        };
        let real = secrets.yaml(false).unwrap();
        let url = "postgresql://njord:p%22w@njord-db-relay:6432/njord?sslmode=require";
        assert!(real.contains(&format!(r#""pgConnectionInfo": "{url}""#)));
        assert!(real.contains(&format!("  url: |-\n    {url}\n")));
        assert!(real.contains(r#""adminKey": "k\\","#));
        let masked = secrets.yaml(true).unwrap();
        assert!(!masked.contains("secret\n") && !masked.contains("p%22w"));
        assert!(masked.contains("postgresql://njord:<redacted>@njord-db-relay"));
        assert!(masked.contains(r#""adminPass": "<redacted>""#));
    }

    #[test]
    fn tokens() {
        assert!(identifier("njord_1") && !identifier("1njord") && !identifier("Njord"));
    }
}
