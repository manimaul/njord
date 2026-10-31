//! PostGIS access for the Njord port (RUST_PORT_PLAN.md §4.1, §5.4), ported from the Kotlin
//! `server/.../db/*`: the schema migration runner (`DbMigrations.kt`), the retry wrapper
//! (`Dao.kt`, with decision D5), and the queries of `ChartDao`, `FeatureDao`, `GeoJsonDao`,
//! `BaseFeatureDao`, `RegionDao` and `NukeDao`, including the writes ingest needs.
//!
//! Every query is a sqlx runtime query against the schema of [`migrations`], so the crate builds
//! without a database. Results use the `njord-model` wire types where the Kotlin DAO returned
//! one; geometries are decoded to `geo-types` (see [`geometry`]).
//!
//! Fixtures: P-DB-1 [`migrations`], P-DB-3 / P-DB-4 / P-DB-5 [`charts`] and [`features`],
//! P-DB-6 [`regions`], P-DB-7 [`retry`], D5 overwrite [`Db::insert_chart`], P-IN-5
//! [`Db::install_chart`]. The tests (`tests/`) run against a dev PostGIS, see `tests/common`.
//!
//! The Kotlin `TileDao` holds no query of its own (it is the tile encoder plus the disk cache),
//! so it is not here.

pub mod base_features;
pub mod charts;
pub mod error;
pub mod features;
pub mod geojson;
pub mod geometry;
pub mod migrations;
pub mod regions;
pub mod retry;

use sqlx::PgPool;
use sqlx::pool::PoolConnection;
use sqlx::postgres::{PgPoolOptions, Postgres};

pub use charts::{ChartFeature, ChartInfo};
pub use error::Error;
pub use features::{FeatureInstall, InstalledChart};
pub use migrations::{AdvisoryLock, CURRENT_VERSION, MigrationLock, VERSION_KEY};
pub use regions::{RegionChart, RegionFeature};
pub use retry::RetryPolicy;

/// Re-exported so dependants use the same sqlx (and pool type) as this crate.
pub use sqlx;

pub type Result<T, E = Error> = std::result::Result<T, E>;

/// A handle on the chart database: a connection pool plus the [`RetryPolicy`] applied when a
/// connection is acquired. Cheap to clone.
#[derive(Clone, Debug)]
pub struct Db {
    pool: PgPool,
    retry: RetryPolicy,
}

impl Db {
    /// A pool for `url` (`postgresql://user:password@host:port/db`, the Kotlin
    /// `pgConnectionInfo`) that connects on first use, so a database that is down at startup is
    /// covered by the [`RetryPolicy`] rather than failing here.
    pub fn connect_lazy(url: &str, max_connections: u32) -> Result<Db> {
        let pool = PgPoolOptions::new()
            .max_connections(max_connections)
            .connect_lazy(url)?;
        Ok(Db::new(pool))
    }

    pub fn new(pool: PgPool) -> Db {
        Db {
            pool,
            retry: RetryPolicy::default(),
        }
    }

    pub fn with_retry(self, retry: RetryPolicy) -> Db {
        Db { retry, ..self }
    }

    pub fn pool(&self) -> &PgPool {
        &self.pool
    }

    /// A pooled connection, acquired under the [`RetryPolicy`].
    pub(crate) async fn conn(&self) -> Result<PoolConnection<Postgres>> {
        retry::acquire(self.retry, || self.pool.acquire()).await
    }

    /// A transaction on a pooled connection, begun under the [`RetryPolicy`]. Dropping it
    /// without [`commit`](sqlx::Transaction::commit) rolls it back.
    pub(crate) async fn begin(&self) -> Result<sqlx::Transaction<'static, Postgres>> {
        retry::acquire(self.retry, || self.pool.begin()).await
    }

    /// `NukeDao.truncateAllAsync`: empties `features`, `charts` and `base_features` (identities
    /// restarted) and keeps `meta_data`, so the schema version survives. `region_export_state` is
    /// kept too, as in Kotlin.
    pub async fn truncate_all(&self) -> Result<()> {
        let mut conn = self.conn().await?;
        sqlx::query("TRUNCATE TABLE features, charts, base_features RESTART IDENTITY CASCADE;")
            .execute(&mut *conn)
            .await?;
        Ok(())
    }
}
