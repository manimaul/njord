# Selective region rerender on chart ingestion

## Context

Njord exports per-region MBTiles archives (`server/src/nativeMain/kotlin/io/madrona/njord/ingest/RegionExporter.kt`) for offline/mobile chart download, covering NOAA regions configured statically in `ChartsConfig.regionExports` (currently 2 regions — `WORLD` and `REGION_15` — but expected to grow to many as more NOAA coverage areas are added).

Today, every chart ingestion unconditionally triggers a rebuild of **every configured region**, regardless of whether the newly ingested chart(s) actually fall inside that region:

- `ChartIngest.ingest()` (`ChartIngest.kt:53`) calls `Singletons.regionExportWorker.schedule()` after every ingested zip.
- `RegionExportWorker` (`RegionExportWorker.kt`) debounces this for 15s, then calls `RegionExporter.exportAll()`.
- `exportAll()` (`RegionExporter.kt:43-54`) loops over **every** `config.regionExports` entry and rebuilds it — a full chart/feature query plus tile rendering at every zoom level.
- `needsRebuild()` (`RegionExporter.kt:266-271`) is a hardcoded stub that always returns `true`, with a comment acknowledging it's meant to skip unnecessary rebuilds but was never implemented.

As the region count grows, ingesting a single chart in one region (e.g. Alaska) triggers a full, expensive rebuild of every other unrelated region (Puget Sound, Gulf of Mexico, etc.) worldwide. The goal is to only rebuild a region when a chart intersecting that region's coverage polygon was actually ingested (or re-ingested) since the region's last export.

Investigation ruled out one shortcut: the existing `charts.updated` column (and the matching but unused `RegionDao.latestChartUpdateInRegion()`) is **not** an ingestion timestamp — it holds the S-57-authored `DSID_UADT` edition date from the chart file itself, which has no relation to when the row was written to our DB. Comparing against it would be semantically wrong (a brand-new chart with an older `DSID_UADT` than an already-archived neighbor would be missed).

## Design

Track ingestion recency and per-region export recency **entirely in Postgres**, and let a single `ST_Intersects` + timestamp-comparison query answer "does this region need a rebuild?" This avoids threading chart IDs through the ingest pipeline (`ChartIngest` → `RegionExportWorker` → `RegionExporter`) and avoids any in-memory accumulator state that would be lost on a crash or overwritten by the debounce's job-cancellation — the DB is the single source of truth, so `RegionExportWorker.schedule()` and `ChartIngest.ingest()` need **no signature changes at all**.

### 1. Schema (`server/src/nativeMain/kotlin/io/madrona/njord/db/DbMigrations.kt`)

Add:
- `charts.ingested_at TIMESTAMPTZ NOT NULL DEFAULT now()` — a real DB-assigned insert timestamp, distinct from the existing `updated` (DSID_UADT) column. No app code needs to set it explicitly; the column default handles it on every `INSERT` (including re-ingestion via `ChartDao.insertAsync(..., overwrite = true)`, which deletes-then-reinserts by chart name, so re-ingesting a chart naturally refreshes `ingested_at`).
- `region_export_state (region_name VARCHAR PRIMARY KEY, exported_at TIMESTAMPTZ NOT NULL)` — bookkeeping table recording when each region was last successfully exported.

The existing migration system (`DB_VERSION` gate, `initializeSchema()` at lines 50-133) only runs its DDL block once, when `dbVersion() == 0` — it has no incremental-delta mechanism, so simply adding these to the `CREATE TABLE IF NOT EXISTS charts (...)` block (line 64-80) won't reach already-provisioned databases (e.g. the running openenc.com instance). Handle both cases:
- Add the new column/table to `initializeSchema()`'s DDL for fresh installs.
- Add a second, unconditional idempotent statement block (`ALTER TABLE charts ADD COLUMN IF NOT EXISTS ingested_at ...`, `CREATE TABLE IF NOT EXISTS region_export_state (...)`) that runs every startup regardless of `dbVersion()`, guarded by the same `DistributedLock` pattern already used in `run()`, so it safely reaches existing deployments. This is a deliberate, minimal patch to the existing "single version blob" migration system — not a rebuild of it (out of scope here).

### 2. `RegionDao.kt` — two new methods, following the existing `ST_Intersects`/text-mode-driver idioms already used by `findChartsInRegion`

```kotlin
suspend fun regionNeedsRebuild(coverageWkt: String, regionName: String): Boolean? = sqlOpAsync { conn ->
    conn.prepareStatement(
        """
        SELECT EXISTS (
            SELECT 1 FROM charts
            WHERE ST_Intersects(covr, ST_GeomFromText($1, 4326))
              AND ingested_at > COALESCE(
                  (SELECT exported_at FROM region_export_state WHERE region_name = $2),
                  '-infinity'::timestamptz
              )
        );
        """.trimIndent()
    ).apply { setString(1, coverageWkt); setString(2, regionName) }
        .executeQuery().use { rs -> if (rs.next()) rs.getBoolean(1) else false }
}

suspend fun markRegionExported(regionName: String): Unit? = sqlOpAsync { conn ->
    conn.prepareStatement(
        """
        INSERT INTO region_export_state (region_name, exported_at) VALUES ($1, now())
        ON CONFLICT (region_name) DO UPDATE SET exported_at = EXCLUDED.exported_at;
        """.trimIndent()
    ).apply { setString(1, regionName) }.execute()
}
```

Doing the comparison entirely in SQL (`now()` on write, `COALESCE(..., '-infinity')` on read) sidesteps a real constraint: the libpq driver here (`libpq/src/nativeMain/kotlin/`) is text-mode only with no `getTimestamp`/`Instant` accessor, so parsing a `TIMESTAMPTZ` value back into Kotlin would require fragile manual string parsing of Postgres's locale-dependent timestamp text. This design never needs to read a timestamp value into Kotlin at all.

### 3. `RegionExporter.kt`

- Replace the `needsRebuild()` stub (line 266-271) with a call to the new DAO method:
  ```kotlin
  private suspend fun needsRebuild(regionConfig: RegionExportConfig): Boolean =
      regionDao.regionNeedsRebuild(regionConfig.coverage, regionConfig.name) ?: true // fail-open: rebuild on DB error
  ```
  No `isWorld` special-casing needed — `WORLD`'s coverage polygon spans the whole globe, so it always geometrically intersects any newly ingested chart, meaning it naturally continues to rebuild on every ingest, matching current behavior exactly.
- In `exportRegion()` (line 67-110), move the `needsRebuild` check to happen **before** the `findChartsInRegion` fetch (currently it's checked after, at line 85, so today's stub provides zero savings even hypothetically — the expensive per-chart row fetch already ran). New order: check `!force && !needsRebuild(regionConfig)` → skip, then proceed to `findChartsInRegion`/rendering as before.
- After a successful archive write and `pruneOldArchives()` call (end of the function, success path only — not on the "no charts found" skip paths), call `regionDao.markRegionExported(regionConfig.name)`. Since `exportForced()` (used by the manual `POST /v1/regions` endpoint in `RegionHandler.kt`) also funnels through `exportRegion()`, this keeps `region_export_state` accurate regardless of whether a rebuild was triggered by ingestion or a manual force, preventing a redundant rebuild on the next debounce cycle.
- `exportAll()` and `exportForced()` need no signature changes.

### 4. No changes to `ChartIngest.kt` or `RegionExportWorker.kt`

`schedule()` stays a plain no-arg debounce trigger; `ingest()`'s call site at `ChartIngest.kt:53` is untouched. All "what actually changed" logic lives in the DB query above.

### Expected one-time transition cost

On first deploy of this change, `region_export_state` starts empty for every region, so `exported_at` defaults to `-infinity` and the first post-deploy export cycle will rebuild every region that has any intersecting chart — a one-time full rebuild, matching today's baseline behavior, after which only genuinely affected regions rebuild.

## Files to change

- `server/src/nativeMain/kotlin/io/madrona/njord/db/DbMigrations.kt` — add column/table to `initializeSchema()` DDL, plus an unconditional idempotent patch step for existing DBs.
- `server/src/nativeMain/kotlin/io/madrona/njord/db/RegionDao.kt` — add `regionNeedsRebuild()` and `markRegionExported()`.
- `server/src/nativeMain/kotlin/io/madrona/njord/ingest/RegionExporter.kt` — wire `needsRebuild()` to the new DAO method, reorder the check, call `markRegionExported()` on success.

## Verification

- Add `server/src/nativeTest/kotlin/RegionDaoTest.kt` following the live-Postgres pattern already used by `ChartQueryTest.kt` (`server/src/nativeTest/kotlin/ChartQueryTest.kt`, connecting to `postgresql://admin:mysecretpassword@localhost:6432/s57server`): insert two charts with distinct `covr` polygons in different regions, call `markRegionExported` for one region, insert a new chart intersecting only the *other* (unmarked) region, and assert `regionNeedsRebuild` returns `true` only for the region with the newer intersecting chart and `false` for the already-exported one.
- Manual end-to-end check against the dev stack (`podman-compose up` in `chart_server_db/`, `./gradlew :server:runDebugExecutable`): ingest a chart intersecting `REGION_15`, confirm via server logs that only `REGION_15` (and `WORLD`) rebuild, not any other configured region; ingest an unrelated chart outside `REGION_15`'s coverage and confirm `REGION_15`'s archive file/timestamp does not change on the next debounce cycle.
- Run `./gradlew :server:allTests` to confirm nothing else regresses.
