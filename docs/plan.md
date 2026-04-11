# Regions and Mobile Datasets 

We need a way to export chart data in the form of geographic regions. For example "US Coast Guard region 15" (Pacific NorthWest - Puget Sound to Canadian Border). The export should be a downloadable file that a mobile app can download and consume. Sqlite files will be used as the region export file format. After charts are ingested region(s) (sqlite files) will be created in a background process. Region processing can also be disabled in the main (application.json) config ("regionExports": []). Regions will be stored in sqlite files within the temp directory (for example /tmp/njord/regions/REGION15.sqlite).

Region export configurations will be specified in the applicatin.json config file. Coverage can be calculated using enc_boundary_wkt.py.
```json
{
    "adminKey": "changeme",
    "adminUser": "admin",
    "adminPass": "admin",
    "adminExpirationSeconds": 604800,
    "pgConnectionInfo": "postgresql://admin@localhost:5432/s57server",
    "host": "0.0.0.0",
    "port": 9000,
    "consoleMetrics": true,
    "chartTempData": "/tmp/njord",
    "webStaticContent": "../web/build/dist/js/productionExecutable",
    "shallowDepth": 3.0,
    "safetyDepth": 6.0,
    "deepDepth": 9.0,
    "debugTile": false,
    "chartIngestWorkers": 5,
    "enableIngestion": true,
    "useTileCache": true,
    "regionExports": [
      {
        "name": "REGION_15",
        "description": "Pacific NorthWest - Puget Sound to Canadian Border",
        "coverage": "POLYGON ((-120.5 43.2,-120.5652407 43.2,-120.5652407 42.0913546,-128.4022651 42.0913546,-128.4022651 43.2,-128.4022651 48.0,-128.4022651 49.262033,-120.5652407 49.262033,-120.5652407 48.0,-120.5 48.0,-120 48,-120 47.4827599,-120 45.812352,-120 45.8123413,-120 43.2,-120.5 43.2))"
      }
    ]
}
```

Region sqlite files will contain the following tables:

```sql
CREATE TABLE IF NOT EXISTS chart
(
    id         INTEGER PRIMARY KEY,
    name       TEXT UNIQUE           NOT NULL, -- DSID_DSNM
    scale      INTEGER               NOT NULL, -- DSPM_CSCL
    file_name  TEXT                  NOT NULL, -- actual file name
    updated    TEXT                  NOT NULL, -- DSID_UADT
    issued     TEXT                  NOT NULL, -- DSID_ISDT
    zoom       INTEGER               NOT NULL, -- Best display zoom level derived from scale and center latitude
    covr_wkb   BLOB                  NOT NULL, -- Coverage area from "M_COVR" layer feature with "CATCOV" = 1
    dsid_props TEXT                  NOT NULL, -- DSID
    chart_txt  TEXT                  NOT NULL  -- Chart text file contents e.g. { "US5WA22A.TXT": "<file contents>" }
);
```

```sql 
CREATE TABLE IF NOT EXISTS features 
(
    id INTEGER PRIMARY KEY,
    layer     TEXT NOT NULL, -- name of layer
    geom      BLOB NOT NULL, -- wkb geometry
    props     TEXT NOT NULL, -- json props
    chart_id  INTEGER NOT NULL,
    FOREIGN KEY(chart_id) REFERENCES charts(id)
);
```

```sql 
CREATE TABLE IF NOT EXISTS lnam_refs (
    fid INTEGER,
    lnam_ref TEXT,
    FOREIGN KEY (fid) REFERENCES features(id)
);
```

Mobile devives will have the equivalent sqlite tables as above plus indexes and tile_cache table:

```sql 
CREATE VIRTUAL TABLE feature_geo_index USING rtree(
   id,                -- Primary Key
   min_z, max_z,      -- Z range 
   min_x, max_x,      -- X range 
   min_y, max_y       -- Y range 
);
```

```sql
CREATE TABLE IF NOT EXISTS tile_cache 
(
    z INTEGER NOT NULL, -- tile z coordinate
    x INTEGER NOT NULL, -- tile x coordinate
    y INTEGER NOT NULL, -- tile y coordinate
    tile BLOB NOT NULL, -- tile data
    PRIMARY KEY (z, x, y)
);
```

# Mobile use - how region sqlite files will be used

When region sqlite files are downloaded by a mobile client their contents will be loaded into the apps' sqlite db. The app can then discard the downloaded file. The mobile app will have its own world base map included. When a tile is queried the same algorithm used in the server
will be applied by the mobile app. 

# Ingestion reports

We want to track chart ingestion via a postgres table. Things we should track are the date and time of ingestion start and end for each zip file uploaded, the name of the zip file, the CompletionReport as a json row and uuid. We should also tag each chart table row with the uuid of the association ingestion.

```sql

CREATE TABLE IF NOT EXISTS ingestions
(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    zip_file_name VARCHAR NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NULL,
    completion_report JSONB NULL
)

ALTER TABLE charts
    ADD COLUMN IF NOT EXISTS ingestion_id UUID REFERENCES ingestions(id) NULL
```

Lets also add an endpoint GET /ingestions. The endpoint will return a total count of ingestions and a list of ingestion items.
The endpoint will have a query param for "after" and "count". The endpoint will return the count specified of ingestions ordered by complete_at descending after the date specified. If after is not specified we'll just return the latest count of ingestions. Count will clamp to 1 to 100.

# Region export process

Region generation will occur after an ingestion if there are no longer any zip files enqueued in the upload directory. How this might work is every time the ingestion lock is released a coroutine could start with a 15 second delay. If there's already a coroutine schedule it will get canceled and reset. After the 15 seconds the coroutine will check for no more zip files enqueued and for a clear lock. If these conditions are met then the region export process will start.

For each region defined in config
1. Check if new charts were added since the last region was created. If not then skip creating a new region.
2. 


```sql

```

# Questions

How does encryption work for protected datasets?
