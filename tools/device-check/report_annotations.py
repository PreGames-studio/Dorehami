#!/usr/bin/env python3
"""Turns a captured device log into GitHub annotations.

Workflow logs live behind a blob URL that API-only environments cannot fetch, but check-run
annotations are readable through the REST API — so the interesting lines of the device log are
re-emitted as annotations (the same trick `.github/workflows/ci.yml` uses for test results).

Priority order (the annotation budget is limited):
  1. every `[device-check]` line — the script's own progress trail and its failure list,
  2. crash / app-lifecycle lines (`FATAL`, `AndroidRuntime`, app's own log tags),
  3. the tail of the log, when nothing above matched.
"""
from __future__ import annotations

import re
import sys

BUDGET = 45
MAX_LINE = 900
PROGRESS_PREFIX = "[device-check]"

CRASH = re.compile(
    r"FATAL|AndroidRuntime|Caused by|at ir\.dorehami|ANR in|Force finishing|beginning of crash|"
    r"am_crash|am_proc_died|Chess3D|GLSurfaceView|EGL_|libEGL|Shader|SoundPool|"
    r"ActivityManager.*ir\.dorehami|ActivityTaskManager.*ir\.dorehami"
)


def clean(line: str) -> str:
    return line.replace("\r", "").replace("%", "%25")[:MAX_LINE]


def main() -> int:
    path = sys.argv[1] if len(sys.argv) > 1 else "device-log.txt"
    try:
        text = open(path, errors="replace").read()
    except FileNotFoundError:
        print(f"::error::no device log found at {path}")
        return 1

    lines = [line for line in text.splitlines() if line.strip()]
    progress = [line for line in lines if line.startswith(PROGRESS_PREFIX)]
    crash = [line for line in lines if CRASH.search(line)]

    print(f"::notice::device log: {len(lines)} lines, {len(progress)} progress, {len(crash)} crash-related")

    shown = 0
    for line in progress[-30:]:
        print("::notice::" + clean(line))
        shown += 1
    for line in crash[-max(0, BUDGET - shown):]:
        print("::error::" + clean(line))
        shown += 1
    if shown == 0:
        for line in lines[-5:]:
            print("::error::" + clean(line))
    return 0


if __name__ == "__main__":
    sys.exit(main())
