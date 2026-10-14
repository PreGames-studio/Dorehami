#!/usr/bin/env python3
"""Publishes a build/device log as the output of a check run, so it can be read through the REST API.

Workflow logs are served from a blob URL that API-only environments cannot download, and a step can
only attach a handful of annotations, so the whole log goes into a fresh check run whose `output`
field is retrievable:

    gh api repos/<owner>/<repo>/commits/<sha>/check-runs \\
      --jq '.[] | select(.name == "<title>") | .output.summary'

Usage: publish_report.py <log-file> [head-sha] [title]
"""
from __future__ import annotations

import json
import os
import subprocess
import sys

SUMMARY_LIMIT = 60_000
DEFAULT_TITLE = "Device log (chess start)"


def main() -> int:
    log_path = sys.argv[1] if len(sys.argv) > 1 else "device-log.txt"
    sha = sys.argv[2] if len(sys.argv) > 2 else os.environ.get("GITHUB_SHA", "")
    title = sys.argv[3] if len(sys.argv) > 3 else DEFAULT_TITLE
    repo = os.environ.get("GITHUB_REPOSITORY", "")
    if not repo or not sha:
        print("::warning::GITHUB_REPOSITORY/GITHUB_SHA not set — skipping the report")
        return 0

    try:
        summary = open(log_path, errors="replace").read()
    except FileNotFoundError:
        summary = "(no report was written)"

    if len(summary) > SUMMARY_LIMIT:
        half = SUMMARY_LIMIT // 2
        summary = summary[:half] + "\n... (trimmed) ...\n" + summary[-half:]

    payload = {
        "name": title,
        "head_sha": sha,
        "status": "completed",
        "conclusion": "neutral",
        "output": {"title": title, "summary": summary},
    }
    proc = subprocess.run(
        ["gh", "api", "--method", "POST", f"/repos/{repo}/check-runs", "--input", "-"],
        input=json.dumps(payload).encode(),
        capture_output=True,
    )
    print(proc.stdout.decode(errors="replace").strip())
    if proc.returncode != 0:
        print("::warning::could not publish the report: " + proc.stderr.decode(errors="replace").strip())
    return 0


if __name__ == "__main__":
    sys.exit(main())
