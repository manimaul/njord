# Vendored JavaScript

The web UI keeps MapLibre GL and Bootstrap as JavaScript (RUST_PORT_PLAN.md §7). They are vendored
here, unmodified, so a build needs no npm and the UI works without internet access (the deb package
runs on boats). Versions match `gradle/libs.versions.toml`, which the Kotlin UI uses:

| File | Package | Version |
|---|---|---|
| `maplibre-gl.js`, `maplibre-gl.css` | [`maplibre-gl`](https://www.npmjs.com/package/maplibre-gl) `dist/` | 4.7.1 |
| `bootstrap.min.css`, `bootstrap.bundle.min.js` (includes Popper) | [`bootstrap`](https://www.npmjs.com/package/bootstrap) `dist/` | 5.3.3 |

Licences: `maplibre-gl.LICENSE.txt` (BSD-3-Clause), `bootstrap.LICENSE` (MIT). To upgrade, copy the
same files from the new packages' `dist/` and update this table.
