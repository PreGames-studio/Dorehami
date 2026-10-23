#!/usr/bin/env python3
"""Turns a captured device log into GitHub error annotations.

Workflow logs live behind a blob URL that API-only environments cannot fetch, but check-run
annotations are readable through the REST API — so the interesting lines of the device log are
re-emitted as annotations (that is also what `.github/workflows/ci.yml` does for tests).
"""
from __future__ import annotations

import re
import sys

# GitHub keeps only the first ten error annotations in a step. Reserve that budget for the
# actionable harness/navigation messages instead of dozens of noisy emulator OpenGL lines.
MAX_ANNOTATIONS = 10
MAX_LINE = 900

INTERESTING = re.compile(
    r"FATAL|AndroidRuntime|Exception|Throwable|Error|error|ABORT|signal|"
    r"device-check|Chess3D|GLSurfaceView|EGL|Shader|OpenGL|SoundPool|W/|E/",
    re.IGNORECASE,
)


def escape(line: str) -> str:
    return line.replace("\r", "").replace("%", "%25")[:MAX_LINE]


def main() -> int:
    path = sys.argv[1] if len(sys.argv) > 1 else "device-log.txt"
    try:
        text = open(path, errors="replace").read()
    except FileNotFoundError:
        print(f"::error::no device log found at {path}")
        return 1

    lines = text.splitlines()
    hits = [line for line in lines if INTERESTING.search(line) and line.strip()]
    device = [line for line in hits if "[device-check]" in line]
    system = [line for line in hits if "[device-check]" not in line]
    # Keep seven recent harness lines (including the final failure), one count, and two crash/
    # graphics lines. This fits GitHub's ten-error budget without hiding the actual failure.
    shown = device[-7:] + [f"... ({len(hits)} matching lines total) ..."] + system[-2:]

    print(f"::notice::device log: {len(lines)} lines, {len(hits)} interesting")
    for line in shown[:MAX_ANNOTATIONS]:
        print("::error::" + escape(line))
    if not hits:
        for line in lines[-3:]:
            print("::error::" + escape(line))
    return 0


if __name__ == "__main__":
    sys.exit(main())
