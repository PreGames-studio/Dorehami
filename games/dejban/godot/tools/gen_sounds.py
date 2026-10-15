#!/usr/bin/env python3
"""مولد صداهای دژبان.

جلوه‌های رابط/ساخت‌وساز با پایتون استاندارد (بدون کتابخانهٔ بیرونی) ساخته
می‌شوند. صداهای مبارزهٔ پوشهٔ assets/sounds/combat جداگانه از ضبط‌های CC0
انتخاب شده‌اند و این اسکریپت آن‌ها را بازتولید یا بازنویسی نمی‌کند.

موسیقی این‌جا ساخته نمی‌شود: دو لوپِ پس‌زمینه از tools/compose_music.py
می‌آیند (طرحِ «سایهٔ رون‌ها» برای منو و «مارشِ دژبان» برای نبرد). این اسکریپت
فقط همان دو بیلدر را صدا می‌زند تا اجرای یک فرمان، همهٔ صداهای بازی را
بازسازی کند و فایل‌ها به نسخهٔ قدیمی برنگردند.

    python3 tools/gen_sounds.py

خروجی: assets/sounds/*.wav  (۱۶ بیتی، مونو، ۲۲٫۰۵ کیلوهرتز؛ بدون کلیپ‌های CC0 در combat/)
"""

from __future__ import annotations

import math
import os
import random
import struct
import sys
import wave

SR = 22050
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "assets", "sounds")

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import compose_music as compose  # noqa: E402  (کنارِ همین فایل است)

# ---------------------------------------------------------------- سازنده‌ها


def silence(duration: float) -> list[float]:
    return [0.0] * int(SR * duration)


def sine(freq: float, t: float) -> float:
    return math.sin(2.0 * math.pi * freq * t)


def square(freq: float, t: float, duty: float = 0.5) -> float:
    return 1.0 if (t * freq) % 1.0 < duty else -1.0


def saw(freq: float, t: float) -> float:
    return 2.0 * ((t * freq) % 1.0) - 1.0


def triangle(freq: float, t: float) -> float:
    return 4.0 * abs(((t * freq) % 1.0) - 0.5) - 1.0


def noise() -> float:
    return random.uniform(-1.0, 1.0)


def adsr(n: int, attack: float, decay: float, sustain: float, release: float) -> list[float]:
    """پوش دامنهٔ نمونه‌ای برای طول n سمپل."""
    a = max(1, int(attack * SR))
    d = max(0, int(decay * SR))
    r = max(1, int(release * SR))
    s = max(0, n - a - d - r)
    env = []
    for i in range(a):
        env.append(i / a)
    for i in range(d):
        env.append(1.0 + (sustain - 1.0) * (i / max(1, d)))
    for _ in range(s):
        env.append(sustain)
    for i in range(r):
        env.append(sustain * (1.0 - i / r))
    if len(env) < n:
        env.extend([0.0] * (n - len(env)))
    return env[:n]


def sweep(f0: float, f1: float, duration: float, wave_fn=sine) -> list[float]:
    n = int(SR * duration)
    out = []
    phase = 0.0
    for i in range(n):
        k = i / max(1, n - 1)
        freq = f0 + (f1 - f0) * k
        phase += freq / SR
        out.append(wave_fn(1.0, phase))
    return out


def lowpass(samples: list[float], cutoff_hz: float) -> list[float]:
    """فیلتر یک‌قطبی ساده برای نرم‌کردن لبه‌های تیز."""
    dt = 1.0 / SR
    rc = 1.0 / (2.0 * math.pi * max(20.0, cutoff_hz))
    alpha = dt / (rc + dt)
    out = []
    prev = 0.0
    for s in samples:
        prev += alpha * (s - prev)
        out.append(prev)
    return out


def highpass(samples: list[float], cutoff_hz: float) -> list[float]:
    dt = 1.0 / SR
    rc = 1.0 / (2.0 * math.pi * max(20.0, cutoff_hz))
    alpha = rc / (rc + dt)
    out = []
    prev_in = 0.0
    prev_out = 0.0
    for s in samples:
        prev_out = alpha * (prev_out + s - prev_in)
        prev_in = s
        out.append(prev_out)
    return out


def bandpass(samples: list[float], center_hz: float, bandwidth_hz: float) -> list[float]:
    """فیلتر تشدیدی باندگذر برای ساخت فرمَنت‌های شبیه صدای انسان."""
    center = min(max(30.0, center_hz), SR * 0.45)
    q = center / max(30.0, bandwidth_hz)
    omega = 2.0 * math.pi * center / SR
    alpha = math.sin(omega) / (2.0 * q)
    a0 = 1.0 + alpha
    b0 = alpha / a0
    b2 = -alpha / a0
    a1 = -2.0 * math.cos(omega) / a0
    a2 = (1.0 - alpha) / a0
    out = []
    x1 = x2 = y1 = y2 = 0.0
    for sample in samples:
        y = b0 * sample + b2 * x2 - a1 * y1 - a2 * y2
        out.append(y)
        x2, x1 = x1, sample
        y2, y1 = y1, y
    return out


def echo(samples: list[float], delay: float, decay: float, taps: int = 3) -> list[float]:
    out = list(samples)
    d = int(delay * SR)
    for tap in range(1, taps + 1):
        shift = d * tap
        gain = decay ** tap
        for i in range(len(samples)):
            j = i + shift
            if j < len(out):
                out[j] += samples[i] * gain
    return out


def mix(*layers: list[float]) -> list[float]:
    length = max(len(layer) for layer in layers)
    out = [0.0] * length
    for layer in layers:
        for i, s in enumerate(layer):
            out[i] += s
    return out


def scale(samples: list[float], gain: float) -> list[float]:
    return [s * gain for s in samples]


def concat(*parts: list[float]) -> list[float]:
    out: list[float] = []
    for part in parts:
        out.extend(part)
    return out


def tone(freq: float, duration: float, wave_fn=sine, attack: float = 0.004, release: float = 0.05) -> list[float]:
    n = max(2, int(SR * duration))
    env = adsr(n, attack, max(0.0, duration * 0.25), 0.7, release)
    return [wave_fn(freq, i / SR) * env[i] * 0.5 for i in range(n)]


def noise_burst(duration: float, attack: float = 0.001, release: float = 0.06, cutoff: float = 4000.0) -> list[float]:
    n = max(2, int(SR * duration))
    env = adsr(n, attack, max(0.0, duration * 0.2), 0.6, release)
    raw = [noise() * env[i] for i in range(n)]
    return lowpass(raw, cutoff)


def normalize(samples: list[float], peak: float = 0.86) -> list[float]:
    top = max(1e-6, max(abs(s) for s in samples))
    return [s / top * peak for s in samples]


def soft_clip(samples: list[float]) -> list[float]:
    return [math.tanh(s * 1.4) * 0.9 for s in samples]


def fade_edges(samples: list[float], seconds: float = 0.02) -> list[float]:
    n = int(SR * seconds)
    out = list(samples)
    for i in range(min(n, len(out))):
        k = i / n
        out[i] *= k
        out[-1 - i] *= k
    return out


def write_wav(name: str, samples: list[float]) -> None:
    path = os.path.join(OUT, name + ".wav")
    data = struct.pack("<%dh" % len(samples), *(int(max(-1.0, min(1.0, s)) * 32000) for s in samples))
    with wave.open(path, "wb") as handle:
        handle.setnchannels(1)
        handle.setsampwidth(2)
        handle.setframerate(SR)
        handle.writeframes(data)
    print("  %-22s %6.1f KB" % (name + ".wav", len(data) / 1024.0))


# ---------------------------------------------------------------- جلوه‌های صوتی


def sfx_build() -> list[float]:
    return normalize(concat(
        mix(scale(tone(190, 0.09, sine, 0.0, 0.05), 0.9), scale(noise_burst(0.05, 0.0, 0.03, 2500), 0.4)),
        silence(0.06),
        mix(scale(tone(255, 0.12, sine, 0.0, 0.07), 0.9), scale(noise_burst(0.06, 0.0, 0.04, 3000), 0.45)),
    ), 0.85)


def sfx_upgrade() -> list[float]:
    notes = [tone(523, 0.1, triangle, 0.002, 0.06), tone(659, 0.1, triangle, 0.002, 0.06),
             tone(880, 0.2, triangle, 0.002, 0.12)]
    return normalize(mix(concat(*notes), scale(echo(highpass(noise_burst(0.14, 0.0, 0.1, 9000), 4000), 0.05, 0.4, 2), 0.3)), 0.8)


def sfx_sell() -> list[float]:
    return normalize(concat(
        tone(660, 0.09, triangle, 0.002, 0.05),
        tone(440, 0.14, triangle, 0.002, 0.09),
    ), 0.7)


def sfx_deny() -> list[float]:
    return normalize(mix(
        scale(tone(150, 0.16, square, 0.001, 0.08), 0.7),
        scale(tone(112, 0.16, square, 0.001, 0.08), 0.5),
    ), 0.6)


def sfx_coin() -> list[float]:
    return normalize(concat(
        tone(1318, 0.05, sine, 0.0, 0.03),
        tone(1975, 0.09, sine, 0.0, 0.05),
    ), 0.55)


def sfx_star() -> list[float]:
    return normalize(mix(
        scale(echo(tone(1568, 0.5, sine, 0.002, 0.3), 0.07, 0.35, 2), 0.8),
        scale(tone(2093, 0.4, sine, 0.002, 0.25), 0.4),
    ), 0.6)


def sfx_ui_click() -> list[float]:
    return normalize(highpass(mix(
        scale(tone(760, 0.04, sine, 0.0, 0.02), 0.8),
        scale(noise_burst(0.03, 0.0, 0.02, 6000), 0.2),
    ), 900), 0.5)


def sfx_ui_hover() -> list[float]:
    return normalize(highpass(scale(tone(980, 0.03, sine, 0.0, 0.02), 0.6), 900), 0.3)


def sfx_ui_back() -> list[float]:
    return normalize(concat(
        tone(560, 0.05, sine, 0.0, 0.03),
        tone(392, 0.08, sine, 0.0, 0.05),
    ), 0.5)


def sfx_wave_start() -> list[float]:
    horn = mix(
        scale(sweep(196, 233, 0.7, saw), 0.55),
        scale(sweep(147, 175, 0.7, square, ), 0.35),
    )
    env = adsr(len(horn), 0.14, 0.3, 0.6, 0.25)
    horn = [horn[i] * env[i] for i in range(len(horn))]
    return normalize(lowpass(horn, 2600), 0.75)


def sfx_wave_clear() -> list[float]:
    return normalize(mix(
        concat(tone(659, 0.12, triangle, 0.002, 0.07), tone(880, 0.22, triangle, 0.002, 0.14)),
        scale(echo(noise_burst(0.1, 0.0, 0.06, 8000), 0.06, 0.3, 2), 0.2),
    ), 0.7)


def sfx_keep_hit() -> list[float]:
    bell = mix(
        scale(tone(392, 0.7, sine, 0.002, 0.5), 1.0),
        scale(tone(415, 0.7, sine, 0.002, 0.5), 0.6),
        scale(tone(587, 0.5, sine, 0.002, 0.4), 0.35),
    )
    return normalize(echo(lowpass(bell, 5000), 0.09, 0.35, 2), 0.8)


def sfx_tower_destroyed() -> list[float]:
    return normalize(mix(
        scale(lowpass(noise_burst(0.35, 0.002, 0.18, 1600), 1200), 1.0),
        scale(sweep(420, 110, 0.3, square), 0.4),
    ), 0.9)


def sfx_victory() -> list[float]:
    melody = [tone(523, 0.16, triangle, 0.004, 0.1), tone(659, 0.16, triangle, 0.004, 0.1),
              tone(784, 0.16, triangle, 0.004, 0.1), tone(1047, 0.5, triangle, 0.006, 0.35)]
    chord = [tone(262, 0.7, saw, 0.05, 0.5), tone(330, 0.7, saw, 0.05, 0.5)]
    return normalize(lowpass(mix(concat(*melody), scale(concat(*chord, silence(0.3)), 0.25)), 4000), 0.85)


def sfx_defeat() -> list[float]:
    melody = [tone(392, 0.3, triangle, 0.006, 0.18), tone(311, 0.3, triangle, 0.006, 0.18),
              tone(233, 0.8, triangle, 0.008, 0.5)]
    drone = scale([sine(98, i / SR) for i in range(int(SR * 1.4))], 0.35)
    return normalize(lowpass(mix(concat(*melody), drone), 2200), 0.85)


def sfx_unlock() -> list[float]:
    arp = [tone(587, 0.09, triangle, 0.002, 0.05), tone(740, 0.09, triangle, 0.002, 0.05),
           tone(880, 0.09, triangle, 0.002, 0.05), tone(1175, 0.4, sine, 0.002, 0.3)]
    return normalize(mix(concat(*arp), scale(echo(tone(1568, 0.3, sine, 0.002, 0.2), 0.08, 0.3, 2), 0.3)), 0.75)


# ---------------------------------------------------------------- موسیقی

# ---------------------------------------------------------------- اجرا

def main() -> None:
    random.seed(1404)
    os.makedirs(OUT, exist_ok=True)
    print("ساخت جلوه‌های صوتی:")
    sfx = {
        "build": sfx_build,
        "upgrade": sfx_upgrade,
        "sell": sfx_sell,
        "deny": sfx_deny,
        "coin": sfx_coin,
        "star": sfx_star,
        "ui_click": sfx_ui_click,
        "ui_hover": sfx_ui_hover,
        "ui_back": sfx_ui_back,
        "wave_start": sfx_wave_start,
        "wave_clear": sfx_wave_clear,
        "keep_hit": sfx_keep_hit,
        "tower_destroyed": sfx_tower_destroyed,
        "victory": sfx_victory,
        "defeat": sfx_defeat,
        "unlock": sfx_unlock,
    }
    for name, builder in sfx.items():
        write_wav(name, soft_clip(fade_edges(builder())))
    print("ساخت موسیقی (tools/compose_music.py):")
    menu, _, menu_title = compose.track_runes()      # طرح ب — سایهٔ رون‌ها
    battle, _, battle_title = compose.track_march()  # طرح الف — مارشِ دژبان
    compose.write_wav(os.path.join(OUT, "music_menu.wav"), menu)
    compose.write_wav(os.path.join(OUT, "music_battle.wav"), battle)
    print("  منو:   %s (%.1f ثانیه)" % (menu_title, len(menu) / SR))
    print("  نبرد:  %s (%.1f ثانیه)" % (battle_title, len(battle) / SR))
    print("مجموع: %d فایل در %s" % (len(sfx) + 2, OUT))


if __name__ == "__main__":
    main()
