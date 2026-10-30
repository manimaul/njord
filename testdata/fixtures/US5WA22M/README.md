# US5WA22M

Real NOAA harbour cell (Tacoma / Commencement Bay, 1:15 000), copied unchanged from
`libgdal/src/nativeTest/resources/US5WA22M`. Base cell edition 40 with update files
`.001`–`.004`, which GDAL applies automatically when they sit next to the `.000`
(`DSID_UPDN` reads back as `4`), plus the four `.TXT` sidecars that become `chart_txt`.

**Pins:** the GDAL read contract for a real cell (P-IN-1, P-IN-2, P-IN-3, P-IN-5), the symbology
generator input (P-SY-2, P-SY-5), golden tiles (P-TE-5) and the update-application fixture
(P-IN-4: copy only the `.000` to a scratch directory to observe the pre-update state).
