//! Streams NOAA's `ENCProdCat_19115.xml` and emits one [`EncCatalogEntry`] per `MD_Metadata`
//! record.
//!
//! Everything here is path scoped rather than leaf-name matched, because the document reuses leaf
//! names across unrelated subtrees. In particular `<title><gco:CharacterString>` appears both under
//! `identificationInfo` (the cell name) and under `dataQualityInfo/.../sourceCitation` (always the
//! literal `"0"`). Matching on the leaf alone yields 7000+ cells all named `0`.
//!
//! Element names are matched literally, prefix included (`gco:Date`), not resolved to
//! namespaces; the root element's bindings are asserted so a change upstream fails loudly instead of yielding zero records.

use quick_xml::events::{BytesStart, Event};
use quick_xml::{Reader, XmlVersion};
use serde::Serialize;

/// One ENC cell as published in NOAA's product catalog.
#[derive(Clone, Debug, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct EncCatalogEntry {
    /// Cell name without extension, e.g. `US5WA22M`.
    pub cell: String,
    /// The raw `<edition>` string (e.g. `"44.2"`): `DSID_EDTN` and `DSID_UPDN` joined by a dot.
    pub edition: String,
    pub url: String,
    pub scale: Option<i32>,
    pub size_mb: Option<f64>,
    /// `revision` date as `YYYYMMDD`; matches the base cell's S-57 `DSID_UADT`.
    pub revision_date: Option<String>,
    /// `publication` date as `YYYYMMDD`; matches `DSID_ISDT` after updates are applied.
    pub publication_date: Option<String>,
}

impl EncCatalogEntry {
    /// How this cell appears in `charts.name` once ingested (S-57 `DSID_DSNM`).
    pub fn chart_name(&self) -> String {
        format!("{}.000", self.cell)
    }

    /// The `<edition>` suffix, i.e. the S-57 update number. `"74.6"` -> `"6"`.
    pub fn update_number(&self) -> Option<&str> {
        self.edition
            .rfind('.')
            .map(|i| &self.edition[i + 1..])
            .filter(|s| !s.trim().is_empty())
    }

    /// Opaque key matched against `/v1/chart_editions`, in the same `"<UPDN>:<UADT>:<ISDT>"`
    /// form the server builds from an ingested chart. `None` when NOAA omitted a part, which
    /// callers treat as always stale.
    ///
    /// The edition *number* (`DSID_EDTN`) is deliberately excluded: GDAL overwrites it with the
    /// value from the last applied `.00N` update file, and NOAA publishes that as `0` for some
    /// cells (e.g. US1GC09M, catalog `74.6`), so an edition-based key never matches for them.
    pub fn revision_key(&self) -> Option<String> {
        Some(format!(
            "{}:{}:{}",
            self.update_number()?,
            self.revision_date.as_deref()?,
            self.publication_date.as_deref()?
        ))
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum Field {
    Cell,
    Edition,
    Url,
    Scale,
    SizeMb,
    Date,
    DateType,
}

const ROOT_ELEMENT: &str = "DS_Series";
const RECORD_ELEMENT: &str = "MD_Metadata";
/// Wraps the catalog's own metadata record (`hierarchyLevel` `series`), which describes the
/// catalog rather than a cell and has no edition or download URL.
const SERIES_ELEMENT: &str = "seriesMetadata";
const DATE_ELEMENT: &str = "CI_Date";

const REQUIRED_NAMESPACES: &[(&str, &str)] = &[
    ("xmlns", "http://www.isotc211.org/2005/gmd"),
    ("xmlns:gco", "http://www.isotc211.org/2005/gco"),
];

/// Path tails, matched against the end of the open element path. The leaf (last element) is
/// checked first, so only the few interesting element names ever run a tail comparison.
const RULES: &[(&[&str], Field)] = &[
    (
        &[
            "identificationInfo",
            "MD_DataIdentification",
            "citation",
            "CI_Citation",
            "title",
            "gco:CharacterString",
        ],
        Field::Cell,
    ),
    (
        &[
            "identificationInfo",
            "MD_DataIdentification",
            "citation",
            "CI_Citation",
            "edition",
            "gco:CharacterString",
        ],
        Field::Edition,
    ),
    // Scoped to `citation`, not `sourceCitation`, so the dataQualityInfo lineage dates (which
    // carry a nilReason and no value) cannot leak in.
    (
        &[
            "citation",
            "CI_Citation",
            "date",
            "CI_Date",
            "date",
            "gco:Date",
        ],
        Field::Date,
    ),
    (
        &[
            "citation",
            "CI_Citation",
            "date",
            "CI_Date",
            "dateType",
            "CI_DateTypeCode",
        ],
        Field::DateType,
    ),
    (
        &[
            "spatialResolution",
            "MD_Resolution",
            "equivalentScale",
            "MD_RepresentativeFraction",
            "denominator",
            "gco:Integer",
        ],
        Field::Scale,
    ),
    (
        &[
            "distributionInfo",
            "MD_Distribution",
            "transferOptions",
            "MD_DigitalTransferOptions",
            "transferSize",
            "gco:Real",
        ],
        Field::SizeMb,
    ),
    (
        &[
            "distributionInfo",
            "MD_Distribution",
            "transferOptions",
            "MD_DigitalTransferOptions",
            "onLine",
            "CI_OnlineResource",
            "linkage",
            "URL",
        ],
        Field::Url,
    ),
];

#[derive(Debug, Default)]
struct Record {
    cell: Option<String>,
    edition: Option<String>,
    url: Option<String>,
    scale: Option<i32>,
    size_mb: Option<f64>,
    revision_date: Option<String>,
    publication_date: Option<String>,
}

/// The parser's state, fed element events by [`parse_reader`] or [`parse_async`].
#[derive(Debug, Default)]
pub struct EncProdCatParser {
    path: Vec<String>,
    text: String,
    active: Option<Field>,
    record: Record,
    // A CI_Date holds its <gco:Date> *before* the <dateType> that says which date it is, so the
    // value has to be parked until the discriminator arrives at </CI_Date>.
    pending_date: Option<String>,
    pending_date_type: Option<String>,
    root_seen: bool,
    /// The open `MD_Metadata` is the series record, which is neither an entry nor counted.
    in_series_record: bool,
    pub entries: Vec<EncCatalogEntry>,
    /// Cell records seen, including any dropped as incomplete. The series record is not one.
    pub records_seen: usize,
    /// Cell records dropped because a required field was missing.
    pub records_skipped: usize,
}

impl EncProdCatParser {
    fn start(&mut self, name: &str, attrs: &[(String, String)]) -> Result<(), String> {
        self.path.push(name.to_owned());
        if self.path.len() == 1 {
            validate_root(name, attrs)?;
            self.root_seen = true;
        }
        if name == RECORD_ELEMENT {
            self.record = Record::default();
            let parent = self
                .path
                .len()
                .checked_sub(2)
                .map(|i| self.path[i].as_str());
            self.in_series_record = parent == Some(SERIES_ELEMENT);
        }
        if name == DATE_ELEMENT {
            self.pending_date = None;
            self.pending_date_type = None;
        }
        self.active = RULES
            .iter()
            .find(|(tail, _)| tail.last() == Some(&name) && self.path.ends_with_strs(tail))
            .map(|(_, field)| *field);
        if self.active.is_some() {
            self.text.clear();
        }
        Ok(())
    }

    fn characters(&mut self, text: &str) {
        if self.active.is_some() {
            self.text.push_str(text);
        }
    }

    fn end(&mut self, name: &str) {
        if let Some(field) = self.active.take() {
            let value = self.text.trim().to_owned();
            let r = &mut self.record;
            match field {
                Field::Cell => r.cell = Some(value),
                Field::Edition => r.edition = Some(value),
                Field::Url => r.url = Some(value),
                Field::Scale => r.scale = value.parse().ok(),
                Field::SizeMb => r.size_mb = value.parse().ok(),
                // Normalise "2025-12-09" to the "20251209" form S-57 stores in UADT/ISDT.
                Field::Date => {
                    self.pending_date =
                        Some(value.replace('-', "")).filter(|s| !s.trim().is_empty())
                }
                Field::DateType => self.pending_date_type = Some(value),
            }
            self.text.clear();
        }

        if name == DATE_ELEMENT {
            match self.pending_date_type.as_deref() {
                Some("revision") => self.record.revision_date = self.pending_date.take(),
                Some("publication") => self.record.publication_date = self.pending_date.take(),
                _ => {}
            }
            self.pending_date = None;
            self.pending_date_type = None;
        }

        if name == RECORD_ELEMENT && std::mem::take(&mut self.in_series_record) {
            // The series record describes the catalog, not a cell: drop it without counting it,
            // so the incomplete-record warning only ever reports real cells.
            self.record = Record::default();
        } else if name == RECORD_ELEMENT {
            self.records_seen += 1;
            let r = std::mem::take(&mut self.record);
            let present = |s: &Option<String>| s.as_deref().is_some_and(|s| !s.trim().is_empty());
            if present(&r.cell) && present(&r.edition) && present(&r.url) {
                self.entries.push(EncCatalogEntry {
                    cell: r.cell.unwrap(),
                    edition: r.edition.unwrap(),
                    url: r.url.unwrap(),
                    scale: r.scale,
                    size_mb: r.size_mb,
                    revision_date: r.revision_date,
                    publication_date: r.publication_date,
                });
            } else {
                // One malformed record must not abort a 7000 cell catalog.
                self.records_skipped += 1;
            }
        }

        self.path.pop();
    }

    /// One `quick-xml` event. `Ok(true)` at the end of the document.
    fn event(&mut self, event: Event) -> Result<bool, String> {
        match event {
            Event::Start(e) => {
                let (name, attrs) = element(&e)?;
                self.start(&name, &attrs)?;
            }
            Event::Empty(e) => {
                let (name, attrs) = element(&e)?;
                self.start(&name, &attrs)?;
                self.end(&name);
            }
            Event::End(e) => {
                let name = e.name().as_ref().to_owned();
                self.end(&name);
            }
            Event::Text(t) => self.characters(&t.xml10_content()),
            Event::CData(t) => self.characters(&t.xml10_content()),
            Event::GeneralRef(r) => {
                let text = match r.resolve_char_ref().map_err(|e| e.to_string())? {
                    Some(c) => c.to_string(),
                    None => {
                        let name = r.xml10_content();
                        quick_xml::escape::resolve_predefined_entity(&name)
                            .ok_or_else(|| format!("undefined entity &{name};"))?
                            .to_owned()
                    }
                };
                self.characters(&text);
            }
            Event::Eof => {
                // expat fails a truncated document at finish(); quick-xml just stops, so check.
                if !self.root_seen {
                    return Err("no element found".to_owned());
                }
                if !self.path.is_empty() {
                    return Err(format!(
                        "document truncated inside <{}>",
                        self.path.join("/")
                    ));
                }
                return Ok(true);
            }
            _ => {}
        }
        Ok(false)
    }
}

trait EndsWith {
    fn ends_with_strs(&self, tail: &[&str]) -> bool;
}

impl EndsWith for Vec<String> {
    fn ends_with_strs(&self, tail: &[&str]) -> bool {
        self.len() >= tail.len()
            && self[self.len() - tail.len()..]
                .iter()
                .zip(tail)
                .all(|(a, b)| a == b)
    }
}

fn element(e: &BytesStart) -> Result<(String, Vec<(String, String)>), String> {
    let name = e.name().as_ref().to_owned();
    let mut attrs = Vec::new();
    for a in e.attributes() {
        let a = a.map_err(|e| e.to_string())?;
        let value = a
            .normalized_value(XmlVersion::Implicit1_0)
            .map_err(|e| e.to_string())?;
        attrs.push((a.key.as_ref().to_owned(), value.into_owned()));
    }
    Ok((name, attrs))
}

fn validate_root(name: &str, attrs: &[(String, String)]) -> Result<(), String> {
    if name != ROOT_ELEMENT {
        return Err(format!(
            "unexpected root element '{name}', expected '{ROOT_ELEMENT}' - is this really ENCProdCat_19115.xml?"
        ));
    }
    for (prefix, uri) in REQUIRED_NAMESPACES {
        let bound = attrs
            .iter()
            .find(|(k, _)| k == prefix)
            .map(|(_, v)| v.as_str());
        if bound != Some(uri) {
            return Err(format!(
                "ENCProdCat namespace binding changed: expected {prefix}={uri} but got {}. \
                 The element path rules in EncProdCatParser must be revisited.",
                bound.unwrap_or("null")
            ));
        }
    }
    Ok(())
}

fn configure<R>(reader: &mut Reader<R>) {
    // Text is trimmed per field after concatenation, so keep every text event untrimmed.
    reader.config_mut().check_end_names = true;
}

/// Parses a catalog from any buffered reader (a file for `--from-file`, or a byte slice).
///
/// # Errors
///
/// The XML is malformed, or the root element does not bind the expected namespaces.
pub fn parse_reader<R: std::io::BufRead>(source: R) -> Result<EncProdCatParser, String> {
    let mut reader = Reader::from_reader(source);
    configure(&mut reader);
    let mut parser = EncProdCatParser::default();
    let mut buf = Vec::new();
    loop {
        let event = reader
            .read_event_into(&mut buf)
            .map_err(|e| format!("catalog XML at byte {}: {e}", reader.buffer_position()))?;
        if parser.event(event)? {
            return Ok(parser);
        }
        buf.clear();
    }
}

/// [`parse_reader`] over an async source: the HTTP body, without materialising the document.
///
/// # Errors
///
/// As [`parse_reader`], plus read errors of `source`.
pub async fn parse_async<R: tokio::io::AsyncBufRead + Unpin>(
    source: R,
) -> Result<EncProdCatParser, String> {
    let mut reader = Reader::from_reader(source);
    configure(&mut reader);
    let mut parser = EncProdCatParser::default();
    let mut buf = Vec::new();
    loop {
        let event = reader
            .read_event_into_async(&mut buf)
            .await
            .map_err(|e| format!("catalog XML at byte {}: {e}", reader.buffer_position()))?;
        if parser.event(event)? {
            return Ok(parser);
        }
        buf.clear();
    }
}

/// Parses a catalog already on disk (`--from-file`).
///
/// # Errors
///
/// The file cannot be opened, or as [`parse_reader`].
pub fn parse_catalog_file(path: &std::path::Path) -> Result<EncProdCatParser, String> {
    let file = std::fs::File::open(path).map_err(|e| format!("{}: {e}", path.display()))?;
    parse_reader(std::io::BufReader::with_capacity(256 * 1024, file))
}
