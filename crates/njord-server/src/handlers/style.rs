//! `GET /v1/style/{depth}/{theme}[?c=]` (`StyleHandler.kt`).

use std::sync::Arc;

use axum::extract::{Path, Query, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use njord_model::{CustomTheme, Depth, Theme, ThemeMode};

use super::{from_name, query_param, with_content_type};
use crate::forwarded::ExternalBaseUrl;
use crate::state::AppState;

/// Kotlin's body for an unknown depth or theme: the handler means `404`, but
/// `call.respond(NotFound, Any())` cannot serialise `Any` and Ktor answers 500 with this text.
/// Kept as observed until a port decision says otherwise (conformance README, candidate defect 3).
const UNKNOWN_STYLE_BODY: &str = "Serializer for class 'Any' is not found.\nPlease ensure that class is marked as '@Serializable' and that the serialization compiler plugin is applied.\n";

/// The MapLibre style (`application/json`) for a depth (`meters`, `feet`, `fathoms`) and theme
/// (`day`, `dusk`, `night`), both case-insensitive. `c` names a custom colour set from
/// `colors.json`; an unknown one falls back to the plain theme. URLs inside are under the
/// external base URL, and the sprite sheet is `<theme>_simplified`.
pub async fn style(
    State(state): State<Arc<AppState>>,
    Path((depth, theme)): Path<(String, String)>,
    Query(query): Query<Vec<(String, String)>>,
    ExternalBaseUrl(base): ExternalBaseUrl,
) -> Response {
    let depth = from_name(Depth::ALL, Depth::as_str, &depth);
    let mode = from_name(ThemeMode::ALL, ThemeMode::as_str, &theme);
    let (Some(depth), Some(mode)) = (depth, mode) else {
        return (
            StatusCode::INTERNAL_SERVER_ERROR,
            with_content_type("text/plain; charset=UTF-8", UNKNOWN_STYLE_BODY),
        )
            .into_response();
    };
    let theme = match query_param(&query, "c") {
        Some(c) if state.colors().has_custom(c) => Theme::Custom(CustomTheme {
            mode,
            name: c.to_owned(),
        }),
        _ => Theme::Mode(mode),
    };
    let name = format!("{}_simplified", mode.path_name());
    let body = njord_symbology::style_string(state.layers(), &base, &name, depth, &theme);
    with_content_type("application/json", body)
}
