//! `GET /v1/sector_svg/{theme}/{sectr1}/{sectr2}/{color}` (`SvgLightSectorHandler.kt`).

use std::sync::Arc;

use axum::extract::{Path, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use njord_model::{Color, Theme, ThemeMode};

use super::{from_name, with_content_type};
use crate::state::AppState;

/// The light-sector arc between bearings `sectr1` and `sectr2` in the S-52 colour `color`, for a
/// theme (unknown falls back to `day`). `image/svg+xml`. A non-numeric bearing or an unknown
/// colour is a 400 with a `text/plain` message that names only the bearings, as Kotlin's does.
///
/// Bearings are parsed with Rust's `f64` grammar, which differs from Kotlin's `toDoubleOrNull`
/// only on spellings no client sends (`inf`, a `d`/`f` suffix, hexadecimal).
pub async fn sector_svg(
    State(state): State<Arc<AppState>>,
    Path((theme, sectr1, sectr2, color)): Path<(String, String, String, String)>,
) -> Response {
    let theme: Theme = from_name(ThemeMode::ALL, ThemeMode::as_str, &theme)
        .unwrap_or(ThemeMode::Day)
        .into();
    let color = from_name(Color::ALL, Color::as_str, &color);
    let (Ok(sectr1), Ok(sectr2), Some(color)) =
        (sectr1.parse::<f64>(), sectr2.parse::<f64>(), color)
    else {
        return (
            StatusCode::BAD_REQUEST,
            with_content_type(
                "text/plain; charset=UTF-8",
                "SECTR1 and SECTR2 are required numeric parameters",
            ),
        )
            .into_response();
    };
    let colors = state.colors();
    let radius = match color {
        Color::LITRD => 68.0,
        Color::LITGN => 74.0,
        _ => 80.0,
    };
    let svg = njord_symbology::build_sector_svg(
        sectr1,
        sectr2,
        colors.color_from(color, &theme),
        colors.color_from(Color::CHGRD, &theme),
        radius,
    );
    with_content_type("image/svg+xml", svg)
}
