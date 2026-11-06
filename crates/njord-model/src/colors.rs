//! S-52 colour tokens, themes and depth units.

use serde::{Deserialize, Serialize};

use crate::{IndexMap, string_enum};

string_enum! {
    /// Depth unit of a style (`/v1/style/{depth}/…`).
    pub enum Depth {
        Fathoms = "fathoms",
        Meters = "meters",
        Feet = "feet",
    }
}

string_enum! {
    /// One of the three S-52 colour tables.
    pub enum ThemeMode {
        Day = "DAY",
        Dusk = "DUSK",
        Night = "NIGHT",
    }
}

impl ThemeMode {
    /// Lower-case form used in URL paths and sprite sheet names (`day`, `dusk`, `night`).
    pub const fn path_name(self) -> &'static str {
        match self {
            ThemeMode::Day => "day",
            ThemeMode::Dusk => "dusk",
            ThemeMode::Night => "night",
        }
    }
}

/// A named colour override set (`colors.json` `custom`), applied on top of a [`ThemeMode`].
#[derive(Clone, Debug, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct CustomTheme {
    pub mode: ThemeMode,
    pub name: String,
}

/// The colour theme of a style: either a built-in mode or a custom theme.
#[derive(Clone, Debug, PartialEq, Eq, Hash)]
pub enum Theme {
    Mode(ThemeMode),
    Custom(CustomTheme),
}

impl Theme {
    /// The custom theme name, or `"Default"` for a built-in mode.
    pub fn color_name(&self) -> &str {
        match self {
            Theme::Mode(_) => "Default",
            Theme::Custom(c) => &c.name,
        }
    }

    /// The colour table the theme is based on.
    pub fn mode(&self) -> ThemeMode {
        match self {
            Theme::Mode(m) => *m,
            Theme::Custom(c) => c.mode,
        }
    }
}

impl From<ThemeMode> for Theme {
    fn from(m: ThemeMode) -> Self {
        Theme::Mode(m)
    }
}

impl From<CustomTheme> for Theme {
    fn from(c: CustomTheme) -> Self {
        Theme::Custom(c)
    }
}

/// The server-relative style URL for a theme and depth unit, e.g. `/v1/style/meters/day` or
/// `/v1/style/feet/dusk?c=sweden`.
pub fn style_path(theme: &Theme, depth: Depth) -> String {
    match theme {
        Theme::Mode(m) => format!("/v1/style/{depth}/{}", m.path_name()),
        Theme::Custom(c) => format!("/v1/style/{depth}/{}?c={}", c.mode.path_name(), c.name),
    }
}

/// Colour token → `#RRGGBB` for one theme mode (`GET /v1/about/colors` values).
pub type ColorLibrary = IndexMap<ThemeMode, IndexMap<Color, String>>;

/// `resources/colors.json`. `GET /v1/about/colors` serves `library`, `/colors/custom` serves
/// `custom`.
#[derive(Clone, Debug, Default, PartialEq, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct Colors {
    pub library: ColorLibrary,
    pub custom: IndexMap<String, ColorLibrary>,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct ColorTuple {
    pub color: Color,
    pub hex: String,
}

string_enum! {
    /// S-52 colour tokens, in the order `colors.json` lists them.
    #[allow(clippy::upper_case_acronyms)]
    pub enum Color {
        NODTA = "NODTA",
        CURSR = "CURSR",
        CHBLK = "CHBLK",
        CHGRD = "CHGRD",
        CHGRF = "CHGRF",
        CHRED = "CHRED",
        CHGRN = "CHGRN",
        CHYLW = "CHYLW",
        CHMGD = "CHMGD",
        CHMGF = "CHMGF",
        CHBRN = "CHBRN",
        CHWHT = "CHWHT",
        SCLBR = "SCLBR",
        CHCOR = "CHCOR",
        LITRD = "LITRD",
        LITGN = "LITGN",
        LITYW = "LITYW",
        ISDNG = "ISDNG",
        DNGHL = "DNGHL",
        TRFCD = "TRFCD",
        TRFCF = "TRFCF",
        LANDA = "LANDA",
        LANDF = "LANDF",
        CSTLN = "CSTLN",
        SNDG1 = "SNDG1",
        SNDG2 = "SNDG2",
        DEPSC = "DEPSC",
        DEPCN = "DEPCN",
        DEPDW = "DEPDW",
        DEPMD = "DEPMD",
        DEPMS = "DEPMS",
        DEPVS = "DEPVS",
        DEPIT = "DEPIT",
        RADHI = "RADHI",
        RADLO = "RADLO",
        ARPAT = "ARPAT",
        NINFO = "NINFO",
        RESBL = "RESBL",
        ADINF = "ADINF",
        RESGR = "RESGR",
        SHIPS = "SHIPS",
        PSTRK = "PSTRK",
        SYTRK = "SYTRK",
        PLRTE = "PLRTE",
        APLRT = "APLRT",
        UINFD = "UINFD",
        UINFF = "UINFF",
        UIBCK = "UIBCK",
        UIAFD = "UIAFD",
        UINFR = "UINFR",
        UINFG = "UINFG",
        UINFO = "UINFO",
        UINFB = "UINFB",
        UINFM = "UINFM",
        UIBDR = "UIBDR",
        UIAFF = "UIAFF",
        OUTLW = "OUTLW",
        OUTLL = "OUTLL",
        RES01 = "RES01",
        RES02 = "RES02",
        RES03 = "RES03",
        BKAJ1 = "BKAJ1",
        BKAJ2 = "BKAJ2",
        MARBL = "MARBL",
        MARCY = "MARCY",
        MARMG = "MARMG",
        MARWH = "MARWH",    }
}
