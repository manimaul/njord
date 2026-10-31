//! Single icons cut from the `@2x` sprite sheets (`util/SpriteSheet.kt`), for `/v1/icon/{name}`.
//!
//! Kotlin decodes each sheet with libgd, copies the icon's rectangle into a new true-colour image
//! and writes it with `gdImagePngPtr`. libgd keeps 7-bit alpha, so a sheet's alpha byte `a` comes
//! back as `255 - (2g + g / 64)` with `g = 127 - a / 2`; the sheet is decoded once per theme with
//! that already applied, and [`crate::gd_png`] writes the icon as libgd does.

use std::path::{Path, PathBuf};
use std::sync::OnceLock;

use njord_model::{IconInfo, IndexMap, Sprite, ThemeMode};

/// One theme's sheet: the index and the decoded RGBA pixels, each loaded on first use. A sheet
/// whose index or image cannot be read serves no icons (Kotlin logs and uses an empty map).
struct Sheet {
    base: PathBuf,
    index: OnceLock<IndexMap<Sprite, IconInfo>>,
    image: OnceLock<Option<Image>>,
}

struct Image {
    width: u32,
    height: u32,
    rgba: Vec<u8>,
}

pub struct SpriteSheet {
    sheets: Vec<(ThemeMode, Sheet)>,
}

impl SpriteSheet {
    /// The sheets under `<resources>/www/sprites/<theme>_simplified@2x.{json,png}`.
    pub fn new(resources: &Path) -> Self {
        let sheets = ThemeMode::ALL
            .iter()
            .map(|&theme| {
                let base = resources
                    .join("www/sprites")
                    .join(format!("{}_simplified@2x", theme.path_name()));
                (
                    theme,
                    Sheet {
                        base,
                        index: OnceLock::new(),
                        image: OnceLock::new(),
                    },
                )
            })
            .collect();
        SpriteSheet { sheets }
    }

    /// `spriteImage`: the icon as a PNG, or `None` when the sheet lacks the sprite. Decodes the
    /// sheet on first use (a few hundred milliseconds), so call it off the async runtime.
    pub fn sprite_image(&self, theme: ThemeMode, sprite: Sprite) -> Option<Vec<u8>> {
        let sheet = &self.sheets.iter().find(|(t, _)| *t == theme)?.1;
        let info = sheet
            .index
            .get_or_init(|| sheet.load_index())
            .get(&sprite)?;
        let image = sheet.image.get_or_init(|| sheet.load_image()).as_ref()?;
        let (w, h) = (
            u32::try_from(info.width).ok()?,
            u32::try_from(info.height).ok()?,
        );
        Some(crate::gd_png::encode_rgba(
            w,
            h,
            &image.copy(info.x, info.y, w, h),
        ))
    }
}

impl Sheet {
    fn load_index(&self) -> IndexMap<Sprite, IconInfo> {
        let path = self.base.with_extension("json");
        let parsed = std::fs::read(&path)
            .map_err(|e| e.to_string())
            .and_then(|bytes| serde_json::from_slice(&bytes).map_err(|e| e.to_string()));
        parsed.unwrap_or_else(|e| {
            tracing::error!("error decoding sheet {}: {e}", path.display());
            IndexMap::new()
        })
    }

    fn load_image(&self) -> Option<Image> {
        let path = self.base.with_extension("png");
        match decode(&path) {
            Ok(image) => Some(image),
            Err(e) => {
                tracing::error!("error decoding sheet {}: {e}", path.display());
                None
            }
        }
    }
}

impl Image {
    /// `gdImageCopy` into a fresh true-colour image: pixels outside the sheet read as gd's 0,
    /// opaque black.
    fn copy(&self, x: i32, y: i32, w: u32, h: u32) -> Vec<u8> {
        let mut out = Vec::with_capacity(w as usize * h as usize * 4);
        for dy in 0..h as i64 {
            for dx in 0..w as i64 {
                let (sx, sy) = (x as i64 + dx, y as i64 + dy);
                if (0..self.width as i64).contains(&sx) && (0..self.height as i64).contains(&sy) {
                    let i = (sy as usize * self.width as usize + sx as usize) * 4;
                    out.extend_from_slice(&self.rgba[i..i + 4]);
                } else {
                    out.extend_from_slice(&[0, 0, 0, 255]);
                }
            }
        }
        out
    }
}

/// The sheet as 8-bit RGBA, alpha passed through libgd's 7-bit round trip.
fn decode(path: &Path) -> Result<Image, String> {
    let file = std::fs::File::open(path).map_err(|e| e.to_string())?;
    let mut decoder = png::Decoder::new(std::io::BufReader::new(file));
    decoder.set_transformations(png::Transformations::EXPAND | png::Transformations::STRIP_16);
    let mut reader = decoder.read_info().map_err(|e| e.to_string())?;
    let mut buf = vec![0; reader.output_buffer_size()];
    let frame = reader.next_frame(&mut buf).map_err(|e| e.to_string())?;
    buf.truncate(frame.buffer_size());
    let mut rgba: Vec<u8> = match frame.color_type {
        png::ColorType::Rgba => buf,
        png::ColorType::Rgb => buf
            .as_chunks::<3>()
            .0
            .iter()
            .flat_map(|&[r, g, b]| [r, g, b, 255])
            .collect(),
        png::ColorType::GrayscaleAlpha => buf
            .as_chunks::<2>()
            .0
            .iter()
            .flat_map(|&[g, a]| [g, g, g, a])
            .collect(),
        png::ColorType::Grayscale => buf.iter().flat_map(|&g| [g, g, g, 255]).collect(),
        png::ColorType::Indexed => return Err("unexpanded palette".to_owned()),
    };
    for px in rgba.as_chunks_mut::<4>().0 {
        px[3] = gd_alpha_round_trip(px[3]);
    }
    Ok(Image {
        width: frame.width,
        height: frame.height,
        rgba,
    })
}

/// libgd's read (`127 - a / 2`) then write (`255 - (2g + g / 64)`) of an alpha byte.
fn gd_alpha_round_trip(a: u8) -> u8 {
    let g = 127 - (a as u32 >> 1);
    (255 - ((g << 1) + (g >> 6))) as u8
}
