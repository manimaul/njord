//! The page themes in `assets/app.css` take their colours from the S-52 colour tables the server
//! ships (`crates/njord-server/resources/colors.json`, served as `/v1/about/colors`). A value
//! annotated with a bare token, `#C9EDFF; /* DEPDW */`, must equal that token in the block's mode.

use std::collections::BTreeMap;
use std::path::Path;

use serde_json::Value;

/// `(mode, token, value)` for every annotated value, by the `:root` block it is in.
fn annotated_colors(css: &str) -> Vec<(String, String, String)> {
    let mut mode = None;
    let mut found = Vec::new();
    for line in css.lines() {
        let line = line.trim();
        if line.starts_with(":root") {
            mode = Some(match line.split_once("data-theme=\"") {
                Some((_, rest)) => rest.split('"').next().unwrap().to_uppercase(),
                None => "DAY".to_owned(),
            });
        } else if line == "}" {
            mode = None;
        }
        let (Some(mode), Some((value, comment))) = (&mode, line.split_once("/*")) else {
            continue;
        };
        let token = comment.trim_end_matches("*/").trim();
        let is_token = token.len() == 5
            && token
                .bytes()
                .all(|b| b.is_ascii_uppercase() || b.is_ascii_digit());
        let Some(hex) = value.rsplit('#').next().filter(|_| value.contains('#')) else {
            continue;
        };
        let hex = hex.trim_end_matches([';', ' ']);
        if is_token && hex.len() == 6 {
            found.push((mode.clone(), token.to_owned(), format!("#{hex}")));
        }
    }
    found
}

#[test]
fn theme_tokens_match_the_colour_library() {
    let root = Path::new(env!("CARGO_MANIFEST_DIR"));
    let css = std::fs::read_to_string(root.join("assets/app.css")).unwrap();
    let json = std::fs::read_to_string(root.join("../njord-server/resources/colors.json")).unwrap();
    let colors: Value = serde_json::from_str(&json).unwrap();
    let library: BTreeMap<String, BTreeMap<String, String>> =
        serde_json::from_value(colors["library"].clone()).unwrap();

    let annotated = annotated_colors(&css);
    assert!(
        annotated.len() > 20,
        "found only {} annotated colours",
        annotated.len()
    );
    let mut modes: Vec<&str> = annotated.iter().map(|(m, _, _)| m.as_str()).collect();
    modes.dedup();
    assert_eq!(modes, ["DAY", "DUSK", "NIGHT"]);

    let mismatched: Vec<String> = annotated
        .iter()
        .filter(|(mode, token, hex)| library[mode].get(token) != Some(hex))
        .map(|(mode, token, hex)| {
            format!(
                "{mode} {token}: app.css has {hex}, colors.json has {:?}",
                library[mode].get(token)
            )
        })
        .collect();
    assert!(mismatched.is_empty(), "{}", mismatched.join("\n"));
}
