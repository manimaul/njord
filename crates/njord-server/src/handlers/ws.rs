//! `/v1/ws/enc_process`: the ingest status as a stream of `WsMsg` JSON texts.
//!
//! On connect the current status file is sent (`Idle` when it is missing or empty), then every
//! change, polled every [`POLL`] (`testdata/concurrency/cadence.json`).
//!
//! The signature is checked first, but an unsigned client is still upgraded and then closed
//! without a message, so a client sees a clean close rather than a failed handshake.

use std::sync::Arc;
use std::time::Duration;

use axum::extract::ws::{Message, WebSocket, WebSocketUpgrade};
use axum::extract::{Query, State};
use axum::response::Response;

use super::query_param;
use crate::forwarded::ExternalBaseUrl;
use crate::ingest::IngestStatus;
use crate::state::AppState;

/// How often the status file is re-read.
pub const POLL: Duration = Duration::from_millis(500);

/// Upgrades to the status socket; see the [module docs](self).
pub async fn enc_process(
    ws: WebSocketUpgrade,
    State(state): State<Arc<AppState>>,
    ExternalBaseUrl(base_url): ExternalBaseUrl,
    Query(query): Query<Vec<(String, String)>>,
) -> Response {
    let signed = query_param(&query, "signature").is_some_and(|s| {
        state
            .verifier
            .verify_query(s, &base_url, jiff::Timestamp::now())
    });
    ws.on_upgrade(move |mut socket| async move {
        if signed {
            stream_status(socket, state).await;
        } else {
            let _ = socket.send(Message::Close(None)).await;
        }
    })
}

fn current(state: &AppState) -> String {
    let content = state.status.read();
    if content.is_empty() {
        IngestStatus::idle_json()
    } else {
        content
    }
}

async fn stream_status(mut socket: WebSocket, state: Arc<AppState>) {
    let mut last = current(&state);
    if socket
        .send(Message::Text(last.clone().into()))
        .await
        .is_err()
    {
        return;
    }
    let mut ticker = tokio::time::interval(POLL);
    ticker.set_missed_tick_behavior(tokio::time::MissedTickBehavior::Delay);
    loop {
        tokio::select! {
            _ = ticker.tick() => {
                let content = current(&state);
                if content != last {
                    last = content;
                    if socket.send(Message::Text(last.clone().into())).await.is_err() {
                        return;
                    }
                }
            }
            incoming = socket.recv() => {
                match incoming {
                    Some(Ok(Message::Close(_))) | None | Some(Err(_)) => return,
                    Some(Ok(_)) => {}
                }
            }
        }
    }
}
