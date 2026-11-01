//! Region `.mbtiles` archives on disk: naming, pruning, checksums and the manifest.
//!
//! [`Regions`] answers the manifest `GET /v1/regions` serves (`testdata/regions/manifest.json`)
//! and the archive lookup of `GET /v1/regions/{archive}`, and names and prunes archives
//! (`testdata/regions/timestamps.json`). Rendering is [`export`], driven by [`worker`].
//!
//! Archives are named `<region>_<yyyy-MM-ddTHH-mm-ss>.mbtiles` in the server's local time, with
//! a `<archive>.sha256` sidecar in `sha256sum` format.

pub mod export;
pub mod worker;

pub use export::{ExportResult, WORLD_REGION_NAME, export_next};
pub use worker::RegionWorker;

use std::io::Read;
use std::path::{Path, PathBuf};

use geo::Area;
use geo_types::{Coord, Geometry, Point, Polygon};
use jiff::Timestamp;
use jiff::civil::DateTime;
use jiff::tz::TimeZone;
use njord_model::RegionManifestEntry;
use sha2::{Digest, Sha256};

use crate::config::RegionExportConfig;

/// The region archive directory, `<chartTempData>/regions`.
pub struct Regions {
    pub dir: PathBuf,
    /// The zone archive timestamps are written in (the server's local zone).
    pub zone: TimeZone,
}

impl Regions {
    /// How many archives of a region [`prune_old_archives`](Self::prune_old_archives) keeps.
    pub const MAX_ARCHIVES: usize = 2;

    pub fn new(dir: impl Into<PathBuf>, zone: TimeZone) -> Regions {
        Regions {
            dir: dir.into(),
            zone,
        }
    }

    /// Now in [`Regions::zone`] as the filesystem-safe stem
    /// `yyyy-MM-ddTHH-mm-ss`.
    pub fn current_timestamp(&self) -> String {
        jiff::Zoned::now()
            .with_time_zone(self.zone.clone())
            .strftime("%Y-%m-%dT%H-%M-%S")
            .to_string()
    }

    /// Deletes every archive of the region beyond the newest
    /// [`MAX_ARCHIVES`](Self::MAX_ARCHIVES), with its sidecar.
    pub fn prune_old_archives(&self, region: &str) {
        for old in self
            .archives_for_region(region)
            .into_iter()
            .skip(Self::MAX_ARCHIVES)
        {
            tracing::info!("pruning old archive {}", file_name(&old));
            if let Err(e) = std::fs::remove_file(&old) {
                tracing::warn!("{}: {e}", old.display());
            }
            let sidecar = self.sidecar(&old);
            if sidecar.exists()
                && let Err(e) = std::fs::remove_file(&sidecar)
            {
                tracing::warn!("{}: {e}", sidecar.display());
            }
        }
    }

    /// Deletes every file in the region directory (the nuke route).
    pub fn clear(&self) {
        let Ok(entries) = std::fs::read_dir(&self.dir) else {
            return;
        };
        for entry in entries.flatten() {
            let path = entry.path();
            let result = if path.is_dir() {
                std::fs::remove_dir_all(&path)
            } else {
                std::fs::remove_file(&path)
            };
            if let Err(e) = result {
                tracing::warn!("{}: {e}", path.display());
            }
        }
        tracing::info!("region archives cleared");
    }

    /// The manifest: one entry per configured region, in configuration order.
    ///
    /// A region with no archive yet has `archive`, `archiveSize`, `archiveSha256` and `createdAt`
    /// null.
    pub fn manifest(&self, regions: &[RegionExportConfig]) -> Vec<RegionManifestEntry> {
        regions
            .iter()
            .map(|region| {
                let latest = self.archives_for_region(&region.name).into_iter().next();
                let coverage = wkt_geometry(&region.coverage);
                RegionManifestEntry {
                    name: region.name.clone(),
                    description: region.description.clone(),
                    coverage: region.coverage.clone(),
                    coverage_geo: coverage_geo(coverage.as_ref()),
                    label_point: coverage
                        .as_ref()
                        .and_then(label_point)
                        .map(|p| geojson::Geometry::from(&p)),
                    archive_size: latest
                        .as_ref()
                        .and_then(|p| std::fs::metadata(p).ok())
                        .map(|m| m.len() as i64),
                    archive_sha256: latest.as_deref().and_then(|p| self.checksum(p)),
                    created_at: latest
                        .as_ref()
                        .and_then(|p| self.archive_timestamp(&region.name, &file_name(p))),
                    archive: latest.as_ref().map(|p| file_name(p)),
                }
            })
            .collect()
    }

    /// The region's archives, newest timestamp first; an unparsable stem sorts last.
    ///
    /// An archive is a file whose name starts with the region name and ends in `.mbtiles`. The
    /// match is a plain prefix, so region `US` also lists the archives of a region `US_EAST`.
    pub fn archives_for_region(&self, region: &str) -> Vec<PathBuf> {
        let Ok(entries) = std::fs::read_dir(&self.dir) else {
            return Vec::new();
        };
        let mut archives: Vec<(Option<Timestamp>, PathBuf)> = entries
            .flatten()
            .map(|e| e.path())
            .filter(|p| {
                let name = file_name(p);
                name.starts_with(region) && name.ends_with(".mbtiles")
            })
            .map(|p| (self.archive_timestamp(region, &file_name(&p)), p))
            .collect();
        archives.sort_by_key(|a| std::cmp::Reverse(a.0));
        archives.into_iter().map(|(_, p)| p).collect()
    }

    /// The archive's timestamp: the stem between `<region>_` and `.mbtiles`, read as local time
    /// in [`Regions::zone`].
    pub fn archive_timestamp(&self, region: &str, file_name: &str) -> Option<Timestamp> {
        let stem = file_name
            .strip_prefix(&format!("{region}_"))
            .unwrap_or(file_name);
        let stem = stem.strip_suffix(".mbtiles").unwrap_or(stem);
        self.parse_stem(stem)
    }

    /// Parses `yyyy-MM-ddTHH-mm-ss` (the time's colons written as dashes) as local time in
    /// [`Regions::zone`].
    ///
    /// Only a dashed `HH-mm-ss` tail is rewritten to colons before the civil date-time is
    /// parsed, so a stem already holding colons parses too.
    pub fn parse_stem(&self, stem: &str) -> Option<Timestamp> {
        let colonized = match stem.split_once('T') {
            Some((date, time)) if is_dashed_time(time) => {
                format!("{date}T{}:{}:{}", &time[0..2], &time[3..5], &time[6..8])
            }
            _ => stem.to_owned(),
        };
        let dt: DateTime = colonized.parse().ok()?;
        self.zone.to_ambiguous_timestamp(dt).compatible().ok()
    }

    /// The archive's sha256: the sidecar's first token when it is 64 characters (not checked
    /// against the file), otherwise the file's sha256, written back to the sidecar.
    pub fn checksum(&self, archive: &Path) -> Option<String> {
        let sidecar = self.sidecar(archive);
        if let Ok(text) = std::fs::read_to_string(&sidecar)
            && let Some(token) = text.split_whitespace().next()
            && token.len() == 64
        {
            return Some(token.to_owned());
        }
        self.write_checksum_sidecar(archive)
    }

    /// Writes the archive's sha256 (streamed, 1 MiB at a time) written to the
    /// sidecar in `sha256sum` format, so `sha256sum -c` works from the region directory.
    pub fn write_checksum_sidecar(&self, archive: &Path) -> Option<String> {
        let checksum = match sha256_file(archive) {
            Ok(checksum) => checksum,
            Err(e) => {
                tracing::error!("failed to compute sha256 for {}: {e}", archive.display());
                return None;
            }
        };
        let line = format!("{checksum}  {}\n", file_name(archive));
        if let Err(e) = std::fs::write(self.sidecar(archive), line) {
            tracing::warn!("{}: {e}", self.sidecar(archive).display());
        }
        Some(checksum)
    }

    /// The checksum sidecar of `archive`, `<archive>.sha256`.
    pub fn sidecar(&self, archive: &Path) -> PathBuf {
        self.dir.join(format!("{}.sha256", file_name(archive)))
    }
}

/// Whether `time` is a dashed `HH-mm-ss`, the tail [`Regions::parse_stem`] rewrites.
fn is_dashed_time(time: &str) -> bool {
    let b = time.as_bytes();
    b.len() == 8
        && b[2] == b'-'
        && b[5] == b'-'
        && [0, 1, 3, 4, 6, 7].iter().all(|&i| b[i].is_ascii_digit())
}

/// Lowercase hex of the file's contents.
fn sha256_file(path: &Path) -> std::io::Result<String> {
    let mut file = std::fs::File::open(path)?;
    let mut hasher = Sha256::new();
    let mut buffer = vec![0u8; 1 << 20];
    loop {
        let n = file.read(&mut buffer)?;
        if n == 0 {
            break;
        }
        hasher.update(&buffer[..n]);
    }
    Ok(format!("{:x}", hasher.finalize()))
}

pub(crate) fn file_name(p: &Path) -> String {
    p.file_name()
        .map(|n| n.to_string_lossy().into_owned())
        .unwrap_or_default()
}

/// The manifest's `labelPoint`: the centroid of the largest polygon of a multipolygon (the first
/// on a tie), else of the geometry.
///
/// Polygon centroids are computed as GEOS does ([`geos_centroid`]) so `labelPoint` matches the
/// manifest golden to the last bit.
fn label_point(coverage: &Geometry) -> Option<Point> {
    match coverage {
        Geometry::Polygon(p) => geos_centroid(std::slice::from_ref(p)),
        Geometry::MultiPolygon(mp) => {
            let mut largest: Option<&Polygon> = None;
            for p in mp.iter() {
                if largest.is_none_or(|l| p.unsigned_area() > l.unsigned_area()) {
                    largest = Some(p);
                }
            }
            match largest {
                Some(p) => geos_centroid(std::slice::from_ref(p)),
                None => None,
            }
        }
        other => njord_geo::geometry::centroid(other),
    }
}

/// GEOS `algorithm::Centroid` for polygons: triangles fanned from the first shell vertex,
/// weighted by twice their signed area (shells counted positive when clockwise, holes negative),
/// `x = Σ a·(x0 + x1 + x2) / 3 / Σ a`. `geo`'s centroid sums in another order and differs in the
/// last bits.
fn geos_centroid(polygons: &[Polygon]) -> Option<Point> {
    let base: Coord = polygons.first()?.exterior().0.first().copied()?;
    let (mut cx, mut cy, mut area2) = (0.0, 0.0, 0.0);
    let mut add_ring = |ring: &[Coord], positive: bool| {
        let sign = if positive { 1.0 } else { -1.0 };
        for w in ring.windows(2) {
            let (p1, p2) = (w[0], w[1]);
            let a2 = (p1.x - base.x) * (p2.y - base.y) - (p2.x - base.x) * (p1.y - base.y);
            cx += sign * a2 * (base.x + p1.x + p2.x);
            cy += sign * a2 * (base.y + p1.y + p2.y);
            area2 += sign * a2;
        }
    };
    for p in polygons {
        let shell = &p.exterior().0;
        add_ring(shell, !is_ccw(shell));
        for hole in p.interiors() {
            add_ring(&hole.0, is_ccw(&hole.0));
        }
    }
    (area2 != 0.0).then(|| Point::new(cx / 3.0 / area2, cy / 3.0 / area2))
}

/// Whether `ring` is counter-clockwise, by signed area (GEOS `Orientation::isCCW`).
fn is_ccw(ring: &[Coord]) -> bool {
    let sum: f64 = ring
        .windows(2)
        .map(|w| (w[1].x - w[0].x) * (w[1].y + w[0].y))
        .sum();
    sum < 0.0
}

fn wkt_geometry(text: &str) -> Option<Geometry> {
    let parsed: wkt::Wkt<f64> = text.parse().ok()?;
    Geometry::try_from(parsed).ok()
}

/// The coverage as GeoJSON, or a `Feature` with a null geometry when the WKT
/// does not parse.
fn coverage_geo(coverage: Option<&Geometry>) -> geojson::GeoJson {
    match coverage {
        Some(g) => geojson::GeoJson::Geometry(geojson::Geometry::from(g)),
        None => geojson::GeoJson::Feature(geojson::Feature::default()),
    }
}
