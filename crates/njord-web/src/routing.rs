//! The URL contract of the web UI (`shared_fe/…/routing`): [`NjordRoute`] patterns,
//! [`RouteMatcher`] and [`RouteRegistry`], ported line for line so every bookmarked URL resolves as
//! it did. Pure Rust, pinned natively by `tests/routes.rs` against `testdata/frontend/routes.json`.

use regex_lite::Regex;

/// The pages, in declaration order: [`RouteRegistry::from`] picks the first that matches.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum NjordRoute {
    About,
    Enc,
    ControlPanel,
    Chart,
    Layer,
    NotFound,
}

impl NjordRoute {
    pub const ALL: [NjordRoute; 6] = [
        NjordRoute::About,
        NjordRoute::Enc,
        NjordRoute::ControlPanel,
        NjordRoute::Chart,
        NjordRoute::Layer,
        NjordRoute::NotFound,
    ];

    /// The route's name (`ControlPanel`), as the route fixtures record it.
    pub const fn name(self) -> &'static str {
        match self {
            NjordRoute::About => "About",
            NjordRoute::Enc => "Enc",
            NjordRoute::ControlPanel => "ControlPanel",
            NjordRoute::Chart => "Chart",
            NjordRoute::Layer => "Layer",
            NjordRoute::NotFound => "NotFound",
        }
    }

    pub const fn path_pattern(self) -> &'static str {
        match self {
            NjordRoute::About => "/",
            NjordRoute::Enc => "/enc",
            NjordRoute::ControlPanel => "/control/:tab/:*path",
            NjordRoute::Chart => "/chart/:name",
            NjordRoute::Layer => "/layer/:name",
            NjordRoute::NotFound => "/404",
        }
    }

    pub const fn title(self) -> &'static str {
        match self {
            NjordRoute::About => "About",
            NjordRoute::Enc => "ENC",
            NjordRoute::ControlPanel => "Control Panel",
            NjordRoute::Chart => "Chart",
            NjordRoute::Layer => "Layer",
            NjordRoute::NotFound => "Not Found",
        }
    }
}

/// A path pattern compiled to a regex: `:name` matches one segment, `:*name` the rest of the
/// path (possibly empty). A trailing `/` or `#` on the path is tolerated.
pub struct RouteMatcher {
    /// The pattern anchored at both ends, for [`RouteMatcher::matches`].
    full: Regex,
    /// The same pattern unanchored, for [`RouteMatcher::groups`] (first match).
    find: Regex,
    keywords: Vec<String>,
}

impl RouteMatcher {
    pub fn build(pattern: &str) -> RouteMatcher {
        let mut keywords = Vec::new();
        let source = compile(pattern, &mut keywords);
        RouteMatcher {
            full: Regex::new(&format!("^(?:{source})$")).expect("route pattern"),
            find: Regex::new(&source).expect("route pattern"),
            keywords,
        }
    }

    pub fn matches(&self, path: &str) -> bool {
        let p = path.strip_suffix('#').unwrap_or(path);
        if p.ends_with('/') {
            self.full.is_match(p)
        } else {
            self.full.is_match(&format!("{p}/"))
        }
    }

    /// The `:keyword` values, from the first match in the path without its query string, each
    /// with one trailing `/` removed. Empty when nothing matches.
    pub fn groups(&self, path: &str) -> Vec<(String, String)> {
        let i = path.rfind('?').unwrap_or(path.len());
        let p = if i > 0 && path.as_bytes()[i - 1] != b'/' {
            format!("{}/", &path[..i])
        } else {
            path[..i].to_owned()
        };
        let Some(caps) = self.find.captures(&p) else {
            return Vec::new();
        };
        self.keywords
            .iter()
            .map(|k| {
                let v = caps.name(k).map_or("", |m| m.as_str());
                (k.clone(), v.strip_suffix('/').unwrap_or(v).to_owned())
            })
            .collect()
    }
}

fn is_keyword(segment: &str) -> bool {
    let name = segment
        .strip_prefix(":*")
        .or_else(|| segment.strip_prefix(':'));
    name.is_some_and(|n| !n.is_empty() && n.chars().all(|c| c.is_ascii_alphanumeric() || c == '_'))
}

fn compile(pattern: &str, keywords: &mut Vec<String>) -> String {
    let mut regex = String::new();
    if pattern == "/" {
        regex.push('/');
    } else {
        for segment in pattern.split('/').filter(|s| !s.is_empty()) {
            regex.push('/');
            if is_keyword(segment) {
                if let Some(keyword) = segment.strip_prefix(":*") {
                    regex.push_str(&format!("(?<{keyword}>.*)"));
                    keywords.push(keyword.to_owned());
                } else {
                    let keyword = &segment[1..];
                    regex.push_str(&format!("(?<{keyword}>[^/]*)"));
                    keywords.push(keyword.to_owned());
                }
            } else {
                regex.push_str(segment);
            }
        }
    }
    regex.push_str("[/]?");
    regex
}

/// The query string after the last `?` of an href (`None` when there is none, or when the `?` is
/// its first character).
pub fn query_string(href: &str) -> Option<String> {
    href.rfind('?')
        .filter(|&i| i > 0)
        .map(|i| href[i + 1..].to_owned())
}

/// `k=v&flag` → `[(k, Some(v)), (flag, None)]`; a pair with more than one `=` is dropped.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct QueryParams {
    pub query_string: Option<String>,
}

impl QueryParams {
    pub fn new(query_string: Option<String>) -> QueryParams {
        QueryParams { query_string }
    }

    pub fn values(&self) -> Option<Vec<(String, Option<String>)>> {
        let qs = self.query_string.as_ref()?;
        let mut values: Vec<(String, Option<String>)> = Vec::new();
        for qp in qs.split('&') {
            let pair: Vec<&str> = qp.split('=').collect();
            let value = match pair.len() {
                1 => None,
                2 => Some(pair[1].to_owned()),
                _ => continue,
            };
            match values.iter_mut().find(|(k, _)| k == pair[0]) {
                Some(slot) => slot.1 = value,
                None => values.push((pair[0].to_owned(), value)),
            }
        }
        Some(values)
    }

    pub fn get(&self, key: &str) -> Option<String> {
        self.values()?
            .into_iter()
            .find(|(k, _)| k == key)
            .and_then(|(_, v)| v)
    }
}

/// A resolved location: the route, the path it was resolved from, the `:keyword` values (`None`
/// for [`NjordRoute::NotFound`]) and the query.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Routing {
    pub route: NjordRoute,
    pub path: String,
    pub args: Option<Vec<(String, String)>>,
    pub params: Option<QueryParams>,
}

impl Routing {
    /// `Routing.from(route)`: the route at its own pattern, as the nav bar links it.
    pub fn from_route(route: NjordRoute) -> Routing {
        Routing {
            route,
            path: route.path_pattern().to_owned(),
            args: None,
            params: None,
        }
    }

    pub fn path_and_params(&self) -> String {
        match self.params.as_ref().and_then(|p| p.query_string.as_ref()) {
            Some(qs) => format!("{}?{qs}", self.path),
            None => self.path.clone(),
        }
    }

    pub fn arg(&self, key: &str) -> Option<&str> {
        self.args
            .as_ref()?
            .iter()
            .find(|(k, _)| k == key)
            .map(|(_, v)| v.as_str())
    }

    pub fn path_segments(&self) -> Vec<&str> {
        self.path
            .split('/')
            .filter(|s| !s.trim().is_empty())
            .collect()
    }
}

pub struct RouteRegistry {
    matchers: Vec<(NjordRoute, RouteMatcher)>,
}

impl Default for RouteRegistry {
    fn default() -> Self {
        RouteRegistry {
            matchers: NjordRoute::ALL
                .iter()
                .map(|&r| (r, RouteMatcher::build(r.path_pattern())))
                .collect(),
        }
    }
}

impl RouteRegistry {
    pub fn from(&self, path: &str, params: QueryParams) -> Routing {
        match self.matchers.iter().find(|(_, m)| m.matches(path)) {
            Some((route, m)) => Routing {
                route: *route,
                path: path.to_owned(),
                args: Some(m.groups(path)),
                params: Some(params),
            },
            None => Routing {
                route: NjordRoute::NotFound,
                path: path.to_owned(),
                args: None,
                params: Some(params),
            },
        }
    }
}

/// The nav bar entries, in order.
pub fn nav_bar_routes(registry: &RouteRegistry) -> Vec<Routing> {
    vec![
        Routing::from_route(NjordRoute::About),
        Routing::from_route(NjordRoute::Enc),
        control_panel(registry, "charts_catalog", ""),
    ]
}

pub fn control_panel(registry: &RouteRegistry, tab: &str, selection: &str) -> Routing {
    registry.from(
        &format!("/control/{tab}/{selection}"),
        QueryParams::default(),
    )
}
