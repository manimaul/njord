#!/usr/bin/env python3
"""Trim NOAA's ENCProdCat_19115.xml to the P-EC-1 fixture testdata/enc_cron/catalog_sample.xml.

    curl -sSLo /tmp/cat/ENCProdCat_19115.xml https://charts.noaa.gov/ENCs/ENCProdCat_19115.xml
    python3 testdata/tools/trim_catalog.py /tmp/cat/ENCProdCat_19115.xml [testdata/enc_cron/catalog_sample.xml]

Kept verbatim from the real document:
  - the DS_Series root with its namespace bindings (EncProdCatParser.validateRoot asserts them)
  - the records named in KEEP_CELLS, each with its dataQualityInfo lineage block whose
    sourceCitation/title is the literal "0" (the decoy that must never become a cell name)
  - the trailing <seriesMetadata> MD_Metadata record, which is a real MD_Metadata element with
    no cell title, edition or download URL and must be skipped, not fatal

Synthesised: one copy of a kept record renamed US5ZZ99M with its <onLine> block removed, so the
sample also has a cell record missing a required field. "ZZ" is not a state code.

The selection of records is the fixture's definition; keep this list in sync with the header
comment the script writes into the output file.
"""
import re
import sys

KEEP_CELLS = [
    "US1EEZ1M",  # first record in the file; scale 1:3,000,000; edition "10.1"
    "US3AK12M",  # classic US<band><state><nn><letter> name, coastal band
    "US509890",  # rescheme-era name without the letter suffix
    "US5A1KFV",  # edition ending in ".0" - the zero update number must survive parsing
]
SYNTH_FROM, SYNTH_NAME = "US3AK12M", "US5ZZ99M"


def main(src, dst):
    xml = open(src, encoding="utf-8").read()
    head, rest = xml.split("<composedOf>", 1)
    records = re.findall(r"<composedOf>.*?</composedOf>", xml, re.S)
    by_cell = {}
    for rec in records:
        m = re.search(r"<title>\s*<gco:CharacterString>([^<]+)</gco:CharacterString>", rec)
        if m and m.group(1) in KEEP_CELLS and m.group(1) not in by_cell:
            by_cell[m.group(1)] = rec
    missing = [c for c in KEEP_CELLS if c not in by_cell]
    if missing:
        raise SystemExit(f"cells no longer in the catalog, update KEEP_CELLS: {missing}")
    tail = xml[xml.rfind("</composedOf>") + len("</composedOf>"):]
    assert "<seriesMetadata>" in tail, "expected the trailing seriesMetadata block"

    synth = by_cell[SYNTH_FROM].replace(SYNTH_FROM, SYNTH_NAME)
    synth = re.sub(r"\s*<onLine>.*?</onLine>", "", synth, flags=re.S)
    assert "<URL>" not in synth

    date_stamp = re.search(r"<gco:DateTime>([^<]+)</gco:DateTime>", by_cell[KEEP_CELLS[0]]).group(1)
    header = f"""<!--
  njord fixture P-EC-1 (testdata/enc_cron/catalog_sample.xml)
  Pins: EncProdCatParser element-path rules - cell/edition/url/scale/size/revision/publication
        extraction, the dataQualityInfo sourceCitation/title "0" decoy never becoming a cell
        name, and incomplete MD_Metadata records (US5ZZ99M without <onLine>, and the trailing
        seriesMetadata record) being skipped rather than fatal.
  Source: https://charts.noaa.gov/ENCs/ENCProdCat_19115.xml dateStamp {date_stamp}, trimmed by
        testdata/tools/trim_catalog.py to {", ".join(KEEP_CELLS)} plus the synthetic {SYNTH_NAME}.
  Regenerate: python3 testdata/tools/trim_catalog.py <full catalog> testdata/enc_cron/catalog_sample.xml
-->
"""
    decl, root = head.split("\n", 1)
    out = decl + "\n" + header + root + "".join(by_cell[c] for c in KEEP_CELLS) + synth + tail
    open(dst, "w", encoding="utf-8", newline="\n").write(out)
    print(f"wrote {dst}: {len(out)} bytes, {len(KEEP_CELLS) + 1} cell records + seriesMetadata")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else "testdata/enc_cron/catalog_sample.xml")
