//! Style assembly (`StyleHandler.styleString`).

use njord_model::{Depth, IndexMap, Source, SourceType, Style, Theme};

use crate::LayerFactory;

/// The MapLibre style for `GET /v1/style/{depth}/{theme}[?c=]`. `base_url` is the external
/// base URL (no trailing slash) and `name` the sprite sheet, `<mode>_simplified`.
pub fn style(
    factory: &LayerFactory,
    base_url: &str,
    name: &str,
    depth: Depth,
    theme: &Theme,
) -> Style {
    let mut sources = IndexMap::new();
    sources.insert(
        Source::SENC.to_owned(),
        Source {
            kind: SourceType::Vector,
            tile_json_url: Some(format!("{base_url}/v1/tile_json")),
            promote_id: Some("LNAM".to_owned()),
            data: None,
        },
    );
    Style {
        glyphs_url: format!("{base_url}/v1/content/fonts/{{fontstack}}/{{range}}.pbf"),
        layers: factory.layers(depth, theme),
        name: name.to_owned(),
        sources,
        sprite_url: format!("{base_url}/v1/content/sprites/{name}"),
        version: 8,
    }
}

/// [`style`] serialised as the server writes it (`explicitNulls = false`).
pub fn style_string(
    factory: &LayerFactory,
    base_url: &str,
    name: &str,
    depth: Depth,
    theme: &Theme,
) -> String {
    serde_json::to_string(&style(factory, base_url, name, depth, theme))
        .expect("a style always serialises")
}
