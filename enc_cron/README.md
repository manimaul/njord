```bash
# Report what would be downloaded without fetching anything
./gradlew :enc_cron:linkDebugExecutableArch
ENC_CRON_OPTS='{"chartEditionsUrl":"http://localhost:9000/v1/chart_editions","chartTempData":"./build/tmp/enc_cron"}' \
  ./enc_cron/build/bin/arch/debugExecutable/enc_cron.kexe --dry-run

# Parse a catalog already on disk instead of fetching 52 MB each time
... enc_cron.kexe --from-file ./ENCProdCat_19115.xml --dry-run
```

The Kotlin/Native run task is a plain `Exec` task, so `--args=` does not work and
`ENC_CRON_OPTS=` on the command line reaches the Gradle daemon rather than the process.
Pass both through Gradle properties instead:

```bash
./gradlew :enc_cron:runDebugExecutableArch -PencCronArgs="--dry-run"
```

```bash
./gradlew :enc_cron:runDebugExecutableArch -PencCronArgs="--dry-run /home/willard/source/njord/enc_cron/src/nativeMain/resources"
```

```bash
./gradlew :enc_cron:runDebugExecutableArch \
  -PencCronArgs="--dry-run" \
  -PencCronOpts='{"chartEditionsUrl":"http://localhost:9000/v1/chart_editions","chartTempData":"./build/tmp/enc_cron"}'
```

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
removed via `DELETE /v1/chart?name=...`, which also drops the tile cache and marks any region
archive containing them for re-export. Credentials come from the environment so a Kubernetes
secret can supply them without a password inside the `ENC_CRON_OPTS` blob:

```bash
ENC_CRON_ADMIN_USER=admin ENC_CRON_ADMIN_PASS=admin \
  ./enc_cron/build/bin/arch/debugExecutable/enc_cron.kexe --dry-run
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

