//! Light sector icons: the arc a sector light is drawn with.
//!
//! The icon is rendered on demand from the `SI` prop that `njord-symbology`'s `lights` writes. The server's
//! `GET /v1/sector_svg` and the web UI's `styleimagemissing` handler both draw with this, which is
//! why it lives in the contract crate.

use std::f64::consts::PI;

/// The icon centre, printed as `100.0` like the golden fixture (`testdata/symbology/svg_sector.json`).
const CENTER: &str = "100.0";

/// Renders a 200×200 SVG of the light sector between bearings `sectr1` and `sectr2`.
///
/// The arc (or a full circle when the bearings coincide) is drawn in `fill_color`, with dashed
/// sector legs in `line_color`. The S-57 bearings are from seaward towards the light, so both are
/// reversed (+180°). `radius` is the arc radius in the 200-unit view box (80 unless the caller
/// picks a smaller one).
pub fn build_sector_svg(
    sectr1: f64,
    sectr2: f64,
    fill_color: &str,
    line_color: &str,
    radius: f64,
) -> String {
    let sector1 = (sectr1 + 180.0) % 360.0;
    let sector2 = (sectr2 + 180.0) % 360.0;
    let cx = 100.0;
    let cy = 100.0;

    let a1 = sector1 * PI / 180.0;
    let a2 = sector2 * PI / 180.0;

    let x1 = cx + radius * a1.sin();
    let y1 = cy - radius * a1.cos();
    let x2 = cx + radius * a2.sin();
    let y2 = cy - radius * a2.cos();

    let (cxs, cys) = (CENTER, CENTER);
    let r = fmt(radius);
    let span = (sector2 - sector1 + 360.0) % 360.0;
    let arc_element = if span == 0.0 {
        format!(
            r#"<circle cx="{cxs}" cy="{cys}" r="{r}" fill="none" stroke="{fill_color}" stroke-width="4"/>"#
        )
    } else {
        let large_arc = if span > 180.0 { 1 } else { 0 };
        format!(
            "<path d=\"M {} {} A {r} {r} 0 {large_arc} 1 {} {}\"\n      fill=\"none\" stroke=\"{fill_color}\" stroke-width=\"4\" stroke-linecap=\"butt\"/>",
            fmt(x1),
            fmt(y1),
            fmt(x2),
            fmt(y2),
        )
    };

    let sector_lines_element = if span == 0.0 {
        String::new()
    } else {
        let line = |x: f64, y: f64| {
            format!(
                "<line x1=\"{cxs}\" y1=\"{cys}\" x2=\"{}\" y2=\"{}\"\n        stroke=\"{line_color}\" stroke-width=\"2\"\n        stroke-dasharray=\"4 6\" stroke-linecap=\"butt\"/>",
                fmt(x),
                fmt(y),
            )
        };
        format!("  {}\n  {}", line(x1, y1), line(x2, y2))
    };

    format!(
        "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 200 200\">\n  {arc_element}\n  {sector_lines_element}\n</svg>"
    )
}

/// One decimal, from the tenths rounded half to even, as the golden fixture prints coordinates.
///
/// The sign of a value in (-1, 0) is dropped (`-0.5` prints `0.5`); the icon never draws there.
fn fmt(x: f64) -> String {
    let tenths = (x * 10.0).round_ties_even() as i64;
    format!("{}.{}", tenths / 10, (tenths % 10).abs())
}
