# SPDX-License-Identifier: Apache-2.0
# Copyright 2026 The Encircle contributors
"""Turns a Kover (JaCoCo-format) XML report into a Markdown table of line coverage per package.

    python3 scripts/coverage-summary.py app/build/reports/kover/reportDebug.xml

Only package-level counters are read, so the output stays short enough for a pull request comment.
"""
import sys
import xml.etree.ElementTree as ET

ROOT = "de/volcanoproject/encircle"


def line_counter(element):
    for counter in element.findall("counter"):
        if counter.get("type") == "LINE":
            return int(counter.get("covered")), int(counter.get("missed"))
    return 0, 0


def bar(percent):
    filled = round(percent / 10)
    return "█" * filled + "░" * (10 - filled)


def main(path):
    sys.stdout.reconfigure(encoding="utf-8")
    report = ET.parse(path).getroot()
    rows = []
    for package in report.findall("package"):
        covered, missed = line_counter(package)
        total = covered + missed
        if total == 0:
            continue
        name = package.get("name").removeprefix(ROOT).strip("/") or "(app)"
        rows.append((name, covered, total))

    covered, missed = line_counter(report)
    total = covered + missed
    overall = 100 * covered / total if total else 0

    print("### Test coverage")
    print()
    print(f"**{overall:.1f} %** of lines are covered by unit tests ({covered} of {total}).")
    print()
    print("| Package | Lines | Coverage | |")
    print("| --- | ---: | ---: | --- |")
    for name, cov, tot in sorted(rows):
        percent = 100 * cov / tot
        print(f"| `{name}` | {cov} / {tot} | {percent:.1f} % | {bar(percent)} |")
    print()
    print("Recognition and selection logic in `analysis` should stay well covered; "
          "UI and Android glue code is checked on devices instead.")


if __name__ == "__main__":
    main(sys.argv[1])
