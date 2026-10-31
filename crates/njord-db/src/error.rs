//! The one error type of the crate.

use std::fmt;

#[derive(Debug)]
pub enum Error {
    /// No connection could be acquired before the [`RetryPolicy`](crate::RetryPolicy) ran out
    /// (Kotlin's `sqlOpAsync` returning `null` for want of a connection); the last attempt's error.
    Connection(sqlx::Error),
    /// A statement failed. Never retried (decision D5).
    Sql(sqlx::Error),
    /// A geometry column held bytes that are not WKB, or a geometry could not be encoded.
    Geometry(String),
    /// A JSON column did not have the shape the wire type needs.
    Json(serde_json::Error),
    /// A transaction had a failed statement and was rolled back instead of committed (the name
    /// of what it was writing, e.g. the chart of a feature install).
    Aborted(String),
    /// The schema could not be brought to [`CURRENT_VERSION`](crate::CURRENT_VERSION).
    Migration(String),
}

impl Error {
    /// The SQLSTATE of a failed statement, e.g. `23502` for a NOT NULL violation.
    pub fn sqlstate(&self) -> Option<String> {
        match self {
            Error::Sql(sqlx::Error::Database(e)) | Error::Connection(sqlx::Error::Database(e)) => {
                e.code().map(|c| c.into_owned())
            }
            _ => None,
        }
    }
}

impl fmt::Display for Error {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Error::Connection(e) => write!(f, "no database connection: {e}"),
            Error::Sql(e) => write!(f, "{e}"),
            Error::Geometry(e) => write!(f, "geometry: {e}"),
            Error::Json(e) => write!(f, "json: {e}"),
            Error::Aborted(what) => write!(f, "{what}: rolled back after a failed statement"),
            Error::Migration(e) => write!(f, "migration: {e}"),
        }
    }
}

impl std::error::Error for Error {
    fn source(&self) -> Option<&(dyn std::error::Error + 'static)> {
        match self {
            Error::Connection(e) | Error::Sql(e) => Some(e),
            Error::Json(e) => Some(e),
            Error::Geometry(_) | Error::Aborted(_) | Error::Migration(_) => None,
        }
    }
}

impl From<sqlx::Error> for Error {
    fn from(e: sqlx::Error) -> Error {
        Error::Sql(e)
    }
}

impl From<serde_json::Error> for Error {
    fn from(e: serde_json::Error) -> Error {
        Error::Json(e)
    }
}
