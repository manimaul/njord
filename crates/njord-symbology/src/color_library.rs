//! S-52 colour lookup per theme.

use njord_model::{Color, ColorTuple, Colors, Theme, ThemeMode};

/// Wraps the parsed `colors.json`; the caller supplies it (the server reads it from its
/// resources, tests from `crates/njord-server/resources/colors.json`).
#[derive(Clone, Debug, Default, PartialEq)]
pub struct ColorLibrary {
    pub color_map: Colors,
}

impl ColorLibrary {
    pub fn new(color_map: Colors) -> Self {
        ColorLibrary { color_map }
    }

    /// The hex colour for `theme`: a custom theme's override, else the theme mode's colour.
    ///
    /// # Panics
    /// When the library has no entry for `color` in the theme's mode.
    pub fn color_from(&self, color: Color, theme: &Theme) -> &str {
        let custom = match theme {
            Theme::Custom(c) => self
                .color_map
                .custom
                .get(&c.name)
                .and_then(|lib| lib.get(&c.mode))
                .and_then(|m| m.get(&color)),
            Theme::Mode(_) => None,
        };
        custom
            .or_else(|| self.mode_color(color, theme.mode()))
            .unwrap_or_else(|| panic!("color key not found {color}"))
    }

    fn mode_color(&self, color: Color, mode: ThemeMode) -> Option<&String> {
        self.color_map.library.get(&mode)?.get(&color)
    }

    /// Every colour of `theme`.
    ///
    /// A built-in mode lists its library entries in `colors.json` order; a
    /// custom theme lists every [`Color`] in declaration order.
    pub fn colors(&self, theme: &Theme) -> Vec<ColorTuple> {
        match theme {
            Theme::Custom(_) => Color::ALL
                .iter()
                .map(|&color| ColorTuple {
                    color,
                    hex: self.color_from(color, theme).to_owned(),
                })
                .collect(),
            Theme::Mode(mode) => self
                .color_map
                .library
                .get(mode)
                .map(|m| {
                    m.iter()
                        .map(|(&color, hex)| ColorTuple {
                            color,
                            hex: hex.clone(),
                        })
                        .collect()
                })
                .unwrap_or_default(),
        }
    }

    pub fn has_custom(&self, name: &str) -> bool {
        self.color_map.custom.contains_key(name)
    }
}
