# Change Reports

_Plan written 2026-10-31. Targets the Rust stack (`crates/`); see [Scheduling](#9-scheduling) for why it is not built in Kotlin._

## 1. Goal

Keep a durable history of every chart and world base map change, and show it in the Control Panel.

- A new **Change Report** tab under `/control/change_reports` lists dates, 25 per page:

  | Date | Actions | Errors |
  |---|---|---|
  | 2026-10-31 | 42 | 1 |

  **Actions** is the number of successful New + Update + Delete changes that day. **Errors** is the number of attempted New, Update or Delete changes that failed.
- Clicking a date opens the **Report** page (`/control/change_reports/2026-10-31`). It shows a count summary (New, Update, Delete, Error), then a table of every chart or base map changed or errored that day:

  | Outcome | Chart | Feature Count Change | Cause |
  |---|---|---|---|
  | Update | [US1EEZ1M.000](/chart/US1EEZ1M.000) | +312 | NOAA ENC Cron |
  | New | ne_10m_ocean.shp (base map) | +1 | Admin |
  | Error | US5WA22M.000 | 0 | Admin |

  - **Outcome**: `New`, `Update`, `Delete` or `Error`.
  - **Chart**: the chart name, linked to `/chart/<name>`. Base maps have no page, so they are shown unlinked with a "(base map)" label.
  - **Feature Count Change**: the signed change in that chart's or base map's stored feature count.
  - **Cause**: `Admin` or `NOAA ENC Cron`.

Hovering over or expanding an `Error` row shows the stored failure message.

## 2. Definitions

A **change event** is one row per chart or base map per action:

| Outcome | When | Feature Count Change |
|---|---|---|
| `new` | A chart was installed and no chart of that name existed before; or a shapefile was loaded and no `base_features` rows of that name and scale existed | `+after` |
| `update` | The chart or shapefile replaced an existing one | `after − before` (can be 0 or negative) |
| `delete` | `DELETE /v1/chart` removed a chart | `−before` |
| `error` | Any of the above failed, or was aborted | `after − before`, measured, not assumed (see below) |

An error also stores what was **attempted** (`new`, `update` or `delete`) and the failure message, so the Errors total can be broken down the same way as the Actions total.

The feature count change on an error is measured because a failed install is not always free of side effects. `Db::insert_chart` commits the chart row (and deletes the old features) in its own transaction, and the features go in a second transaction (`njord-server/src/ingest/chart.rs` `install_cell`). If that second transaction fails or the ingest is aborted, the chart is left with zero features. The report should show `−before` in that case, not 0.

**Days are UTC dates.** The date is taken when the event is recorded and stored in its own `day` column, so grouping does not depend on the database session time zone.

## 3. Schema (migration 4)

Additive: one new table, with no foreign key to `charts`, because a deleted chart's history must outlive the chart.

```sql
CREATE TABLE IF NOT EXISTS change_events
(
    id                   BIGSERIAL PRIMARY KEY,
    day                  DATE        NOT NULL,
    occurred_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    kind                 VARCHAR     NOT NULL CHECK (kind IN ('chart', 'base_map')),
    name                 VARCHAR     NOT NULL,          -- chart DSID_DSNM, or shapefile name
    outcome              VARCHAR     NOT NULL CHECK (outcome IN ('new', 'update', 'delete', 'error')),
    attempted            VARCHAR     NULL     CHECK (attempted IN ('new', 'update', 'delete')),
    feature_count_change BIGINT      NOT NULL,
    cause                VARCHAR     NOT NULL CHECK (cause IN ('admin', 'noaa_enc_cron')),
    message              VARCHAR     NULL,              -- error text
    zip_file             VARCHAR     NULL               -- the claimed upload, for tracing
);
CREATE INDEX IF NOT EXISTS change_events_day_idx ON change_events (day DESC, id);
```

- `crates/njord-db/src/migrations.rs`: add `MIGRATION_4`, bump `CURRENT_VERSION` to 4, and add the table to `INITIAL_SCHEMA`.
- Regenerate the P-DB-1 fixture `testdata/db/schema.json`, then extend `crates/njord-db/tests/migrations.rs` so that a fresh database and a database migrated from version 3 still end with identical schemas.
- `DELETE /v1/nuke` (`truncate_all`) also truncates `change_events`. Nuke is a full reset, and history that refers to charts which no longer exist is noise. Recording the nuke as an event instead is an open question (§10).
- Retention: no pruning at first. Each row is about 100 bytes, and a full NOAA reseed is roughly 1,500 rows. A `changeReportRetentionDays` config option can be added later if the table grows.

## 4. Recording the cause

Changes reach the server through three entry points, and the cause has to travel with each one:

| Entry point | Who uses it | How the cause is known |
|---|---|---|
| `POST /v1/enc_save`, `POST /v1/enc_url` → zip in `save/` | Admin (web UI) | The handler writes a sidecar file with `cause: admin` |
| `enc_cron` publishes `enc_bundle-<stamp>-NNNN.zip` straight into `save/` | NOAA ENC Cron | `njord-enc-cron` writes a sidecar file with `cause: noaa_enc_cron` |
| `DELETE /v1/chart?name=` | Admin, and `enc_cron` orphan deletion | New optional `cause=noaa_enc_cron` query parameter, sent by `njord-enc-cron/src/transport.rs` `delete_chart`; it defaults to `admin` |
| `POST /v1/chart` (chart row only) | Admin | Always `admin` |

**Sidecar:** `save/<zip>.meta.json` = `{"cause":"noaa_enc_cron"}`. It is written *before* the zip is renamed into `save/`, so the ingest worker never claims a zip without its sidecar. `IngestWorker::pending_zips` already ignores anything that does not end in `.zip`. `try_claim_and_ingest` moves the sidecar into the claim directory together with the zip, and passes the parsed cause to `chart::ingest` / `natural_earth::ingest`. If the sidecar is missing (for example a zip from an older cron image, or one copied in by hand), the worker falls back to `noaa_enc_cron` when the name starts with `enc_bundle-` and to `admin` otherwise. `DELETE /v1/enc_save` (reset) already empties `save/`, so it removes sidecars too.

The cause is asserted by the caller, not proven: the cron job signs its requests with the same admin credentials (`ENC_CRON_ADMIN_USER/PASS`). That is acceptable here because both callers are trusted admins and the cause is informational only.

## 5. Server changes (`crates/njord-server`, `crates/njord-db`)

### 5.1 Model (`crates/njord-model/src/change_report.rs`)

These types are shared with `njord-web`, use serde `camelCase`, and compile for wasm32:

```rust
enum ChangeOutcome { New, Update, Delete, Error }          // "new" | "update" | "delete" | "error"
enum ChangeCause   { Admin, NoaaEncCron }                   // "admin" | "noaa_enc_cron"
enum ChangeKind    { Chart, BaseMap }
struct ChangeReportDay  { date: String /* YYYY-MM-DD */, actions: i64, errors: i64 }
struct ChangeReportPage { page: Vec<ChangeReportDay>, page_index: i32, page_size: i32, total_days: i64 }
struct ChangeCounts     { new: i64, update: i64, delete: i64, error: i64 }
struct ChangeItem       { outcome, attempted: Option<ChangeOutcome>, kind, name, feature_count_change: i64,
                          cause, message: Option<String>, occurred_at: Timestamp }
struct ChangeReport     { date: String, counts: ChangeCounts, items: Vec<ChangeItem> }
```

The UI turns these into display text ("NOAA ENC Cron", "New"…), so the wire values stay stable.

### 5.2 DB (`crates/njord-db/src/change_events.rs`)

- `record_change_event(&mut PgConnection, &NewChangeEvent)` takes a connection, so an event can be written inside the transaction that made the change.
- `change_report_days(page, page_size) -> ChangeReportPage`:
  `SELECT day, count(*) FILTER (WHERE outcome <> 'error'), count(*) FILTER (WHERE outcome = 'error') FROM change_events GROUP BY day ORDER BY day DESC LIMIT $1 OFFSET $2`, plus `count(DISTINCT day)`. Offset paging is fine here because there is at most one row per day.
- `change_report(day) -> Option<ChangeReport>`: the counts and every item, ordered by `outcome` (error first), then `name`.
- `insert_chart` also returns the previous feature count (`SELECT count(*) FROM features WHERE chart_name = $1` inside the same transaction, before `delete_chart`), as `Option<i64>` (`None` = no chart existed). This tells the caller whether the outcome is New or Update and gives it `before`.
- The private `delete_chart` returns the number of feature rows it deleted, so `Db::delete_chart` can report `−before`.
- `delete_base_features` returns the number of rows deleted instead of `bool`.

### 5.3 Chart ingest (`ingest/chart.rs`, `ingest/report.rs`)

`install_cell` already knows each chart's fate. Record one event per cell:

- **Success:** write the event *inside* the feature-install transaction, just before `commit`. The change and its record then commit or roll back together. This needs a small `FeatureInstall::record(&NewChangeEvent)` in `njord-db/src/features.rs`, because the transaction is owned there.
- **Failure** (failed to open, `chart_insert` error, chart insert, feature insert or commit failure, or an abort after the lock was lost): after the rollback, query `feature_count(chart)` for `after` and write an `error` event in its own statement. `attempted` is `new` or `update`, based on `before`. A cell that failed to open has no `DSID_DSNM`, so its `.000` file name is used, as `Report::fail_chart` does today.
- An aborted ingest records its in-flight cells as errors with the message `aborted`. Cells that had not started are not recorded.

`Report` and the `CompletionReport` over the websocket stay as they are. The change report is a separate, durable record and does not change the ingest protocol that the UI and conformance suite depend on.

### 5.4 Base map ingest (`ingest/natural_earth.rs`)

Record one event per shapefile, with kind `base_map` and name = shapefile name (for example `ne_10m_ocean.shp`):

- `before` = the count returned by `delete_base_features`, `after` = the sum of inserted rows across the shapefile's layers.
- The outcome is `error` when the file failed to open, when the ingest was aborted, or when any row insert failed. Row failures are only logged today. In that last case the message is `<failed> of <total> features failed to insert`, and the measured delta is still stored.
- Base map ingest is not transactional (rows are inserted one at a time), so each event is written after the shapefile finishes.

### 5.5 Deletes (`handlers/charts.rs`)

- `202` → a `delete` event with `−before`.
- A DB error → an `error` event with `attempted = delete`.
- `204` (no such chart) → nothing is recorded.
- The cause comes from the new `cause` query parameter. An unknown value is 400, so that typos are not silently counted as Admin.

### 5.6 Routes (`njord-server/src/lib.rs`)

| Route | Answer |
|---|---|
| `GET /v1/change_reports?page=<n>` | `ChangeReportPage`, 25 per page (`page` defaults to 0) |
| `GET /v1/change_reports/{date}` | `ChangeReport`; 400 for a malformed date, 404 for a day with no events |

Both routes are **unsigned**, like `/v1/chart_catalog` and `/v1/chart_editions`. They expose chart names, counts and error text, but no secrets. Error messages come from GDAL and sqlx, so check they contain no filesystem paths beyond the claim directory, or strip those paths before they are stored. Whether to require a signature instead is an open question (§10).

## 6. Cron changes (`crates/njord-enc-cron`)

- `bundle.rs` `build_and_publish`: write `<bundle>.meta.json` into `save/` before renaming the bundle in.
- `transport.rs` `delete_chart`: append `&cause=noaa_enc_cron`.
- Tests: the existing transport fake asserts the query string, and a bundle test asserts the sidecar exists and is written before the zip.

## 7. Web UI (`crates/njord-web`)

- `ui/control_panel.rs`: add `TabData { key: "change_reports", sub_path: None, title: "Change Report" }` and a `"change_reports"` arm in the content match. The date page uses the tab's existing `:*path` sub-path (`/control/change_reports/<date>`), so **no new `NjordRoute` is needed** and the pinned route fixture `testdata/frontend/routes.json` does not change.
- New `ui/change_reports.rs`:
  - `ChangeReportsTab`: if the path is empty it shows the date list, otherwise the report page for that date.
  - **Date list:** an `nj-table nj-table-striped` table (as in `chart_table`) with columns Date | Actions | Errors. Each date is a `<Link>` to `/control/change_reports/<date>`. "Newer" and "Older" buttons step through the pages, and the current page is kept in the query string (`?page=2`), so the back button and bookmarks work.
  - **Report page:** a row of four count badges (New, Update, Delete, Error), then the Outcome | Chart | Feature Count Change | Cause table. Chart names link to `/chart/<name>`; base maps are shown as plain text. Feature counts are signed (`+312`, `−40`, `0`). Error rows are styled `table-danger`, and the message appears in a `title` tooltip and an expandable line. There is a "Back to Change Report" link.
- `api.rs` gets `change_report_page(page)` and `change_report(date)`, typed through `njord-model`. `store.rs` gets an `Async<ChangeReportPage>` keyed by page and an `Async<ChangeReport>` keyed by date. They load only when the tab is opened, like the other control-panel loads.
- Times are UTC dates. Each date heading is labelled "(UTC)" so viewers in other time zones are not confused.

## 8. Tests

| Layer | Test |
|---|---|
| `njord-model` | serde round trips and wire values for each enum (`noaa_enc_cron`, `base_map`) |
| `njord-db` (`tests/change_events.rs`) | day aggregation and paging over 30 synthetic days; `insert_chart` returns `None`, then `Some(n)`; the `delete_chart` count; an event written in a rolled-back `FeatureInstall` does not persist |
| `njord-server` ingest | Fixture `US5WA22M` installed → `new +n`. Same zip again → `update 0`. Signed delete → `delete −n`. A zip holding a corrupt `.000` → `error`, attempted `new`. Sidecar present, absent, and missing with an `enc_bundle-` name → the cause in each case. A Natural Earth fixture → a `base_map` event |
| `njord-enc-cron` | sidecar written before the zip; `cause=noaa_enc_cron` sent on delete |
| `njord-web` | the control-panel tab list; parsing the sub-path into a date |
| Conformance | new `conformance/test_api_13_change_reports.py`: upload → page lists today with `actions ≥ 1` → report lists the chart with cause `admin`; delete → `delete` row; bad date → 400. Mark it Rust-only (the Kotlin server has no such route), using the suite's implementation detection |
| Manual QA | both pages at 1440 and 390 widths, light and dark themes; paging; links to chart pages; the back button |

## 9. Scheduling

The schema is frozen while Kotlin and Rust share a database ([RUST_PORT_PLAN.md](RUST_PORT_PLAN.md) §8, §11), and this feature adds migration 4. It belongs in **§11 "Follow-ups after the port"**, and lands once Phase 7 has retired the Kotlin server. Until then a Kotlin instance would meet a schema version it does not know. Nothing is ported back to Kotlin.

Suggested order, one commit each:

1. `njord-model` types plus `njord-db` migration 4, `change_events.rs` and the count-returning `insert_chart`, `delete_chart` and `delete_base_features`, with DB tests and the regenerated schema fixture.
2. Server recording in chart ingest, base map ingest and deletes, plus the sidecar and cause plumbing, with ingest tests.
3. The two `GET /v1/change_reports` routes plus the conformance test.
4. `njord-enc-cron` sidecar and delete cause.
5. The `njord-web` tab and report page, plus manual QA.
6. Add this document to `RUST_PORT_PLAN.md` §11, and add `RELEASE_NOTES.md` entries.

History starts empty when it is deployed: past changes are not reconstructed, because there is no record of them to rebuild from.

## 10. Open questions

- Should the change report routes require an admin signature, rather than being public like the chart catalog?
- Should `DELETE /v1/nuke` wipe history (the plan above) or record one "nuke" event?
- Should `POST /v1/chart` (inserting a chart row with no features) be recorded? The plan records it as an Admin `new`/`update`, with change `−before`.
- Should days be UTC (the plan above) or the server's configured local zone?
