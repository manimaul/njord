//! Every `/v1` call the UI makes, typed through `njord-model`.
//!
//! Requests go through [`get`], [`post`] and [`delete`]; the rest are one wrapper per endpoint.
//! Every call returns an [`ApiResult`], whose [`ApiError`] carries the HTTP status (if there was
//! one) and the message the page shows.

use njord_model::{
    AboutJson, AdminResponse, AdminSignature, ChangeReport, ChangeReportPage, Chart, ChartCatalog,
    ColorLibrary, IconInfo, IndexMap, LayerQueryResultPage, RegionManifestEntry, S57Attribute,
    S57ExpectedInput, S57Object, Sprite, ThemeMode,
};
use serde::Serialize;
use serde::de::DeserializeOwned;
use wasm_bindgen::{JsCast, JsValue};
use wasm_bindgen_futures::JsFuture;
use web_sys::{Headers, RequestInit, Response};

use crate::state::DEFAULT_ERROR;

/// Why a call produced no value.
///
/// The message is what the page shows: `401 - Unauthorized`, `<status> - Network status`, or
/// [`DEFAULT_ERROR`] when the request never got an answer.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ApiError {
    pub status: Option<u16>,
    pub message: String,
}

impl ApiError {
    fn status(status: u16) -> ApiError {
        let message = if status == 401 {
            "401 - Unauthorized".to_owned()
        } else {
            format!("{status} - Network status")
        };
        ApiError {
            status: Some(status),
            message,
        }
    }

    fn network() -> ApiError {
        ApiError {
            status: None,
            message: DEFAULT_ERROR.to_owned(),
        }
    }
}

/// The result of an API call.
pub type ApiResult<T> = Result<T, ApiError>;

/// `/v1/<api>?k=v&…`; parameter values are sent as given (callers pass already-encoded values).
pub fn versioned_api(api: &str, params: &[(&str, &str)]) -> String {
    let mut url = format!("/v1/{api}");
    for (i, (k, v)) in params.iter().enumerate() {
        url.push(if i == 0 { '?' } else { '&' });
        url.push_str(k);
        url.push('=');
        url.push_str(v);
    }
    url
}

async fn fetch(url: &str, init: &RequestInit) -> ApiResult<Response> {
    let window = web_sys::window().ok_or_else(ApiError::network)?;
    let response = JsFuture::from(window.fetch_with_str_and_init(url, init))
        .await
        .map_err(|_| ApiError::network())?;
    response.dyn_into().map_err(|_| ApiError::network())
}

async fn decode<T: DeserializeOwned>(response: Response) -> ApiResult<T> {
    if !response.ok() {
        return Err(ApiError::status(response.status()));
    }
    let text = match response.text() {
        Ok(promise) => JsFuture::from(promise)
            .await
            .ok()
            .and_then(|t| t.as_string()),
        Err(_) => None,
    };
    // A body that does not decode is a failure carrying the (successful) status.
    text.and_then(|t| serde_json::from_str(&t).ok())
        .ok_or_else(|| ApiError::status(response.status()))
}

fn headers(pairs: &[(&str, &str)]) -> JsValue {
    let headers = Headers::new().expect("Headers");
    for (k, v) in pairs {
        let _ = headers.set(k, v);
    }
    headers.into()
}

/// `GET /v1/<api>` with `Accept: application/json`, decoding the JSON body.
///
/// # Errors
///
/// A network failure, a non-2xx status, or a body that does not decode as `T`.
pub async fn get<T: DeserializeOwned>(api: &str, params: &[(&str, &str)]) -> ApiResult<T> {
    let init = RequestInit::new();
    init.set_method("GET");
    init.set_headers(&headers(&[("Accept", "application/json")]));
    decode(fetch(&versioned_api(api, params), &init).await?).await
}

/// `POST /v1/<api>` with `body` as JSON, decoding the JSON answer.
///
/// # Errors
///
/// As [`get`]; also when `body` does not serialize.
pub async fn post<B: Serialize, T: DeserializeOwned>(api: &str, body: &B) -> ApiResult<T> {
    let init = RequestInit::new();
    init.set_method("POST");
    init.set_headers(&headers(&[
        ("Content-Type", "application/json"),
        ("Accept", "application/json"),
    ]));
    let json = serde_json::to_string(body).map_err(|_| ApiError::network())?;
    init.set_body(&JsValue::from_str(&json));
    decode(fetch(&versioned_api(api, &[]), &init).await?).await
}

/// `DELETE /v1/<api>`, ignoring any body.
///
/// # Errors
///
/// A network failure or a non-2xx status.
pub async fn delete(api: &str, params: &[(&str, &str)]) -> ApiResult<()> {
    let init = RequestInit::new();
    init.set_method("DELETE");
    let response = fetch(&versioned_api(api, params), &init).await?;
    if response.ok() {
        Ok(())
    } else {
        Err(ApiError::status(response.status()))
    }
}

/// `GET /v1/about/s57objects`: the S-57 object classes by acronym.
///
/// # Errors
///
/// As [`get`].
pub async fn s57_objects() -> ApiResult<IndexMap<String, S57Object>> {
    get("about/s57objects", &[]).await
}

/// `GET /v1/about/s57attributes`: the S-57 attributes by acronym.
///
/// # Errors
///
/// As [`get`].
pub async fn s57_attributes() -> ApiResult<IndexMap<String, S57Attribute>> {
    get("about/s57attributes", &[]).await
}

/// `GET /v1/about/expectedInput`: the enumerated values of each S-57 attribute.
///
/// # Errors
///
/// As [`get`].
pub async fn expected_inputs() -> ApiResult<IndexMap<String, Vec<S57ExpectedInput>>> {
    get("about/expectedInput", &[]).await
}

/// `GET /v1/about/colors`: the built-in colour library.
///
/// # Errors
///
/// As [`get`].
pub async fn colors() -> ApiResult<ColorLibrary> {
    get("about/colors", &[]).await
}

/// `GET /v1/about/colors/custom`: the custom colour libraries by name.
///
/// # Errors
///
/// As [`get`].
pub async fn custom_colors() -> ApiResult<IndexMap<String, ColorLibrary>> {
    get("about/colors/custom", &[]).await
}

/// `GET /v1/about/version`: the server's build information.
///
/// # Errors
///
/// As [`get`].
pub async fn about() -> ApiResult<AboutJson> {
    get("about/version", &[]).await
}

/// `DELETE /v1/enc_save`: aborts the chart install, clearing uploaded and staged files.
///
/// # Errors
///
/// As [`delete`].
pub async fn reset_chart_install(signature: &AdminResponse) -> ApiResult<()> {
    delete("enc_save", &[("signature", &signature.signature_encoded)]).await
}

/// `GET /v1/chart?name=…`: one installed chart.
///
/// # Errors
///
/// As [`get`].
pub async fn chart_info(name: &str) -> ApiResult<Chart> {
    get("chart", &[("name", name)]).await
}

/// `GET /v1/chart_catalog?name=…`: one page of installed charts, starting at `name`
/// (the previous page's `nextName`, or empty for the first page).
///
/// # Errors
///
/// As [`get`].
pub async fn chart_catalog(name: &str) -> ApiResult<ChartCatalog> {
    get("chart_catalog", &[("name", name)]).await
}

/// `GET /v1/admin`: the basic-auth login (the browser asks for the credentials).
/// Returns a signature.
///
/// # Errors
///
/// As [`get`]; a rejected login is a 401.
pub async fn admin() -> ApiResult<AdminResponse> {
    get("admin", &[]).await
}

/// `POST /v1/admin/verify`: checks that a stored signature is still valid.
///
/// # Errors
///
/// As [`post`].
pub async fn verify_admin(signature: &AdminSignature) -> ApiResult<AdminResponse> {
    post("admin/verify", signature).await
}

/// The sprite sheet index of a theme (`/v1/content/sprites/day_simplified@2x.json`).
///
/// # Errors
///
/// As [`get`].
pub async fn sprite_sheet(mode: ThemeMode) -> ApiResult<IndexMap<Sprite, IconInfo>> {
    get(
        &format!("content/sprites/{}_simplified@2x.json", mode.path_name()),
        &[],
    )
    .await
}

/// The URL of a theme's sprite sheet image (`/v1/content/sprites/day_simplified@2x.png`).
pub fn sprite_sheet_png(mode: ThemeMode) -> String {
    format!("/v1/content/sprites/{}_simplified@2x.png", mode.path_name())
}

/// `GET /v1/regions`: the downloadable region archives.
///
/// # Errors
///
/// As [`get`].
pub async fn regions() -> ApiResult<Vec<RegionManifestEntry>> {
    get("regions", &[]).await
}

/// `GET /v1/feature/layer/<layer>?start_id=…`: one page of a layer's features.
///
/// # Errors
///
/// As [`get`].
pub async fn features_by_layer(layer: &str, start_id: i64) -> ApiResult<LayerQueryResultPage> {
    get(
        &format!("feature/layer/{layer}"),
        &[("start_id", &start_id.to_string())],
    )
    .await
}

/// `DELETE /v1/chart?name=…`: removes an installed chart.
///
/// # Errors
///
/// As [`delete`].
pub async fn delete_chart(signature: &AdminResponse, name: &str) -> ApiResult<()> {
    delete(
        "chart",
        &[("name", name), ("signature", &signature.signature_encoded)],
    )
    .await
}

/// `GET /v1/change_reports?page=…`, signed: one page of the change history's days.
///
/// # Errors
///
/// As [`get`]; an expired signature is a 401.
pub async fn change_report_page(
    signature: &AdminResponse,
    page: i32,
) -> ApiResult<ChangeReportPage> {
    get(
        "change_reports",
        &[
            ("page", &page.to_string()),
            ("signature", &signature.signature_encoded),
        ],
    )
    .await
}

/// `GET /v1/change_reports/<date>`, signed: one day's change report.
///
/// # Errors
///
/// As [`get`]; a day with no events is a 404.
pub async fn change_report(signature: &AdminResponse, date: &str) -> ApiResult<ChangeReport> {
    get(
        &format!("change_reports/{date}"),
        &[("signature", &signature.signature_encoded)],
    )
    .await
}
