# Change Reports

_Plan written 2026-10-31, revised 2026-11-04 with the open questions resolved, and implemented the same day; §11 lists where the plan and the code differ in name._

## 1. Goal

Keep a durable history of every chart and world base map change, and show it in the Control Panel.

- A new **Change Report** tab under `/control/change_reports` lists dates, 25 per page:

  | Date (UTC) | Local time | Actions | Errors |
  |---|---|---|---|
  | 2026-10-31 | Oct 30, 2026, 5:00 PM PDT – Oct 31, 2026, 5:00 PM PDT | 42 | 1 |

  **Local time** is the span the UTC day covers in the browser's time zone. **Actions** is the number of successful New + Update + Delete + Nuke changes that day. **Errors** is the number of attempted changes that failed.
- Clicking a date opens the **Report** page (`/control/change_reports/2026-10-31`). It shows a count summary (New, Update, Delete, Nuke, Error), then a table of the charts and base maps changed or errored that day, 250 per page (the counts cover the whole day; "Previous" and "Next" step through the pages, and the page is kept in `?page=`):

  | Outcome | Chart | Feature Count Change | Cause | Local time |
  |---|---|---|---|---|
  | Update | [US1EEZ1M.000](/chart/US1EEZ1M.000) | +312 | NOAA ENC Cron | Oct 30, 2026, 8:02 PM PDT |
  | New | ne_10m_ocean.shp (base map) | +1 | Admin | Oct 31, 2026, 9:15 AM PDT |
  | Error | US5WA22M.000 | 0 | Admin | Oct 31, 2026, 9:20 AM PDT |
  | Nuke | all charts and base maps | −1,204,118 | Admin | Oct 31, 2026, 10:00 AM PDT |

  - **Outcome**: `New`, `Update`, `Delete`, `Nuke` or `Error`.
  - **Chart**: the chart name, linked to `/chart/<name>` only while that chart is installed (`installed` on the item, from a join against `charts`); a deleted or never-installed chart is plain text, so the report never links to a page that 404s. Base maps have no page, so they are shown unlinked with a "(base map)" label. A nuke row reads "all charts and base maps".
  - **Feature Count Change**: the signed change in that chart's or base map's stored feature count.
  - **Cause**: `Admin` or `NOAA ENC Cron`.
  - **Local time**: `occurredAt` in the browser's time zone and locale (`Date.toLocaleString`), with the zone abbreviation.

Hovering over or expanding an `Error` row shows the stored failure message.

Both pages are admin-only, like the rest of the Control Panel: the routes behind them require the admin signature (§5.6).

## 2. Definitions

A **change event** is one row per chart or base map per action:

| Outcome | When | Feature Count Change |
|---|---|---|
| `new` | A chart was installed and no chart of that name existed before; or a shapefile was loaded and no `base_features` rows of that name and scale existed | `+after` |
| `update` | The chart or shapefile replaced an existing one | `after − before` (can be 0 or negative) |
| `delete` | `DELETE /v1/chart` removed a chart | `−before` |
| `nuke` | `DELETE /v1/nuke` emptied the server (one row, kind `all`, name `*`) | `−(charts' features + base features before)` |
| `error` | Any of the above failed, or was aborted | `after − before`, measured, not assumed (see below) |

An error also stores what was **attempted** (`new`, `update`, `delete` or `nuke`) and the failure message, so the Errors total can be broken down the same way as the Actions total.

The feature count change on an error is measured because a failed install is not always free of side effects. `Db::insert_chart` commits the chart row (and deletes the old features) in its own transaction, and the features go in a second transaction (`njord-server/src/ingest/chart.rs` `install_cell`). If that second transaction fails or the ingest is aborted, the chart is left with zero features. The report should show `−before` in that case, not 0.

**`POST /v1/chart` is recorded.** It inserts a chart row with no features, so the chart has a feature count of 0 afterwards: an Admin `new` with change `0` when no chart of that name existed, or an Admin `update` with change `−before` when it replaced one.

**Nuke preserves history.** `DELETE /v1/nuke` records one `nuke` event and leaves every earlier event in place. The report then reads as it happened: the charts' installs, the nuke, and whatever was installed afterwards (all `new`, since the `charts` table is empty again).

**Time.** `occurred_at` is a `TIMESTAMPTZ` written with Postgres `now()`, exactly as `region_export_state.exported_at` is, so the two clocks agree and both are UTC instants on the wire (`njord_model::instant`). A report **day is the UTC date** of `occurred_at`, held in a generated `day` column so grouping never depends on the database session's time zone. The UI labels dates "(UTC)".

## 3. Schema

One new table, with no foreign key to `charts`, because a deleted or nuked chart's history must outlive the chart.

```sql
CREATE TABLE IF NOT EXISTS change_events
(
    id                   BIGSERIAL PRIMARY KEY,
    occurred_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    day                  DATE        NOT NULL GENERATED ALWAYS AS ((occurred_at AT TIME ZONE 'UTC')::date) STORED,
    kind                 VARCHAR     NOT NULL CHECK (kind IN ('chart', 'base_map', 'all')),
    name                 VARCHAR     NOT NULL,          -- chart DSID_DSNM, shapefile name, or '*' for a nuke
    outcome              VARCHAR     NOT NULL CHECK (outcome IN ('new', 'update', 'delete', 'nuke', 'error')),
    attempted            VARCHAR     NULL     CHECK (attempted IN ('new', 'update', 'delete', 'nuke')),
    feature_count_change BIGINT      NOT NULL,
    cause                VARCHAR     NOT NULL CHECK (cause IN ('admin', 'noaa_enc_cron')),
    message              VARCHAR     NULL,              -- error text
    zip_file             VARCHAR     NULL               -- the claimed upload, for tracing
);
CREATE INDEX IF NOT EXISTS change_events_day_idx ON change_events (day DESC, id);
```

- `crates/njord-db/src/migrations.rs`: add `MIGRATION_4` with the statements above, bump `CURRENT_VERSION` to 4, and add the table to `INITIAL_SCHEMA` so a fresh database and a migrated one end identical.
- Regenerate `testdata/db/schema.json` and add `change_events` to `TABLES` in `crates/njord-db/tests/migrations.rs`.
- `Db::truncate_all` **does not** truncate `change_events`; the nuke handler records its event instead (§5.5).
- Retention: no pruning at first. Each row is about 100 bytes, and a full NOAA reseed is roughly 1,500 rows. A `changeReportRetentionDays` config option can be added later if the table grows.

## 4. Recording the cause

Changes reach the server through four entry points, and the cause has to travel with each one:

| Entry point | Who uses it | How the cause is known |
|---|---|---|
| `POST /v1/enc_save`, `POST /v1/enc_url` → zip in `save/` | Admin (web UI) | The handler writes a sidecar file with `cause: admin` |
| `njord-enc-cron` publishes `enc_bundle-<stamp>-NNNN.zip` straight into `save/` | NOAA ENC Cron | `njord-enc-cron` writes a sidecar file with `cause: noaa_enc_cron` |
| `DELETE /v1/chart?name=` | Admin, and `njord-enc-cron` orphan deletion | New optional `cause=noaa_enc_cron` query parameter, sent by `njord-enc-cron/src/transport.rs` `delete_chart`; it defaults to `admin` |
| `POST /v1/chart`, `DELETE /v1/nuke` | Admin | Always `admin` |

**Sidecar:** `save/<zip>.meta.json` = `{"cause":"noaa_enc_cron"}`. It is written *before* the zip is renamed into `save/`, so the ingest worker never claims a zip without its sidecar. `IngestWorker::pending_zips` already ignores anything that does not end in `.zip`. `try_claim_and_ingest` moves the sidecar into the claim directory together with the zip, and passes the parsed cause to `chart::ingest` / `natural_earth::ingest`. If the sidecar is missing (a zip from an older cron build, or one copied in by hand), the worker falls back to `noaa_enc_cron` when the name starts with `enc_bundle-` and to `admin` otherwise. `DELETE /v1/enc_save` (reset) already empties `save/`, so it removes sidecars too.

The cause is asserted by the caller, not proven: the cron job signs its requests with the same admin credentials (`ENC_CRON_ADMIN_USER/PASS`). That is acceptable here because both callers are trusted admins and the cause is informational only.

## 5. Server changes (`crates/njord-server`, `crates/njord-db`)

### 5.1 Model (`crates/njord-model/src/change_report.rs`)

These types are shared with `njord-web`, use serde `camelCase`, and compile for wasm32:

```rust
enum ChangeOutcome { New, Update, Delete, Nuke, Error }    // "new" | "update" | "delete" | "nuke" | "error"
enum ChangeCause   { Admin, NoaaEncCron }                   // "admin" | "noaa_enc_cron"
enum ChangeKind    { Chart, BaseMap, All }                  // "chart" | "base_map" | "all"
struct ChangeReportDay  { date: String /* YYYY-MM-DD */, actions: i64, errors: i64 }
struct ChangeReportPage { page: Vec<ChangeReportDay>, page_index: i32, page_size: i32, total_days: i64 }
struct ChangeCounts     { new: i64, update: i64, delete: i64, nuke: i64, error: i64 }
struct ChangeItem       { outcome, attempted: Option<ChangeOutcome>, kind, name, feature_count_change: i64,
                          cause, message: Option<String>, occurred_at: Timestamp,
                          installed: bool /* a chart of this name exists now */ }
struct ChangeReport     { date: String, counts: ChangeCounts /* the whole day */, items: Vec<ChangeItem> /* one page */,
                          page_index: i32, page_size: i32, total_items: i64 }
```

The UI turns these into display text ("NOAA ENC Cron", "New"…), so the wire values stay stable.

### 5.2 DB (`crates/njord-db/src/change_events.rs`)

- `record_change_event(&mut PgConnection, &NewChangeEvent)` takes a connection, so an event can be written inside the transaction that made the change.
- `change_report_days(page, page_size) -> ChangeReportPage`:
  `SELECT day, count(*) FILTER (WHERE outcome <> 'error'), count(*) FILTER (WHERE outcome = 'error') FROM change_events GROUP BY day ORDER BY day DESC LIMIT $1 OFFSET $2`, plus `count(DISTINCT day)`. Offset paging is fine here because there is at most one row per day.
- `change_report(day, page, page_size) -> Option<ChangeReport>`: the whole day's counts (`GROUP BY outcome`, whose sum is `total_items`) and one page of items, ordered by `outcome` (error first), then `name`, then `id`, so the order is total and `LIMIT $2 OFFSET $3` paging is stable. `None` only when the day has no events; a page past the end has the counts and no items.
- `insert_chart` also returns the previous feature count (`SELECT count(*) FROM features WHERE chart_name = $1` inside the same transaction, before `delete_chart`), as `Option<i64>` (`None` = no chart existed). This tells the caller whether the outcome is New or Update and gives it `before`.
- The private `delete_chart` returns the number of feature rows it deleted, so `Db::delete_chart` can report `−before`.
- `delete_base_features` returns the number of rows deleted instead of `bool`.
- `truncate_all` counts `features` and `base_features` before truncating and returns the sum, so the nuke handler can record `−before`.

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

### 5.5 Chart row insert, deletes and nuke (`handlers/charts.rs`, `handlers/nuke.rs`)

`POST /v1/chart`:

- `insert_chart` succeeded → a `new` event with change `0`, or an `update` event with `−before`, cause `admin`.
- A DB error (400) → an `error` event with `attempted` from `before`, when it is known.

`DELETE /v1/chart`:

- `202` → a `delete` event with `−before`.
- A DB error → an `error` event with `attempted = delete`.
- `204` (no such chart) → nothing is recorded.
- The cause comes from the new `cause` query parameter. An unknown value is 400, so that typos are not silently counted as Admin.

`DELETE /v1/nuke`:

- `truncate_all` succeeded → one `nuke` event, kind `all`, name `*`, change `−before`, cause `admin`.
- `truncate_all` failed (500) → an `error` event with `attempted = nuke`. The event is written in its own statement after the failed truncate, so a nuke that fails is still visible in the report.

### 5.6 Routes (`njord-server/src/lib.rs`)

| Route | Answer |
|---|---|
| `GET /v1/change_reports?page=<n>&signature=` | `ChangeReportPage`, 25 per page (`page` defaults to 0) |
| `GET /v1/change_reports/{date}?page=<n>&signature=` | `ChangeReport`, 250 items per page (`page` defaults to 0; a page past the end has the counts and no items); 400 for a malformed date or page, 404 for a day with no events |

Both routes are **signed** (`handlers::Signed`), like `DELETE /v1/chart` and `/v1/nuke`: an empty 401 without a valid admin signature. The report is operator data, and the error messages come from GDAL and sqlx, so they may name files under `chartTempData`; keeping the routes behind the signature means those need no scrubbing.

## 6. Cron changes (`crates/njord-enc-cron`)

- `bundle.rs` `build_and_publish`: write `<bundle>.meta.json` into `save/` before renaming the bundle in.
- `transport.rs` `delete_chart`: append `&cause=noaa_enc_cron`.
- Tests: the existing transport fake asserts the query string, and a bundle test asserts the sidecar exists and is written before the zip.

## 7. Web UI (`crates/njord-web`)

- `ui/control_panel.rs`: add `TabData { key: "change_reports", sub_path: None, title: "Change Report" }` and a `"change_reports"` arm in the content match. The date page uses the tab's existing `:*path` sub-path (`/control/change_reports/<date>`), so **no new `NjordRoute` is needed** and the pinned route fixture `testdata/frontend/routes.json` does not change.
- New `ui/change_reports.rs`:
  - `ChangeReportsTab`: if the path is empty it shows the date list, otherwise the report page for that date.
  - **Date list:** an `nj-table nj-table-striped` table (as in `chart_table`) with columns Date | Actions | Errors. Each date is a `<Link>` to `/control/change_reports/<date>`. "Newer" and "Older" buttons step through the pages, and the current page is kept in the query string (`?page=2`), so the back button and bookmarks work.
  - **Report page:** a row of five count badges (New, Update, Delete, Nuke, Error), then the Outcome | Chart | Feature Count Change | Cause table. Chart names link to `/chart/<name>`; base maps and the nuke row are plain text. Feature counts are signed (`+312`, `−40`, `0`). Error rows are styled `table-danger`, and the message appears in a `title` tooltip and an expandable line. There is a "Back to Change Report" link. When the day has more than 250 items, a "Rows a–b of n" line and "Previous" / "Next" buttons page through them, with the page in `?page=` as on the date list; the count badges always cover the whole day.
- `api.rs` gets `change_report_page(signature, page)` and `change_report(signature, date)`, typed through `njord-model` and passing the stored admin signature as `delete_chart` does. `store.rs` gets an `Async<ChangeReportPage>` keyed by page and an `Async<ChangeReport>` keyed by date. They load only when the tab is opened, like the other control-panel loads, and a 401 sends the user to the admin login as the other signed calls do.
- Days are UTC: that is how `change_events.day` groups them and how the URL names them, so each date is labelled "(UTC)". Beside it the browser shows the span that UTC day covers in its own time zone (`utc_day_bounds` in `jiff`, formatted by `Date.toLocaleString` with the zone abbreviation), and each change row shows its `occurredAt` the same way, so a viewer in another time zone can see that a 03:00 UTC cron run was the previous evening locally.

## 8. Tests

| Layer | Test |
|---|---|
| `njord-model` | serde round trips and wire values for each enum (`noaa_enc_cron`, `base_map`, `nuke`) |
| `njord-db` (`tests/change_events.rs`) | day aggregation and paging over 30 synthetic days; a 31-item day paged by 10 (whole-day counts on every page, the pages concatenate to the full list, a page past the end is empty, page and size clamped); `insert_chart` returns `None`, then `Some(n)`; the `delete_chart` count; `truncate_all` returns the count and leaves `change_events` alone; an event written in a rolled-back `FeatureInstall` does not persist; `day` is the UTC date of `occurred_at` under a non-UTC session time zone |
| `njord-server` ingest | Fixture `US5WA22M` installed → `new +n`. Same zip again → `update 0`. Signed delete → `delete −n`. `POST /v1/chart` of a new name → `new 0`; of an installed chart → `update −n`. A zip holding a corrupt `.000` → `error`, attempted `new`. Sidecar present, absent, and missing with an `enc_bundle-` name → the cause in each case. A Natural Earth fixture → a `base_map` event. Nuke → one `nuke` event with `−(all features)` and every earlier event still present |
| `njord-server` routes | both routes 401 unsigned; signed `page` and `date` answers; 400 for a malformed date or page; 404 for an empty day; a report page past the end keeps the counts with no items |
| `njord-enc-cron` | sidecar written before the zip; `cause=noaa_enc_cron` sent on delete |
| `njord-web` | the control-panel tab list; parsing the sub-path into a date |
| Conformance | new `conformance/test_api_13_change_reports.py`, marked `port_only` (no golden exists for it, so it checks the live server only): unsigned → 401; signed upload → page lists today with `actions ≥ 1` → report lists the chart with cause `admin`; delete → `delete` row; bad date → 400. `test_api_99_nuke.py` gains a check that the nuke row appears and the earlier rows survive |
| Manual QA | both pages at 1440 and 390 widths, light and dark themes; paging; links to chart pages; the back button; the 401 path when the signature has expired |

## 9. Order of work

One commit each:

1. `njord-model` types plus `njord-db` migration 4, `change_events.rs` and the count-returning `insert_chart`, `delete_chart`, `delete_base_features` and `truncate_all`, with DB tests and the regenerated schema fixture.
2. Server recording in chart ingest, base map ingest, the chart row insert, deletes and nuke, plus the sidecar and cause plumbing, with ingest tests.
3. The two signed `GET /v1/change_reports` routes plus the conformance test.
4. `njord-enc-cron` sidecar and delete cause.
5. The `njord-web` tab and report page, plus manual QA.
6. `RELEASE_NOTES.md` entry.

History starts empty when it is deployed: past changes are not reconstructed, because there is no record of them to rebuild from.

## 10. Decisions

Resolved 2026-11-04:

- **The report routes require the admin signature.** They are not public like `/v1/chart_catalog`.
- **Nuke records a `nuke` event and preserves earlier events.** `truncate_all` leaves `change_events` alone.
- **`POST /v1/chart` is recorded.** The chart has 0 features afterwards, so the change is `0` for a new chart and `−before` for a replaced one.
- **Time follows `region_export_state.exported_at`**: `TIMESTAMPTZ` set by Postgres `now()`, reported as a UTC date.

## 11. As built

The implementation follows the plan above. Names that differ from the plan's sketches:

- `njord-db`: the previous feature count comes from `Db::insert_chart_tracked` (`insert_chart`
  keeps its signature and delegates to it); `Db::delete_chart(name, Option<ChangeCause>)`
  returns the features deleted and records the `delete` event inside its own transaction;
  `Db::chart_feature_count` and `Db::base_feature_count` give the `before` of a failure that
  happened before anything changed. `NewChangeEvent` (`change_events.rs`) has constructors for
  each case (`installed`, `install_failed`, `deleted`, `failed`, `nuked`), `REPORT_PAGE_SIZE` is
  25, and the schema fixture is regenerated with `NJORD_SCHEMA_DUMP=1` (see
  `tests/migrations.rs`).
- `njord-model`: the sidecar's shape is `UploadMeta` (`{"cause": …}`), shared by the server and
  `njord-enc-cron`; `ChangeKind::All` and `ChangeOutcome::Nuke` carry the nuke.
- `njord-server`: the sidecar lives in `ingest/cause.rs`; the chart ingest threads an `Upload`
  (cause plus zip name) through `install_cell`. `ingest/natural_earth.rs` records `new` when the
  shapefile had no rows before and `update` otherwise, counting a replaced shapefile's rows as
  its `before`.
- `njord-web`: paging pushes `?page=N` through `Store::push_query`, because `push_route` ignores
  a change that is only in the query string. The tab shows "Admin access required" until the
  admin is logged in, like the installer.
- Conformance: `test_api_13_change_reports.py` is `port_only`; `test_api_99_nuke.py` checks the
  nuke row on the Rust server only.
