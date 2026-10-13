#!/usr/bin/env python3
"""Device smoke test: open Dorehami, walk to the chess setup screen, start a game against the bot.

This reproduces the report "pressing «شروع بازی با ربات» crashes and closes the app" on a real
Android device/emulator, because that path is the only one plain JVM tests cannot cover:
`GLSurfaceView` + EGL configuration + `SoundPool` all run for the first time here.

Run from a workflow that already installed the debug APK (see `.github/workflows/device-check.yml`):

    python3 tools/device-check/chess_start_crash.py [debug-apk-path]

The script taps by *text* (uiautomator dump), so it keeps working when the layout shifts, and it
prints the crash buffer / GL log lines on failure. Every line is also emitted as a GitHub workflow
command (`::notice::` / `::error::`), so the result is readable through the check-run annotations
API even when the workflow log itself cannot be downloaded.

Exit code 0 = the app survived every step.
"""
from __future__ import annotations

import os
import re
import subprocess
import sys
import time

PKG = "ir.dorehami.app"
ACTIVITY = f"{PKG}/.MainActivity"
SHOTS = os.environ.get("SHOT_DIR", "device-shots")

failures: list[str] = []


def sh(*args: str, timeout: int = 120, binary: bool = False) -> subprocess.CompletedProcess:
    """Runs one adb command. [binary] = do not decode stdout (screencap returns a PNG)."""
    return subprocess.run(
        list(args),
        capture_output=True,
        timeout=timeout,
        **({} if binary else {"text": True, "errors": "replace"}),
    )


def emit(kind: str, message: str) -> None:
    """Prints a line and mirrors it as a GitHub annotation (percent-escaped, single line)."""
    message = message.replace("\r", "").replace("%", "%25")
    print(f"[device-check] {message}", flush=True)
    print(f"::{kind}::{message}"[:1200], flush=True)


def log(message: str) -> None:
    emit("notice", message)


def screenshot(name: str) -> None:
    """Saves a PNG of the current screen; never fatal (screenshots are a review aid)."""
    try:
        os.makedirs(SHOTS, exist_ok=True)
        with open(os.path.join(SHOTS, f"{name}.png"), "wb") as fh:
            fh.write(sh("adb", "exec-out", "screencap", "-p", binary=True).stdout)
    except Exception as exc:  # noqa: BLE001 — a failed screenshot must not fail the run
        log(f"screenshot «{name}» failed: {exc!r}")


def alive() -> bool:
    return sh("adb", "shell", "pidof", PKG).stdout.strip() != ""


def nodes() -> list[tuple[str, str, tuple[int, int, int, int]]]:
    sh("adb", "shell", "uiautomator", "dump", "/sdcard/window_dump.xml")
    xml = sh("adb", "exec-out", "cat", "/sdcard/window_dump.xml").stdout or ""
    out = []
    for tag in re.findall(r"<node\b[^>]*>", xml):
        text = re.search(r'text="([^"]*)"', tag)
        desc = re.search(r'content-desc="([^"]*)"', tag)
        bounds = re.search(r'bounds="\[(\d+),(\d+)]\[(\d+),(\d+)]"', tag)
        if not bounds:
            continue
        x1, y1, x2, y2 = (int(v) for v in bounds.groups())
        out.append((text.group(1) if text else "", desc.group(1) if desc else "", (x1, y1, x2, y2)))
    return out


def tap_text(fragment: str, *, required: bool = True, tries: int = 3) -> bool:
    """Taps the first node whose text/description contains [fragment]."""
    for attempt in range(tries):
        for text, desc, (x1, y1, x2, y2) in nodes():
            if fragment in text or fragment in desc:
                if x2 - x1 < 4 or y2 - y1 < 4:
                    continue
                x, y = (x1 + x2) // 2, (y1 + y2) // 2
                log(f"tap «{fragment}» at ({x},{y}) — node text={text!r}")
                sh("adb", "shell", "input", "tap", str(x), str(y))
                return True
        if attempt < tries - 1:
            time.sleep(1.0)
    message = f"could not find «{fragment}» on screen"
    emit("error", message)
    if required:
        failures.append(message)
        for text, desc, bounds in nodes()[:25]:
            log(f"    node text={text!r} desc={desc!r} bounds={bounds}")
    return False


def step(name: str, action, settle: float = 1.5) -> bool:
    """Runs one UI step; a dead process or a missing node marks the step failed."""
    if not alive():
        emit("error", f"app was already dead before step «{name}»")
        failures.append(f"app was already dead before step «{name}»")
        return False
    action()
    time.sleep(settle)
    screenshot(name)
    if not alive():
        emit("error", f"app crashed during step «{name}»")
        failures.append(f"app crashed during step «{name}»")
        return False
    log(f"✔ «{name}» — app still alive")
    return True


LOG_TAGS = re.compile(
    r"FATAL|AndroidRuntime|Caused by|at ir\.dorehami|ANR in|Force finishing|beginning of crash|"
    r"Chess3D|GLSurfaceView|EGL|Shader|SoundPool|ActivityManager.*ir\.dorehami|am_crash|am_proc_died"
)


def dump_logs() -> None:
    crash = sh("adb", "logcat", "-d", "-b", "crash", "-v", "time").stdout.strip()
    if crash:
        emit("error", "---- crash buffer ----")
        for line in crash.splitlines()[:40]:
            emit("error", line)
    else:
        log("crash buffer is empty (no fatal exception recorded)")
    log("---- app log lines ----")
    out = sh("adb", "logcat", "-d", "-v", "time").stdout
    kept = [line for line in out.splitlines() if LOG_TAGS.search(line)]
    for line in kept[-25:]:
        emit("error", line)
    if not kept:
        log("no app-related log lines found")


def main() -> int:
    apk = sys.argv[1] if len(sys.argv) > 1 else "app/build/outputs/apk/debug/app-debug.apk"
    sh("adb", "wait-for-device", timeout=300)
    log(f"device: {sh('adb', 'shell', 'getprop', 'ro.product.model').stdout.strip()} / API {sh('adb', 'shell', 'getprop', 'ro.build.version.sdk').stdout.strip()}")

    install = sh("adb", "install", "-r", "-t", apk, timeout=300)
    emit("notice", (install.stdout.strip() or install.stderr.strip()))
    if "Success" not in install.stdout:
        emit("error", f"install failed: {install.stdout} {install.stderr}")
        return 1

    sh("adb", "logcat", "-c")
    sh("adb", "shell", "am", "force-stop", PKG)
    sh("adb", "shell", "am", "start", "-n", ACTIVITY)
    time.sleep(7)
    screenshot("00-home")
    if not alive():
        emit("error", "app died on launch")
        dump_logs()
        return 1

    step("01-games", lambda: tap_text("ورود به بازی"))
    step("02-chess-setup", lambda: tap_text("شطرنج"))
    # Level / colour defaults are exactly what a user gets when they just press the big button.
    step("03-chess-board", lambda: tap_text("شروع بازی با ربات"), settle=8.0)

    time.sleep(1.0)
    if not alive():
        if not any("03-chess-board" in f for f in failures):
            emit("error", "app crashed after the board was shown")
            failures.append("app crashed after the board was shown")
    else:
        log("chess opened and stayed alive")

    if failures:
        emit("error", "RESULT: FAIL — " + " | ".join(failures))
        dump_logs()
        return 1
    log("RESULT: PASS — chess started without a crash")
    return 0


if __name__ == "__main__":
    sys.exit(main())
