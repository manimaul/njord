//! The pure selection logic of a run: which cells to fetch, which charts are orphans, which
//! orphans may be deleted, and how the fetch is split into bundles.

use std::collections::HashSet;

use indexmap::IndexMap;

use crate::catalog::EncCatalogEntry;
use crate::config::EncCronConfig;

/// NOAA's `transferSize` is the compressed figure; S-57 deflates to roughly a quarter of its raw
/// size, so scale it up to approximate what the ingest will actually have to hold open.
const UNCOMPRESSED_RATIO: f64 = 4.0;

/// A cell is stale when Njord's stored revision key differs from NOAA's in *either* direction.
///
/// Keys are compared as opaque strings and never parsed as numbers: `"1.10"` and `"1.1"` are
/// distinct S-57 states but equal as floats. A cell with no revision key is always re-fetched.
/// Sorted by cell, so a capped run makes the same progress every time.
pub fn select_stale<'a>(
    catalog: &'a [EncCatalogEntry],
    have: &IndexMap<String, String>,
    config: &EncCronConfig,
) -> Vec<&'a EncCatalogEntry> {
    let mut stale: Vec<&EncCatalogEntry> = catalog
        .iter()
        .filter(|e| {
            config.scale_filter.is_empty()
                || e.scale.is_some_and(|s| config.scale_filter.contains(&s))
        })
        .filter(|e| match e.revision_key() {
            None => true,
            Some(key) => have.get(&e.chart_name()) != Some(&key),
        })
        .collect();
    stale.sort_by(|a, b| a.cell.cmp(&b.cell));
    stale
}

/// Chart names Njord holds that NOAA's catalog no longer lists, sorted. `scaleFilter` does not
/// apply: membership in the catalog is what makes a chart an orphan.
pub fn select_orphans(catalog: &[EncCatalogEntry], have: &IndexMap<String, String>) -> Vec<String> {
    let listed: HashSet<String> = catalog.iter().map(EncCatalogEntry::chart_name).collect();
    let mut orphans: Vec<String> = have
        .keys()
        .filter(|k| !listed.contains(*k))
        .cloned()
        .collect();
    orphans.sort();
    orphans
}

fn producer(name: &str) -> String {
    name.chars().take(2).collect::<String>().to_uppercase()
}

/// The orphans this job may delete: those whose S-57 producer code (the leading two letters of
/// the cell name) appears in the catalog just diffed against. NOAA's catalog covers `US` only, so
/// a chart from another hydrographic office is absent for reasons unrelated to withdrawal.
pub fn deletable_orphans(orphans: &[String], catalog: &[EncCatalogEntry]) -> Vec<String> {
    let producers: HashSet<String> = catalog.iter().map(|e| producer(&e.cell)).collect();
    orphans
        .iter()
        .filter(|o| producers.contains(&producer(o)))
        .cloned()
        .collect()
}

/// Splits `cells` into batches bounded by both cell count and estimated uncompressed size. A
/// single cell over the byte cap still gets a batch of its own.
pub fn batches<'a>(
    cells: &[&'a EncCatalogEntry],
    config: &EncCronConfig,
) -> Vec<Vec<&'a EncCatalogEntry>> {
    let mut out = Vec::new();
    let mut current: Vec<&EncCatalogEntry> = Vec::new();
    let mut current_bytes = 0i64;
    for &cell in cells {
        let estimated = (cell.size_mb.unwrap_or(0.0) * 1024.0 * 1024.0 * UNCOMPRESSED_RATIO) as i64;
        let full = current.len() >= config.bundle_size_cells
            || (!current.is_empty()
                && current_bytes + estimated > config.max_bundle_uncompressed_bytes);
        if full {
            out.push(std::mem::take(&mut current));
            current_bytes = 0;
        }
        current.push(cell);
        current_bytes += estimated;
    }
    if !current.is_empty() {
        out.push(current);
    }
    out
}
