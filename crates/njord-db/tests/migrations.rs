//! P-DB-1 / DB-1: the schema contract. An empty database is provisioned at the current version
//! and matches `db/schema.json`; a version-1 database (surrogate `charts.id`,
//! `features.chart_id`, no `ingested_at`, no `region_export_state`, `lnam_refs` never written)
//! migrates in order with its data kept and ends with the same schema; re-running and racing
//! runners are no-ops. Mirrors the Kotlin `DbMigrationsTest`.

mod common;

use common::{drop_database, exec, fresh_db, migrate};
use njord_db::{AdvisoryLock, CURRENT_VERSION, Db};
use njord_testdata::{Failures, cases, first_difference, strip_nulls};
use serde_json::{Value, json};
use sqlx::Row;

const TABLES: [&str; 5] = [
    "meta_data",
    "charts",
    "features",
    "base_features",
    "region_export_state",
];

/// `DbMigrationsTest.schemaOf`: columns (udt, nullability, default), `pg_indexes` definitions and
/// constraints of one table, each sorted.
async fn schema_of(db: &Db, table: &str) -> Value {
    let pool = db.pool();
    let columns = sqlx::query(
        "SELECT column_name::text, udt_name::text, is_nullable::text, column_default::text
         FROM information_schema.columns
         WHERE table_schema = 'public' AND table_name = $1 ORDER BY column_name;",
    )
    .bind(table)
    .fetch_all(pool)
    .await
    .unwrap()
    .iter()
    .map(|r| {
        let default: Option<String> = r.get(3);
        json!({
            "name": r.get::<String, _>(0),
            "type": r.get::<String, _>(1),
            "nullable": r.get::<String, _>(2) == "YES",
            "default": default.filter(|d| !d.is_empty()),
        })
    })
    .collect::<Vec<_>>();
    let indexes: Vec<String> = sqlx::query_scalar(
        "SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' AND tablename = $1 ORDER BY indexdef;",
    )
    .bind(table)
    .fetch_all(pool)
    .await
    .unwrap();
    let constraints: Vec<String> = sqlx::query_scalar(
        "SELECT conname || ': ' || pg_get_constraintdef(oid) FROM pg_constraint
         WHERE conrelid = ('public.' || $1)::regclass ORDER BY 1;",
    )
    .bind(table)
    .fetch_all(pool)
    .await
    .unwrap();
    let mut schema = json!({ "columns": columns, "indexes": indexes, "constraints": constraints });
    strip_nulls(&mut schema);
    schema
}

async fn check_schema(db: &Db, label: &str, failures: &mut Failures) -> usize {
    let cases = cases("db/schema.json");
    assert_eq!(
        cases
            .iter()
            .map(|c| c["input"].as_str().unwrap())
            .collect::<Vec<_>>(),
        TABLES
    );
    for case in &cases {
        let table = case["input"].as_str().unwrap();
        if let Some(d) = first_difference(&case["expected"], &schema_of(db, table).await, "") {
            failures.push(format!("{label} {table}: {d}"));
        }
    }
    cases.len()
}

#[tokio::test]
async fn p_db_1_empty_database_is_provisioned_at_the_current_version() {
    let db = fresh_db("mig_empty").await;
    assert_eq!(
        db.db_version().await.unwrap(),
        0,
        "no meta_data means version 0"
    );
    migrate(&db).await;
    assert_eq!(db.db_version().await.unwrap(), CURRENT_VERSION);
    assert_eq!(
        CURRENT_VERSION, 3,
        "bump with Kotlin's DbMigrator and db/schema.json"
    );
    let value: String = sqlx::query_scalar("SELECT value FROM meta_data WHERE key = 'version';")
        .fetch_one(db.pool())
        .await
        .unwrap();
    assert_eq!(value, "3", "the handshake row Kotlin reads");

    let mut failures = Failures::default();
    let checked = check_schema(&db, "fresh", &mut failures).await;
    migrate(&db).await;
    assert_eq!(
        db.db_version().await.unwrap(),
        CURRENT_VERSION,
        "re-run is a no-op"
    );
    failures.assert_none(checked);
    drop_database(db).await;
}

#[tokio::test]
async fn p_db_1_version_1_database_migrates_with_its_data() {
    let db = fresh_db("mig_v1").await;
    exec(&db, VERSION_1_SCHEMA).await;
    exec(&db, VERSION_1_DATA).await;
    assert_eq!(db.db_version().await.unwrap(), 1);
    migrate(&db).await;
    assert_eq!(db.db_version().await.unwrap(), CURRENT_VERSION);

    let mut failures = Failures::default();
    let checked = check_schema(&db, "migrated", &mut failures).await;

    let charts: Vec<(String, i32)> = sqlx::query("SELECT name, scale FROM charts ORDER BY name;")
        .fetch_all(db.pool())
        .await
        .unwrap()
        .iter()
        .map(|r| (r.get(0), r.get(1)))
        .collect();
    assert_eq!(charts, [("A.000".into(), 10000), ("B.000".into(), 20000)]);
    let features: Vec<Vec<String>> = sqlx::query(
        "SELECT chart_name, layer, props->>'LNAM', array_to_string(lnam_refs, ','), z_min::text, z_max::text
         FROM features ORDER BY id;",
    )
    .fetch_all(db.pool())
    .await
    .unwrap()
    .iter()
    .map(|r| (0..6).map(|i| r.get::<Option<String>, _>(i).unwrap_or_default()).collect())
    .collect();
    assert_eq!(
        features,
        [
            ["A.000", "BOYLAT", "L1", "R1,R2", "0", "22"],
            ["A.000", "TOPMAR", "L2", "", "3", "15"],
            ["B.000", "LIGHTS", "L3", "", "0", "22"],
        ],
        "chart_name from chart_id, lnam_refs from props.LNAM_REFS, the rest untouched"
    );
    let ingested: i64 =
        sqlx::query_scalar("SELECT count(*) FROM charts WHERE ingested_at IS NOT NULL;")
            .fetch_one(db.pool())
            .await
            .unwrap();
    assert_eq!(
        ingested, 2,
        "ingested_at defaults to now() for existing rows"
    );
    failures.assert_none(checked);
    drop_database(db).await;
}

#[tokio::test]
async fn concurrent_runners_provision_once() {
    let db = fresh_db("mig_race").await;
    let (a, b) = (db.clone(), db.clone());
    let run = |db: Db| async move {
        let lock = AdvisoryLock::new(db.pool().clone(), AdvisoryLock::MIGRATION_KEY);
        db.migrate(&lock).await.unwrap()
    };
    let (va, vb) = tokio::join!(run(a), run(b));
    assert_eq!((va, vb), (CURRENT_VERSION, CURRENT_VERSION));
    let rows: i64 = sqlx::query_scalar("SELECT count(*) FROM meta_data;")
        .fetch_one(db.pool())
        .await
        .unwrap();
    assert_eq!(rows, 1);
    drop_database(db).await;
}

/// `DbMigrationsTest.VERSION_1_SCHEMA`.
const VERSION_1_SCHEMA: &str = r#"
CREATE TABLE meta_data (key VARCHAR PRIMARY KEY, value VARCHAR NOT NULL);
INSERT INTO meta_data (key, value) VALUES ('version', '1');

CREATE TABLE charts
(
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR UNIQUE           NOT NULL,
    scale      INTEGER                  NOT NULL,
    file_name  VARCHAR                  NOT NULL,
    updated    VARCHAR                  NOT NULL,
    issued     VARCHAR                  NOT NULL,
    zoom       INTEGER                  NOT NULL,
    covr       GEOMETRY(GEOMETRY, 4326) NOT NULL,
    dsid_props JSONB                    NOT NULL,
    chart_txt  JSONB                    NOT NULL
);
CREATE INDEX charts_gist ON charts USING GIST (covr);
CREATE INDEX charts_idx ON charts (id);

CREATE TABLE features
(
    id        BIGSERIAL PRIMARY KEY,
    layer     VARCHAR                       NOT NULL,
    geom      GEOMETRY(GEOMETRY, 4326)      NOT NULL,
    props     JSONB                         NOT NULL,
    chart_id  BIGINT REFERENCES charts (id) NOT NULL,
    lnam_refs VARCHAR[]                     NULL,
    z_min     INTEGER                       NOT NULL DEFAULT 0,
    z_max     INTEGER                       NOT NULL DEFAULT 22
);
CREATE INDEX features_gist ON features USING GIST (geom);
CREATE INDEX features_idx ON features (id);
CREATE INDEX features_layer_idx ON features (layer);
CREATE INDEX features_chart_zoom_idx ON features (chart_id, z_min, z_max);
CREATE INDEX features_lnam_idx ON features USING GIN (lnam_refs);

CREATE TABLE base_features
(
    id    BIGSERIAL PRIMARY KEY,
    geom  GEOMETRY(GEOMETRY, 4326) NOT NULL,
    props JSONB                    NOT NULL,
    name  VARCHAR                  NOT NULL,
    scale INTEGER                  NOT NULL,
    layer VARCHAR                  NOT NULL
);
CREATE INDEX base_features_gist ON base_features USING GIST (geom);
CREATE INDEX base_features_scale_idx ON base_features (scale);
"#;

/// `DbMigrationsTest.VERSION_1_DATA`.
const VERSION_1_DATA: &str = r#"
INSERT INTO charts (name, scale, file_name, updated, issued, zoom, covr, dsid_props, chart_txt) VALUES
 ('A.000', 10000, 'A.000', '20240101', '20240101', 14, ST_GeomFromText('POLYGON((-1 -1, -1 1, 1 1, 1 -1, -1 -1))', 4326), '{"DSID_UPDN":"1"}', '{}'),
 ('B.000', 20000, 'B.000', '20240101', '20240101', 13, ST_GeomFromText('POLYGON((10 10, 10 11, 11 11, 11 10, 10 10))', 4326), '{"DSID_UPDN":"2"}', '{}');
INSERT INTO features (layer, geom, props, chart_id, z_min, z_max) VALUES
 ('BOYLAT', ST_GeomFromText('POINT(0 0)', 4326), '{"LNAM":"L1","LNAM_REFS":["R1","R2"]}', (SELECT id FROM charts WHERE name = 'A.000'), 0, 22),
 ('TOPMAR', ST_GeomFromText('POINT(0 0)', 4326), '{"LNAM":"L2"}', (SELECT id FROM charts WHERE name = 'A.000'), 3, 15),
 ('LIGHTS', ST_GeomFromText('POINT(10.5 10.5)', 4326), '{"LNAM":"L3","LNAM_REFS":[]}', (SELECT id FROM charts WHERE name = 'B.000'), 0, 22);
"#;
