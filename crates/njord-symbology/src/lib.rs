//! S-57 chart symbology: the display props added to chart features and the MapLibre style that
//! draws them.
//!
//! Each S-57 object class has a [`Layerable`] that (a) adds symbology props (colours, sprites,
//! label text) to a [`ChartFeature`] before it is encoded into a vector tile, and (b) contributes
//! the style layers that render those props. One module under [`layers`] per layerable.
//!
//! The crate is pure logic: no I/O and no resource paths. The caller supplies the
//! [`SymbologyConfig`] (depth thresholds) and the colour library ([`ColorLibrary`] over
//! [`njord_model::Colors`], i.e. `colors.json`).
//!
//! # Entry points
//!
//! - [`LayerFactory`]: the registry of layerables in draw order.
//!   [`LayerFactory::pre_tile_encode`] symbolizes one feature at tile time.
//! - [`style()`] / [`style_string`]: the full style document for a depth unit and theme.
//! - [`add_sounding_conversions`] / [`display_depths`]: depth text in metres, feet and fathoms.
//! - [`build_sector_svg`]: the SVG for a light's sector arcs.
//! - [`number`]: the number formatting the style JSON and fixtures expect.
//!
//! ```text
//! let factory = LayerFactory::new(config, ColorLibrary::new(colors));
//! factory.pre_tile_encode(&mut feature);                       // per feature, at tile time
//! style(&factory, base_url, "day_simplified", depth, &theme);  // per style request
//! ```
//!
//! The fixture tests in `tests/` compare against the golden output in `testdata/`.

pub mod color_library;
pub mod colour;
pub mod config;
pub mod feature;
pub mod filters;
pub mod layer_factory;
pub mod layerable;
pub mod layers;
pub mod number;
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
