#!/usr/bin/env python3
"""Turns a captured device log into GitHub error annotations.

Workflow logs live behind a blob URL that API-only environments cannot fetch, but check-run
annotations are readable through the REST API — so the interesting lines of the device log are
re-emitted as annotations (that is also what `.github/workflows/ci.yml` does for tests).
"""
from __future__ import annotations

import re
import sys

MAX_ANNOTATIONS = 45
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
    # The crash block is always at the end; show the tail first, then the head.
    tail = hits[-MAX_ANNOTATIONS:]
    head = hits[:2]
    shown = head + [f"... ({len(hits)} matching lines total) ..."] + tail

    print(f"::notice::device log: {len(lines)} lines, {len(hits)} interesting")
    for line in shown[: MAX_ANNOTATIONS + 3]:
        print("::error::" + escape(line))
    if not hits:
        for line in lines[-3:]:
            print("::error::" + escape(line))
    return 0


if __name__ == "__main__":
    sys.exit(main())
