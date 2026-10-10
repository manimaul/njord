# Regenerate All Region Archives

There's no endpoint that rebuilds every region at once. `POST /v1/regions?name=<region>`
(`RegionHandler`) deletes that region's `region_export_state` row, which marks it stale, and
`RegionExportWorker` renders stale regions one at a time. To rebuild all of them, mark each
configured region stale.

In k8s, the worker runs **only in the `njord-ingest-rs` pod**, because `enableIngestion` is
`false` for `njord-chart-dep`. The POST is answered by a chart pod, and its `wake()` call doesn't
reach the ingest pod. The ingest worker finds the stale regions on its next idle poll, which can
take up to 15 minutes (`IDLE_DELAY_MS`). Cycle the ingest pod to make it check right away.

## Steps

```shell
# 1. Admin signature. One is reusable for adminExpirationSeconds (7 days).
sig=$(curl -s -u "${NJORD_ADMIN_USER}:${NJORD_ADMIN_PASS}" https://openenc.com/v1/admin | jq -r .signatureEncoded)

# 2. Mark every configured region stale (expect 202 for each)
for r in $(curl -s https://openenc.com/v1/regions | jq -r '.[].name'); do
  curl -s -o /dev/null -w "$r %{http_code}\n" -X POST "https://openenc.com/v1/regions?name=$r&signature=$sig"
done

# 3. Optional: start rendering now instead of within 15 min.
#    Skip this if an ingest is running: cycling the pod interrupts it.
./gradlew :cycleIngestPod

# 4. Watch progress
kubectl -n njord logs -f -l app=njord-ingest -c njord-ingest
curl -s https://openenc.com/v1/regions | jq -r '.[] | "\(.name)  \(.createdAt)  \(.archive)"'
```

A region is finished when its `createdAt` in the manifest moves past the time you ran step 2.

## Notes

- **Ingestion comes first.** Rendering pauses while zips are queued in
  `/mnt/njord/charts/save` or the ingest lock is held, for example during the nightly NOAA run.
- **Clients keep getting the old archive.** The manifest lists the newest archive on disk, and up
  to `MAX_ARCHIVES` (2) are kept per region. Clients keep downloading the previous archive until
  its replacement finishes.
- **One region:** run step 2 for that name only.
- **Don't use `DELETE /v1/nuke`.** It deletes the archives, and it also deletes every chart.
