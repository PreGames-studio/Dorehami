#!/usr/bin/env python3
"""Publishes the device log as the output of a check run, so it can be read through the REST API.

Workflow logs are served from a blob URL that API-only environments cannot download, and a step can
only attach a handful of annotations, so the whole log goes into a fresh check run whose `output`
field is retrievable:

    gh api repos/<owner>/<repo>/commits/<sha>/check-runs \
      --jq '.[] | select(.name == "device log") | .output.summary'

Usage: publish_report.py <log-file> [head-sha]
"""
from __future__ import annotations

import json
import os
import subprocess
import sys

SUMMARY_LIMIT = 60_000
TITLE = "Device log (chess start)"


def main() -> int:
    log_path = sys.argv[1] if len(sys.argv) > 1 else "device-log.txt"
    sha = sys.argv[2] if len(sys.argv) > 2 else os.environ.get("GITHUB_SHA", "")
    repo = os.environ.get("GITHUB_REPOSITORY", "")
    if not repo or not sha:
        print("::warning::GITHUB_REPOSITORY/GITHUB_SHA not set — skipping the report")
        return 0

    try:
        text = open(log_path, errors="replace").read()
    except FileNotFoundError:
        text = "(no device log was written)"

    head = text[: SUMMARY_LIMIT // 2]
    tail = text[-(SUMMARY_LIMIT - len(head)):]
    summary = head + ("\n... (trimmed) ...\n" if len(text) > SUMMARY_LIMIT else "") + tail if len(text) > SUMMARY_LIMIT else text

    payload = {
        "name": "device log",
        "head_sha": sha,
        "status": "completed",
        "conclusion": "neutral",
        "output": {"title": TITLE, "summary": summary},
    }
    proc = subprocess.run(
        ["gh", "api", "--method", "POST", f"/repos/{repo}/check-runs", "--input", "-"],
        input=json.dumps(payload).encode(),
        capture_output=True,
    )
    print(proc.stdout.decode(errors="replace").strip())
    if proc.returncode != 0:
        print("::warning::could not publish the device log check run: " + proc.stderr.decode(errors="replace").strip())
    return 0


if __name__ == "__main__":
    sys.exit(main())
