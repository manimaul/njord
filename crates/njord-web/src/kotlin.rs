//! How the Kotlin/JS UI printed values it showed as text: enum constant names, `Duration` and
//! data-class `toString()`. Numbers need nothing here: Kotlin/JS prints a `Double` the way
//! JavaScript does (`14.0` as `14`), which Rust's `Display` matches for every value the UI shows.

use njord_model::{Depth, InsertItem, ThemeMode, WsMsg};

/// The Kotlin constant name of a [`Depth`] (`FEET`), which the nav bar shows.
pub const fn depth_name(depth: Depth) -> &'static str {
    match depth {
        Depth::Fathoms => "FATHOMS",
        Depth::Meters => "METERS",
        Depth::Feet => "FEET",
    }
}

/// The Kotlin constant name of a [`ThemeMode`] (`Day`).
pub const fn mode_name(mode: ThemeMode) -> &'static str {
    match mode {
        ThemeMode::Day => "Day",
        ThemeMode::Dusk => "Dusk",
        ThemeMode::Night => "Night",
    }
}

/// `kotlin.time.Duration.toString()` for whole seconds: `6d 23h 59m 58s`, `1h 0m 5s`, `0s`,
/// negative values as `-(1h 30m)`.
pub fn duration_to_string(seconds: i64) -> String {
    if seconds == 0 {
        return "0s".to_owned();
    }
    let negative = seconds < 0;
    let total = seconds.unsigned_abs();
    let (days, hours, minutes, secs) = (
        total / 86_400,
        total % 86_400 / 3600,
        total % 3600 / 60,
        total % 60,
    );
    let (has_days, has_hours, has_minutes, has_seconds) =
        (days != 0, hours != 0, minutes != 0, secs != 0);
    let mut parts = Vec::new();
    if has_days {
        parts.push(format!("{days}d"));
    }
    if has_hours || (has_days && (has_minutes || has_seconds)) {
        parts.push(format!("{hours}h"));
    }
    if has_minutes || (has_seconds && (has_hours || has_days)) {
        parts.push(format!("{minutes}m"));
    }
    if has_seconds {
        parts.push(format!("{secs}s"));
    }
    let body = parts.join(" ");
    match (negative, parts.len() > 1) {
        (false, _) => body,
        (true, false) => format!("-{body}"),
        (true, true) => format!("-({body})"),
    }
}

fn insert_item(item: &InsertItem) -> String {
    format!(
        "InsertItem(chartName={}, featureCount={})",
        item.chart_name, item.feature_count
    )
}

/// The data-class `toString()` of a [`WsMsg`], which the chart installer prints for a finished or
/// failed ingest.
pub fn ws_msg_to_string(msg: &WsMsg) -> String {
    match msg {
        WsMsg::Error { is_fatal, message } => {
            format!("Error(isFatal={is_fatal}, message={message})")
        }
        WsMsg::Info {
            feature,
            total_features,
            chart,
            total_charts,
        } => format!(
            "Info(feature={feature}, totalFeatures={total_features}, chart={chart}, totalCharts={total_charts})"
        ),
        WsMsg::CompletionReport {
            total_feature_count,
            total_chart_count,
            items,
            failed_charts,
            ms,
        } => format!(
            "CompletionReport(totalFeatureCount={total_feature_count}, totalChartCount={total_chart_count}, items=[{}], failedCharts=[{}], ms={ms})",
            items.iter().map(insert_item).collect::<Vec<_>>().join(", "),
            failed_charts.join(", "),
        ),
        WsMsg::Extracting { progress } => format!("Extracting(progress={progress})"),
        WsMsg::Idle => "Idle".to_owned(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn durations() {
        for (s, want) in [
            (0, "0s"),
            (5, "5s"),
            (60, "1m"),
            (3605, "1h 0m 5s"),
            (3600, "1h"),
            (86_400, "1d"),
            (86_401, "1d 0h 0m 1s"),
            (90_000, "1d 1h"),
            (604_798, "6d 23h 59m 58s"),
            (-5, "-5s"),
            (-5400, "-(1h 30m)"),
        ] {
            assert_eq!(duration_to_string(s), want, "{s}");
        }
    }

    #[test]
    fn ws_messages() {
        let report = WsMsg::CompletionReport {
            total_feature_count: 5163,
            total_chart_count: 1,
            items: vec![InsertItem {
                chart_name: "US5WA22M".into(),
                feature_count: 5158,
            }],
            failed_charts: vec![],
            ms: 812,
        };
        assert_eq!(
            ws_msg_to_string(&report),
            "CompletionReport(totalFeatureCount=5163, totalChartCount=1, items=[InsertItem(chartName=US5WA22M, featureCount=5158)], failedCharts=[], ms=812)"
        );
        let error = WsMsg::Error {
            is_fatal: true,
            message: "bad zip".into(),
        };
        assert_eq!(
            ws_msg_to_string(&error),
            "Error(isFatal=true, message=bad zip)"
        );
    }
}
