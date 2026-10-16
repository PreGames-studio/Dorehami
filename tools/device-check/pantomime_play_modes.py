#!/usr/bin/env python3
"""Device check: Pantomime group play modes on an emulator.

Reproduces the owner's report (PH-B01 in `games/pantohub/reports/PH-001-audit.md`): in the
«عادی» (NORMAL) mode a correct guess must end the turn and hand the phone to the next team, while
in «سرعتی» (SPEEDY) the turn keeps going until the timer runs out. The bug lived in the Compose
layer (two actions dispatched in one tap against a not-yet-recomposed state), which JVM tests
cannot see, so this walks the real app:

  1. home → games → Pantomime → group play → new game → «انتخاب بازی عادی» → create;
  2. start a turn, reveal the word, «حدس درست» → the next team's «نوبت بعدی» screen must appear,
     with no stale-command error and no «واژهٔ دیگری … پیدا نشد» message;
  3. second team: «رد واژه» keeps the turn going, then «حدس درست» ends it → round 2 for team 1;
  4. end that game, start a SPEEDY game: «حدس درست» must NOT end the turn; «پایان نوبت» does.

Run from a workflow that already has an emulator (see `.github/workflows/pantomime-device-check.yml`):

    python3 tools/device-check/pantomime_play_modes.py [debug-apk-path]

Helpers (adb, uiautomator dump, taps, annotations) are shared with `chess_start_crash.py`.
Exit code 0 = every expectation held and the app never died.
"""
from __future__ import annotations

import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import chess_start_crash as dc  # noqa: E402 — shared device helpers
from chess_start_crash import (  # noqa: E402
    ACTIVITY,
    GAMES_MARKER,
    HOME_MARKER,
    PKG,
    alive,
    dump_logs,
    emit,
    find_text,
    log,
    on_text,
    screen_size,
    screenshot,
    sh,
    tap_text,
    visible_texts,
    wait_text,
)

failures = dc.failures  # the same list `go_to()` appends to

# Screen texts (exact strings from games/pantohub/ui).
MODE_MARKER = "نمایش بده، حدس بزن، امتیاز بگیر"
GROUP_HOME_MARKER = "بازی گروهیِ حدس و نمایش"
NEW_GAME = "شروع بازی جدید"
PLAY_MODE_MARKER = "دوست داری بازی چطور پیش بره؟"
CREATE = "ساخت بازی و شروع"
READY_MARKER = "نوبت بعدی"
START_TURN = "شروع نوبت و انتخاب واژه"
REVEAL = "نمایش واژه برای اجراکننده"
SECRET_MARKER = "پوشاندن سریع واژه"
CORRECT = "حدس درست"
SKIP = "رد واژه"
END_TURN = "پایان نوبت"
STALE_ERROR = "این فرمان مربوط به وضعیت قبلی"
NO_WORD = "واژهٔ دیگری مطابق فیلترها"


def fail(message: str, shot: str | None = None) -> None:
    emit("error", "✘ " + message)
    failures.append(message)
    if shot:
        screenshot(shot)
    log(f"visible texts: {visible_texts(40)}")


def expect(condition: bool, ok: str, bad: str, shot: str) -> bool:
    screenshot(shot)
    if condition:
        log("✔ " + ok)
        return True
    fail(bad)
    return False


def scroll(direction: str, step_px: int = 650) -> None:
    """Swipes inside the page; "down" reveals content further down."""
    w, h = screen_size()
    x = w // 2
    low, high = int(h * 0.72), int(h * 0.72) - step_px
    a, b = (low, high) if direction == "down" else (high, low)
    sh("adb", "shell", "input", "swipe", str(x), str(a), str(x), str(b), "260")
    time.sleep(0.7)


def tappable(fragment: str) -> bool:
    """The label is on screen with a real, reachable target (not clipped under a system bar)."""
    node = find_text(fragment)
    if node is None:
        return False
    x1, y1, x2, y2 = node.clickable or node.bounds
    _, h = screen_size()
    cy = (y1 + y2) // 2
    return x2 - x1 >= 4 and y2 - y1 >= 4 and int(h * 0.05) < cy < int(h * 0.95)


def reach_and_tap(fragment: str, max_scrolls: int = 12) -> bool:
    """Scrolls (down first, then up) until [fragment] is tappable, then taps it."""
    for direction in ("down", "up"):
        for _ in range(max_scrolls):
            if tappable(fragment):
                return tap_text(fragment)
            scroll(direction)
    fail(f"«{fragment}» never became tappable (scrolled both ways)")
    return False


def check_alive(where: str) -> bool:
    if alive():
        return True
    fail(f"app died during «{where}»")
    return False


def start_game(mode_button: str, setup_marker: str, shot: str) -> bool:
    if not wait_text(NEW_GAME, 40.0):  # the first launch seeds the 5000-word bank
        fail(f"«{NEW_GAME}» never appeared on the group home", shot + "-home")
        return False
    tap_text(NEW_GAME)
    if not wait_text(PLAY_MODE_MARKER, 8.0):
        fail("play-mode screen never appeared", shot + "-mode")
        return False
    reach_and_tap(mode_button)
    if not wait_text(setup_marker, 8.0):
        fail(f"setup screen «{setup_marker}» never appeared", shot + "-setup")
        return False
    reach_and_tap(CREATE)
    ok = wait_text(READY_MARKER, 15.0)
    expect(ok, f"game created ({setup_marker}) — first team is up", f"«{READY_MARKER}» never appeared after «{CREATE}»", shot)
    return ok and check_alive(shot)


def begin_turn_and_reveal(shot: str) -> bool:
    reach_and_tap(START_TURN)
    if not wait_text(REVEAL, 10.0):
        fail(f"«{REVEAL}» never appeared after «{START_TURN}»", shot + "-start")
        return False
    reach_and_tap(REVEAL)
    if not wait_text(SECRET_MARKER, 8.0):
        fail("the performer's word screen never appeared", shot + "-reveal")
        return False
    # Let the next-word prefetch finish so the tap below takes the instant path — the path the
    # owner hit on the phone.
    time.sleep(2.5)
    screenshot(shot)  # black on purpose: the word screen sets FLAG_SECURE
    return check_alive(shot)


def tap_and_settle(fragment: str) -> None:
    tap_text(fragment)
    time.sleep(2.5)


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
    emit("notice", install.stdout.strip() or install.stderr.strip())
    if "Success" not in install.stdout:
        emit("error", f"install failed: {install.stdout} {install.stderr}")
        return 1

    sh("adb", "logcat", "-c")
    sh("adb", "shell", "am", "force-stop", PKG)
    sh("adb", "shell", "am", "start", "-n", ACTIVITY)
    time.sleep(8)
    screenshot("00-home")
    if not alive() or not wait_text(HOME_MARKER, 15.0):
        fail("the app did not reach its home screen")
        dump_logs()
        return 1

    # ---- into Pantomime group play
    tap_text(HOME_MARKER)
    if not wait_text(GAMES_MARKER, 8.0):
        fail("game list never appeared", "01-games")
        dump_logs()
        return 1
    reach_and_tap("پانتومیم")
    if not wait_text(MODE_MARKER, 8.0):
        fail("Pantomime mode screen never appeared", "02-pantomime")
        dump_logs()
        return 1
    reach_and_tap("شروع بازی گروهی")
    if not wait_text(GROUP_HOME_MARKER, 8.0):
        fail("group home never appeared", "03-group-home")
        dump_logs()
        return 1

    # ---- NORMAL: a correct guess ends the turn (the owner's report)
    if start_game("انتخاب بازی عادی", "نوع بازی: عادی", "10-normal-ready"):
        if begin_turn_and_reveal("11-normal-word"):
            tap_and_settle(CORRECT)
            ended = wait_text(READY_MARKER, 6.0)
            stale = on_text(STALE_ERROR)
            no_word = on_text(NO_WORD)
            expect(
                ended and not stale and not no_word,
                "NORMAL: «حدس درست» ended the turn and handed over to the next team",
                "NORMAL: the turn did NOT end after «حدس درست» "
                f"(next-team screen={ended}, stale-command error={stale}, «no other word» message={no_word})",
                "12-normal-after-correct",
            )
            expect(on_text("۱/۳ نوبت"), "scoreboard counted the finished turn (۱/۳)", "scoreboard did not count the finished turn", "13-normal-scoreboard")
            if not ended:  # recover so the rest of the walk still runs
                reach_and_tap(END_TURN)
                wait_text(READY_MARKER, 6.0)

        # second team: a skip keeps the turn going, a correct guess ends it
        if check_alive("normal-team-2") and begin_turn_and_reveal("14-normal-team2-word"):
            tap_and_settle(SKIP)
            running = wait_text(REVEAL, 6.0) and not on_text(READY_MARKER)
            expect(running, "NORMAL: «رد واژه» kept the turn going with a fresh hidden word", "NORMAL: «رد واژه» ended the turn or left no word", "15-normal-after-skip")
            if running:
                reach_and_tap(REVEAL)
                if wait_text(SECRET_MARKER, 8.0):
                    time.sleep(2.5)
                    tap_and_settle(CORRECT)
                    expect(
                        wait_text(READY_MARKER, 6.0) and on_text("دور ۲ از ۳") and not on_text(STALE_ERROR),
                        "NORMAL: both teams played — team 1 is up for round 2",
                        "NORMAL: the second team's correct guess did not hand over to round 2",
                        "16-normal-round-2",
                    )

        # finish this game so a new one can be created
        if check_alive("normal-finish"):
            reach_and_tap("پایان بازی")
            if wait_text("پایان و ثبت نتیجه", 6.0):
                tap_text("پایان و ثبت نتیجه")
            if wait_text("نتیجهٔ بازی", 8.0):
                reach_and_tap("بازگشت به پانتومیم")
            else:
                fail("the finished-game screen never appeared", "17-normal-finished")

    # ---- SPEEDY regression: a correct guess keeps the turn going
    if check_alive("speedy") and start_game("انتخاب بازی سرعتی", "نوع بازی: سرعتی", "20-speedy-ready"):
        if begin_turn_and_reveal("21-speedy-word"):
            tap_and_settle(CORRECT)
            running = wait_text(REVEAL, 6.0) and not on_text(READY_MARKER)
            expect(running, "SPEEDY: «حدس درست» kept the turn going (next word hidden)", "SPEEDY: the turn ended after one correct guess", "22-speedy-after-correct")
            expect(on_text("۰/۳ نوبت") and not on_text(STALE_ERROR), "SPEEDY: no turn counted yet and no error card", "SPEEDY: unexpected turn count or error card", "23-speedy-scoreboard")
            reach_and_tap(END_TURN)
            expect(wait_text(READY_MARKER, 6.0), "SPEEDY: «پایان نوبت» handed over to the next team", "SPEEDY: «پایان نوبت» did not end the turn", "24-speedy-end-turn")

    check_alive("end of walk")
    if failures:
        emit("error", "RESULT: FAIL — " + " | ".join(failures))
        dump_logs()
        return 1
    log("RESULT: PASS — NORMAL ends the turn on a correct guess, skips keep it; SPEEDY keeps going")
    return 0


if __name__ == "__main__":
    sys.exit(main())
