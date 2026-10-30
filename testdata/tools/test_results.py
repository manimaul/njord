#!/usr/bin/env python3
"""Summarise Gradle native test XML reports: `test_results.py <module> [<module> ...]`.

Prints one line per test class plus every failure message, so a long `./gradlew allTests` run
can be read without opening the HTML report. Run from the repo root.
"""
import glob
import sys
import xml.etree.ElementTree as ET

failed = 0
for module in sys.argv[1:]:
    for path in sorted(glob.glob(f"{module}/build/test-results/archTest/*.xml")):
        root = ET.parse(path).getroot()
        print(f"{path.split('/')[-1]} tests={root.get('tests')} failures={root.get('failures')} errors={root.get('errors')}")
        for case in root.iter("testcase"):
            for child in case:
                if child.tag in ("failure", "error"):
                    failed += 1
                    print(f"  FAIL {case.get('name')}: {(child.get('message') or '')[:800]}")
sys.exit(1 if failed else 0)
