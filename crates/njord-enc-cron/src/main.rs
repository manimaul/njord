//! `njord-enc-cron [resourcesDir] [--from-file <catalog.xml>] [--dry-run]`.
//!
//! Configuration comes from `<resourcesDir>/config/enc_cron.json` overlaid with the
//! `ENC_CRON_OPTS` environment variable; see [`EncCronConfig`].

use std::process::ExitCode;

use njord_enc_cron::config::EncCronConfig;
use njord_enc_cron::log;
use njord_enc_cron::run::{CommandLine, run};

#[tokio::main(flavor = "current_thread")]
async fn main() -> ExitCode {
    let result = async {
        let options = CommandLine::parse(std::env::args().skip(1))?;
        let config = EncCronConfig::from_env(options.resources_dir.as_deref())?;
        run(&config, &options).await
    }
    .await;
    match result {
        Ok(()) => ExitCode::SUCCESS,
        Err(e) => {
            // Non-zero so the CronJob's restartPolicy: OnFailure retries.
            log::error(format!("enc_cron failed: {e}"));
            ExitCode::FAILURE
        }
    }
}
