//! The nightly NOAA ENC updater (`enc_cron`).
//!
//! One [`run`](run::run) streams NOAA's ISO-19115 product catalog ([`catalog`]), diffs it against
//! `GET /v1/chart_editions` ([`select`]), deletes withdrawn charts with `DELETE /v1/chart`
//! ([`run::delete_orphans`]), and bundles the cells whose revision changed into zips dropped in
//! the ingest `save/` directory ([`bundle`]). [`config::EncCronConfig`] holds the settings and
//! [`transport`] all outbound HTTP.
//!
//! The protocol with the server is only that directory, those two routes and `GET /v1/admin` for
//! the delete signature, so the job needs no access to the server's database.

pub mod bundle;
pub mod catalog;
pub mod config;
pub mod log;
pub mod run;
pub mod select;
pub mod transport;
