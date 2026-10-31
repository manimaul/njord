//! `Main.kt`: one cron run. Parse NOAA's product catalog, ask Njord which chart revisions it
//! already holds, delete withdrawn charts, and fetch only the cells whose revision moved.

use std::path::{Path, PathBuf};

use crate::bundle::BundleBuilder;
use crate::catalog::{self, EncCatalogEntry};
use crate::config::EncCronConfig;
use crate::log;
use crate::select::{batches, deletable_orphans, select_orphans, select_stale};
use crate::transport::{EncHttp, EncTransport, Error};

/// Cap on orphan names logged per run, so a catalog hiccup can't bury the rest of the output.
const ORPHAN_LOG_LIMIT: usize = 50;
const DRY_RUN_LOG_LIMIT: usize = 20;

/// `enc_cron [resourcesDir] [--from-file <catalog.xml>] [--dry-run]`.
#[derive(Debug, Default)]
pub struct CommandLine {
    pub resources_dir: Option<PathBuf>,
    pub catalog_file: Option<PathBuf>,
    pub dry_run: bool,
}

impl CommandLine {
    pub fn parse(args: impl IntoIterator<Item = String>) -> Result<Self, String> {
        let mut out = CommandLine::default();
        let mut args = args.into_iter();
        while let Some(arg) = args.next() {
            match arg.as_str() {
                "--from-file" => {
                    let path = args.next().ok_or("--from-file requires a path")?;
                    if !Path::new(&path).exists() {
                        return Err(format!("catalog file does not exist: {path}"));
                    }
                    out.catalog_file = Some(path.into());
                }
                "--dry-run" => out.dry_run = true,
                _ if arg.starts_with("--") => return Err(format!("unknown option: {arg}")),
                // A resources dir that does not exist is ignored, as in Kotlin.
                _ => out.resources_dir = Some(PathBuf::from(&arg)).filter(|p| p.exists()),
            }
        }
        Ok(out)
    }
}

pub async fn run(config: &EncCronConfig, options: &CommandLine) -> Result<(), Error> {
    match &options.catalog_file {
        Some(file) => log::info(format!("catalog={}", file.display())),
        None => log::info(format!("catalog={}", config.catalog_url)),
    }
    log::info(format!("editions={}", config.chart_editions_url));
    log::info(format!("save dir={}", config.save_dir().display()));

    let http = EncHttp::new(config)?;
    let have = http.fetch_chart_editions().await?;
    log::info(format!("njord holds {} charts", have.len()));

    let catalog = match &options.catalog_file {
        Some(file) => catalog::parse_catalog_file(file)?.entries,
        None => http.fetch_catalog().await?,
    };
    log::info(format!("noaa catalog lists {} cells", catalog.len()));
    if catalog.is_empty() {
        return Err("product catalog yielded no cells".into());
    }

    let orphans = select_orphans(&catalog, &have);
    if !orphans.is_empty() {
        log::info(format!(
            "{} chart(s) held but not listed in the catalog:",
            orphans.len()
        ));
        for orphan in orphans.iter().take(ORPHAN_LOG_LIMIT) {
            log::info(format!("  {orphan}"));
        }
        if orphans.len() > ORPHAN_LOG_LIMIT {
            log::info(format!(
                "  ... and {} more",
                orphans.len() - ORPHAN_LOG_LIMIT
            ));
        }
        delete_orphans(&orphans, &catalog, config, &http, options.dry_run).await?;
    }

    let stale = select_stale(&catalog, &have, config);
    if stale.is_empty() {
        log::info("all cells up to date, nothing to do");
        return Ok(());
    }

    let capped = &stale[..stale.len().min(config.max_cells_per_run)];
    let size_mb: f64 = capped.iter().map(|c| c.size_mb.unwrap_or(0.0)).sum();
    let deferred = stale.len() - capped.len();
    log::info(format!(
        "{} cell(s) out of date; fetching {} this run (~{} MB){}",
        stale.len(),
        capped.len(),
        tenths(size_mb),
        if deferred > 0 {
            format!(", {deferred} deferred to a later run")
        } else {
            String::new()
        }
    ));

    if options.dry_run {
        for cell in capped.iter().take(DRY_RUN_LOG_LIMIT) {
            log::info(format!(
                "  would fetch {} edition {}: {} -> {}",
                cell.cell,
                cell.edition,
                have.get(&cell.chart_name())
                    .map(String::as_str)
                    .unwrap_or("<absent>"),
                cell.revision_key().as_deref().unwrap_or("<incomplete>")
            ));
        }
        if capped.len() > DRY_RUN_LOG_LIMIT {
            log::info(format!(
                "  ... and {} more",
                capped.len() - DRY_RUN_LOG_LIMIT
            ));
        }
        return Ok(());
    }

    publish_bundles(capped, config, &http).await
}

/// Kotlin's `(x * 10).toInt() / 10.0` printed as a `Double`: truncated to tenths, always with a
/// fractional part.
fn tenths(x: f64) -> String {
    let t = (x * 10.0) as i64;
    format!("{}.{}", t / 10, (t % 10).abs())
}

/// Removes charts NOAA has withdrawn, so cancelled cells stop being served (P-EC-5).
///
/// Every guard that refuses does so before a signature is fetched: disabled, no API base URL, no
/// credentials, then [`deletable_orphans`] limits the blast radius to producers this catalog
/// covers, and `maxOrphanDeletes` abandons the pass entirely if the count looks less like
/// withdrawals and more like a truncated download. The caller has already logged the full list.
pub async fn delete_orphans<T: EncTransport>(
    orphans: &[String],
    catalog: &[EncCatalogEntry],
    config: &EncCronConfig,
    http: &T,
    dry_run: bool,
) -> Result<(), Error> {
    if !config.delete_orphans {
        return Ok(());
    }
    let Some(base_url) = config.njord_base_url() else {
        log::warn(format!(
            "cannot derive an API base url from chartEditionsUrl={}; nothing deleted",
            config.chart_editions_url
        ));
        return Ok(());
    };
    if config.admin_user.trim().is_empty() || config.admin_pass.trim().is_empty() {
        log::warn(
            "orphan deletion is enabled but no admin credentials are configured; nothing deleted",
        );
        return Ok(());
    }

    let deletable = deletable_orphans(orphans, catalog);
    let foreign = orphans.len() - deletable.len();
    if foreign > 0 {
        log::info(format!(
            "{foreign} orphan(s) come from a producer this catalog does not cover; leaving them in place"
        ));
    }
    if deletable.is_empty() {
        return Ok(());
    }
    if deletable.len() > config.max_orphan_deletes {
        log::warn(format!(
            "{} withdrawn chart(s) exceeds maxOrphanDeletes={}; deleting none. A truncated catalog \
             looks exactly like a mass withdrawal - confirm the catalog is complete before raising the ceiling.",
            deletable.len(),
            config.max_orphan_deletes
        ));
        return Ok(());
    }
    if dry_run {
        for name in &deletable {
            log::info(format!("  would delete {name}"));
        }
        return Ok(());
    }

    let signature = http
        .fetch_admin_signature(base_url, &config.admin_user, &config.admin_pass)
        .await?;
    let mut deleted = 0;
    for name in &deletable {
        if http.delete_chart(base_url, name, &signature).await? {
            deleted += 1;
            log::info(format!("  deleted {name}"));
        } else {
            log::warn(format!("  {name} was already gone"));
        }
    }
    log::info(format!("deleted {deleted} withdrawn chart(s)"));
    Ok(())
}

async fn publish_bundles<T: EncTransport>(
    cells: &[&EncCatalogEntry],
    config: &EncCronConfig,
    http: &T,
) -> Result<(), Error> {
    std::fs::create_dir_all(config.work_dir())?;
    std::fs::create_dir_all(config.save_dir())?;

    let builder = BundleBuilder::new(config, http);
    let stamp = jiff::Timestamp::now().strftime("%Y%m%d").to_string();
    let mut bundle_number = 0;
    let mut published = 0;

    for batch in batches(cells, config) {
        let queued = queued_bundle_count(config);
        if queued >= config.max_queued_bundles {
            log::info(format!(
                "{queued} bundle(s) already queued in {} (max {}); stopping so ingest can catch up",
                config.save_dir().display(),
                config.max_queued_bundles
            ));
            break;
        }
        bundle_number += 1;
        let name = format!("enc_bundle-{stamp}-{bundle_number:04}.zip");
        log::info(format!("building {name} from {} cell(s)", batch.len()));
        if builder.build_and_publish(&batch, &name).await?.is_some() {
            published += 1;
        }
    }

    log::info(format!("done: published {published} bundle(s)"));
    Ok(())
}

fn queued_bundle_count(config: &EncCronConfig) -> usize {
    std::fs::read_dir(config.save_dir())
        .map(|entries| {
            entries
                .filter_map(Result::ok)
                .filter(|e| {
                    e.file_name()
                        .to_string_lossy()
                        .to_lowercase()
                        .ends_with(".zip")
                })
                .count()
        })
        .unwrap_or(0)
}

#[cfg(test)]
mod tests {
    use super::tenths;

    #[test]
    fn tenths_prints_like_a_kotlin_double() {
        assert_eq!(tenths(0.0), "0.0");
        assert_eq!(tenths(12.0), "12.0");
        assert_eq!(tenths(12.37), "12.3");
        assert_eq!(tenths(818.99), "818.9");
    }
}
