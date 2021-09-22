CREATE TABLE charts
(
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR UNIQUE NOT NULL,
    scale      INTEGER        NOT NULL, -- DSPM_CSCL
    file_name  VARCHAR        NOT NULL, -- DSID_DSNM
    updated    VARCHAR        NOT NULL, -- DSID_UADT
    issued     VARCHAR        NOT NULL, -- DSID_ISDT

    -- Although these could be stored in th features table these we need some of this meta data in order to
    -- derive MINZ and MAXX when SCAMIN and SCAMAX are not defined. This allows us to NOT have to rely on insertion
    -- order.
    zoom       INTEGER        NOT NULL, -- Best display zoom level derived from scale and center latitude
    dsid_props JSONB          NOT NULL, -- DSID
    chart_txt  JSONB          NOT NULL  -- Chart text file contents e.g. { "US5WA22A.TXT": "<file contents>" }
);

-- indices

-- CREATE INDEX charts_gist ON charts USING GIST (m_covr_geom);
CREATE INDEX charts_idx ON charts (id);

------------------------------------------------------------------

CREATE TABLE styles
(
    id BIGSERIAL PRIMARY KEY,
    name  VARCHAR UNIQUE NOT NULL,
    style JSONB          NOT NULL
);

------------------------------------------------------------------

CREATE TABLE features
(
    id       BIGSERIAL PRIMARY KEY,
    layer    VARCHAR                       NOT NULL,
    geom     GEOMETRY(GEOMETRY, 4326)      NOT NULL,
    props    JSONB                         NOT NULL,
    chart_id BIGINT REFERENCES charts (id) NOT NULL,
    z_range  INT4RANGE                     NOT NULL
);

-- indices
CREATE INDEX features_gist ON features USING GIST (geom);
CREATE INDEX features_idx ON features (id);
CREATE INDEX features_layer_idx ON features (layer);
CREATE INDEX features_zoom_idx ON features USING gist (z_range);
