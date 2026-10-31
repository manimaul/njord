//! `GET /v1/tile_json` (`TileJsonHandler.kt`).

use axum::Json;
use njord_model::TileJson;

use crate::forwarded::ExternalBaseUrl;

/// TileJSON 2.2.0 whose single tile URL is under the request's external base URL.
pub async fn tile_json(ExternalBaseUrl(base): ExternalBaseUrl) -> Json<TileJson> {
    Json(TileJson {
        tiles: vec![format!("{base}/v1/tile/{{z}}/{{x}}/{{y}}")],
        ..TileJson::default()
    })
}
