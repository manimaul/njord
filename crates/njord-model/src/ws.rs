//! `/v1/ws/enc_process` messages and the ingest status file.

use serde::{Deserialize, Serialize};

/// Kotlin's `sealed class WsMsg`, tagged by its default polymorphic discriminator: a `"type"`
/// member holding the fully qualified class name.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(tag = "type", rename_all_fields = "camelCase")]
pub enum WsMsg {
    #[serde(rename = "io.madrona.njord.model.ws.WsMsg.Error")]
    Error { is_fatal: bool, message: String },

    #[serde(rename = "io.madrona.njord.model.ws.WsMsg.Info")]
    Info {
        feature: i64,
        total_features: i64,
        chart: i32,
        total_charts: i32,
    },

    #[serde(rename = "io.madrona.njord.model.ws.WsMsg.CompletionReport")]
    CompletionReport {
        total_feature_count: i64,
        total_chart_count: i32,
        items: Vec<InsertItem>,
        failed_charts: Vec<String>,
        ms: i64,
    },

    #[serde(rename = "io.madrona.njord.model.ws.WsMsg.Extracting")]
    Extracting { progress: f32 },

    #[serde(rename = "io.madrona.njord.model.ws.WsMsg.Idle")]
    Idle,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct InsertItem {
    pub chart_name: String,
    pub feature_count: i64,
}
