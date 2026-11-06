//! `GET /v1/style/{depth}/{theme}[?c=]`: the MapLibre style.

use std::sync::Arc;

use axum::extract::{Path, Query, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use njord_model::{CustomTheme, Depth, Theme, ThemeMode};

use super::{from_name, query_param, with_content_type};
use crate::forwarded::ExternalBaseUrl;
use crate::state::AppState;

/// The body of the 500 answered for an unknown depth or theme.
///
/// A 404 is what is meant, but existing deployments answer a 500 with this serializer error
/// text and the conformance goldens record it, so it is kept until the API contract changes
/// (conformance README, candidate defect 3).
const UNKNOWN_STYLE_BODY: &str = "Serializer for class 'Any' is not found.\nPlease ensure that class is marked as '@Serializable' and that the serialization compiler plugin is applied.\n";

/// The MapLibre style (`application/json`) for a depth and theme.
///
/// The depth is `meters`, `feet` or `fathoms` and the theme `day`, `dusk` or `night`, both
/// case-insensitive. `c` names a custom colour set from
/// `colors.json`; an unknown one falls back to the plain theme. URLs inside are under the
/// external base URL, and the sprite sheet is `<theme>_simplified`.
#[utoipa::path(get, path = "/v1/style/{depth}/{theme}", tag = "style", operation_id = "style",
    summary = "The MapLibre style for a depth unit and theme",
    description = "Both path segments are case-insensitive. The URLs inside are under the request's \
external base URL, and the sprite sheet is `<theme>_simplified`.",
    params(
        ("depth" = String, Path, example = "meters", description = "`meters`, `feet` or `fathoms`"),
        ("theme" = String, Path, example = "day", description = "`day`, `dusk` or `night`"),
        ("c" = Option<String>, Query,
            description = "A custom color set from `colors.json` (see `/v1/about/colors/custom`); an unknown name falls back to the plain theme")),
    responses(
        (status = 200, description = "The style", body = njord_model::Style),
        (status = 500, description = "Unknown depth or theme. A 404 is meant; existing clients expect this text",
            content_type = "text/plain", body = String)))]
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
