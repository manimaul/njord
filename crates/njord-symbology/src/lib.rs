//! S-57 symbology (RUST_PORT_PLAN.md §5.1, Phase 2): the per-object-class rules that add display
//! props to a chart feature before it is encoded into a tile (`preTileEncode`), and the MapLibre
//! style whose layers draw them. Ported from the Kotlin `server/…/layers/` package, one module
//! per Kotlin file.
//!
//! Pure logic: no I/O, no resource paths. The caller supplies the [`SymbologyConfig`] (depth
//! thresholds) and the colour library ([`njord_model::Colors`], i.e. `colors.json`).
//!
//! ```text
//! let factory = LayerFactory::new(config, ColorLibrary::new(colors));
//! factory.pre_tile_encode(&mut feature);              // per feature, at tile time
//! style(&factory, base_url, "day_simplified", depth, &theme);  // per style request
//! ```
//!
//! Fixture tests (P-SY-1 … P-SY-9) are in `tests/`.

pub mod color_library;
pub mod colour;
pub mod config;
pub mod feature;
pub mod filters;
pub mod kotlin;
pub mod layer_factory;
pub mod layerable;
pub mod layers;
pub mod paint_brushes;
pub mod props;
pub mod soundings;
pub mod style;
pub mod svg_light_sector;

pub use color_library::ColorLibrary;
pub use colour::Colour;
pub use config::SymbologyConfig;
pub use feature::{ChartFeature, GeometryType};
pub use layer_factory::{Entry, LayerFactory};
pub use layerable::{Context, LayerBuilder, Layerable};
pub use soundings::{DisplayDepths, add_sounding_conversions, display_depths};
pub use style::{style, style_string};
pub use svg_light_sector::build_sector_svg;
