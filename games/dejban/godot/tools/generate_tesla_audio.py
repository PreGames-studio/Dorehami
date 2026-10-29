#!/usr/bin/env python3
"""Synthesize two original, deterministic short electrical crackle sounds."""
from __future__ import annotations
import math
import random
import struct
import wave
from pathlib import Path

OUT = Path(__file__).resolve().parents[1] / 'assets/sounds/combat'
RATE = 22050


def make(name: str, seed: int) -> None:
    rng = random.Random(seed)
    duration = .32
    count = int(RATE * duration)
    last = 0.0
    phase = 0.0
    frames = bytearray()
    for index in range(count):
        t = index / RATE
        env = min(1.0, t / .012) * math.exp(-t * 10.5)
        phase += math.tau * (1800.0 - 1800.0 * t + 270.0 * math.sin(t * 39)) / RATE
        noise = rng.uniform(-1.0, 1.0)
        highpass = noise - last * .78
        last = noise
        crackle = .55 + .45 * math.sin(t * 123.0 + seed)**8
        sample = env * (0.42 * math.sin(phase) + .27 * highpass * crackle + .12 * math.sin(phase * .48))
        value = max(-32767, min(32767, round(sample * 24000)))
        frames.extend(struct.pack('<h', value))
    OUT.mkdir(parents=True, exist_ok=True)
    with wave.open(str(OUT / name), 'wb') as audio:
        audio.setnchannels(1)
        audio.setsampwidth(2)
        audio.setframerate(RATE)
        audio.writeframes(frames)


if __name__ == '__main__':
    make('tesla-zap-a.wav', 1729)
    make('tesla-zap-b.wav', 1733)
