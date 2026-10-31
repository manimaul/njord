//! Region archives on disk (`ingest/RegionExporter.kt`): the manifest `GET /v1/regions` serves
//! (`buildManifest`, P-RE-5 `testdata/regions/manifest.json`) and the archive lookup of
//! `GET /v1/regions/{archive}`. Rendering archives is the export worker's job (Phase 4); this
//! only reads what a renderer left in `<chartTempData>/regions`.
//!
//! Archives are named `<region>_<yyyy-MM-ddTHH-mm-ss>.mbtiles` in the server's local time, with
//! a `<archive>.sha256` sidecar in `sha256sum` format.

use std::path::{Path, PathBuf};

use geo::Area;
use geo_types::{Coord, Geometry, Point, Polygon};
use jiff::Timestamp;
use jiff::civil::DateTime;
use jiff::tz::TimeZone;
use njord_model::RegionManifestEntry;
use sha2::{Digest, Sha256};

use crate::config::RegionExportConfig;

pub struct Regions {
    pub dir: PathBuf,
    /// The zone archive timestamps are written in (the server's local zone).
    pub zone: TimeZone,
}

impl Regions {
    pub fn new(dir: impl Into<PathBuf>, zone: TimeZone) -> Regions {
        Regions {
            dir: dir.into(),
            zone,
        }
    }

    /// `buildManifest`: one entry per configured region, in configuration order. A region with
    /// no archive yet has `archive`, `archiveSize`, `archiveSha256` and `createdAt` null.
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

    /// `archivesForRegion`: the files whose name starts with the region name (a plain prefix,
    /// as in Kotlin) and ends in `.mbtiles`, newest timestamp first; an unparsable stem sorts
    /// last.
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

    /// `parseArchiveTimestamp`: the stem between `<region>_` and `.mbtiles`, read as local time
    /// in [`Regions::zone`].
    pub fn archive_timestamp(&self, region: &str, file_name: &str) -> Option<Timestamp> {
        let stem = file_name
            .strip_prefix(&format!("{region}_"))
            .unwrap_or(file_name);
        let stem = stem.strip_suffix(".mbtiles").unwrap_or(stem);
        self.parse_stem(stem)
    }

    /// `parseTimestampStem`: `yyyy-MM-ddTHH-mm-ss` (the time's colons written as dashes).
    pub fn parse_stem(&self, stem: &str) -> Option<Timestamp> {
        let (date, time) = stem.split_once('T')?;
        let parts: Vec<&str> = time.split('-').collect();
        let [h, m, s] = parts[..] else {
            return None;
        };
        if [h, m, s].iter().any(|p| p.len() != 2) {
            return None;
        }
        let dt: DateTime = format!("{date}T{h}:{m}:{s}").parse().ok()?;
        self.zone.to_ambiguous_timestamp(dt).compatible().ok()
    }

    /// `checksumForArchive`: the sidecar's first token when it is 64 characters (not checked
    /// against the file), otherwise the file's sha256, written back to the sidecar.
    pub fn checksum(&self, archive: &Path) -> Option<String> {
        let sidecar = self.sidecar(archive);
        if let Ok(text) = std::fs::read_to_string(&sidecar)
            && let Some(token) = text.split_whitespace().next()
            && token.len() == 64
        {
            return Some(token.to_owned());
        }
        let bytes = match std::fs::read(archive) {
            Ok(bytes) => bytes,
            Err(e) => {
                tracing::error!("failed to compute sha256 for {}: {e}", archive.display());
                return None;
            }
        };
        let checksum = format!("{:x}", Sha256::digest(&bytes));
        let line = format!("{checksum}  {}\n", file_name(archive));
        if let Err(e) = std::fs::write(&sidecar, line) {
            tracing::warn!("{}: {e}", sidecar.display());
        }
        Some(checksum)
    }

    fn sidecar(&self, archive: &Path) -> PathBuf {
        self.dir.join(format!("{}.sha256", file_name(archive)))
    }
}

fn file_name(p: &Path) -> String {
    p.file_name()
        .map(|n| n.to_string_lossy().into_owned())
        .unwrap_or_default()
}

/// `wktToLabelPoint`, `centroidOfLargestPart`: the centroid of the largest polygon of a
/// multipolygon (the first on a tie), else of the geometry, computed as GEOS does
/// ([`geos_centroid`]) so the manifest's `labelPoint` matches Kotlin's to the last bit.
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

/// `Orientation::isCCW` by signed area.
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

/// `wktToGeojson`: the coverage as GeoJSON, or a `Feature` with a null geometry when the WKT
/// does not parse.
fn coverage_geo(coverage: Option<&Geometry>) -> geojson::GeoJson {
    match coverage {
        Some(g) => geojson::GeoJson::Geometry(geojson::Geometry::from(g)),
        None => geojson::GeoJson::Feature(geojson::Feature::default()),
    }
}
