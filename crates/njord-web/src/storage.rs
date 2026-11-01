//! Settings kept in the browser's `localStorage`.
//!
//! Values are JSON under the keys `MapLocation`, `Theme`, `Depth` and `AdminResponse`, in the
//! format earlier versions of the UI wrote, so an upgrade in place keeps a viewer's map position,
//! theme and login. An empty string means "unset".

use njord_model::{CustomTheme, Theme, ThemeMode};
use serde::Serialize;
use serde::de::DeserializeOwned;
use serde_json::{Value, json};

fn storage() -> Option<web_sys::Storage> {
    web_sys::window()?.local_storage().ok().flatten()
}

fn get_raw(key: &str) -> Option<String> {
    storage()?
        .get_item(key)
        .ok()
        .flatten()
        .filter(|v| !v.trim().is_empty())
}

fn set_raw(key: &str, value: &str) {
    if let Some(s) = storage() {
        let _ = s.set_item(key, value);
    }
}

/// The JSON value stored under `key`, or `None` if it is unset or does not decode.
pub fn get<T: DeserializeOwned>(key: &str) -> Option<T> {
    serde_json::from_str(&get_raw(key)?).ok()
}

/// Stores `value` as JSON under `key`; `None` stores the empty string (unset).
pub fn set<T: Serialize>(key: &str, value: Option<&T>) {
    let text = value
        .and_then(|v| serde_json::to_string(v).ok())
        .unwrap_or_default();
    set_raw(key, &text);
}

// `Theme` is stored as a `[tag, value]` pair, the tag naming the variant with one of these
// fully qualified names.
const THEME_MODE: &str = "io.madrona.njord.model.ThemeMode";
const CUSTOM_THEME: &str = "io.madrona.njord.model.CustomTheme";

/// The stored [`Theme`], or `None` if it is unset or not recognised.
pub fn get_theme() -> Option<Theme> {
    let value: Value = serde_json::from_str(&get_raw("Theme")?).ok()?;
    let [tag, body] = value.as_array()?.as_slice() else {
        return None;
    };
    match tag.as_str()? {
        THEME_MODE => serde_json::from_value::<ThemeMode>(body.clone())
            .ok()
            .map(Theme::Mode),
        CUSTOM_THEME => serde_json::from_value::<CustomTheme>(body.clone())
            .ok()
            .map(Theme::Custom),
        _ => None,
    }
}

/// Stores `theme` as its `[tag, value]` pair.
pub fn set_theme(theme: &Theme) {
    let value = match theme {
        Theme::Mode(m) => json!([THEME_MODE, m]),
        Theme::Custom(c) => json!([CUSTOM_THEME, c]),
    };
    set_raw("Theme", &value.to_string());
}
