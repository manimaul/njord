//! `GET /v1/icon/{name}[?theme=]`: one sprite as a PNG.

use std::sync::Arc;

use axum::extract::{Path, Query, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use njord_model::{Sprite, ThemeMode};

use super::{from_name, query_param, with_content_type};
use crate::state::AppState;

/// One sprite as `image/png`.
///
/// The sprite is named by `name` without its extension (`BOYLAT13.png` and `BOYLAT13` are the same
/// icon), case-insensitive, cut from the theme's `@2x` sheet (`theme` `day`, `dusk`, `night`,
/// default and fallback `day`). An empty 404 for an unknown sprite.
pub async fn icon(
    State(state): State<Arc<AppState>>,
    Path(name): Path<String>,
    Query(query): Query<Vec<(String, String)>>,
) -> Response {
    let theme = query_param(&query, "theme")
        .and_then(|t| from_name(ThemeMode::ALL, ThemeMode::as_str, t))
        .unwrap_or(ThemeMode::Day);
    let Some(sprite) = from_name(Sprite::ALL, Sprite::as_str, strip_ext(&name)) else {
        return StatusCode::NOT_FOUND.into_response();
    };
    let image = tokio::task::spawn_blocking(move || state.sprites.sprite_image(theme, sprite))
        .await
        .ok()
        .flatten();
    match image {
        Some(png) => with_content_type("image/png", png),
        None => StatusCode::NOT_FOUND.into_response(),
    }
}

/// The name without its extension: everything before the last `.`, unless the name starts with it.
fn strip_ext(name: &str) -> &str {
    match name.rfind('.') {
        Some(i) if i > 0 => &name[..i],
        _ => name,
    }
}
