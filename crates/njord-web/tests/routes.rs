//! `testdata/frontend/routes.json`: the route list and the `RouteMatcher` and `RouteRegistry`
//! behaviour recorded in the golden fixture, which the UI must keep so URLs stay stable.

use njord_testdata::{cases, load, testdata};
use njord_web::routing::{NjordRoute, QueryParams, RouteMatcher, RouteRegistry};
use serde_json::{Map, Value, json};

fn pairs<V: Into<Value>>(pairs: Vec<(String, V)>) -> Value {
    Value::Object(pairs.into_iter().map(|(k, v)| (k, v.into())).collect())
}

#[test]
fn routes_listed_in_order() {
    let fixture = load(&testdata().join("frontend/routes.json"));
    let want = fixture["routes"].as_array().unwrap();
    let got: Vec<Value> = NjordRoute::ALL
        .iter()
        .map(|r| json!({"name": r.name(), "pathPattern": r.path_pattern(), "title": r.title()}))
        .collect();
    assert_eq!(&got, want);
}

#[test]
fn matcher_and_registry_cases() {
    let registry = RouteRegistry::default();
    let all = cases("frontend/routes.json");
    let mut failures = Vec::new();
    for case in &all {
        let input = &case["input"];
        let expected = &case["expected"];
        let got = if let Some(pattern) = input["pattern"].as_str() {
            let path = input["path"].as_str().unwrap();
            let m = RouteMatcher::build(pattern);
            json!({"matches": m.matches(path), "groups": pairs(m.groups(path))})
        } else {
            let path = input["registry"].as_str().unwrap();
            let query = input["query"].as_str().map(str::to_owned);
            let routing = registry.from(path, QueryParams::new(query));
            let mut out = Map::new();
            out.insert("route".into(), routing.route.name().into());
            if let Some(args) = routing.args.clone() {
                out.insert("args".into(), pairs(args));
            }
            // The fixture has `params` only when there was a query string.
            if let Some(values) = routing.params.as_ref().and_then(QueryParams::values) {
                out.insert("params".into(), pairs(values));
            }
            out.insert("pathAndParams".into(), routing.path_and_params().into());
            Value::Object(out)
        };
        if &got != expected {
            failures.push(format!("{input}: got {got}, want {expected}"));
        }
    }
    assert!(
        failures.is_empty(),
        "{} of {} cases differ:\n{}",
        failures.len(),
        all.len(),
        failures.join("\n")
    );
}
