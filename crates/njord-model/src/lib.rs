//! Njord wire types: every JSON shape that crosses the HTTP and WebSocket API.
//!
//! This crate is the contract between `njord-server` and `njord-web`. It must compile for both
//! native targets and `wasm32-unknown-unknown`, so it depends only on serde, [`indexmap`],
//! [`jiff`] and the georust [`geojson`] crate (without its `geo-types` feature; conversions to
//! `geo-types` belong in `njord-geo`).
//!
//! The `openapi` feature (off by default; `njord-server` turns it on) derives `utoipa::ToSchema`
//! for every wire type, so the server's OpenAPI document is generated from these definitions.
//! GeoJSON members are described as plain objects. The web UI never enables it.
//!
//! The types are grouped by API area:
//! - [`chart`]: charts, the catalog, editions and uploads ([`Chart`], [`ChartCatalog`],
//!   [`ChartEditions`]).
//! - [`feature`]: feature lookups and GeoJSON inserts ([`FeatureRecord`], [`LayerQueryResultPage`]).
//! - [`style`], [`colors`] and [`sprite`]: the MapLibre style, S-52 colour themes and sprite
//!   sheets ([`Style`], [`Theme`], [`Depth`], [`Sprite`]).
//! - [`ws`]: ingest progress messages ([`WsMsg`]).
//! - [`admin`], [`about`], [`region`] and [`s57`]: admin tokens, server metadata, the region
//!   archive manifest and the S-57 object catalogue.
//! - [`change_report`]: the history of chart and base map changes ([`ChangeReport`]).
//! - [`instant`] and [`sector_svg`]: the timestamp wire format and the light sector icon, both
//!   shared by the server and the web UI.
//!
//! # Wire format
//!
//! The JSON shapes are fixed by existing clients and by the golden fixtures in `testdata/` and
//! `conformance/golden/`:
//! - API responses write every field, defaults included, and `Option` fields serialise as `null`
//!   unless noted otherwise.
//! - The MapLibre style ([`style`]) omits its `Option` fields when they are `None`.
//! - Maps are [`IndexMap`]s and JSON objects keep their key order.
//! - GeoJSON is the georust [`geojson`] crate. It reads, but does not write, `"id": null`,
//!   `"bbox": null` and `"crs": null` members, and does not keep GeoJSON key order; no client
//!   depends on either.
//!
//! Round trips of the golden fixtures live in `tests/`.
//!
//! # Examples
//!
//! ```
//! use njord_model::ChartCatalog;
//!
//! let json = r#"{"totalChartCount":1,"nextName":null,"page":[{"name":"US5WA22M"}]}"#;
//! let catalog: ChartCatalog = serde_json::from_str(json).unwrap();
//! assert_eq!(catalog.page[0].name, "US5WA22M");
//! assert_eq!(catalog.next_name, None);
//! // `None` is written as an explicit `null`.
//! assert_eq!(serde_json::to_string(&catalog).unwrap(), json);
//! ```

pub mod about;
pub mod admin;
pub mod change_report;
pub mod chart;
pub mod colors;
pub mod feature;
pub mod instant;
pub mod region;
pub mod s57;
pub mod sector_svg;
pub mod sprite;
pub mod style;
pub mod ws;

pub use about::{AboutJson, TileJson};
pub use admin::{AdminResponse, AdminSignature};
pub use change_report::{
    ChangeCause, ChangeCounts, ChangeItem, ChangeKind, ChangeOutcome, ChangeReport,
    ChangeReportDay, ChangeReportPage, UploadMeta,
};
pub use chart::{
    Chart, ChartCatalog, ChartEditions, ChartFeatureInfo, ChartInsert, ChartItem, EncUpload,
    EncUrlRequest, TileCacheCounts,
};
pub use colors::{Color, ColorLibrary, ColorTuple, Colors, CustomTheme, Depth, Theme, ThemeMode};
pub use feature::{FeatureInsert, FeatureRecord, LayerQueryResult, LayerQueryResultPage, Pair};
pub use region::RegionManifestEntry;
pub use s57::{S57Attribute, S57ExpectedInput, S57ObjAcronyms, S57Object, S57Symbol};
pub use sprite::{IconInfo, Sprite};
pub use style::{Layer, Layout, Paint, Source, SourceType, Style};
pub use ws::{InsertItem, WsMsg};

pub use geojson;
pub use indexmap::IndexMap;

/// A JSON object whose values are arbitrary JSON.
///
/// Used for S-57 feature properties and `dsid_props`.
pub type JsonObject = serde_json::Map<String, serde_json::Value>;

/// The [`Theme`] or [`Depth`] name in a URL path segment could not be parsed.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct UnknownVariant {
    /// The enum the value was parsed as, e.g. `"Depth"`.
    pub type_name: &'static str,
    /// The unrecognised input.
    pub value: String,
}

impl std::fmt::Display for UnknownVariant {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "unknown {} '{}'", self.type_name, self.value)
    }
}

impl std::error::Error for UnknownVariant {}

/// Declares a fieldless enum whose wire form is a fixed string per variant.
///
/// Generates `ALL` (declaration order), `as_str`, `Display`, `FromStr` and serde impls.
macro_rules! string_enum {
    (
        $(#[$meta:meta])*
        $vis:vis enum $name:ident { $( $(#[$vmeta:meta])* $variant:ident = $wire:literal ),+ $(,)? }
    ) => {
        $(#[$meta])*
        #[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord, Hash)]
        $vis enum $name { $( $(#[$vmeta])* $variant ),+ }

        impl $name {
            /// Every variant, in declaration order (the order the golden fixtures list them in).
            pub const ALL: &'static [$name] = &[$($name::$variant),+];

            /// The wire name.
            pub const fn as_str(self) -> &'static str {
                match self { $($name::$variant => $wire),+ }
            }
        }

        impl std::fmt::Display for $name {
            fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
                f.write_str(self.as_str())
            }
        }

        impl std::str::FromStr for $name {
            type Err = $crate::UnknownVariant;

            fn from_str(s: &str) -> Result<Self, Self::Err> {
                match s {
                    $($wire => Ok($name::$variant),)+
                    _ => Err($crate::UnknownVariant { type_name: stringify!($name), value: s.to_owned() }),
                }
            }
        }

        impl serde::Serialize for $name {
            fn serialize<S: serde::Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error> {
                serializer.serialize_str(self.as_str())
            }
        }

        impl<'de> serde::Deserialize<'de> for $name {
            fn deserialize<D: serde::Deserializer<'de>>(deserializer: D) -> Result<Self, D::Error> {
                let s = <std::borrow::Cow<'de, str>>::deserialize(deserializer)?;
                s.parse().map_err(|_| {
                    serde::de::Error::unknown_variant(&s, &[$($wire),+])
                })
            }
        }

        // The OpenAPI schema (`openapi` feature): a string enum of the wire names, written by
        // hand like the serde impls above, since `ToSchema` cannot be derived without them.
        // `ComposeSchema` is what the derive emits; `PartialSchema` follows from utoipa's blanket
        // impl, and it is what lets the enum be a key or value of an `IndexMap` field.
        #[cfg(feature = "openapi")]
        impl utoipa::__dev::ComposeSchema for $name {
            fn compose(
                _: Vec<utoipa::openapi::RefOr<utoipa::openapi::schema::Schema>>,
            ) -> utoipa::openapi::RefOr<utoipa::openapi::schema::Schema> {
                utoipa::openapi::schema::Object::builder()
                    .schema_type(utoipa::openapi::schema::Type::String)
                    .enum_values(Some([$($wire),+]))
                    .into()
            }
        }

        #[cfg(feature = "openapi")]
        impl utoipa::ToSchema for $name {}
    };
}

pub(crate) use string_enum;
