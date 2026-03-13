# Release Notes

## 1.1

### Performance Improvements

- **Tile caching** — MVT tiles are now cached, eliminating redundant PostGIS queries for repeated tile requests
- **Batch DB queries** — Replaced N+1 query pattern with a single batched `ANY($2)` query
- **Composite index** — Replaced `INT4RANGE` z_range column with `z_min`/`z_max` integer columns and a composite B-tree index for 5–20x faster feature lookups
- **`ST_ClipByBox2D`** — Replaced `ST_Intersection` with the faster clip function for tile envelope clipping
- **HashMap layer dispatch** — O(1) layer lookup replaces O(n) linear scan over 100+ layer instances
- **PGBouncer** — Connection pooling support

### ⚠️ Breaking Change: Database Schema

Version 1.1 introduces an incompatible database schema change. Adopters upgrading from 1.0 must start with a **fresh database** — existing data cannot be migrated. Re-ingest your S-57 chart files after upgrading.

---

## 1.0

Initial release.

- S-57 layer coverage: 100+ object classes implemented (navigation aids, buoys, beacons, depths, lights, traffic separation zones, infrastructure, coastal features, and more)
- MVT tile serving backed by PostGIS
- Web UI with MapLibre GL frontend
