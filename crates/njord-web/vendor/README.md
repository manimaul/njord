# Vendored JavaScript

The web UI keeps MapLibre GL as JavaScript (RUST_PORT_PLAN.md §7). It is vendored here, unmodified,
so a build needs no npm and the UI works without internet access (the deb package runs on boats).

| File | Package | Version |
|---|---|---|
| `maplibre-gl-01590c9875ea5bb0.mjs` | [`maplibre-gl`](https://www.npmjs.com/package/maplibre-gl) `dist/maplibre-gl.mjs` | 6.19.0 |
| `maplibre-gl-worker-c8564c4a4147326a.mjs` | `dist/maplibre-gl-worker.mjs` | 6.19.0 |
| `maplibre-gl.css` | `dist/maplibre-gl.css` | 6.19.0 |

Licence: `maplibre-gl.LICENSE.txt` (BSD-3-Clause), the package's `LICENSE.txt`.

MapLibre GL 6 is ES modules only: a main module and a worker module it starts by URL, and no
`maplibregl` global. `../index.html` therefore has a module script that imports the main module,
names the worker with `setWorkerUrl` and publishes the module as `maplibregl`, which the bindings
in `../src/maplibre.rs` use.

**The two modules carry a content hash in their names**: the first 16 hex digits of the file's
SHA-256. Trunk copies them as they are (`rel="copy-file"`; it cannot hash a file that another
script imports by name), and the server caches a file as immutable only when its name ends in
such a hash (`njord-server`'s `handlers/web.rs`). A file under a fixed name would be downloaded
again on every page load. Trunk hashes the stylesheet itself.

To upgrade, from the new package's `dist/`:

```bash
cp maplibre-gl.css vendor/maplibre-gl.css
cp maplibre-gl.mjs "vendor/maplibre-gl-$(shasum -a 256 maplibre-gl.mjs | cut -c1-16).mjs"
cp maplibre-gl-worker.mjs "vendor/maplibre-gl-worker-$(shasum -a 256 maplibre-gl-worker.mjs | cut -c1-16).mjs"
```

Then delete the old two modules, put the new names in `../index.html` (four places) and update
this table.

Styling is Tailwind CSS, not a vendored stylesheet: Trunk compiles `../assets/app.css` with the
Tailwind standalone CLI (version pinned in `../Trunk.toml`), which it downloads once into its
cache next to wasm-bindgen and wasm-opt. The built site has no runtime JavaScript besides MapLibre
and the app itself.
