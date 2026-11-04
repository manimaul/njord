//! PostGIS access for Njord: schema migrations and every query the server and ingest run.
//!
//! [`Db`] is the entry point: a connection pool whose methods are grouped by table.
//! - [`migrations`]: [`Db::migrate`] provisions or upgrades the schema to [`CURRENT_VERSION`].
//! - [`charts`]: chart rows, the catalog and editions, and the tile path's feature queries
//!   ([`Db::find_chart_features_4326`]).
//! - [`features`]: the feature routes and the ingest feature insert ([`Db::install_chart`],
//!   [`FeatureInstall`]).
//! - [`base_features`]: the Natural Earth base map.
//! - [`change_events`]: the change history the change reports read ([`NewChangeEvent`]).
//! - [`geojson`]: the GeoJSON routes.
//! - [`regions`]: what the region exporter reads, and its export state.
//! - [`retry`]: the [`RetryPolicy`] for acquiring a connection.
//!
//! Every query is a sqlx runtime query against the schema of [`migrations`], so the crate builds
//! without a database. Results use the `njord-model` wire types where a route serves them;
//! geometries are decoded to `geo-types` (see [`geometry`]).
//!
//! # Errors
//!
//! Every method returns [`Error`]. Only acquiring a connection is retried, under the
//! [`RetryPolicy`], and failing that is [`Error::Connection`]; a failing statement is
//! [`Error::Sql`] and is never retried. A chart insert or overwrite, a chart delete and a
//! [`FeatureInstall`] each run in one transaction, so a failure leaves none of them half-written.
//!
//! The tests (`tests/`) run against a dev PostGIS, see `tests/common`. The tile disk cache and
//! encoder are not here: they live in the server and hold no query of their own.

pub mod base_features;
pub mod change_events;
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

pub use change_events::{NewChangeEvent, REPORT_PAGE_SIZE, record_change_event};
pub use charts::{ChartFeature, ChartInfo};
pub use error::Error;
pub use features::{FeatureInstall, InstalledChart};
pub use migrations::{AdvisoryLock, CURRENT_VERSION, MigrationLock, VERSION_KEY};
pub use regions::{RegionChart, RegionFeature};
pub use retry::RetryPolicy;

/// Re-exported so dependants use the same sqlx (and pool type) as this crate.
pub use sqlx;

/// A result with this crate's [`Error`].
pub type Result<T, E = Error> = std::result::Result<T, E>;

/// A handle on the chart database: a connection pool plus the [`RetryPolicy`] applied when a
/// connection is acquired. Cheap to clone.
#[derive(Clone, Debug)]
pub struct Db {
    pool: PgPool,
    retry: RetryPolicy,
}

impl Db {
    /// A pool for `url` that connects on first use.
    ///
    /// `url` is `postgresql://user:password@host:port/db`, the server's `pgConnectionInfo`.
    /// Connecting lazily means a database that is down at startup is covered by the
    /// [`RetryPolicy`] rather than failing here.
    ///
    /// # Errors
    ///
    /// [`Error::Sql`] when `url` cannot be parsed.
    pub fn connect_lazy(url: &str, max_connections: u32) -> Result<Db> {
        let pool = PgPoolOptions::new()
            .max_connections(max_connections)
            .connect_lazy(url)?;
        Ok(Db::new(pool))
    }

    /// A handle on `pool` with the default [`RetryPolicy`].
    pub fn new(pool: PgPool) -> Db {
        Db {
            pool,
            retry: RetryPolicy::default(),
        }
    }

    /// The same handle with another [`RetryPolicy`].
    pub fn with_retry(self, retry: RetryPolicy) -> Db {
        Db { retry, ..self }
    }

    /// The underlying pool.
    pub fn pool(&self) -> &PgPool {
        &self.pool
    }

    /// A pooled connection, acquired under the [`RetryPolicy`].
    pub(crate) async fn conn(&self) -> Result<PoolConnection<Postgres>> {
        retry::acquire(self.retry, || self.pool.acquire()).await
    }

    /// A transaction on a pooled connection, begun under the [`RetryPolicy`].
    ///
    /// Dropping it without [`commit`](sqlx::Transaction::commit) rolls it back.
    pub(crate) async fn begin(&self) -> Result<sqlx::Transaction<'static, Postgres>> {
        retry::acquire(self.retry, || self.pool.begin()).await
    }

    /// Empties `features`, `charts` and `base_features`, restarting their identities, and
    /// returns how many feature and base feature rows there were.
    ///
    /// `meta_data` is kept, so the schema version survives, and so are `region_export_state`
    /// and `change_events`: the nuke is recorded there by its handler, never erased.
    ///
    /// # Errors
    ///
    /// [`Error::Connection`] when no connection could be acquired, [`Error::Sql`] when a
    /// statement fails. Count and `TRUNCATE` are one transaction.
    pub async fn truncate_all(&self) -> Result<i64> {
        let mut tx = self.begin().await?;
        let before: i64 = sqlx::query_scalar(
            "SELECT (SELECT count(*) FROM features) + (SELECT count(*) FROM base_features);",
        )
        .fetch_one(&mut *tx)
        .await?;
        sqlx::query("TRUNCATE TABLE features, charts, base_features RESTART IDENTITY CASCADE;")
            .execute(&mut *tx)
            .await?;
        tx.commit().await?;
        Ok(before)
    }
}
