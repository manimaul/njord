//! The nightly NOAA ENC updater (`enc_cron`): streams NOAA's ISO-19115 product catalog, diffs it
//! against `GET /v1/chart_editions`, deletes withdrawn charts with `DELETE /v1/chart`, and bundles
//! the cells whose revision changed into zips dropped in the ingest `save/` directory.
//!
//! The protocol with the server is only that directory and those two routes, so this binary runs
//! unchanged against the Kotlin or the Rust server.

pub mod bundle;
pub mod catalog;
pub mod config;
pub mod log;
pub mod run;
pub mod select;
pub mod transport;
