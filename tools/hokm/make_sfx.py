"""ساختِ افکت‌های صوتیِ بازی حکم (WAV مونو، ۲۲۰۵۰ هرتز) — بدون هیچ منبعِ خارجی.

هر صدا با سنتزِ افزایشی (افزودنِ هارمونیک‌ها) و پوشِ ADSR ساخته می‌شود تا حسِ واقعی بدهد:
صدای کشیده شدنِ کارت روی نمد، زنگِ برنده شدن، فانفارِ کوت و …
"""
import math
import os
import random
import struct
import wave

SR = 22050
OUT = "/home/user/Dorehami/games/hokm/ui/src/main/res/raw"
random.seed(7)  # خروجیِ قطعی


def env_adsr(i, n, attack=0.01, decay=0.15, sustain=0.6, release=0.3):
    """پوشِ سادهٔ ADSR بر اساسِ نسبتِ پیشرفت."""
    t = i / n
    a, d, r = attack, decay, release
    s = max(0.0, 1.0 - a - d - r)
    if t < a:
        return t / a if a > 0 else 1.0
    if t < a + d:
        return 1.0 - (1 - sustain) * ((t - a) / d if d > 0 else 1.0)
    if t < a + d + s:
        return sustain
    if r <= 0:
        return 0.0
    return sustain * max(0.0, 1.0 - (t - a - d - s) / r)


def exp_decay(i, n, k=6.0):
    return math.exp(-k * i / n)


def tone(freq, dur, harmonics=(1.0,), decay=6.0, vibrato=0.0, attack=0.004):
    """یک نت با هارمونیک‌های قابل تنظیم."""
    n = int(SR * dur)
    out = [0.0] * n
    for h, amp in enumerate(harmonics, start=1):
        for i in range(n):
            f = freq * h
            if vibrato:
                f *= 1.0 + vibrato * math.sin(2 * math.pi * 5.0 * i / SR)
            out[i] += amp * math.sin(2 * math.pi * f * i / SR)
    for i in range(n):
        a = min(1.0, i / (attack * SR)) if attack > 0 else 1.0
        out[i] *= a * exp_decay(i, n, decay)
    return out


def noise(dur, decay=10.0, bright=0.0):
    """نویز با پوشِ افت‌دار؛ bright روی تأکیدِ فرکانس‌های بالا اثر می‌گذارد."""
    n = int(SR * dur)
    raw = [random.uniform(-1, 1) for _ in range(n)]
    out = []
    prev = 0.0
    for i, v in enumerate(raw):
        if bright > 0:
            v = (v - prev) * bright + v * (1 - bright)
            prev = raw[i]
        out.append(v * exp_decay(i, n, decay))
    return out


def mix(*parts, offsets=None):
    length = 0
    for idx, p in enumerate(parts):
        off = int((offsets[idx] if offsets else 0) * SR)
        length = max(length, off + len(p))
    out = [0.0] * length
    for idx, p in enumerate(parts):
        off = int((offsets[idx] if offsets else 0) * SR)
        for i, s in enumerate(p):
            out[off + i] += s
    return out


def normalize(samples, peak=0.92):
    m = max(abs(s) for s in samples) or 1.0
    k = peak / m
    return [s * k for s in samples]


def fade_edges(samples, ms_in=2.0, ms_out=25.0):
    n = len(samples)
    fi = int(SR * ms_in / 1000)
    fo = int(SR * ms_out / 1000)
    for i in range(min(fi, n)):
        samples[i] *= i / fi
    for i in range(min(fo, n)):
        samples[n - 1 - i] *= i / fo
    return samples


def write(name, samples):
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name)
    samples = normalize(samples)
    samples = fade_edges(samples)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(
            b"".join(
                struct.pack("<h", max(-32767, min(32767, int(s * 32767))))
                for s in samples
            )
        )
    print(f"{name:20s} {os.path.getsize(path)//1024:4d} KB  {len(samples)/SR:.2f}s")


# ---------------------------------------------------------------------------
# صداها
# ---------------------------------------------------------------------------

# ورق روی نمد کشیده می‌شود
write("hokm_card_deal.wav", mix(noise(0.20, decay=9.0, bright=0.35), offsets=[0.0]))

# ورق با شدت روی میز انداخته می‌شود
write(
    "hokm_card_played.wav",
    mix(
        noise(0.14, decay=14.0, bright=0.6),
        tone(180, 0.08, harmonics=(1.0, 0.4), decay=18.0),
        offsets=[0.0, 0.005],
    ),
)

# دو نتِ زنگ‌مانند برای بردنِ یک دست
write(
    "hokm_trick_won.wav",
    mix(
        tone(659.25, 0.34, harmonics=(1.0, 0.5, 0.22), decay=5.0, vibrato=0.004),
        tone(987.77, 0.30, harmonics=(1.0, 0.35, 0.15), decay=6.0),
        offsets=[0.0, 0.10],
    ),
)

# بریدن با حکم: یک «هویـش» رو به پایین + ضربه
write(
    "hokm_trick_ruffed.wav",
    mix(
        noise(0.22, decay=4.5, bright=0.8),
        tone(140, 0.20, harmonics=(1.0, 0.6, 0.3), decay=9.0),
        offsets=[0.0, 0.06],
    ),
)

# زنگِ تعیینِ حکم
write(
    "hokm_trump_set.wav",
    mix(
        tone(880, 0.45, harmonics=(1.0, 0.45, 0.3, 0.12), decay=4.0),
        tone(1320, 0.38, harmonics=(1.0, 0.3), decay=5.0),
        tone(1760, 0.30, harmonics=(1.0, 0.2), decay=7.0),
        offsets=[0.0, 0.03, 0.06],
    ),
)

# بردنِ دست: سه نتِ بالارونده
write(
    "hokm_hand_won.wav",
    mix(
        tone(523.25, 0.30, harmonics=(1.0, 0.4), decay=6.0),
        tone(659.25, 0.30, harmonics=(1.0, 0.4), decay=6.0),
        tone(783.99, 0.45, harmonics=(1.0, 0.45, 0.2), decay=4.0),
        offsets=[0.0, 0.11, 0.22],
    ),
)

# باختنِ دست: دو نتِ پایین‌رونده
write(
    "hokm_hand_lost.wav",
    mix(
        tone(392.0, 0.32, harmonics=(1.0, 0.35), decay=6.0),
        tone(311.13, 0.45, harmonics=(1.0, 0.4, 0.2), decay=4.0),
        offsets=[0.0, 0.13],
    ),
)

# کوت: فانفارِ کوتاه با درخشش
write(
    "hokm_kot.wav",
    mix(
        tone(523.25, 0.7, harmonics=(1.0, 0.5, 0.3, 0.15), decay=3.0),
        tone(659.25, 0.7, harmonics=(1.0, 0.5, 0.3), decay=3.0),
        tone(783.99, 0.8, harmonics=(1.0, 0.5, 0.3, 0.1), decay=2.6),
        tone(1046.5, 0.9, harmonics=(1.0, 0.4, 0.25), decay=3.2),
        noise(0.5, decay=4.0, bright=0.7),
        offsets=[0.0, 0.08, 0.16, 0.24, 0.02],
    ),
)

# حاکم‌کُتی: فانفارِ بزرگ‌تر با بم و سنج
write(
    "hokm_hakem_koti.wav",
    mix(
        tone(130.81, 1.0, harmonics=(1.0, 0.7, 0.5, 0.3, 0.2), decay=2.2),
        tone(196.0, 1.0, harmonics=(1.0, 0.6, 0.4, 0.25), decay=2.4),
        tone(392.0, 0.9, harmonics=(1.0, 0.5, 0.35, 0.2), decay=2.6),
        tone(523.25, 0.9, harmonics=(1.0, 0.5, 0.3), decay=2.8),
        tone(783.99, 1.0, harmonics=(1.0, 0.45, 0.25), decay=3.0),
        noise(0.7, decay=3.0, bright=0.85),
        offsets=[0.0, 0.05, 0.10, 0.20, 0.30, 0.0],
    ),
)

# بردنِ کل بازی
write(
    "hokm_match_won.wav",
    mix(
        tone(523.25, 0.5, harmonics=(1.0, 0.5, 0.3), decay=4.0),
        tone(659.25, 0.5, harmonics=(1.0, 0.5, 0.3), decay=4.0),
        tone(783.99, 0.5, harmonics=(1.0, 0.5, 0.3), decay=4.0),
        tone(1046.5, 1.2, harmonics=(1.0, 0.6, 0.4, 0.25, 0.12), decay=2.0),
        tone(1318.5, 1.1, harmonics=(1.0, 0.4, 0.2), decay=2.6),
        offsets=[0.0, 0.12, 0.24, 0.36, 0.40],
    ),
)

# باختنِ کل بازی
write(
    "hokm_match_lost.wav",
    mix(
        tone(440.0, 0.6, harmonics=(1.0, 0.4), decay=3.4),
        tone(349.23, 0.6, harmonics=(1.0, 0.4), decay=3.4),
        tone(261.63, 1.0, harmonics=(1.0, 0.5, 0.3, 0.15), decay=2.2),
        offsets=[0.0, 0.16, 0.32],
    ),
)

# نوبتِ شما
write(
    "hokm_your_turn.wav",
    mix(
        tone(880, 0.10, harmonics=(1.0, 0.3), decay=12.0),
        tone(1174.66, 0.16, harmonics=(1.0, 0.3), decay=9.0),
        offsets=[0.0, 0.09],
    ),
)

# حرکتِ غیرمجاز
write(
    "hokm_invalid_move.wav",
    mix(
        tone(150, 0.26, harmonics=(1.0, 0.8, 0.6, 0.4, 0.3), decay=7.0),
        noise(0.10, decay=12.0, bright=0.2),
        offsets=[0.0, 0.0],
    ),
)
