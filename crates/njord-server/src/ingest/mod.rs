//! The write path's background work: claiming uploaded zips
//! ([`worker`]), reading S-57 cells and Natural Earth shapefiles into PostGIS ([`chart`],
//! [`natural_earth`]), and the status file the WebSocket route streams ([`status`]). Every
//! change is recorded in the change history with the cause the upload's sidecar names
//! ([`cause`], `docs/change_reports.md`).

pub mod cause;
pub mod chart;
pub mod natural_earth;
pub mod report;
pub mod status;
pub mod worker;
pub mod zip;

pub use report::Report;
pub use status::IngestStatus;
pub use worker::IngestWorker;
