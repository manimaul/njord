//! The `charts` row for a cell, from any [`S57Reader`].
//!
//! The rules are documented on [`S57Reader::chart_insert`].

use std::path::Path;

use geojson::{Feature, Geometry, GeometryValue};
use njord_model::{ChartInsert, IndexMap, JsonObject};
use serde_json::Value;

use crate::feature::int_value;
use crate::{Error, S57Reader};
use njord_geo::tile_system::{TILE_SYSTEM, TWEAK_ZOOM_TO_SCALE_BY};

pub(crate) fn chart_insert<R: S57Reader + ?Sized>(cell: &R) -> Result<ChartInsert, Error> {
    let err = |m: &str| Error(m.to_string());
    let dsid = cell
        .features("DSID")
        .ok_or_else(|| err("dsid is missing"))?;
    let props = dsid
        .into_iter()
        .next()
        .map(|f| f.props)
        .ok_or_else(|| err("DSID props are missing"))?;
    let chart_txt = read_chart_txt(cell.path(), charset(&props))?;

    let mut south = f64::INFINITY;
    let mut north = f64::NEG_INFINITY;
    let mut coverage = Vec::new();
    for feature in cell
        .features("M_COVR")
        .ok_or_else(|| err("M_COVR is missing"))?
    {
        if feature.props.get("CATCOV").and_then(int_value) != Some(1) {
            continue;
        }
        if let Some(g) = feature.geometry {
            let e = g.envelope();
            south = south.min(e.south);
            north = north.max(e.north);
            coverage.push(g.geojson());
        }
    }
    let geometry = if coverage.len() == 1 {
        coverage.pop().unwrap()
    } else {
        let polygons = coverage
            .into_iter()
            .flat_map(|g| match g.value {
                GeometryValue::Polygon { coordinates } => vec![coordinates],
                GeometryValue::MultiPolygon { coordinates } => coordinates,
                _ => Vec::new(),
            })
            .collect();
        Geometry::new(GeometryValue::MultiPolygon {
            coordinates: polygons,
        })
    };

    let scale = props
        .get("DSPM_CSCL")
        .and_then(int_value)
        .ok_or_else(|| err("DSID DSPM_CSCL is missing"))?;
    let center_lat = if south.is_finite() && north.is_finite() {
        (south + north) / 2.0
    } else {
        0.0
    };
    let file_name = cell
        .path()
        .file_name()
        .map(|n| n.to_string_lossy().into_owned())
        .unwrap_or_default();
    let name = match string_value(&props, "DSID_DSNM") {
        Some(n) if !n.is_empty() => n,
        _ => file_name.clone(),
    };
    Ok(ChartInsert {
        name,
        scale,
        file_name,
        updated: string_value(&props, "DSID_UADT").unwrap_or_default(),
        issued: string_value(&props, "DSID_ISDT").unwrap_or_default(),
        zoom: TILE_SYSTEM.scale_to_zoom_int(f64::from(scale), center_lat, TWEAK_ZOOM_TO_SCALE_BY),
        covr: Feature {
            bbox: None,
            geometry: Some(geometry),
            id: None,
            properties: Some(JsonObject::new()),
            foreign_members: None,
        },
        dsid_props: props,
        chart_txt,
    })
}

/// A string prop's content, or a number or boolean prop as text.
fn string_value(props: &JsonObject, key: &str) -> Option<String> {
    match props.get(key)? {
        Value::String(s) => Some(s.clone()),
        Value::Number(n) => Some(n.to_string()),
        Value::Bool(b) => Some(b.to_string()),
        _ => None,
    }
}

#[derive(Clone, Copy)]
enum Charset {
    Ascii,
    Latin1,
    Utf8,
}

fn charset(props: &JsonObject) -> Charset {
    let lexical_level = props
        .get("DSSI_NALL")
        .and_then(int_value)
        .or_else(|| props.get("DSSI_AALL").and_then(int_value));
    match lexical_level {
        Some(0) => Charset::Ascii,
        Some(1) => Charset::Latin1,
        _ => Charset::Utf8,
    }
}

fn read_chart_txt(cell: &Path, charset: Charset) -> Result<IndexMap<String, String>, Error> {
    let dir = match cell.parent() {
        Some(d) if !d.as_os_str().is_empty() => d,
        _ => Path::new("."),
    };
    let io = |e: std::io::Error| Error(format!("{}: {e}", dir.display()));
    let mut files = Vec::new();
    for entry in std::fs::read_dir(dir).map_err(io)? {
        let path = entry.map_err(io)?.path();
        let name = path
            .file_name()
            .map(|n| n.to_string_lossy().into_owned())
            .unwrap_or_default();
        if path.is_file() && name.to_ascii_uppercase().ends_with(".TXT") {
            files.push((name, path));
        }
    }
    files.sort();
    files
        .into_iter()
        .map(|(name, path)| {
            let bytes =
                std::fs::read(&path).map_err(|e| Error(format!("{}: {e}", path.display())))?;
            let text = match charset {
                Charset::Latin1 => bytes.iter().map(|&b| char::from(b)).collect(),
                // US-ASCII is decoded as UTF-8, so non-ASCII bytes are kept where valid.
                Charset::Ascii | Charset::Utf8 => String::from_utf8_lossy(&bytes).into_owned(),
            };
            Ok((name, text))
        })
        .collect()
}
