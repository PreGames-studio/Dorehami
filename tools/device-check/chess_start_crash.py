#!/usr/bin/env python3
"""Device smoke test: open Dorehami, walk to the chess setup screen, start a game against the bot.

This reproduces the report "pressing «شروع بازی با ربات» crashes and closes the app" on a real
Android device/emulator, because that path is the only one plain JVM tests cannot cover:
`GLSurfaceView` + EGL configuration + `SoundPool` all run for the first time here.

Run from a workflow that already installed the debug APK (see `.github/workflows/device-check.yml`):

    python3 tools/device-check/chess_start_crash.py [debug-apk-path]

The script drives the UI by *text* (uiautomator dump) and waits for a marker of each screen before
moving on, so a slow emulator or a swallowed tap never turns into a false failure. Every line is
also emitted as a GitHub workflow command (`::notice::` / `::error::`), so the result stays readable
through the check-run annotations API even when the workflow log itself cannot be downloaded.

Exit code 0 = the app survived every step.
"""
from __future__ import annotations

import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "ir.dorehami.app"
ACTIVITY = f"{PKG}/.MainActivity"
SHOTS = os.environ.get("SHOT_DIR", "device-shots")

# Markers of the screens on the way to the board (a text that is only on that screen).
HOME_MARKER = "ورود به بازی"
GAMES_MARKER = "انتخاب بازی"
SETUP_MARKER = "قوانین بازی"
BOARD_MARKER = "تسلیم"

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
    message = message.replace("\r", "").replace("\n", " ").replace("%", "%25")
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


def press(x: int, y: int, hold_ms: int = 120) -> None:
    """Presses a point.

    `input tap` is a zero-length touch, which Compose occasionally drops; a `swipe` between the same
    two points with a short duration is a real press-and-release and is far more reliable.
    """
    sh("adb", "shell", "input", "swipe", str(x), str(y), str(x), str(y), str(hold_ms))


def alive() -> bool:
    return sh("adb", "shell", "pidof", PKG).stdout.strip() != ""


BOUNDS = re.compile(r"\[(\d+),(\d+)]\[(\d+),(\d+)]")


class Node:
    """One labelled element: its own bounds and the bounds of the clickable element around it."""

    __slots__ = ("text", "desc", "bounds", "clickable")

    def __init__(self, text: str, desc: str, bounds: tuple[int, int, int, int], clickable):
        self.text = text
        self.desc = desc
        self.bounds = bounds
        self.clickable = clickable  # bounds of the nearest clickable ancestor, if any

    @property
    def matches_any(self) -> bool:
        return bool(self.text or self.desc)


def ui_dump() -> tuple[list[Node], str]:
    """Reads the accessibility tree: labelled nodes plus the raw XML (for diagnostics)."""
    sh("adb", "shell", "uiautomator", "dump", "/sdcard/window_dump.xml")
    xml = sh("adb", "exec-out", "cat", "/sdcard/window_dump.xml").stdout or ""
    out: list[Node] = []
    try:
        root = ET.fromstring(xml)

        def walk(element, clickable_bounds):
            m = BOUNDS.match(element.attrib.get("bounds", ""))
            bounds = tuple(int(v) for v in m.groups()) if m else None
            inner = bounds if element.attrib.get("clickable") == "true" and bounds else clickable_bounds
            text = element.attrib.get("text", "")
            desc = element.attrib.get("content-desc", "")
            if (text or desc) and bounds:
                out.append(Node(text, desc, bounds, inner))
            for child in element:
                walk(child, inner)

        walk(root, None)
    except ET.ParseError:
        # Fall back to a flat scan: good enough to find labels, only the tap target is less exact.
        for tag in re.findall(r"<node\b[^>]*>", xml):
            text = re.search(r'text="([^"]*)"', tag)
            desc = re.search(r'content-desc="([^"]*)"', tag)
            m = BOUNDS.search(tag)
            if not m:
                continue
            bounds = tuple(int(v) for v in m.groups())
            out.append(Node(text.group(1) if text else "", desc.group(1) if desc else "", bounds, bounds))
    return out, xml


def nodes() -> list[Node]:
    return ui_dump()[0]


def visible_texts(limit: int = 20) -> list[str]:
    return [n.text for n in nodes() if n.text][:limit]


def on_text(fragment: str) -> bool:
    return any(fragment in n.text or fragment in n.desc for n in nodes())


def wait_text(fragment: str, timeout: float = 8.0) -> bool:
    deadline = time.time() + timeout
    while True:
        if on_text(fragment):
            return True
        if time.time() >= deadline:
            return False
        time.sleep(0.6)


def screen_size() -> tuple[int, int]:
    out = sh("adb", "shell", "wm", "size").stdout
    m = re.search(r"(\d+)x(\d+)", out)
    return (int(m.group(1)), int(m.group(2))) if m else (1080, 1920)


def scroll_down(step_px: int = 500) -> None:
    """Swipes up in the middle of the screen, which scrolls a Compose column down."""
    w, h = screen_size()
    x = w // 2
    sh("adb", "shell", "input", "swipe", str(x), str(int(h * 0.72)), str(x), str(int(h * 0.72) - step_px), "220")


def tap_text(fragment: str, *, scroll: bool = False) -> bool:
    """Taps the element labelled [fragment] — the clickable around it, not just the label.

    Compose draws a button's text in a child node; tapping the middle of the *label* normally works,
    but only the clickable parent is guaranteed to receive the press, so that is what we aim at.
    """
    for node in nodes():
        if fragment in node.text or fragment in node.desc:
            x1, y1, x2, y2 = node.clickable or node.bounds
            if x2 - x1 < 4 or y2 - y1 < 4:
                continue
            x, y = (x1 + x2) // 2, (y1 + y2) // 2
            where = "clickable" if node.clickable else "label"
            log(f"tap «{fragment}» at ({x},{y}) on {where} bounds=[{x1},{y1}][{x2},{y2}] text={node.text!r}")
            press(x, y)
            return True
    if scroll:
        log(f"«{fragment}» is not visible — scrolling down")
        scroll_down()
    return False


def go_to(fragment: str, marker: str, *, tries: int = 4, scroll: bool = False) -> bool:
    """Taps [fragment] until the screen marked by [marker] is on screen.

    A tap can land while a Compose screen is still animating in, which looks exactly like a
    swallowed press; retrying keeps the device test about crashes, not about timing.
    """
    for attempt in range(tries):
        if attempt:
            log(f"retry {attempt}/{tries - 1}: «{fragment}» → «{marker}»")
        tap_text(fragment, scroll=scroll and attempt > 0)
        if wait_text(marker, 4.0):
            return True
    emit("error", f"«{marker}» never appeared after tapping «{fragment}»")
    failures.append(f"could not reach «{marker}» by tapping «{fragment}»")
    log(f"visible texts: {visible_texts()}")
    _, xml = ui_dump()
    print(f"[device-check] ---- UI hierarchy ({len(xml)} chars) ----", flush=True)
    chunk = 1200
    for i in range(0, min(len(xml), 18_000), chunk):
        print("[device-check] " + xml[i:i + chunk], flush=True)
    print("[device-check] ---- end of UI hierarchy ----", flush=True)
    return False


def step(name: str, action, settle: float = 1.5) -> bool:
    """Runs one UI step; a dead process marks the step failed."""
    if not alive():
        emit("error", f"app was already dead before step «{name}»")
        failures.append(f"app was already dead before step «{name}»")
        return False
    ok = action()
    time.sleep(settle)
    screenshot(name)
    if not alive():
        emit("error", f"app crashed during step «{name}»")
        failures.append(f"app crashed during step «{name}»")
        return False
    log(f"✔ «{name}» — app still alive")
    return ok


LOG_TAGS = re.compile(
    r"FATAL|AndroidRuntime: FATAL|Caused by|at ir\.dorehami|ANR in|Force finishing|"
    r"beginning of crash|Chess3D|GLSurfaceView|EGL|Shader|SoundPool|"
    r"ActivityManager.*ir\.dorehami|am_crash|am_proc_died"
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
    log(
        "device: "
        + sh("adb", "shell", "getprop", "ro.product.model").stdout.strip()
        + " / API "
        + sh("adb", "shell", "getprop", "ro.build.version.sdk").stdout.strip()
        + f" / screen {screen_size()}"
    )

    install = sh("adb", "install", "-r", "-t", apk, timeout=300)
    emit("notice", (install.stdout.strip() or install.stderr.strip()))
    if "Success" not in install.stdout:
        emit("error", f"install failed: {install.stdout} {install.stderr}")
        return 1

    sh("adb", "logcat", "-c")
    sh("adb", "shell", "am", "force-stop", PKG)
    sh("adb", "shell", "am", "start", "-n", ACTIVITY)
    time.sleep(8)
    screenshot("00-home")
    if not alive():
        emit("error", "app died on launch")
        dump_logs()
        return 1
    if not wait_text(HOME_MARKER, 10.0):
        emit("error", f"home screen never showed «{HOME_MARKER}»")
        failures.append("home screen marker not found")
        log(f"visible texts: {visible_texts()}")

    # 1) home → game list
    step("01-games", lambda: go_to(HOME_MARKER, GAMES_MARKER))
    # 2) game list → chess setup (this press is the one the user reported below)
    step("02-chess-setup", lambda: go_to("شطرنج", SETUP_MARKER, scroll=True), settle=2.0)
    # 3) setup → 3D board against the bot: the screen that "crashed and closed the app"
    step("03-chess-board", lambda: go_to("شروع بازی با ربات", BOARD_MARKER, scroll=True), settle=10.0)

    # Exercise the parts of the board that touch GL: the camera button and a tap on the board.
    if alive() and on_text(BOARD_MARKER):
        step("04-board-camera", lambda: tap_text("نمای پیشفرض"), settle=3.0)
        w, h = screen_size()
        step(
            "05-board-tap",
            lambda: sh("adb", "shell", "input", "tap", str(w // 2), str(int(h * 0.42))),
            settle=3.0,
        )

    if alive() and any("crashed" in f for f in failures):
        pass
    elif not alive() and not any("crashed" in f for f in failures):
        emit("error", "app crashed after the board was shown")
        failures.append("app crashed after the board was shown")

    if failures:
        emit("error", "RESULT: FAIL — " + " | ".join(failures))
        dump_logs()
        return 1
    log("RESULT: PASS — chess started on the device without a crash")
    log(f"final screen texts: {visible_texts()}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
