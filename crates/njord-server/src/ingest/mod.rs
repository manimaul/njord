//! The write path's background work: claiming uploaded zips
//! ([`worker`]), reading S-57 cells and Natural Earth shapefiles into PostGIS ([`chart`],
//! [`natural_earth`]), and the status file the WebSocket route streams ([`status`]). Every
//! change is recorded in the change history with the cause the upload's sidecar names
//! ([`cause`], `docs/change_reports.md`). A lock left held by a process that stopped mid-ingest
//! is freed when the workers start ([`recover`]).

pub mod cause;
pub mod chart;
pub mod natural_earth;
pub mod recover;
pub mod report;
pub mod status;
pub mod worker;
pub mod zip;

pub use recover::{Interrupted, recover_interrupted};
pub use report::Report;
pub use status::IngestStatus;
pub use worker::IngestWorker;
