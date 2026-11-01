# testdata

Golden fixtures for the Rust port ([RUST_PORT_PLAN.md §5](../docs/RUST_PORT_PLAN.md#5-tests-to-write-before-the-port),
[Phase 0 checklist](../docs/RUST_PORT_PHASE0_CHECKLIST.md)). Every expected value in here is
was produced by the **Kotlin** implementation, which has since been removed (Phase 7); the
fixtures stay as the Rust crates' regression tests. Fixture IDs (`P-SY-1`, `P-TE-5`, …) are
defined in §5 of the plan. `generator` headers name the Kotlin test that wrote each file; that code
is in the git history (`master`).

The Rust helper crate `njord-testdata` reads this directory, or `$NJORD_TESTDATA`.

## Layout

| Directory | Fixture IDs | Contents |
|---|---|---|
| `symbology/` | P-SY-1 … P-SY-9 | `pre_tile_encode/<KEY>.json` (one file per `Layerable`; non-uppercase keys under `custom_key/`), `pre_tile_encode_cell/<LAYER>.json` (every US5WA22M feature), `depth_bands.json`, `display_depths.json`, `light_description.json`, `light_number_format.json`, `attribute_helpers.json`, `natsur.json`, `style/<depth>_<theme>[_<custom>].json`, `sprites.json`, `svg_sector.json` |
| `geo/` | P-TE-1 … P-TE-5, LB-1 | `tile_system.json`, `mvt/<case>.bin` + `.json`, `ogr_geometry.json`, `filtered_props.json`, `base_map_scale.json`, golden tiles |
| `s57/` | P-IN-1 … P-IN-6 | `chart_insert.json`, `layer_counts.json`, `US5WA22M/features/<LAYER>.json`, `synthetic/<CELL>/features/<LAYER>.json`, `US5WA22M/db_state.json`, `update_cell/{dsid,changed_features}.json`, `natural_earth.json` (shapefile), `natural_earth_names.json` (layer name / scale tables) |
| `ws/` | P-IN-7 | `messages.json` — every `WsMsg` subtype |
| `db/` | P-DB-1, P-DB-3, P-DB-5, P-DB-7 | `schema.json`, `feature_queries.json`, catalog / editions tables, `retry.json` |
| `regions/` | P-RE-1 … P-RE-5 | `schema.sql`, `metadata.json`, `timestamps.json`, `manifest.json` |
| `admin/` | P-SEC-1, P-SEC-2 | `hmac_vectors.json`, `signature_vectors.json`, `verify_cases.json` |
| `security/` | P-SEC-3 | `paths.json` |
| `concurrency/` | P-CC-1, P-CC-3 | `lock_semantics.json` (D6 decision table), `cadence.json` |
| `enc_cron/` | P-EC-1 … P-EC-5 | `catalog_sample.xml` + `parsed.json`, `selection.json`, `bundle_layout.json`, `config.json`, `delete_orphans.json` |
| `frontend/` | §3.10 | `routes.json` — `NjordRoute` patterns and `RouteMatcher` behaviour, mirrored from the former `shared_fe` `RouteFixtureTest` |
| `fixtures/` | inputs | `US5WA22M/` (real NOAA cell with `.001`–`.004` updates and `.TXT` sidecars), `synthetic/` (two generated cells, see its README), `ne_110_extract/` (Natural Earth 110m coastline shapefile) |
| `tools/` | — | `make_synthetic_cells.py`, `trim_catalog.py` (input generators, need GDAL Python bindings) |

Empty directories hold a `.gitkeep` until their first fixture lands.

## Conventions

**Table fixtures** are one JSON object per file: a header followed by `cases`, a list of
`{ "input": …, "expected": … }`. They are table-driven on both sides.

```json
{
  "pins": "displayDepths: metres split into whole and tenths parts, drying heights keep their sign",
  "decisions": ["D1", "SY-1", "SY-2"],
  "generator": "SoundgTest.displayDepths",
  "source": "0.0..31.0 in 0.1 steps plus -0.1..-3.0",
  "cases": [
    { "input": 12.3, "expected": { "METERS": 12, "METERS_T": 3 } }
  ]
}
```

**Port divergences.** D2–D9 are accepted in Kotlin and fixed in the Rust port
([TEST_COVERAGE_AUDIT.md §3.1](../docs/TEST_COVERAGE_AUDIT.md#31-decisions-2026-10-30)). A case
where the two legitimately differ keeps the Kotlin value in `expected` and adds
`"port": { "decision": "D4", "expected": <corrected value> }`; the decision must also appear in
the header's `decisions`. The Rust tests assert `port.expected` when present. Conformance cases
that only pass on the port are tagged `port_only` and skipped against a Kotlin server.

```json
{ "input": "not-base64!", "expected": { "throws": "IllegalArgumentException" },
  "port": { "decision": "D4", "expected": false } }
```

**Binary fixtures** (MVT tiles, protobuf) are `<name>.bin` plus a `<name>.json` sidecar holding
the same header, `bin` (file name), `bytes` (count) and `decoded`, a readable view of the bytes.
The bytes are Kotlin's; the Rust tests compare the decoded view, because two correct MVT encoders
may legally order tags differently (§5.2, P-TE-2). A binary fixture whose Kotlin bytes are an
accepted defect also carries `"port": { "decision": "D13", "decoded": <view> }`; the Rust tests
compare against `port.decoded` for it.

**Every fixture file carries a header naming the decision it pins** (§8, "Fixtures freeze bugs
as behavior"). JSON files carry it as the top-level `pins` / `decisions` / `generator` /
`source` fields; XML and SQL files carry it as a leading comment; opaque inputs such as the S-57
cells carry a `README.md` sidecar in their directory. `decisions` lists the defect decisions
(`D1`–`D9`) and audit IDs (`SY-3`, `TE-1`, …) the expected values depend on, so a fixture whose
decision is later reversed can be found with `grep`. No fixture is frozen while a decision it
depends on is still open.

**Formatting**: two-space indented JSON, keys in the order the Kotlin serializer emits them,
`\n` line endings.

## Verify

The fixtures are frozen: with the Kotlin generators gone there is no regenerate mode. The Rust
tests only verify (`cargo test --workspace`, see [README.md](../README.md#testing); the database
tests need the dev PostGIS). A deliberate behaviour change edits the affected expected values (or
adds a `port` block) by hand, in the same commit as the code change, and the header's `decisions`
names why.

Inputs under `fixtures/` are regenerated with the scripts in `tools/`, which need the GDAL
Python bindings (`brew install gdal` / `python3-gdal`); both scripts are deterministic.

**GDAL version.** Fixtures whose values come out of the GDAL S-57 reader or GEOS geometry ops
(`s57/*`, `symbology/pre_tile_encode_cell/*`, `geo/ogr_geometry.json`, golden tiles) record the GDAL
release in their header `source`. They were generated on macOS with GDAL 3.13.1; a different GDAL
may move geometry vertices and fail those comparisons.

**Known Kotlin quirks pinned as behaviour** (see TEST_COVERAGE_AUDIT.md §3): `TileSystem.mapSize`
overflows `Int` from z19 (D10), so `geo/tile_system.json` records nonsense for z ≥ 19; style layer ids
depend on how many times the style was served, so `symbology/style/*` pins the first request of a
fresh `LayerFactory`.
