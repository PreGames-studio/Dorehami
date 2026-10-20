#!/usr/bin/env python3
"""مولد موسیقی پس‌زمینهٔ دژبان — چهار طرحِ متفاوت برای انتخاب.

    python3 tools/compose_music.py --track all --out soundsheets/music_candidates
    python3 tools/compose_music.py --track a --analyze

هر طرح یک لوپِ کاملِ بدون درز است: دنبالهٔ افکت‌ها (ریورب/دیلِی) به ابتدای
فایل تا می‌شود، پس با LOOP_FORWARD روی کل فایل هیچ کلیک یا افتِ صدایی شنیده
نمی‌شود. خروجی هم‌قالبِ بقیهٔ صداهای بازی است: WAV، مونو، ۲۲٫۰۵ کیلوهرتز،
۱۶ بیت — همان چیزی که src/autoload/Audio.gd برای موسیقی می‌خواند.

طرح‌ها:
  a  مارشِ دژبان      — مارشِ حماسیِ ارکستری، ر مینور، ۱۰۰ ضربه
  b  سایهٔ رون‌ها      — امبینتِ تاریکِ فانتزی، لا مینور، ۶۰ ضربه
  c  ضربانِ خط مقدم   — الکترونیکِ تاکتیکی، لا مینور، ۱۲۴ ضربه
  d  کاروانِ شب        — ایرانیِ حماسی، لا مینورِ هارمونیک، ۹۲ ضربه
"""

from __future__ import annotations

import argparse
import math
import os
import re
import struct
import sys
import wave

import numpy as np

SR = 22050
TAIL = 2.6          # دنبالهٔ افکت‌ها که به ابتدای لوپ تا می‌شود
RNG = np.random.default_rng(1404)

NOTES = {"C": 0, "C#": 1, "Db": 1, "D": 2, "D#": 3, "Eb": 3, "E": 4, "F": 5,
         "F#": 6, "Gb": 6, "G": 7, "G#": 8, "Ab": 8, "A": 9, "A#": 10, "Bb": 10, "B": 11}


# ------------------------------------------------------------------ پایه‌ها

def n(name: str) -> float:
    """نام نت («A3»، «F#2») به فرکانس. لا۴ = ۴۴۰ هرتز."""
    m = re.fullmatch(r"([A-G][#b]?)(-?\d)", name)
    if not m:
        raise ValueError("نت ناشناس: %s" % name)
    semi = NOTES[m.group(1)] + 12 * (int(m.group(2)) + 1)
    return 440.0 * (2.0 ** ((semi - 69) / 12.0))


def seconds(beats: float, bpm: float) -> float:
    return beats * 60.0 / bpm


def frames(dur: float) -> int:
    return max(2, int(round(SR * dur)))


def timeline(nsamples: int) -> np.ndarray:
    return np.arange(nsamples, dtype=np.float64) / SR


def bandsaw(freq, nsamples: int, max_harm: int = 0) -> np.ndarray:
    # freq می‌تواند عدد یا آرایه باشد (ویبراتو)؛ برای شمارشِ هارمونیک‌ها بیشینه‌اش را می‌گیریم.
    t = timeline(nsamples)
    f_hi = float(np.max(np.abs(np.asarray(freq, dtype=float))))
    nyq = SR * 0.45
    kmax = min(max_harm or 30, max(1, int(nyq / max(20.0, f_hi))))
    out = np.zeros(nsamples)
    for k in range(1, kmax + 1):
        out += np.sin(2 * math.pi * freq * k * t) / k
    return out * (2.0 / math.pi)


def banner(freq, nsamples: int, kind: str = "saw") -> np.ndarray:
    t = timeline(nsamples)
    nyq = SR * 0.45
    f_hi = float(np.max(np.abs(np.asarray(freq, dtype=float))))
    out = np.zeros(nsamples)
    if kind == "square":
        kmax = min(30, max(1, int(nyq / max(20.0, f_hi))))
        for k in range(1, kmax + 1, 2):
            out += np.sin(2 * math.pi * freq * k * t) / k
        return out * (4.0 / math.pi)
    if kind == "triangle":
        kmax = min(24, max(1, int(nyq / max(20.0, f_hi))))
        for k in range(1, kmax + 1, 2):
            out += ((-1) ** ((k - 1) // 2)) * np.sin(2 * math.pi * freq * k * t) / (k * k)
        return out * (8.0 / math.pi ** 2)
    return bandsaw(freq, nsamples)


def env(n: int, attack: float, decay: float, sustain: float, release: float,
        curve: float = 2.0) -> np.ndarray:
    a, d = int(SR * attack), int(SR * decay)
    r = int(SR * release)
    s = max(0, n - a - d - r)
    parts = []
    if a > 0:
        parts.append(np.linspace(0.0, 1.0, a) ** (1.0 / curve))
    if d > 0:
        parts.append(sustain + (1.0 - sustain) * np.linspace(1.0, 0.0, d) ** curve)
    if s > 0:
        parts.append(np.full(s, sustain))
    if r > 0:
        parts.append(sustain * np.linspace(1.0, 0.0, r) ** curve)
    out = np.concatenate(parts) if parts else np.ones(n)
    if len(out) < n:
        out = np.concatenate([out, np.zeros(n - len(out))])
    return out[:n]


def exp_env(n: int, tau: float, attack: float = 0.002) -> np.ndarray:
    t = timeline(n)
    e = np.exp(-t / max(1e-4, tau))
    a = max(1, int(attack * SR))
    e[:a] *= np.linspace(0.0, 1.0, a)
    return e


# ------------------------------------------------------------------ افکت‌ها

def bound(x: np.ndarray, peak: float) -> np.ndarray:
    """قلهٔ یک صدا را روی مقدارِ خواسته‌شده می‌نشاند.

    نویزِ گاوسی قله‌های پرت دارد (چند برابرِ انحراف معیار)؛ اگر مهار نشود،
    کوبشی‌ها به‌جای «پرکاشن» یک جُستِ تیزِ تکی می‌شوند و نرمال‌سازیِ نهایی هم
    به‌جای بدنهٔ موسیقی، همان قله‌های پرت را می‌گیرد.
    """
    m = float(np.max(np.abs(x))) or 1.0
    return x * (peak / m)


def fft_filter(x: np.ndarray, kind: str, cutoff: float, order: int = 2) -> np.ndarray:
    if len(x) < 8:
        return x
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1.0 / SR)
    f = np.maximum(f, 1.0)
    if kind == "low":
        h = 1.0 / np.sqrt(1.0 + (f / cutoff) ** (2.0 * order))
    elif kind == "high":
        h = 1.0 / np.sqrt(1.0 + (cutoff / f) ** (2.0 * order))
    else:
        h = (1.0 / np.sqrt(1.0 + (f / cutoff) ** (2.0 * order))) * \
            (1.0 / np.sqrt(1.0 + (cutoff * 0.45 / f) ** (2.0 * order)))
    return np.fft.irfft(spec * h, len(x))


def convolve_fft(x: np.ndarray, ir: np.ndarray) -> np.ndarray:
    total = len(x) + len(ir) - 1
    fft_len = 1 << max(1, (total - 1).bit_length())
    y = np.fft.irfft(np.fft.rfft(x, fft_len) * np.fft.rfft(ir, fft_len), fft_len)
    return y[:len(x)]


_REVERB_CACHE: dict[tuple, np.ndarray] = {}


def reverb(x: np.ndarray, decay: float = 1.9, damp: float = 4200.0, pre: float = 0.018) -> np.ndarray:
    key = (round(decay, 3), round(damp, 1), round(pre, 3))
    ir = _REVERB_CACHE.get(key)
    if ir is None:
        n = int(SR * (pre + decay))
        rng = np.random.default_rng(77)
        ir = rng.normal(0.0, 1.0, n)
        ir *= np.exp(-np.arange(n) / (decay * SR) * 4.2)
        ir[:int(pre * SR)] = 0.0
        ir = fft_filter(ir, "low", damp, 2)
        ir /= np.sqrt(np.sum(ir ** 2)) or 1.0
        _REVERB_CACHE[key] = ir
    return convolve_fft(x, ir)


def delay(x: np.ndarray, seconds_d: float, decay: float = 0.42, taps: int = 3) -> np.ndarray:
    out = x.copy()
    step = int(seconds_d * SR)
    for k in range(1, taps + 1):
        g = decay ** k
        if step * k >= len(x):
            break
        out[step * k:] += x[:-step * k] * g
    return out


def place(buf: np.ndarray, x: np.ndarray, at: float, gain: float = 1.0) -> None:
    i = int(at * SR)
    if i >= len(buf):
        return
    j = min(len(buf), i + len(x))
    buf[i:j] += x[:j - i] * gain


# ------------------------------------------------------------------ سازها

def pad(freq: float, dur: float, gain: float = 0.2, bright: float = 1600.0,
        detune: float = 0.013, attack: float = 0.9) -> np.ndarray:
    ns = frames(dur)
    voice = np.zeros(ns)
    for k, mul in enumerate((1.0 - detune, 1.0, 1.0 + detune)):
        voice += banner(freq * mul, ns)
    voice /= 3.0
    voice = fft_filter(voice, "low", bright, 2)
    return voice * env(ns, attack, 0.4, 0.85, min(1.2, dur * 0.4), 1.6) * gain


def strings(freq: float, dur: float, gain: float = 0.2, bright: float = 2400.0) -> np.ndarray:
    ns = frames(dur)
    t = timeline(ns)
    vib = 1.0 + 0.004 * np.sin(2 * math.pi * 5.2 * t)
    voice = banner(freq, ns) * vib * 0.6 + banner(freq * 2.0, ns) * 0.25
    voice = fft_filter(voice, "low", bright, 2)
    return voice * env(ns, 0.06, 0.25, 0.72, min(0.4, dur * 0.3)) * gain


def brass(freq: float, dur: float, gain: float = 0.26) -> np.ndarray:
    ns = frames(dur)
    t = timeline(ns)
    # شیب کوچکِ کوک در آغاز، حسِ سازِ بادی
    bend = 1.0 + 0.02 * np.exp(-t * 14.0)
    voice = banner(freq * bend, ns) * 0.75 + banner(freq * 2.0, ns, "square") * 0.18
    voice = fft_filter(voice, "low", 3000.0, 3)
    return voice * env(ns, 0.055, 0.2, 0.8, min(0.35, dur * 0.35), 1.4) * gain


def pluck(freq: float, dur: float, gain: float = 0.24, decay: float = 2.6,
          bright: float = 5200.0) -> np.ndarray:
    """سنتور/عودِ افزودنی: هر هارمونیک میراییِ خودش را دارد."""
    ns = frames(dur)
    t = timeline(ns)
    rng = np.random.default_rng(int(freq * 7) % 9991)
    out = np.zeros(ns)
    kmax = min(18, max(1, int(SR * 0.45 / freq)))
    for k in range(1, kmax + 1):
        amp = (1.0 / k ** 1.15) * (0.6 + 0.8 * rng.random())
        tau = decay / (1.0 + 0.55 * (k - 1))
        out += amp * np.sin(2 * math.pi * freq * k * t + rng.random() * 6.28) * np.exp(-t / tau)
    out = fft_filter(out, "low", bright, 2)
    a = max(1, int(0.002 * SR))
    out[:a] *= np.linspace(0.0, 1.0, a)
    return out * gain * 0.5


def bell(freq: float, dur: float, gain: float = 0.16) -> np.ndarray:
    ns = frames(dur)
    t = timeline(ns)
    out = np.zeros(ns)
    for k, (mul, tau, amp) in enumerate(((1.0, 1.0, 1.0), (2.76, 0.7, 0.5),
                                         (5.4, 0.45, 0.28), (8.93, 0.3, 0.14))):
        out += amp * np.sin(2 * math.pi * freq * mul * t) * np.exp(-t / (dur * 0.45 * tau))
    a = max(1, int(0.004 * SR))
    out[:a] *= np.linspace(0.0, 1.0, a)
    return out * gain


def choir(freq: float, dur: float, gain: float = 0.18) -> np.ndarray:
    ns = frames(dur)
    t = timeline(ns)
    out = np.zeros(ns)
    for mul, det in ((1.0, -0.008), (1.0, 0.0), (1.0, 0.009), (2.0, 0.004), (3.0, -0.005)):
        vib = 1.0 + 0.006 * np.sin(2 * math.pi * (4.6 + det * 40) * t)
        out += np.sin(2 * math.pi * freq * mul * (1 + det) * vib * t) * (1.0 if mul == 1 else 0.3 / mul)
    out /= 2.6
    breath = bound(fft_filter(RNG.normal(0, 1, ns), "band", 900.0, 2), 1.0) * 0.06
    out = fft_filter(out + breath, "band", 1100.0, 2)
    return out * env(ns, min(1.4, dur * 0.4), 0.5, 0.8, min(1.6, dur * 0.4), 1.5) * gain


def ney(freq: float, dur: float, gain: float = 0.2) -> np.ndarray:
    ns = frames(dur)
    t = timeline(ns)
    vib = 1.0 + 0.012 * np.sin(2 * math.pi * 5.0 * t) * np.minimum(1.0, t * 2.0)
    tone = np.sin(2 * math.pi * freq * vib * t) + 0.35 * banner(freq * vib, ns) * 0.4
    breath = bound(fft_filter(RNG.normal(0, 1, ns), "band", 2600.0, 2), 1.0) * 0.05
    out = fft_filter(tone * 0.7 + breath, "low", 4200.0, 2)
    return out * env(ns, 0.09, 0.3, 0.78, min(0.4, dur * 0.35)) * gain


# ------------------------------------------------------------------ کوبشی

def kick(dur: float = 0.5, gain: float = 0.9) -> np.ndarray:
    ns = frames(dur)
    t = timeline(ns)
    f = 55.0 + 110.0 * np.exp(-t * 26.0)
    body = np.sin(2 * math.pi * np.cumsum(f) / SR) * np.exp(-t / 0.17)
    click = bound(fft_filter(RNG.normal(0, 1, ns), "band", 2200.0, 2), 1.0) * np.exp(-t / 0.012) * 0.25
    return (body + click) * gain


def taiko(dur: float = 0.9, gain: float = 0.8, tone: float = 74.0) -> np.ndarray:
    ns = frames(dur)
    t = timeline(ns)
    f = tone * (1.0 + 0.5 * np.exp(-t * 20.0))
    body = np.sin(2 * math.pi * np.cumsum(f) / SR) * np.exp(-t / 0.32)
    skin = bound(fft_filter(RNG.normal(0, 1, ns), "band", 320.0, 2), 1.0) * np.exp(-t / 0.09) * 0.5
    return (body * 1.2 + skin) * gain


def snare(dur: float = 0.3, gain: float = 0.5, accent: float = 1.0) -> np.ndarray:
    ns = frames(dur)
    t = timeline(ns)
    noise = bound(fft_filter(RNG.normal(0, 1, ns), "high", 1400.0, 2), 1.0) * np.exp(-t / 0.075)
    tone = np.sin(2 * math.pi * 196.0 * t) * np.exp(-t / 0.05) * 0.5
    return (noise + tone) * gain * accent


def rimshot(dur: float = 0.16, gain: float = 0.5) -> np.ndarray:
    ns = frames(dur)
    t = timeline(ns)
    return (bound(fft_filter(RNG.normal(0, 1, ns), "band", 2600.0, 2), 1.0) * np.exp(-t / 0.03) +
            np.sin(2 * math.pi * 320.0 * t) * np.exp(-t / 0.02) * 0.4) * gain


def clap(dur: float = 0.35, gain: float = 0.45) -> np.ndarray:
    ns = frames(dur)
    out = np.zeros(ns)
    for k, at in enumerate((0.0, 0.009, 0.019, 0.030)):
        part = bound(fft_filter(RNG.normal(0, 1, ns), "band", 1250.0, 2), 1.0)
        part *= np.exp(-np.maximum(0.0, timeline(ns) - at) / (0.055 if k < 3 else 0.16))
        part[:int(at * SR)] = 0.0
        out += part
    return out * gain * 0.6


def hat(dur: float = 0.09, gain: float = 0.28, open_hat: bool = False) -> np.ndarray:
    ns = frames(dur)
    t = timeline(ns)
    tau = 0.22 if open_hat else 0.028
    return bound(fft_filter(RNG.normal(0, 1, ns), "high", 6200.0, 2), 1.0) * np.exp(-t / tau) * gain


def cymbal_swell(dur: float = 3.0, gain: float = 0.3) -> np.ndarray:
    ns = frames(dur)
    t = timeline(ns)
    shape = np.linspace(0.0, 1.0, ns) ** 1.8
    swell = bound(fft_filter(RNG.normal(0, 1, ns), "high", 5200.0, 2), 1.0) * shape
    return swell * gain


def tombak_dum(dur: float = 0.42, gain: float = 0.75) -> np.ndarray:
    ns = frames(dur)
    t = timeline(ns)
    f = 62.0 + 60.0 * np.exp(-t * 30.0)
    body = np.sin(2 * math.pi * np.cumsum(f) / SR) * np.exp(-t / 0.14)
    slap = bound(fft_filter(RNG.normal(0, 1, ns), "band", 900.0, 2), 1.0) * np.exp(-t / 0.02) * 0.4
    return (body + slap) * gain


def tombak_tek(dur: float = 0.2, gain: float = 0.5, soft: bool = False) -> np.ndarray:
    ns = frames(dur)
    t = timeline(ns)
    snap = bound(fft_filter(RNG.normal(0, 1, ns), "band", 3400.0 if not soft else 2800.0, 2), 1.0)
    snap *= np.exp(-t / (0.022 if not soft else 0.016))
    tone = np.sin(2 * math.pi * 430.0 * t) * np.exp(-t / 0.03) * 0.35
    return (snap + tone) * gain * (0.55 if soft else 1.0)


def riq(dur: float = 0.22, gain: float = 0.22) -> np.ndarray:
    ns = frames(dur)
    t = timeline(ns)
    jingle = bound(fft_filter(RNG.normal(0, 1, ns), "high", 7000.0, 2), 1.0) * np.exp(-t / 0.05)
    return jingle * gain


# ------------------------------------------------------------------ لوپ

def wrap_loop(buf: np.ndarray, loop_ns: int, tail_ns: int) -> np.ndarray:
    """دنبالهٔ ریورب/دیلِی را به ابتدای لوپ تا می‌کند تا حلقه بی‌درز شود."""
    out = buf[:loop_ns].copy()
    if tail_ns > 0 and len(buf) > loop_ns:
        extra = buf[loop_ns:loop_ns + tail_ns]
        out[:len(extra)] += extra
    return out


def finish(buf: np.ndarray, target_rms: float = 0.165, ceiling: float = 0.95,
           drive: float = 1.15) -> np.ndarray:
    """حذفِ دی‌سی، نرم‌کردنِ قله‌ها و هم‌ترازیِ بلندی بین چهار طرح."""
    buf = buf - float(np.mean(buf))
    m = float(np.max(np.abs(buf))) or 1.0
    buf = np.tanh(buf / m * drive) * 0.97    # فقط قله‌های پرت نرم می‌شوند
    rms = float(np.sqrt(np.mean(buf ** 2))) or 1.0
    gain = min(target_rms / rms, ceiling / (float(np.max(np.abs(buf))) or 1.0))
    return buf * gain


# ------------------------------------------------------------------ طرح الف: مارشِ دژبان

def track_march() -> tuple[np.ndarray, int, str]:
    bpm, bars = 100.0, 16
    beat = 60.0 / bpm
    loop_ns = frames(bars * 4 * beat)
    buf = np.zeros(loop_ns + frames(TAIL))

    chords = ["Dm", "Dm", "Bb", "Bb", "F", "F", "C", "C",
              "Dm", "Dm", "Bb", "Bb", "Gm", "Gm", "A", "Dm"]
    roots = {"Dm": "D3", "Bb": "A#2", "F": "F3", "C": "C3", "Gm": "G2", "A": "A2"}
    tones = {"Dm": ["D4", "F4", "A4"], "Bb": ["Bb3", "D4", "F4"], "F": ["F3", "A3", "C4"],
             "C": ["C4", "E4", "G4"], "Gm": ["G3", "Bb3", "D4"], "A": ["A3", "C#4", "E4"]}

    for bar, chord in enumerate(chords):
        at = bar * 4 * beat
        break_bars = 8 <= bar < 12
        # سیم‌های پایین: هشتم‌های ریشه با پنجم در پایان میزان
        root = roots[chord]
        for k in range(8):
            note = root if k % 8 != 7 else root
            g = 0.20 if not break_bars else 0.15
            place(buf, fft_filter(strings(n(note), beat * 0.55, 1.0, 1600.0), "low", 900.0, 2), at + k * beat * 0.5, g)
        # پدِ آکورد روی هر میزان
        for step in tones[chord]:
            place(buf, pad(n(step), beat * 3.4, 0.075 if break_bars else 0.055, 1500.0), at, 1.0)
        # تیمپانی روی ضربِ اولِ میزان‌های فرد
        if bar % 2 == 0 and not break_bars:
            place(buf, taiko(1.2, 0.5, 78.0), at, 1.0)
        # طبلِ کوچک: الگوی ۱۶تایی با تکیه روی ضرب ۲ و ۴
        pattern = [0, 0, 1, 0, 2, 0, 1, 0, 0, 0, 1, 0, 2, 0, 2, 1]
        for step, hit in enumerate(pattern):
            if hit:
                accent = 0.55 if hit == 2 else 0.32
                place(buf, snare(0.26, 1.0, accent), at + step * beat * 0.25, 0.5)
        # فیلِ آخرِ هر چهار میزان
        if bar % 4 == 3:
            for k in range(8):
                place(buf, snare(0.2, 1.0, 0.4), at + 3 * beat + k * beat * 0.125, 0.4)

    # مارشِ تیمپانی: در آخرین میزانِ چرخه رولِ کوتاه
    roll_at = 15 * 4 * beat + 2 * beat
    for k in range(16):
        place(buf, taiko(0.3, 0.35, 88.0), roll_at + k * beat * 0.125, 1.0)

    # برنجی: هوکِ چهارمیزانی
    def phrase(octave: int, gain: float, at: float) -> None:
        mel = [("A3", 0.0, 1.5), ("D4", 1.5, 0.5), ("F4", 2.0, 1.0), ("E4", 3.0, 1.0),
               ("D4", 4.0, 2.0), ("A3", 6.0, 1.5), ("Bb3", 7.5, 0.5), ("C4", 8.0, 1.0),
               ("D4", 9.0, 1.0), ("F4", 10.0, 2.0), ("E4", 12.0, 1.5), ("C#4", 13.5, 0.5),
               ("D4", 14.0, 2.0)]
        for name, off, dur in mel:
            f = n(name) * (2 ** octave)
            place(buf, brass(f, seconds(dur, bpm) * 0.98, 0.3), at + seconds(off, bpm), gain)
    phrase(0, 1.0, seconds(0, bpm))                      # میزان ۱–۴
    phrase(0, 1.0, seconds(16, bpm))                     # میزان ۵–۸
    phrase(1, 0.75, seconds(32, bpm))                    # میزان ۹–۱۲ (اوج)
    phrase(0, 1.0, seconds(48, bpm))                     # میزان ۱۳–۱۶
    # پیش‌درآمدِ برنجی در میزان ۱۶ برای برگشت به ابتدای لوپ
    for k, name in enumerate(("A3", "C#4", "D4")):
        place(buf, brass(n(name), beat * 0.28, 0.34), seconds(63.0 + k * 0.34, bpm), 1.0)

    # سنجِ اوج‌گیر در میزان آخر
    place(buf, cymbal_swell(beat * 3.6, 0.22) + reverb(cymbal_swell(beat * 3.6, 0.22), 2.2, 5000.0) * 0.6,
          seconds(61.0, bpm), 1.0)

    # ریوربِ کلِ ارکستر
    wet = reverb(buf, 1.7, 3800.0, 0.02)
    buf = buf * 0.86 + wet * 0.34
    return finish(wrap_loop(buf, loop_ns, frames(TAIL)), drive=1.6), loop_ns, "مارشِ دژبان"


# ------------------------------------------------------------------ طرح ب: سایهٔ رون‌ها

def track_runes() -> tuple[np.ndarray, int, str]:
    bpm, bars = 60.0, 8
    beat = 60.0 / bpm
    loop_ns = frames(bars * 4 * beat)
    buf = np.zeros(loop_ns + frames(TAIL))

    chords = [("Am", ["A3", "C4", "E4"]), ("F", ["F3", "A3", "C4"]),
              ("Dm", ["D3", "F3", "A3"]), ("E", ["E3", "G#3", "B3"])]
    bass = {"Am": "A2", "F": "F2", "Dm": "D3", "E": "E2"}

    # درونِ پیوسته
    drone = pad(n("A2"), bars * 4 * beat, 0.16, 700.0, 0.004, 2.0) + \
        pad(n("E3"), bars * 4 * beat, 0.09, 900.0, 0.005, 2.0)
    place(buf, drone, 0.0, 1.0)

    for idx, (chord, tones) in enumerate(chords):
        at = idx * 2 * 4 * beat
        dur = 2 * 4 * beat
        for step in tones:
            place(buf, pad(n(step), dur * 0.98, 0.1, 1200.0, 0.01, 1.4), at, 1.0)
            place(buf, choir(n(step) * 2.0, dur * 0.9, 0.075), at, 1.0)
        place(buf, strings(n(bass[chord]), dur * 0.6, 0.14, 800.0), at, 1.0)
        # ضربانِ قلبِ طبلِ بزرگ: دو ضربه در میزانِ آکورد و یک ضربهٔ نرم‌تر در میزانِ دوم
        place(buf, taiko(1.6, 0.75, 68.0), at, 1.0)
        place(buf, taiko(1.4, 0.55, 68.0), at + 2.5 * beat, 1.0)
        place(buf, taiko(1.4, 0.38, 68.0), at + 6.0 * beat, 1.0)
        # زنگ‌های رون: پنتاتونیکِ لا، تُنُک
        shimmer = ["A5", "C6", "E5", "G5", "B5"][idx % 5]
        place(buf, bell(n(shimmer), 3.4, 0.13), at + 0.6 * beat, 1.0)
        place(buf, bell(n(shimmer) * 1.5, 3.0, 0.09), at + 3.4 * beat, 1.0)

    # بالاگیرِ نویزی در میزان آخر (برگشت به ابتدای لوپ)
    riser = np.zeros(frames(4 * beat))
    shape = np.linspace(0.0, 1.0, len(riser)) ** 2.2
    riser += bound(fft_filter(RNG.normal(0, 1, len(riser)), "band", 2200.0, 2), 1.0) * shape * 0.16
    place(buf, reverb(riser, 2.4, 5200.0), 7 * 4 * beat, 1.0)

    wet = reverb(buf, 3.4, 3200.0, 0.03)
    buf = buf * 0.78 + wet * 0.5
    return finish(wrap_loop(buf, loop_ns, frames(TAIL))), loop_ns, "سایهٔ رون‌ها"


# ------------------------------------------------------------------ طرح ج: ضربانِ خط مقدم

def track_pulse() -> tuple[np.ndarray, int, str]:
    bpm, bars = 124.0, 16
    beat = 60.0 / bpm
    loop_ns = frames(bars * 4 * beat)
    buf = np.zeros(loop_ns + frames(TAIL))

    chords = [("Am", ["A2", "C3", "E3"]), ("F", ["F2", "A2", "C3"]),
              ("C", ["C3", "E3", "G3"]), ("G", ["G2", "B2", "D3"])]
    bass_steps = {"Am": ["A1", "A1", "A1", "C2"], "F": ["F1", "F1", "F1", "A1"],
                  "C": ["C2", "C2", "C2", "E2"], "G": ["G1", "G1", "G1", "B1"]}

    for bar in range(bars):
        chord, tones = chords[bar % 4]
        at = bar * 4 * beat
        bright = 420.0 + 260.0 * (bar % 8)          # بازشدنِ فیلتر در هر ۸ میزان
        # باسِ شانزدهمی
        line = bass_steps[chord]
        for k in range(16):
            note = line[(k // 4) % len(line)] if k % 8 != 7 else line[3]
            if k % 4 == 3 and (bar % 4 == 3):
                note = line[2]
            voice = banner(n(note) * 4.0, frames(beat * 0.22), "square")
            voice = fft_filter(voice, "low", bright, 2)
            place(buf, voice * exp_env(len(voice), 0.09, 0.003), at + k * beat * 0.25, 0.5)
        # درامز: کیک چهارضربی، کلپ روی ۲ و ۴، هتِ هشتمی
        for k in range(4):
            place(buf, kick(0.4, 0.8), at + k * beat, 1.0)
            place(buf, hat(0.07, 0.22, open_hat=(k == 2)), at + k * beat + beat * 0.5, 1.0)
        for k in (1, 3):
            place(buf, clap(0.3, 0.5), at + k * beat, 1.0)
        for k in range(8):
            if k % 2 == 1:
                place(buf, hat(0.06, 0.16), at + k * beat * 0.5, 1.0)
        # استابِ آکورد هر دو میزان
        if bar % 2 == 0 and bar >= 4:
            for step in tones:
                place(buf, fft_filter(banner(n(step) * 2.0, frames(beat * 1.2)), "low", 2600.0, 2)
                      * env(frames(beat * 1.2), 0.01, 0.2, 0.4, beat * 0.6) * 0.1,
                      at + beat * 0.0, 1.0)
        # پرشدگیِ آخرِ هر ۸ میزان
        if bar % 8 == 7:
            for k in range(8):
                place(buf, snare(0.22, 0.55, 0.5 + k * 0.05), at + 3 * beat + k * beat * 0.125, 1.0)

    # ملودیِ سی‌نت: یک هوکِ دومیزانی، از میزان ۵
    hook = [("A4", 0.0, 0.75), ("C5", 0.75, 0.25), ("B4", 1.0, 1.0), ("G4", 2.0, 0.5),
            ("A4", 2.5, 1.5), ("E4", 4.0, 0.75), ("G4", 4.75, 0.25), ("A4", 5.0, 1.0),
            ("B4", 6.0, 0.5), ("C5", 6.5, 1.5)]
    for bar in (4, 6, 8, 10, 12, 14):
        at = bar * 4 * beat
        for name, off, dur in hook:
            voice = banner(n(name), frames(seconds(dur, bpm) * 0.95)) * 0.55 + \
                banner(n(name) * 2.0, frames(seconds(dur, bpm) * 0.95), "square") * 0.12
            voice = fft_filter(voice, "low", 3200.0, 2) * env(len(voice), 0.008, 0.12, 0.5,
                                                             seconds(dur, bpm) * 0.4)
            echo = delay(voice, seconds(0.75, bpm), 0.35, 2)
            place(buf, echo, at + seconds(off, bpm), 0.26)

    # بالاگیرِ نویزی در میزان ۱۶ و ۸
    for bar in (7, 15):
        nsr = frames(2 * beat)
        shape = np.linspace(0.0, 1.0, nsr) ** 2.0
        riser = bound(fft_filter(RNG.normal(0, 1, nsr), "band", 3000.0, 1), 1.0) * shape * 0.2
        place(buf, riser, bar * 4 * beat + 2 * beat, 1.0)

    wet = reverb(buf, 1.5, 3600.0, 0.016)
    buf = buf * 0.87 + wet * 0.3
    return finish(wrap_loop(buf, loop_ns, frames(TAIL))), loop_ns, "ضربانِ خط مقدم"


# ------------------------------------------------------------------ طرح د: کاروانِ شب

def track_caravan() -> tuple[np.ndarray, int, str]:
    bpm, bars = 92.0, 16
    beat = 60.0 / bpm
    loop_ns = frames(bars * 4 * beat)
    buf = np.zeros(loop_ns + frames(TAIL))

    # ریتمِ ۳+۳+۲ (هشتم‌های ۰،۳،۶ سنگین) — حسِ ۶/۸ ایرانی
    for bar in range(bars):
        at = bar * 4 * beat
        place(buf, tombak_dum(0.45, 0.8), at, 1.0)
        place(buf, tombak_tek(0.2, 0.55), at + 3 * beat * 0.5, 1.0)
        place(buf, tombak_dum(0.4, 0.6), at + 6 * beat * 0.5, 1.0)
        for step, soft in ((1, True), (4, True), (5, False), (7, True)):
            place(buf, tombak_tek(0.18, 0.5, soft), at + step * beat * 0.5, 1.0)
        for k in range(8):
            place(buf, riq(0.18, 0.16), at + k * beat * 0.5, 1.0)
        if bar % 4 == 0:
            place(buf, taiko(1.3, 0.5, 74.0), at, 1.0)

    # درونِ باس: لا با پنجم؛ در میزان‌های ۱۳–۱۶ فا و می
    for bar in range(bars):
        at = bar * 4 * beat
        root = "A2" if bar < 12 else ("F2" if bar == 12 or bar == 13 else "E2")
        place(buf, strings(n(root), beat * 3.6, 0.22, 500.0), at, 1.0)
        if bar % 2 == 1:
            place(buf, strings(n(root) * 1.5, beat * 1.6, 0.12, 600.0), at + 2 * beat, 1.0)

    # ملودی سنتور (پلاکِ افزودنی): چهار عبارتِ چهارمیزانی که تمامِ ۱۶ میزان را پر می‌کند
    phrase_a = [("A4", 0.0, 0.5), ("C5", 0.5, 0.5), ("B4", 1.0, 1.0), ("A4", 2.0, 0.5), ("G#4", 2.5, 1.5),
                ("A4", 4.0, 0.5), ("B4", 4.5, 0.5), ("C5", 5.0, 1.0), ("D5", 6.0, 0.5), ("C5", 6.5, 1.5),
                ("B4", 8.0, 0.5), ("A4", 8.5, 0.5), ("G#4", 9.0, 1.0), ("A4", 10.0, 1.0), ("B4", 11.0, 1.0),
                ("C5", 12.0, 0.5), ("B4", 12.5, 0.5), ("A4", 13.0, 2.0)]
    phrase_b = [("E5", 0.0, 0.5), ("D5", 0.5, 0.5), ("C5", 1.0, 1.0), ("B4", 2.0, 0.5), ("A4", 2.5, 1.5),
                ("C5", 4.0, 0.5), ("B4", 4.5, 0.5), ("A4", 5.0, 1.0), ("G#4", 6.0, 1.0), ("A4", 7.0, 1.0),
                ("E5", 8.0, 0.5), ("F5", 8.5, 0.5), ("E5", 9.0, 1.0), ("D5", 10.0, 0.5), ("C5", 10.5, 1.5),
                ("B4", 12.0, 0.5), ("C5", 12.5, 0.5), ("B4", 13.0, 0.5), ("A4", 13.5, 1.5)]
    # اوجِ اکتاوِ بالا (میزان ۹–۱۲)
    phrase_c = [(name, off, dur) for name, off, dur in phrase_a]
    # فرودِ پایانی روی فا و می و برگشت به لا (میزان ۱۳–۱۶)
    phrase_d = [("A4", 0.0, 1.0), ("C5", 1.0, 1.0), ("A4", 2.0, 1.0), ("C5", 3.0, 1.0),
                ("D5", 4.0, 1.0), ("C5", 5.0, 1.0), ("A4", 6.0, 1.0), ("F4", 7.0, 1.0),
                ("G#4", 8.0, 1.0), ("B4", 9.0, 1.0), ("G#4", 10.0, 1.0), ("E4", 11.0, 1.0),
                ("A4", 12.0, 1.0), ("B4", 12.5, 0.5), ("C5", 13.0, 0.5), ("B4", 13.5, 1.0), ("E4", 15.0, 1.0)]
    plan = [(phrase_a, 0, 0), (phrase_b, 0, 1), (phrase_c, 1, 2), (phrase_d, 0, 3)]
    for phrase, octave, index in plan:
        at = seconds(index * 16, bpm)
        for name, off, dur in phrase:
            f = n(name) * (2 ** octave)
            place(buf, pluck(f, seconds(dur, bpm) * 1.5, 0.30), at + seconds(off, bpm), 1.0)
            # زینتِ کوتاه پیش از نت‌های بلند
            if dur >= 1.0:
                place(buf, pluck(f * 1.5, seconds(0.35, bpm), 0.12), at + seconds(off - 0.3, bpm), 1.0)
    # نِی در نیمهٔ دوم، یک اکتاو پایین‌تر از سنتورِ اوج
    for phrase, octave, index in plan[2:]:
        at = seconds(index * 16, bpm)
        for name, off, dur in phrase:
            if dur >= 1.0:
                place(buf, ney(n(name) * (2 ** (octave - 1)), seconds(dur, bpm) * 1.1, 0.18),
                      at + seconds(off, bpm), 1.0)
    # رولِ پایانیِ تنبک برای برگشت به ابتدای لوپ (میزان ۱۶)
    roll = seconds(15 * 4 + 2.0, bpm)
    for k in range(8):
        place(buf, tombak_tek(0.16, 0.5, soft=k % 2 == 1), roll + seconds(k * 0.25, bpm), 1.0)

    wet = reverb(buf, 2.2, 4000.0, 0.02)
    buf = buf * 0.85 + wet * 0.36
    return finish(wrap_loop(buf, loop_ns, frames(TAIL)), drive=1.4), loop_ns, "کاروانِ شب"


TRACKS = {"a": ("a_march.wav", track_march), "b": ("b_runes.wav", track_runes),
          "c": ("c_pulse.wav", track_pulse), "d": ("d_caravan.wav", track_caravan)}


# ------------------------------------------------------------------ خروجی

def write_wav(path: str, samples: np.ndarray) -> None:
    data = np.clip(samples, -1.0, 1.0)
    pcm = (data * 32767.0).astype("<i2").tobytes()
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm)


def analyze(name: str, samples: np.ndarray, loop_ns: int) -> None:
    peak = float(np.max(np.abs(samples)))
    rms = float(np.sqrt(np.mean(samples ** 2)))
    dc = float(np.mean(samples))
    spec = np.abs(np.fft.rfft(samples * np.hanning(len(samples))))
    f = np.fft.rfftfreq(len(samples), 1.0 / SR)
    centroid = float(np.sum(f * spec) / (np.sum(spec) or 1.0))
    edge = int(0.03 * SR)
    seam = float(np.sqrt(np.mean(samples[:edge] ** 2))) - float(np.sqrt(np.mean(samples[-edge:] ** 2)))
    print("  %-18s %5.1f ثانیه | قله %.2f | RMS %.3f | DC %+.4f | مرکزِ طیف %.0f هرتز | اختلافِ درز %+.3f"
          % (name, len(samples) / SR, peak, rms, dc, centroid, seam))
    bands = [(0, 200), (200, 800), (800, 2000), (2000, 6000), (6000, 11000)]
    total = float(np.sum(spec ** 2)) or 1.0
    share = []
    for lo, hi in bands:
        sel = (f >= lo) & (f < hi)
        share.append("%d-%d: %.0f%%" % (lo, hi, 100.0 * float(np.sum(spec[sel] ** 2)) / total))
    print("      توازنِ باندها: %s" % " | ".join(share))
    edges = np.linspace(0, loop_ns, 9).astype(int)
    bars = ["%.3f" % float(np.sqrt(np.mean(samples[edges[k]:edges[k + 1]] ** 2))) for k in range(8)]
    print("      انرژیِ هشت بخش (تیزیِ تنظیم): %s" % " | ".join(bars))


def main() -> int:
    ap = argparse.ArgumentParser(description="سازندهٔ موسیقی پس‌زمینهٔ دژبان")
    ap.add_argument("--track", default="all", help="a | b | c | d | all")
    ap.add_argument("--out", default=os.path.join("soundsheets", "music_candidates"))
    ap.add_argument("--analyze", action="store_true", help="گزارشِ قله/RMS/طیف")
    args = ap.parse_args()

    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out_dir = args.out if os.path.isabs(args.out) else os.path.join(root, args.out)
    os.makedirs(out_dir, exist_ok=True)

    wanted = list(TRACKS) if args.track == "all" else list(args.track.replace(",", " "))
    for key in wanted:
        if key not in TRACKS:
            sys.exit("طرح ناشناس: %s (a/b/c/d)" % key)
        filename, builder = TRACKS[key]
        samples, loop_ns, title = builder()
        path = os.path.join(out_dir, filename)
        write_wav(path, samples)
        print("طرح %s — %s" % (key, title))
        print("  %s  (%.1f ثانیه، %.1f مگابایت)"
              % (os.path.relpath(path, root), len(samples) / SR, os.path.getsize(path) / 1048576))
        if args.analyze:
            analyze(title, samples, loop_ns)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
