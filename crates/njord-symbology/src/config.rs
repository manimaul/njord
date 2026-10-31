//! The `ChartsConfig` settings the symbology reads. The server fills this from its configuration.

/// Depth thresholds in metres (`shallowDepth` / `safetyDepth` / `deepDepth`) and the debug tile
/// switch. Kotlin holds the thresholds as `Float`, and the rules compare in `f32`.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct SymbologyConfig {
    pub shallow_depth: f32,
    pub safety_depth: f32,
    pub deep_depth: f32,
    /// Registers the `Debug` layerable (`ChartsConfig.debugTile`).
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
