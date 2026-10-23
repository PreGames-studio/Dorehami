#!/usr/bin/env python3
"""Device smoke test for chess, the chess-only store, and the Hokm setup/table.

The chess path reproduces the report "pressing «شروع بازی با ربات» crashes and closes the app" on
an Android device/emulator, where `GLSurfaceView`, EGL configuration, and `SoundPool` really run.
The store path checks its new navigation owner, and the Hokm path exercises its bundled fonts,
artwork, Compose theme, and table rendering rather than merely compiling those resources.

Run from a workflow that already installed the debug APK (see `.github/workflows/device-check.yml`):

    python3 tools/device-check/chess_start_crash.py [debug-apk-path]

The script taps by *text* (uiautomator dump), so it keeps working when the layout shifts, and it
prints the crash buffer / GL log lines on failure. Exit code 0 = the app survived every step.
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


def sh(*args: str, timeout: int = 120) -> subprocess.CompletedProcess:
    return subprocess.run(list(args), capture_output=True, text=True, timeout=timeout)


def log(msg: str) -> None:
    print(f"[device-check] {msg}", flush=True)


def screenshot(name: str) -> None:
    os.makedirs(SHOTS, exist_ok=True)
    path = os.path.join(SHOTS, f"{name}.png")
    with open(path, "wb") as fh:
        fh.write(sh("adb", "exec-out", "screencap", "-p").stdout.encode("utf-8", "surrogateescape"))


def alive() -> bool:
    return sh("adb", "shell", "pidof", PKG).stdout.strip() != ""


def nodes() -> list[tuple[str, str, tuple[int, int, int, int]]]:
    sh("adb", "shell", "uiautomator", "dump", "/sdcard/window_dump.xml")
    xml = sh("adb", "exec-out", "cat", "/sdcard/window_dump.xml").stdout
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


def tap_text(fragment: str, *, required: bool = True) -> bool:
    for text, desc, (x1, y1, x2, y2) in nodes():
        if fragment in text or fragment in desc:
            if x2 - x1 < 4 or y2 - y1 < 4:
                continue
            x, y = (x1 + x2) // 2, (y1 + y2) // 2
            log(f"tap «{fragment}» at ({x},{y}) — node text={text!r}")
            sh("adb", "shell", "input", "tap", str(x), str(y))
            return True
    message = f"could not find «{fragment}» on screen"
    log(message)
    if required:
        failures.append(message)
        for text, desc, bounds in nodes():
            log(f"    node text={text!r} desc={desc!r} bounds={bounds}")
    return False


def expect_text(fragment: str) -> bool:
    """Confirms navigation reached the intended screen, not merely that a tap was delivered."""
    current = nodes()
    if any(fragment in text or fragment in desc for text, desc, _ in current):
        log(f"✔ screen contains «{fragment}»")
        return True
    message = f"expected screen to contain «{fragment}»"
    failures.append(message)
    log(message)
    for text, desc, bounds in current:
        log(f"    node text={text!r} desc={desc!r} bounds={bounds}")
    return False


def relaunch(section: str) -> bool:
    """Starts a clean Home → Games flow without relying on game-specific back dialogs."""
    log(f"relaunching for {section}")
    sh("adb", "shell", "am", "force-stop", PKG)
    sh("adb", "shell", "am", "start", "-n", ACTIVITY)
    time.sleep(6)
    if not alive():
        failures.append(f"app died while relaunching for {section}")
        dump_logs()
        return False
    return True


def step(name: str, action, settle: float = 1.5) -> bool:
    """Runs one UI step; a dead process or a missing node marks the step failed."""
    if not alive():
        failures.append(f"app was already dead before step «{name}»")
        return False
    action()
    time.sleep(settle)
    screenshot(name)
    if not alive():
        failures.append(f"app crashed during step «{name}»")
        log(f"💥 app is dead after «{name}»")
        return False
    log(f"✔ «{name}» — app still alive")
    return True


def dump_logs() -> None:
    log("---- logcat (crash buffer) ----")
    print(sh("adb", "logcat", "-d", "-b", "crash", "-v", "time").stdout)
    log("---- logcat: AndroidRuntime / chess / GLes ----")
    out = sh("adb", "logcat", "-d", "-v", "time").stdout
    keep = re.compile(
        r"AndroidRuntime|FATAL|Chess3D|ChessRenderer|GLSurfaceView|EGL_|libEGL|OpenGL|Shader|SoundPool|"
        + PKG
    )
    for line in out.splitlines():
        if keep.search(line):
            print(line)


def main() -> int:
    apk = sys.argv[1] if len(sys.argv) > 1 else "app/build/outputs/apk/debug/app-debug.apk"
    sh("adb", "wait-for-device", timeout=300)
    log(f"device: {sh('adb', 'shell', 'getprop', 'ro.product.model').stdout.strip()} / API {sh('adb', 'shell', 'getprop', 'ro.build.version.sdk').stdout.strip()}")

    install = sh("adb", "install", "-r", "-t", apk, timeout=300)
    if install.stdout.strip():
        log(f"install stdout: {install.stdout.strip()}")
    if install.stderr.strip():
        log(f"install stderr: {install.stderr.strip()}")
    if install.returncode != 0 or "Success" not in (install.stdout + install.stderr):
        message = f"install failed (exit {install.returncode}): {install.stdout} {install.stderr}"
        failures.append(message)
        dump_logs()
        # Keep the actionable error after noisy logcat output so GitHub's annotation limit cannot
        # hide it; this also includes stderr when adb prints no separate failure line.
        log(message)
        return 1

    sh("adb", "logcat", "-c")
    sh("adb", "shell", "am", "force-stop", PKG)
    sh("adb", "shell", "am", "start", "-n", ACTIVITY)
    time.sleep(7)
    screenshot("00-home")
    if not alive():
        failures.append("app died on launch")
        dump_logs()
        return 1

    step("01-games", lambda: tap_text("ورود به بازی"))
    step("02-chess-menu", lambda: tap_text("شطرنج"))
    step("03-chess-offline", lambda: tap_text("آفلاین"))
    step("04-chess-solo-setup", lambda: tap_text("بازی تکی"))
    # Level / colour defaults are exactly what a user gets when they just press the big button.
    step("05-chess-board", lambda: tap_text("شروع بازی با ربات"), settle=6.0)

    # Open the board's view control if the board came up.
    if alive() and not failures:
        step("06-board-tap", lambda: tap_text("نمای پیش‌فرض", required=False), settle=1.0)

    time.sleep(1.0)
    if not alive():
        if not any("05-chess-board" in f for f in failures):
            failures.append("app crashed after the board was shown")
        dump_logs()
    else:
        log("✅ chess opened and stayed alive")

    # The store must be reachable from chess itself and must return to chess, not Home.
    if not failures and relaunch("chess store"):
        step("07-store-games", lambda: tap_text("ورود به بازی"))
        step("08-store-chess-menu", lambda: tap_text("شطرنج"))
        step("09-chess-store", lambda: tap_text("فروشگاه شطرنج"))
        if not failures:
            expect_text("موجودی کیف‌پول")
        step("10-store-back-to-chess", lambda: tap_text("بازگشت"))
        if not failures:
            expect_text("یک میز، هر طور که دوست داری")

    # Open the actual Hokm UI so missing font/image resources and composition-time crashes are
    # covered too. Starting the bots also renders the recolored gameplay table on the device.
    if not failures and relaunch("Hokm"):
        step("11-hokm-games", lambda: tap_text("ورود به بازی"))
        step("12-hokm-setup", lambda: tap_text("حکم"), settle=3.0)
        if not failures:
            expect_text("بازی اصیل ایرانی")
        step("13-hokm-table", lambda: tap_text("شروع بازی با ربات‌ها"), settle=7.0)
        if alive() and not failures:
            log("✅ chess store navigation and Hokm setup/table stayed alive")

    if failures:
        log("FAILURES:")
        for f in failures:
            log(f"  - {f}")
        dump_logs()
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
