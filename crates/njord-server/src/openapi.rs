//! The OpenAPI document (`GET /v1/openapi.json`) and the Swagger UI that renders it
//! (`/v1/api_docs/`), both generated from the handlers' `#[utoipa::path]` attributes.
//!
//! Swagger UI is the vendored copy, served without its top bar and in the site's themes: the
//! page is this module's own `index.html` (`api_docs/`), which follows the Control Panel's Theme
//! picker, and `theme.css` maps Swagger UI's dark mode onto the dusk and night palettes
//! ([`themed_stylesheet`]).
//!
//! Every route is mounted through `utoipa_axum::routes!` in [`crate::router`], so a handler
//! cannot be added without appearing in the document; `tests/openapi.rs` pins the path list and
//! checks that every path template parameter is declared. The two wildcard routes
//! (`/v1/about/{*path}`, `/v1/content/{*content}`) are the exception: axum's `{*x}` is not an
//! OpenAPI template, so they are mounted by hand and listed here with a plain `{path}`
//! parameter.
//!
//! The document carries two security schemes. [`ADMIN_SIGNATURE`] is the `?signature=` query
//! parameter every mutating route takes ([`crate::handlers::Signed`]); in Swagger UI, *Authorize*
//! with the `signatureEncoded` value `GET /v1/admin` answers. [`ADMIN_BASIC`] is that route's
//! basic auth.

use njord_model::{
    Color, FeatureRecord, IndexMap, LayerQueryResultPage, S57Attribute, S57ExpectedInput,
    S57Object, ThemeMode,
};
use std::sync::{Arc, LazyLock};
use utoipa::openapi::security::{ApiKey, ApiKeyValue, Http, HttpAuthScheme, SecurityScheme};

use axum::Router;
use axum::extract::Path;
use axum::http::{StatusCode, header};
use axum::response::{IntoResponse, Json, Redirect, Response};
use axum::routing::get;
use utoipa::openapi::OpenApi as OpenApiDocument;
use utoipa::{Modify, OpenApi, ToSchema};
use utoipa_swagger_ui::Config;

use crate::handlers::{text, with_content_type};

/// Where Swagger UI is served (`/v1/api_docs` redirects to `/v1/api_docs/`).
pub const API_DOCS_PATH: &str = "/v1/api_docs";

/// The OpenAPI 3.1 document, as JSON.
pub const OPENAPI_PATH: &str = "/v1/openapi.json";

/// The security scheme of the signed routes: the `signature` query parameter.
pub const ADMIN_SIGNATURE: &str = "admin_signature";

/// The security scheme of `GET /v1/admin`: HTTP basic auth with the configured admin credentials.
pub const ADMIN_BASIC: &str = "admin_basic";

/// The document's static parts: title, description, tags, security schemes and the two
/// wildcard routes. The other paths and their schemas are collected by the router.
#[derive(OpenApi)]
#[openapi(
    info(
        title = "Njord",
        description = "Njord serves S-57 Electronic Navigational Charts as Mapbox Vector Tiles, \
with a MapLibre style, sprites and glyphs for drawing them, and offline region archives for the \
MX Mariner apps.\n\n\
Every route answers an empty `405` for the verbs it does not implement. Routes marked with a \
lock take the admin signature as the `signature` query parameter: `GET /v1/admin` (HTTP basic \
auth) mints one, and its `signatureEncoded` value is what *Authorize* takes here.",
        license(name = "Apache-2.0", url = "https://www.apache.org/licenses/LICENSE-2.0"),
    ),
    paths(crate::handlers::about::about, crate::handlers::content::content),
    components(schemas(AboutDocument, FeatureLookup, Binary)),
    modifiers(&Customize),
    tags(
        (name = "about", description = "Build identity and the S-57 and S-52 dictionaries"),
        (name = "style", description = "The MapLibre style, TileJSON, sprites and light sector icons"),
        (name = "tiles", description = "Vector tiles and the tile cache"),
        (name = "charts", description = "Ingested charts and their features"),
        (name = "ingest", description = "ENC uploads and the ingest status"),
        (name = "regions", description = "Offline region archives (`.mbtiles`)"),
        (name = "change reports", description = "The history of chart and base map changes"),
        (name = "admin", description = "Admin signatures and the nuke"),
        (name = "content", description = "Static resources: sprites and glyphs"),
    )
)]
pub struct ApiDoc;

/// A binary response or request body: `type: string, format: binary`.
///
/// For the document only. `Vec<u8>` would be described as an array of integers.
#[derive(ToSchema)]
#[schema(value_type = String, format = Binary)]
pub struct Binary(pub Vec<u8>);

/// What `GET /v1/about/{path}` answers, by `path`.
///
/// `version` is the build ([`njord_model::AboutJson`]); `s57objects` and `s57attributes` are
/// keyed by acronym, `expectedInput` by attribute acronym; `colors` is the S-52 color table of
/// each theme mode and `colors/custom` the named custom color sets.
///
/// The maps are spelled out rather than named by `njord_model::ColorLibrary`: the derive takes
/// an alias for a named schema and would emit a component called `IndexMap`.
#[derive(serde::Serialize, ToSchema)]
#[serde(untagged)]
pub enum AboutDocument {
    Version(njord_model::AboutJson),
    S57Objects(IndexMap<String, S57Object>),
    S57Attributes(IndexMap<String, S57Attribute>),
    ExpectedInput(IndexMap<String, Vec<S57ExpectedInput>>),
    Colors(IndexMap<ThemeMode, IndexMap<Color, String>>),
    CustomColors(IndexMap<String, IndexMap<ThemeMode, IndexMap<Color, String>>>),
}

/// What `GET /v1/feature/{by}/{arg}` answers: a page of a layer, or one feature by `LNAM`.
#[derive(serde::Serialize, ToSchema)]
#[serde(untagged)]
pub enum FeatureLookup {
    Layer(LayerQueryResultPage),
    // Boxed only to keep the variants close in size (clippy); the schema is the same.
    Lnam(Box<FeatureRecord>),
}

/// The version (`GET /v1/about/version`'s, not the bare crate version) and the security schemes.
struct Customize;

impl Modify for Customize {
    fn modify(&self, openapi: &mut utoipa::openapi::OpenApi) {
        openapi.info.version = crate::handlers::about::VERSION.to_owned();
        let components = openapi.components.get_or_insert_with(Default::default);
        components.add_security_scheme(
            ADMIN_SIGNATURE,
            SecurityScheme::ApiKey(ApiKey::Query(ApiKeyValue::with_description(
                "signature",
                "The `signatureEncoded` value of `GET /v1/admin`. It is bound to the external \
                 base URL it was minted for and expires after `adminExpirationSeconds`; \
                 `POST /v1/admin/verify` refreshes it.",
            ))),
        );
        components.add_security_scheme(
            ADMIN_BASIC,
            SecurityScheme::Http(
                Http::builder()
                    .scheme(HttpAuthScheme::Basic)
                    .description(Some("The configured `adminUser` and `adminPass`."))
                    .build(),
            ),
        );
    }
}

/// Swagger UI's configuration: "Try it out" is on, the entered signature survives a reload, the
/// operation filter is shown, and the layout is the base one, without the top bar (its URL box
/// and its own dark-mode toggle; [`PAGE`] sets the theme instead).
static CONFIG: LazyLock<Arc<Config<'static>>> = LazyLock::new(|| {
    Arc::new(
        Config::new([OPENAPI_PATH])
            .try_it_out_enabled(true)
            .persist_authorization(true)
            .display_request_duration(true)
            .filter(true)
            .use_base_layout(),
    )
});

/// The page at `/v1/api_docs/`: Swagger UI's own `index.html` plus the theme script, which turns
/// on Swagger UI's dark mode for dusk and night and follows the site's Theme picker (through the
/// Control Panel's document when framed, through its stored value in a tab of its own).
const PAGE: &str = include_str!("api_docs/index.html");

/// `theme.css`: the site's dusk and night palettes for the custom properties
/// [`themed_stylesheet`] introduces.
const THEME_CSS: &str = include_str!("api_docs/theme.css");

/// Swagger UI's dark-mode colours and the role each one plays, as `theme.css` names them. Every
/// value occurs only in `html.dark-mode` rules of the vendored `swagger-ui.css`, so replacing it
/// touches nothing in the light theme (`tests/openapi.rs` checks each one is found).
pub const DARK_MODE_ROLES: [(&str, &str); 14] = [
    ("#1c2022", "bg"),
    ("#2a2e30", "surface"),
    ("#0d1014", "surface"),
    ("#202225", "raised"),
    ("#434b4f", "raised"),
    ("#3b424d", "raised"),
    ("#080a0b", "deep"),
    ("#545d61", "border"),
    ("#6b757a", "border"),
    ("#e4e6e6", "fg"),
    ("#f0f1f1", "input-fg"),
    ("#b7bcbf", "muted"),
    ("#8c969a", "muted"),
    ("#51a8ff", "link"),
];

/// The vendored `swagger-ui.css` with each dark-mode colour of [`DARK_MODE_ROLES`] replaced by
/// `var(--nj-sw-<role>, <colour>)`: Swagger UI's own dark mode unless `theme.css` sets the role
/// for the page's `data-theme`. Rewriting the stylesheet keeps Swagger UI's complete dark-mode
/// rule set (some 250 selectors) instead of re-targeting it by hand.
pub fn themed_stylesheet(stylesheet: &str) -> String {
    DARK_MODE_ROLES
        .iter()
        .fold(stylesheet.to_owned(), |css, (color, role)| {
            css.replace(color, &format!("var(--nj-sw-{role}, {color})"))
        })
}

/// The stylesheet, rewritten once.
static STYLESHEET: LazyLock<String> = LazyLock::new(|| {
    let file = utoipa_swagger_ui::serve("swagger-ui.css", CONFIG.clone())
        .ok()
        .flatten()
        .expect("swagger-ui.css is in the vendored Swagger UI");
    themed_stylesheet(&String::from_utf8_lossy(&file.bytes))
});

/// The document at [`OPENAPI_PATH`] and Swagger UI under [`API_DOCS_PATH`] (`/v1/api_docs`
/// redirects to `/v1/api_docs/`). The page and `theme.css` are this module's, `swagger-ui.css`
/// is the vendored one rewritten by [`themed_stylesheet`], and everything else is served from
/// the vendored copy, with `swagger-initializer.js` carrying [`CONFIG`].
pub fn api_docs<S: Clone + Send + Sync + 'static>(openapi: OpenApiDocument) -> Router<S> {
    let document = Arc::new(openapi);
    let slash_path = format!("{API_DOCS_PATH}/");
    Router::new()
        .route(
            OPENAPI_PATH,
            get(move || {
                let document = document.clone();
                async move { Json(document) }
            }),
        )
        .route(
            API_DOCS_PATH,
            get(move || async move { Redirect::to(&slash_path) }),
        )
        .route(&format!("{API_DOCS_PATH}/"), get(|| async { page() }))
        .route(&format!("{API_DOCS_PATH}/{{*file}}"), get(asset))
}

fn page() -> Response {
    with_content_type("text/html; charset=utf-8", PAGE)
}

async fn asset(Path(file): Path<String>) -> Response {
    match file.as_str() {
        "index.html" => page(),
        "theme.css" => with_content_type("text/css; charset=utf-8", THEME_CSS),
        "swagger-ui.css" => with_content_type("text/css; charset=utf-8", STYLESHEET.as_str()),
        _ => match utoipa_swagger_ui::serve(&file, CONFIG.clone()) {
            Ok(Some(file)) => (
                [(header::CONTENT_TYPE, file.content_type)],
                file.bytes.into_owned(),
            )
                .into_response(),
            Ok(None) => StatusCode::NOT_FOUND.into_response(),
            Err(error) => text(StatusCode::INTERNAL_SERVER_ERROR, error.to_string()),
        },
    }
}
