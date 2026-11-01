//! Schema provisioning and migration.
//!
//! [`Db::migrate`] provisions an empty database at [`CURRENT_VERSION`] or migrates an older one
//! forward. A fresh database and a migrated version-1 database end with the same schema, which
//! the fixture `db/schema.json` records.
//!
//! The schema version record is the `meta_data` row `key = 'version'` (text value): read as 0
//! when the table or row is missing, and stamped after each migration and by provisioning. Other
//! deployments of the same database read this row too, so a database provisioned or migrated
//! here is accepted by any of them that knows the same [`CURRENT_VERSION`].
//!
//! Migration runs under a [`MigrationLock`]. [`AdvisoryLock`] (a session advisory lock)
//! serialises the instances that use it; a migrator that takes a different lock is not excluded
//! from migrating the same database at the same moment.

use std::future::Future;
use std::time::Duration;

use sqlx::pool::PoolConnection;
use sqlx::postgres::Postgres;
use sqlx::{AssertSqlSafe, PgPool, Row};
use tokio::sync::Mutex;

use crate::{Db, Error, Result};

/// The `meta_data.key` holding the schema version.
pub const VERSION_KEY: &str = "version";

/// The highest migration version: what provisioning stamps and [`Db::migrate`] waits for.
pub const CURRENT_VERSION: i32 = 3;

/// Mutual exclusion between instances migrating one database.
pub trait MigrationLock {
    /// Takes the lock if it is free; `false` when another instance holds it.
    ///
    /// # Errors
    ///
    /// Fails when the lock's backing store cannot be reached.
    fn try_acquire(&self) -> impl Future<Output = Result<bool>> + Send;
    /// Releases the lock if held.
    fn release(&self) -> impl Future<Output = ()> + Send;
}

/// A PostgreSQL session advisory lock held on a dedicated pooled connection.
///
/// [`try_acquire`](MigrationLock::try_acquire) fails with [`Error::Connection`] when no
/// connection can be had and [`Error::Sql`] when `pg_try_advisory_lock` fails. A connection
/// whose unlock fails is detached from the pool rather than returned holding the lock.
pub struct AdvisoryLock {
    pool: PgPool,
    key: i64,
    held: Mutex<Option<PoolConnection<Postgres>>>,
}

impl AdvisoryLock {
    /// The key the server's migration runner locks with (ASCII `njordmig`).
    pub const MIGRATION_KEY: i64 = 0x6e6a_6f72_646d_6967;

    /// A lock on advisory `key`, taken through `pool`.
    pub fn new(pool: PgPool, key: i64) -> AdvisoryLock {
        AdvisoryLock {
            pool,
            key,
            held: Mutex::new(None),
        }
    }
}

impl MigrationLock for AdvisoryLock {
    async fn try_acquire(&self) -> Result<bool> {
        let mut held = self.held.lock().await;
        if held.is_some() {
            return Ok(true);
        }
        let mut conn = self.pool.acquire().await.map_err(Error::Connection)?;
        let acquired: bool = sqlx::query_scalar("SELECT pg_try_advisory_lock($1);")
            .bind(self.key)
            .fetch_one(&mut *conn)
            .await?;
        if acquired {
            *held = Some(conn);
        }
        Ok(acquired)
    }

    async fn release(&self) {
        if let Some(mut conn) = self.held.lock().await.take() {
            let unlocked = sqlx::query("SELECT pg_advisory_unlock($1);")
                .bind(self.key)
                .execute(&mut *conn)
                .await;
            if unlocked.is_err() {
                // The session may still hold the lock; never hand it back to the pool.
                conn.detach();
            }
        }
    }
}

/// One schema revision after version 1: `sql` is one multi-statement simple query (one
/// implicit transaction, so it applies entirely or not at all), then `action`, then the version
/// stamp. With no action the stamp rides in the same transaction as `sql`. An action returning
/// `false` stops the run without stamping, so the next startup retries it; actions and the SQL
/// of migrations with one must be re-runnable.
struct Migration {
    version: i32,
    sql: &'static str,
    action: Option<Action>,
}

#[derive(Clone, Copy)]
enum Action {
    BackfillLnamRefs,
    MigrateFeaturesToChartName,
}

/// Version 1 databases predate these columns, tables and indices.
const MIGRATION_2: &str = r#"
ALTER TABLE charts ADD COLUMN IF NOT EXISTS ingested_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE TABLE IF NOT EXISTS region_export_state
(
    region_name VARCHAR PRIMARY KEY,
    exported_at TIMESTAMPTZ NOT NULL
);

DROP INDEX IF EXISTS charts_idx;
DROP INDEX IF EXISTS features_idx;

DROP INDEX IF EXISTS features_layer_idx;
CREATE INDEX IF NOT EXISTS features_layer_id_idx ON features (layer, id);

CREATE INDEX IF NOT EXISTS features_lnam_expr_idx ON features ((props->>'LNAM'));
"#;

/// `charts.name` replaces the surrogate `charts.id` as the primary key; `features.chart_id`
/// becomes `features.chart_name`.
const MIGRATION_3: &str = "ALTER TABLE features ADD COLUMN IF NOT EXISTS chart_name VARCHAR NULL;";

const MIGRATIONS: [Migration; 2] = [
    Migration {
        version: 2,
        sql: MIGRATION_2,
        action: Some(Action::BackfillLnamRefs),
    },
    Migration {
        version: 3,
        sql: MIGRATION_3,
        action: Some(Action::MigrateFeaturesToChartName),
    },
];

/// The schema at [`CURRENT_VERSION`], for an empty database.
///
/// Run with the version stamp as one implicit transaction, so provisioning applies entirely or
/// not at all.
const INITIAL_SCHEMA: &str = r#"
CREATE TABLE IF NOT EXISTS meta_data
(
    key   VARCHAR PRIMARY KEY,
    value VARCHAR NOT NULL
);

CREATE TABLE IF NOT EXISTS charts
(
    name        VARCHAR PRIMARY KEY,
    scale       INTEGER                  NOT NULL,
    file_name   VARCHAR                  NOT NULL,
    updated     VARCHAR                  NOT NULL,
    issued      VARCHAR                  NOT NULL,
    zoom        INTEGER                  NOT NULL,
    covr        GEOMETRY(GEOMETRY, 4326) NOT NULL,
    dsid_props  JSONB                    NOT NULL,
    chart_txt   JSONB                    NOT NULL,
    ingested_at TIMESTAMPTZ              NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS charts_gist ON charts USING GIST (covr);

CREATE TABLE IF NOT EXISTS region_export_state
(
    region_name VARCHAR PRIMARY KEY,
    exported_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS features
(
    id         BIGSERIAL PRIMARY KEY,
    layer      VARCHAR                          NOT NULL,
    geom       GEOMETRY(GEOMETRY, 4326)         NOT NULL,
    props      JSONB                            NOT NULL,
    chart_name VARCHAR REFERENCES charts (name) NOT NULL,
    lnam_refs  VARCHAR[]                        NULL,
    z_min      INTEGER                          NOT NULL DEFAULT 0,
    z_max      INTEGER                          NOT NULL DEFAULT 22
);
CREATE INDEX IF NOT EXISTS features_gist ON features USING GIST (geom);
CREATE INDEX IF NOT EXISTS features_chart_zoom_idx ON features (chart_name, z_min, z_max);
CREATE INDEX IF NOT EXISTS features_layer_id_idx ON features (layer, id);
CREATE INDEX IF NOT EXISTS features_lnam_expr_idx ON features ((props->>'LNAM'));
CREATE INDEX IF NOT EXISTS features_lnam_idx ON features USING GIN (lnam_refs);

CREATE TABLE IF NOT EXISTS base_features
(
    id    BIGSERIAL PRIMARY KEY,
    geom  GEOMETRY(GEOMETRY, 4326) NOT NULL,
    props JSONB                    NOT NULL,
    name  VARCHAR                  NOT NULL,
    scale INTEGER                  NOT NULL,
    layer VARCHAR                  NOT NULL
);
CREATE INDEX IF NOT EXISTS base_features_gist ON base_features USING GIST (geom);
CREATE INDEX IF NOT EXISTS base_features_scale_idx ON base_features (scale);
"#;

/// The final swap of migration 3, one implicit transaction.
const CHART_NAME_SWAP: &str = r#"
UPDATE features f SET chart_name = c.name FROM charts c
WHERE c.id = f.chart_id AND f.chart_name IS NULL;

ALTER TABLE features ALTER COLUMN chart_name SET NOT NULL;

ALTER TABLE features DROP COLUMN chart_id;

ALTER TABLE charts DROP CONSTRAINT IF EXISTS charts_name_key;
ALTER TABLE charts DROP COLUMN id;
ALTER TABLE charts ADD PRIMARY KEY (name);

CREATE INDEX IF NOT EXISTS features_chart_zoom_idx ON features (chart_name, z_min, z_max);
ALTER TABLE features ADD CONSTRAINT features_chart_name_fkey
    FOREIGN KEY (chart_name) REFERENCES charts (name);
"#;

fn stamp_sql(version: i32) -> String {
    format!(
        "INSERT INTO meta_data (key, value) VALUES ('{VERSION_KEY}', '{version}')\n\
         ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value;"
    )
}

impl Db {
    /// The recorded schema version.
    ///
    /// The version is 0 when `meta_data` or its version row is missing or the value is not an
    /// integer.
    ///
    /// # Errors
    ///
    /// A database that cannot be reached is [`Error::Connection`], never 0; a failing query is
    /// [`Error::Sql`].
    pub async fn db_version(&self) -> Result<i32> {
        let mut conn = self.conn().await?;
        let exists: bool =
            sqlx::query_scalar("SELECT to_regclass('public.meta_data') IS NOT NULL;")
                .fetch_one(&mut *conn)
                .await?;
        if !exists {
            return Ok(0);
        }
        let value: Option<String> =
            sqlx::query_scalar("SELECT value FROM meta_data WHERE key = $1;")
                .bind(VERSION_KEY)
                .fetch_optional(&mut *conn)
                .await?;
        Ok(value.and_then(|v| v.parse().ok()).unwrap_or(0))
    }

    /// Brings the database to [`CURRENT_VERSION`] and returns the version it ends at.
    ///
    /// An empty database (version 0) is provisioned with the current schema; an older one gets
    /// each later migration in order. `lock` is held while working; when another instance holds
    /// it, this waits (polling every 500 ms) for that instance's stamp. The version is re-read
    /// once the lock is held, so a waiter that wins the lock after the work is done does nothing.
    ///
    /// Returns a version below [`CURRENT_VERSION`] only when a migration's backfill did not
    /// finish; the next startup retries it.
    ///
    /// # Errors
    ///
    /// [`Error::Migration`] when provisioning or a migration statement fails, and the errors of
    /// [`db_version`](Db::db_version) and [`MigrationLock::try_acquire`].
    pub async fn migrate(&self, lock: &impl MigrationLock) -> Result<i32> {
        loop {
            let version = self.db_version().await?;
            if version >= CURRENT_VERSION {
                tracing::info!("DB schema version ready {version}");
                return Ok(version);
            }
            if lock.try_acquire().await? {
                let result = self.migrate_locked().await;
                lock.release().await;
                return result;
            }
            tracing::info!("waiting for schema migration by another instance");
            tokio::time::sleep(Duration::from_millis(500)).await;
        }
    }

    async fn migrate_locked(&self) -> Result<i32> {
        let version = self.db_version().await?;
        if version == 0 {
            self.raw(&format!("{INITIAL_SCHEMA}\n{}", stamp_sql(CURRENT_VERSION)))
                .await
                .map_err(|e| Error::Migration(format!("schema initialization failed: {e}")))?;
            tracing::info!("DB schema initialized to version {CURRENT_VERSION}");
            return Ok(CURRENT_VERSION);
        }
        let mut reached = version;
        for migration in MIGRATIONS.iter().filter(|m| m.version > version) {
            tracing::info!("applying DB migration {}", migration.version);
            let fail = |e: Error| Error::Migration(format!("migration {}: {e}", migration.version));
            match migration.action {
                None => {
                    self.raw(&format!(
                        "{}\n{}",
                        migration.sql,
                        stamp_sql(migration.version)
                    ))
                    .await
                    .map_err(fail)?;
                }
                Some(action) => {
                    self.raw(migration.sql).await.map_err(fail)?;
                    let finished = match action {
                        Action::BackfillLnamRefs => self.backfill_lnam_refs().await,
                        Action::MigrateFeaturesToChartName => {
                            self.migrate_features_to_chart_name().await
                        }
                    };
                    match finished {
                        Ok(true) => {}
                        Ok(false) => {
                            tracing::warn!(
                                "DB migration {} incomplete - retrying on next startup",
                                migration.version
                            );
                            return Ok(reached);
                        }
                        Err(e) => {
                            tracing::warn!("DB migration {} incomplete: {e}", migration.version);
                            return Ok(reached);
                        }
                    }
                    self.raw(&stamp_sql(migration.version))
                        .await
                        .map_err(fail)?;
                }
            }
            reached = migration.version;
            tracing::info!("DB schema migrated to version {reached}");
        }
        Ok(reached)
    }

    /// Runs `sql` as one simple query: several statements, one implicit transaction.
    async fn raw(&self, sql: &str) -> Result<()> {
        let mut conn = self.conn().await?;
        sqlx::raw_sql(AssertSqlSafe(sql.to_owned()))
            .execute(&mut *conn)
            .await?;
        Ok(())
    }

    /// Migration 2's backfill of `features.lnam_refs` from `props.LNAM_REFS`, one chart per
    /// statement (`chart_id` leads `features_chart_zoom_idx`).
    async fn backfill_lnam_refs(&self) -> Result<bool> {
        let mut conn = self.conn().await?;
        let chart_ids: Vec<i64> = sqlx::query_scalar("SELECT id FROM charts ORDER BY id;")
            .fetch_all(&mut *conn)
            .await?;
        tracing::info!(
            "backfilling features.lnam_refs across {} charts",
            chart_ids.len()
        );
        let mut updated = 0;
        for chart_id in chart_ids {
            updated += sqlx::query(
                "UPDATE features SET lnam_refs = ARRAY(SELECT jsonb_array_elements_text(props->'LNAM_REFS'))
                 WHERE chart_id = $1
                   AND lnam_refs IS NULL
                   AND jsonb_typeof(props->'LNAM_REFS') = 'array';",
            )
            .bind(chart_id)
            .execute(&mut *conn)
            .await?
            .rows_affected();
        }
        tracing::info!("features.lnam_refs backfill complete - {updated} rows updated");
        Ok(true)
    }

    /// Migration 3: fills `features.chart_name` per chart, then swaps the keys in one
    /// transaction. Skipped when `features.chart_id` is already gone (a crash between the swap
    /// and the stamp).
    async fn migrate_features_to_chart_name(&self) -> Result<bool> {
        let mut conn = self.conn().await?;
        let needs_migration: bool = sqlx::query_scalar(
            "SELECT EXISTS (
                 SELECT 1 FROM information_schema.columns
                 WHERE table_name = 'features' AND column_name = 'chart_id'
             );",
        )
        .fetch_one(&mut *conn)
        .await?;
        if !needs_migration {
            tracing::info!("features.chart_name migration already applied");
            return Ok(true);
        }
        let charts = sqlx::query("SELECT id, name FROM charts ORDER BY id;")
            .fetch_all(&mut *conn)
            .await?;
        tracing::info!(
            "backfilling features.chart_name across {} charts",
            charts.len()
        );
        let mut updated = 0;
        for row in charts {
            let id: i64 = row.try_get(0)?;
            let name: String = row.try_get(1)?;
            updated += sqlx::query(
                "UPDATE features SET chart_name = $1 WHERE chart_id = $2 AND chart_name IS NULL;",
            )
            .bind(name)
            .bind(id)
            .execute(&mut *conn)
            .await?
            .rows_affected();
        }
        tracing::info!("features.chart_name backfill complete - {updated} rows updated");
        sqlx::raw_sql(CHART_NAME_SWAP).execute(&mut *conn).await?;
        tracing::info!("charts.name is now the primary key; features keyed by chart_name");
        Ok(true)
    }
}
