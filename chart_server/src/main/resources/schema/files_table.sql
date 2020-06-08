CREATE TABLE IF NOT EXISTS files (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT,
    file        TEXT,    -- local path to file
                         -- http://www.naturalearthdata.com/downloads/10m-physical-vectors/
    type        INTEGER, -- 0 (s57), 1 (future), 2 (shp coastline), 3 (shp islands), 4 (shp reefs), 5 (shp ocean)
    md5sum      TEXT,
    depths      TEXT,
    soundings   TEXT,
    datum       TEXT,
    projection  TEXT,
    updated     TEXT,
    scale       INTEGER,
    z           INTEGER, -- optimal tile z index calculated from scale
    min_x       INTEGER, -- min tile x
    max_x       INTEGER, -- max tile x
    min_y       INTEGER, -- min tile y
    max_y       INTEGER, -- max tile y
    outline_wkt TEXT,    -- chart outline geometry LineString as WKT
    full_eval   INTEGER  --
);
CREATE INDEX IF NOT EXISTS idx_features ON files (z, min_x, max_x, min_y, max_y);