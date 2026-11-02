# Vendored JavaScript

The web UI keeps MapLibre GL as JavaScript (RUST_PORT_PLAN.md §7). It is vendored here, unmodified,
so a build needs no npm and the UI works without internet access (the deb package runs on boats).
The version matches the one the former Kotlin UI used:

| File | Package | Version |
|---|---|---|
| `maplibre-gl.js`, `maplibre-gl.css` | [`maplibre-gl`](https://www.npmjs.com/package/maplibre-gl) `dist/` | 4.7.1 |

Licence: `maplibre-gl.LICENSE.txt` (BSD-3-Clause). To upgrade, copy the same files from the new
package's `dist/` and update this table.

Styling is Tailwind CSS, not a vendored stylesheet: Trunk compiles `../assets/app.css` with the
Tailwind standalone CLI (version pinned in `../Trunk.toml`), which it downloads once into its
cache next to wasm-bindgen and wasm-opt. The built site has no runtime JavaScript besides MapLibre
and the app itself.
