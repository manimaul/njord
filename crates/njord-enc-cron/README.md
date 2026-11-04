# njord-enc-cron

Nightly NOAA ENC updater ([design](../../docs/NOAA_ENC_UPDATE_CRON.md)): streams NOAA's ISO-19115 product catalog,
diffs it against `GET /v1/chart_editions`, and downloads only the cells whose revision changed, bundling them into
zips renamed into the server's `save/` directory.

Usage: `njord-enc-cron [resources dir] [--from-file <catalog.xml>] [--dry-run]`. Configuration is
`<resources dir>/config/enc_cron.json` (shipped as [`resources/config/enc_cron.json`](resources/config/enc_cron.json))
with the `ENC_CRON_OPTS` JSON object merged over it.

```bash
cargo build --release -p njord-enc-cron

# Report what would be downloaded without fetching anything
ENC_CRON_OPTS='{"chartEditionsUrl":"http://localhost:9000/v1/chart_editions","chartTempData":"/tmp/njord"}' \
  ./target/release/njord-enc-cron ./crates/njord-enc-cron/resources --dry-run

# Parse a catalog already on disk instead of fetching ~52 MB each time
./target/release/njord-enc-cron ./crates/njord-enc-cron/resources --from-file ./ENCProdCat_19115.xml --dry-run
```

`chartTempData` must be the server's own `chartTempData`: bundles are renamed into its `save/` directory, where the
ingest worker picks them up. Each bundle is published with a `<bundle>.meta.json` sidecar
(`{"cause":"noaa_enc_cron"}`), written before the bundle is renamed in, so the server's change
report ([docs/change_reports.md](../../docs/change_reports.md)) attributes every chart in it to the
cron rather than to the admin.

Every run also reports charts Njord holds that the catalog no longer lists - withdrawn cells, or
cells ingested from somewhere other than NOAA:

```
INFO  3 chart(s) held but not listed in the catalog:
INFO    US3AK4AM.000
INFO    US4AK4BM.000
INFO    US5AK4CM.000
```

The list is capped at 50 names per run.

## Deleting withdrawn charts

With `deleteOrphans` (default true) and admin credentials configured, those charts are then
removed via `DELETE /v1/chart?name=...&cause=noaa_enc_cron`, which also drops the tile cache,
marks any region archive containing them for re-export, and records the delete under the cron's
name in the change report. Credentials come from the environment so a Kubernetes
secret can supply them without a password inside the `ENC_CRON_OPTS` blob:

```bash
ENC_CRON_ADMIN_USER=admin ENC_CRON_ADMIN_PASS=admin \
  ./target/release/njord-enc-cron ./crates/njord-enc-cron/resources --dry-run
```

Without both set, deletion logs a WARN and does nothing. Two guards limit the damage a bad
catalog can do:

- Only orphans whose two-letter producer code appears in the catalog are eligible, so a chart
  ingested from a non-NOAA hydrographic office is never deleted.
- If more than `maxOrphanDeletes` (default 25) charts qualify, the pass is abandoned and they are
  only reported - a truncated catalog download is indistinguishable from a mass withdrawal.

`--dry-run` prints `would delete ...` instead.

Charts are matched on a revision key of `"<DSID_UPDN>:<DSID_UADT>:<DSID_ISDT>"`, **not** on the
S-57 edition number. GDAL overwrites `DSID_EDTN` with the value carried by the last applied
`.00N` update file, and NOAA publishes `0` there for some cells (e.g. `US1GC09M`, catalog edition
`74.6`), so an edition-based comparison never matches for them and re-downloads them forever.
