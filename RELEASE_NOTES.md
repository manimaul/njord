# Release Notes

## 1.2

### New Features

- **Light sectors** — Lights now render sector geometry showing the angular coverage and color of each sector
- **Region export for mobile** — Post-ingestion export of SQLite archives per geographic region for offline chart rendering on mobile clients (`libsqlite` module)
- **Feature bounding box export** — SQLite region exports include per-feature bounding boxes to support spatial indexing on mobile
- **ENC URL ingestion** — New endpoint (`EncUrlHandler`) to trigger ingestion directly from a remote ENC URL
- **Auto external URL detection** — Server automatically detects its external URL, removing the need to configure it manually
- **NOAA daily update cron** — Kubernetes cron job for automated daily NOAA ENC ingestion

### New S-57 Layers

40+ new object class layers including: ADMARE, ACHPNT, CONZNE, COSARE, CUSZNE, DRGARE, DRYDOC, EXEZNE, FSHZNE, GRIDRN, HRBARE, HRBFAC, LNDELV, LOCMAG, MAGVAR, MCOVR, MCSCL, MNPUB, MNSYS, MQUAL, MSDAT, MSREL, MVDAT, PIPOHD, PIPSOL, RADSTA, RAILWY, RAPIDS, RDOSTA, ROADWY, RSCSTA, SBDARE, SLOTOP, SMCFAC, SPRING, TESARE, TIDEWY, TSFEB, UWTROC, VEGATN, WATFAL, WATTUR, WRECKS

### Bug Fixes

- Fixed ingestion memory leak
- Fixed TSEZNE transparency (semi-transparent fill per S-52 `AC(TRFCF,3)`)
- Fixed light sector colors for day/dusk/night display modes
- Fixed daily cron job scheduling
- Fixed ingestion to process one zip file at a time (prevents concurrent ingest conflicts)

### Packaging & Deployment

- Debian packages for Linux x64, ARM64, and Raspberry Pi
- macOS development documentation and build support

---

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
