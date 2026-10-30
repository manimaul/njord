-- pins: sqlite_master DDL of a region archive: metadata, tiles (+ unique tile_index), charts, chart_tiles (WITHOUT ROWID, FK to charts) and chart_tiles_tile_idx
-- decisions: RE-1
-- generator: RegionExportTest

CREATE INDEX chart_tiles_tile_idx
    ON chart_tiles (zoom_level, tile_column, tile_row);

CREATE UNIQUE INDEX tile_index
    ON tiles (zoom_level, tile_column, tile_row);

CREATE TABLE chart_tiles (
    chart_name  TEXT    NOT NULL REFERENCES charts(name) ON DELETE CASCADE,
    zoom_level  INTEGER NOT NULL,
    tile_column INTEGER NOT NULL,
    tile_row    INTEGER NOT NULL,
    PRIMARY KEY (chart_name, zoom_level, tile_column, tile_row)
) WITHOUT ROWID;

CREATE TABLE charts (
    name        TEXT    PRIMARY KEY,
    scale       INTEGER NOT NULL,
    file_name   TEXT    NOT NULL,
    updated     TEXT    NOT NULL,
    issued      TEXT    NOT NULL,
    zoom        INTEGER NOT NULL,
    covr        TEXT    NOT NULL,
    dsid_props  TEXT    NOT NULL,
    chart_txt   TEXT    NOT NULL,
    ingested_at TEXT    NOT NULL
);

CREATE TABLE metadata (
    name  TEXT NOT NULL,
    value TEXT NOT NULL
);

CREATE TABLE tiles (
    zoom_level  INTEGER NOT NULL,
    tile_column INTEGER NOT NULL,
    tile_row    INTEGER NOT NULL,
    tile_data   BLOB    NOT NULL
);
