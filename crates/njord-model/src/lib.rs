//! Njord wire types: every JSON shape that crosses the HTTP/WebSocket API, ported from the Kotlin
//! `shared` module (`io.madrona.njord.model`) and the in-house `geojson` module.
//!
//! This crate is the contract between `njord-server` and `njord-web` (RUST_PORT_PLAN.md §3.1). It
//! must compile for both native targets and `wasm32-unknown-unknown`, so it depends only on serde,
//! `indexmap`, `jiff` and the georust `geojson` crate (without its `geo-types` feature;
//! conversions to `geo-types` belong in `njord-geo`).
//!
//! Serialisation mirrors the Kotlin side exactly:
//! - API responses go through Ktor's default `Json` (`encodeDefaults = true`, explicit nulls), so
//!   `Option` fields serialise as `null` unless noted otherwise.
//! - The MapLibre style ([`style`]) is written with `explicitNulls = false`, so its `Option`
//!   fields are omitted when `None`.
//! - Maps are [`IndexMap`]s and JSON objects keep their key order, matching Kotlin's
//!   `LinkedHashMap`.
//! - GeoJSON is the georust [`geojson`] crate, which does not reproduce the Kotlin `geojson`
//!   module's `"id": null` / `"bbox": null` / `"crs": null` members or its key order. It reads
//!   them, and nothing on the wire depends on them.
//!
//! Golden-fixture round trips against `testdata/` and `conformance/golden/` live in `tests/`.

pub mod about;
pub mod admin;
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

/// A JSON object whose values are arbitrary JSON: Kotlin's `Map<String, JsonElement>` /
/// `JsonObject`, used for S-57 feature properties and `dsid_props`.
pub type JsonObject = serde_json::Map<String, serde_json::Value>;

/// The [`Theme`] or [`Depth`] name in a URL path segment could not be parsed.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct UnknownVariant {
    pub type_name: &'static str,
    pub value: String,
}

impl std::fmt::Display for UnknownVariant {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "unknown {} '{}'", self.type_name, self.value)
    }
}

impl std::error::Error for UnknownVariant {}

/// A fieldless enum whose wire form is a fixed string per variant, as Kotlin serialises an
/// `enum class` (the `@SerialName`, or the constant name when there is none). Generates
/// `ALL` (declaration order), `as_str`, `Display`, `FromStr` and serde impls.
macro_rules! string_enum {
    (
        $(#[$meta:meta])*
        $vis:vis enum $name:ident { $( $(#[$vmeta:meta])* $variant:ident = $wire:literal ),+ $(,)? }
    ) => {
        $(#[$meta])*
        #[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord, Hash)]
        $vis enum $name { $( $(#[$vmeta])* $variant ),+ }

        impl $name {
            /// Every variant, in the Kotlin declaration order.
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
    };
}

pub(crate) use string_enum;
