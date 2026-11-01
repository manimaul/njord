//! `testdata/security/paths.json`: caller-supplied names as paths.
//!
//! The `content_resource`
//! and `archive_name` cases are checked by the content and region routes (tests/conformance.rs,
//! tests/behaviour.rs); here the upload file names and zip entry names, each with its
//! `port.expected`.

use njord_server::handlers::enc_save::valid_upload_name;
use njord_server::ingest::zip::{entry_target, extract};
use njord_testdata::{Failures, cases, port_expected};
use std::io::Write;

#[test]
fn upload_file_names() {
    let mut failures = Failures::default();
    let mut checked = 0;
    for case in cases("security/paths.json") {
        if case["input"]["kind"] != "upload_filename" {
            continue;
        }
        checked += 1;
        let name = case["input"]["name"].as_str().unwrap();
        let expected = port_expected(&case);
        let status = if valid_upload_name(name) { 200 } else { 400 };
        let mut result = (expected["status"] == status)
            .then_some(())
            .ok_or(format!("status {status}, expected {}", expected["status"]));
        if status == 200
            && result.is_ok()
            && (expected["writes"] != name || expected["tmp"] != format!("{name}.tmp"))
        {
            result = Err(format!("writes {name} / {name}.tmp, expected {expected}"));
        }
        failures.check(name, result);
    }
    failures.assert_none(checked);
}

fn zip_with(entries: &[(&str, &[u8])]) -> Vec<u8> {
    let mut cursor = std::io::Cursor::new(Vec::new());
    {
        let mut w = zip::ZipWriter::new(&mut cursor);
        let options = zip::write::SimpleFileOptions::default();
        for (name, data) in entries {
            if name.ends_with('/') {
                w.add_directory(*name, options).unwrap();
            } else {
                w.start_file(*name, options).unwrap();
                w.write_all(data).unwrap();
            }
        }
        w.finish().unwrap();
    }
    cursor.into_inner()
}

/// Every entry's target, and a whole-zip extraction with the entry in it: an unsafe name aborts
/// the extraction with nothing written.
#[test]
fn zip_entries() {
    let root = std::path::Path::new(env!("CARGO_TARGET_TMPDIR")).join("njord-server-tests/zip");
    let _ = std::fs::remove_dir_all(&root);
    let mut failures = Failures::default();
    let mut checked = 0;
    for (i, case) in cases("security/paths.json").iter().enumerate() {
        if case["input"]["kind"] != "zip_entry" {
            continue;
        }
        checked += 1;
        let name = case["input"]["name"].as_str().unwrap();
        let expected = port_expected(case);
        let dir = root.join(format!("case_{i}"));
        let target = dir.join("out");
        std::fs::create_dir_all(&target).unwrap();
        let zip_path = dir.join("in.zip");
        std::fs::write(&zip_path, zip_with(&[(name, b"data"), ("ok.txt", b"ok")])).unwrap();
        let extracted = extract(&zip_path, &target, |_| false, |_, _| {});
        let outside_before = std::fs::read_dir(&dir).unwrap().count();
        let actual_extracted = extracted.is_ok();
        let mut result = (expected["extracted"] == actual_extracted)
            .then_some(())
            .ok_or(format!(
                "extracted {actual_extracted}, expected {}: {extracted:?}",
                expected["extracted"]
            ));
        if result.is_ok() {
            // what was written, relative to the target: files, and directories that hold
            // nothing (an explicit `dir/` entry); the parents of a file are implied
            let mut written: Vec<String> = walk(&target)
                .iter()
                .filter(|p| p.is_file() || std::fs::read_dir(p).unwrap().next().is_none())
                .map(|p| {
                    let rel = p
                        .strip_prefix(&target)
                        .unwrap()
                        .to_string_lossy()
                        .into_owned();
                    if p.is_dir() { format!("{rel}/") } else { rel }
                })
                .filter(|w| w != "ok.txt")
                .collect();
            written.sort();
            let mut expected_writes: Vec<String> = expected["writes"]
                .as_array()
                .unwrap()
                .iter()
                .map(|w| w.as_str().unwrap().to_owned())
                .collect();
            expected_writes.sort();
            if actual_extracted && written != expected_writes {
                result = Err(format!("wrote {written:?}, expected {expected_writes:?}"));
            }
            if !actual_extracted && (!walk(&target).is_empty() || outside_before != 2) {
                result = Err(format!("an aborted extraction wrote {:?}", walk(&target)));
            }
            // `entry_target` agrees with the extraction
            let t = entry_target(&target, name);
            assert_eq!(t.is_some(), actual_extracted, "{name}: {t:?}");
        }
        failures.check(name, result);
    }
    failures.assert_none(checked);
}

fn walk(dir: &std::path::Path) -> Vec<std::path::PathBuf> {
    let mut out = Vec::new();
    for entry in std::fs::read_dir(dir).unwrap().flatten() {
        let path = entry.path();
        if path.is_dir() {
            out.push(path.clone());
            out.extend(walk(&path));
        } else {
            out.push(path);
        }
    }
    out
}
