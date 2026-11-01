//! The settings the symbology reads. The server fills this from its `ChartsConfig`.

/// Depth thresholds in metres and the debug tile switch.
///
/// The thresholds come from the `shallowDepth` / `safetyDepth` / `deepDepth` configuration keys.
/// They are held as `f32` and the rules compare in `f32`, which decides the colour band of a depth
/// right at a threshold the way the goldens record.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct SymbologyConfig {
    pub shallow_depth: f32,
    pub safety_depth: f32,
    pub deep_depth: f32,
    /// Registers the `Debug` layerable (the `debugTile` configuration key).
    pub debug_tile: bool,
}

impl Default for SymbologyConfig {
    /// The shipped `config/application.json` values: 3 / 6 / 9 m, no debug tile.
    fn default() -> Self {
        SymbologyConfig {
            shallow_depth: 3.0,
            safety_depth: 6.0,
            deep_depth: 9.0,
            debug_tile: false,
        }
    }
}
