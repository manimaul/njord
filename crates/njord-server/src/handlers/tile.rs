//! `GET /v1/tile/{z}/{x}/{y}[?info=true]`: the vector tiles.

use std::sync::Arc;

use axum::Json;
use axum::extract::{Path, Query, Request, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};

use super::{query_param, web, with_content_type};
use crate::state::AppState;
use crate::tile_encoder::{self, TileXyz};

/// The highest zoom served.
pub const MAX_ZOOM: i32 = 24;

/// The vector tile (`application/protobuf`), or with `info=true` (case-insensitive) its chart
/// features as JSON.
///
/// - Coordinates that are not all integers fall through to the web app ([`web::fallback`]), as
///   for a path no route matches.
/// - `0 ≤ z ≤` [`MAX_ZOOM`] and `0 ≤ x, y < 2^z` are checked first; anything else is an empty
///   400, with nothing encoded or cached.
/// - With `useTileCache` on and `debugTile` off, a tile is served from and stored in the tile
///   cache; `info` never touches it.
/// - A database failure is an empty 500 and nothing is cached, rather than a tile missing the
///   failed query's features.
pub async fn tile(
    State(state): State<Arc<AppState>>,
    Path((z, x, y)): Path<(String, String, String)>,
    Query(query): Query<Vec<(String, String)>>,
    request: Request,
) -> Response {
    let (Ok(z), Ok(x), Ok(y)) = (z.parse::<i32>(), x.parse::<i32>(), y.parse::<i32>()) else {
        return web::fallback(State(state), request.uri().clone()).await;
    };
    if !(0..=MAX_ZOOM).contains(&z)
        || !(0..1i64 << z).contains(&(x as i64))
        || !(0..1i64 << z).contains(&(y as i64))
    {
        return StatusCode::BAD_REQUEST.into_response();
    }
    let t = TileXyz {
        z: z as u32,
        x: x as i64,
        y: y as i64,
    };
    let info = query_param(&query, "info").is_some_and(|v| v.eq_ignore_ascii_case("true"));
    if info {
        return match tile_encoder::info(&state.db, state.symbology.clone(), t).await {
            Ok(features) => Json(features).into_response(),
            Err(e) => {
                tracing::error!("tile info {z}/{x}/{y}: {e}");
                StatusCode::INTERNAL_SERVER_ERROR.into_response()
            }
        };
    }
    let cached = state.config.use_tile_cache && !state.config.debug_tile;
    if cached && let Some(bytes) = state.tile_cache.get(z, x, y).await {
        return with_content_type("application/protobuf", bytes);
    }
    match tile_encoder::tile(
        &state.db,
        state.symbology.clone(),
        t,
        state.config.debug_tile,
    )
    .await
    {
        Ok(bytes) => {
            if cached {
                state.tile_cache.put(z, x, y, &bytes).await;
            }
            with_content_type("application/protobuf", bytes)
        }
        Err(e) => {
            tracing::error!("tile {z}/{x}/{y}: {e}");
            StatusCode::INTERNAL_SERVER_ERROR.into_response()
        }
    }
}
