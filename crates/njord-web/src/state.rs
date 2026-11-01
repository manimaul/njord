//! [`Async`]: the load state every page renders from.

/// The message shown when a failure has no more specific one.
pub const DEFAULT_ERROR: &str = "An unknown error occurred :(";

#[derive(Clone, Debug, Default, PartialEq)]
pub enum Async<T> {
    #[default]
    Uninitialized,
    /// Loading, keeping the previous value if there was one.
    Loading(Option<T>),
    Complete(T),
    Fail {
        value: Option<T>,
        message: String,
    },
}

impl<T> Async<T> {
    pub fn value(&self) -> Option<&T> {
        match self {
            Async::Uninitialized => None,
            Async::Loading(v) | Async::Fail { value: v, .. } => v.as_ref(),
            Async::Complete(v) => Some(v),
        }
    }

    pub fn into_value(self) -> Option<T> {
        match self {
            Async::Uninitialized => None,
            Async::Loading(v) | Async::Fail { value: v, .. } => v,
            Async::Complete(v) => Some(v),
        }
    }

    pub fn is_complete(&self) -> bool {
        matches!(self, Async::Complete(_))
    }

    pub fn is_loading(&self) -> bool {
        matches!(self, Async::Loading(_))
    }

    /// `Async.map`: a `Fail` keeps its message but drops its value, a `Loading` drops its value.
    pub fn map<R>(self, f: impl FnOnce(T) -> R) -> Async<R> {
        match self {
            Async::Uninitialized => Async::Uninitialized,
            Async::Loading(_) => Async::Loading(None),
            Async::Complete(v) => Async::Complete(f(v)),
            Async::Fail { message, .. } => Async::Fail {
                value: None,
                message,
            },
        }
    }

    /// `Async.mapNotNull`: a `Complete` whose mapping is `None` becomes `Loading`.
    pub fn map_not_null<R>(self, f: impl FnOnce(T) -> Option<R>) -> Async<R> {
        match self {
            Async::Complete(v) => f(v).map_or(Async::Loading(None), Async::Complete),
            other => other.map(|_| unreachable!()),
        }
    }

    /// `Async.flatMap`.
    pub fn and_then<R>(self, f: impl FnOnce(T) -> Async<R>) -> Async<R> {
        match self {
            Async::Complete(v) => f(v),
            other => other.map(|_| unreachable!()),
        }
    }

    /// A network result as an `Async`, keeping `previous` as the value of a failure
    /// (`NetworkResponse.toAsync(previous)`).
    pub fn from_result(result: Result<T, crate::api::ApiError>, previous: Option<T>) -> Async<T> {
        match result {
            Ok(v) => Async::Complete(v),
            Err(e) => Async::Fail {
                value: previous,
                message: e.message,
            },
        }
    }
}
