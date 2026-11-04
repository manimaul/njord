//! All outbound HTTP for the cron job: the NOAA catalog, Njord's edition listing, the admin
//! signature, chart deletes and cell zips.

use std::path::Path;
use std::time::Duration;

use base64::Engine;
use futures_util::TryStreamExt;
use indexmap::IndexMap;
use njord_model::{AdminResponse, ChartEditions};
use reqwest::StatusCode;
use tokio::io::AsyncWriteExt;

use crate::catalog::{self, EncCatalogEntry};
use crate::config::EncCronConfig;
use crate::log;

/// Any failure of a run; the message is logged as is.
pub type Error = Box<dyn std::error::Error + Send + Sync>;

/// The calls [`BundleBuilder`](crate::bundle::BundleBuilder) and
/// [`delete_orphans`](crate::run::delete_orphans) make, so tests can stand in a fake for the
/// network. [`EncHttp`] is the only production implementation.
#[allow(async_fn_in_trait)]
pub trait EncTransport {
    /// Downloads `url` to `dest`. Returns bytes written.
    async fn download(&self, url: &str, dest: &Path) -> Result<u64, Error>;
    /// A short lived admin signature from `GET /v1/admin`, already URL encoded.
    async fn fetch_admin_signature(
        &self,
        base_url: &str,
        user: &str,
        pass: &str,
    ) -> Result<String, Error>;
    /// Deletes a chart by name. `true` when a row was removed, `false` when the server had
    /// nothing by that name - a benign race with a concurrent ingest, not a failure.
    async fn delete_chart(
        &self,
        base_url: &str,
        name: &str,
        signature: &str,
    ) -> Result<bool, Error>;
}

/// `DELETE /v1/chart?name=&cause=noaa_enc_cron&signature=`: the orphan delete, attributed to
/// the cron.
pub fn delete_chart_url(base_url: &str, name: &str, signature: &str) -> String {
    format!("{base_url}/v1/chart?name={name}&cause=noaa_enc_cron&signature={signature}")
}

pub struct EncHttp<'a> {
    config: &'a EncCronConfig,
    client: reqwest::Client,
}

impl<'a> EncHttp<'a> {
    /// A client with `config`'s request timeout.
    ///
    /// # Errors
    ///
    /// The HTTP client cannot be built.
    pub fn new(config: &'a EncCronConfig) -> Result<Self, Error> {
        let client = reqwest::Client::builder()
            .timeout(Duration::from_secs(config.request_timeout_seconds))
            .read_timeout(Duration::from_secs(60))
            .build()?;
        Ok(Self { config, client })
    }

    /// Charts Njord already has, as `chartName -> "<UPDN>:<UADT>:<ISDT>"`.
    ///
    /// Failure here must propagate: with no baseline every cell looks stale, and silently falling
    /// back to "download everything" would hammer NOAA and flood the ingest queue.
    ///
    /// # Errors
    ///
    /// Every attempt fails: no answer, a non-2xx status, or a body that does not decode.
    pub async fn fetch_chart_editions(&self) -> Result<IndexMap<String, String>, Error> {
        self.with_retry("chart editions", || async {
            let response = self
                .client
                .get(&self.config.chart_editions_url)
                .send()
                .await?
                .error_for_status()?;
            Ok(response.json::<ChartEditions>().await?.editions)
        })
        .await
    }

    /// Streams the product catalog through the parser without materialising the document. A
    /// truncated body cannot be resumed, so the retry wraps the whole fetch-and-parse.
    ///
    /// # Errors
    ///
    /// The fetch or the parse fails on every attempt.
    pub async fn fetch_catalog(&self) -> Result<Vec<EncCatalogEntry>, Error> {
        let parser = self
            .with_retry("product catalog", || async {
                let response = self
                    .client
                    .get(&self.config.catalog_url)
                    // Identity encoding, so a proxy can never hand the parser undecoded gzip.
                    .header(reqwest::header::ACCEPT_ENCODING, "identity")
                    .send()
                    .await?
                    .error_for_status()?;
                let body = tokio_util::io::StreamReader::new(
                    response.bytes_stream().map_err(std::io::Error::other),
                );
                let reader = tokio::io::BufReader::with_capacity(64 * 1024, body);
                Ok(catalog::parse_async(reader).await?)
            })
            .await?;
        if parser.records_skipped > 0 {
            log::warn(format!(
                "catalog: skipped {} of {} records missing required fields",
                parser.records_skipped, parser.records_seen
            ));
        }
        Ok(parser.entries)
    }

    async fn with_retry<T, F, Fut>(&self, what: &str, mut block: F) -> Result<T, Error>
    where
        F: FnMut() -> Fut,
        Fut: Future<Output = Result<T, Error>>,
    {
        let attempts = self.config.max_retries;
        let mut last: Option<Error> = None;
        for attempt in 0..attempts {
            match block().await {
                Ok(t) => return Ok(t),
                Err(e) => {
                    let remaining = attempts - attempt - 1;
                    if remaining > 0 {
                        let backoff = 2_000 * u64::from(attempt + 1);
                        log::warn(format!(
                            "{what} failed ({e}); {remaining} attempt(s) left, retrying in {backoff}ms"
                        ));
                        tokio::time::sleep(Duration::from_millis(backoff)).await;
                    }
                    last = Some(e);
                }
            }
        }
        let cause = last.map(|e| format!(": {e}")).unwrap_or_default();
        Err(format!("{what} failed after {attempts} attempts{cause}").into())
    }
}

impl EncTransport for EncHttp<'_> {
    /// Writes through a sibling `.tmp` file, so a crashed or truncated transfer never leaves a
    /// plausible looking zip behind. A non-2xx answer is an error, never a saved body.
    async fn download(&self, url: &str, dest: &Path) -> Result<u64, Error> {
        self.with_retry(&format!("download {url}"), || async {
            let mut tmp_name = dest.file_name().unwrap_or_default().to_os_string();
            tmp_name.push(".tmp");
            let tmp = dest.with_file_name(tmp_name);
            let result: Result<u64, Error> = async {
                let mut response = self.client.get(url).send().await?.error_for_status()?;
                let mut file = tokio::fs::File::create(&tmp).await?;
                let mut total = 0u64;
                while let Some(chunk) = response.chunk().await? {
                    file.write_all(&chunk).await?;
                    total += chunk.len() as u64;
                }
                file.flush().await?;
                drop(file);
                tokio::fs::rename(&tmp, dest).await?;
                Ok(total)
            }
            .await;
            if result.is_err() {
                let _ = tokio::fs::remove_file(&tmp).await;
            }
            result
        })
        .await
    }

    /// The signature is an HMAC over the base URL the server saw on this request, so it is only
    /// valid for calls made against the same `base_url`.
    async fn fetch_admin_signature(
        &self,
        base_url: &str,
        user: &str,
        pass: &str,
    ) -> Result<String, Error> {
        self.with_retry("admin signature", || async {
            let credentials =
                base64::engine::general_purpose::STANDARD.encode(format!("{user}:{pass}"));
            let response = self
                .client
                .get(format!("{base_url}/v1/admin"))
                .header(
                    reqwest::header::AUTHORIZATION,
                    format!("Basic {credentials}"),
                )
                .send()
                .await?
                .error_for_status()?;
            Ok(response.json::<AdminResponse>().await?.signature_encoded)
        })
        .await
    }

    /// The URL is assembled by hand because `signature` arrives already URL encoded.
    /// `cause=noaa_enc_cron` attributes the delete to the cron in the change report.
    async fn delete_chart(
        &self,
        base_url: &str,
        name: &str,
        signature: &str,
    ) -> Result<bool, Error> {
        self.with_retry(&format!("delete chart {name}"), || async {
            let response = self
                .client
                .delete(delete_chart_url(base_url, name, signature))
                .send()
                .await?
                .error_for_status()?;
            Ok(response.status() == StatusCode::ACCEPTED)
        })
        .await
    }
}

#[cfg(test)]
mod tests {
    use super::delete_chart_url;

    #[test]
    fn delete_names_the_cron_as_the_cause() {
        assert_eq!(
            delete_chart_url("https://njord.test", "US5WA22M.000", "sig%3D"),
            "https://njord.test/v1/chart?name=US5WA22M.000&cause=noaa_enc_cron&signature=sig%3D"
        );
    }
}
