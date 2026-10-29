#!/usr/bin/env python3
"""Generate two reproducible synthetic blue-energy laser pulse sounds."""
from __future__ import annotations

import argparse
import math
import random
import struct
import wave
from pathlib import Path

OUT = Path(__file__).resolve().parents[1] / "assets/sounds/combat"
RATE = 22050


def write_sample(destination: Path, name: str, seed: int) -> None:
    rng = random.Random(seed)
    duration = 0.34
    samples = bytearray()
    phase = 0.0
    for index in range(int(RATE * duration)):
        t = index / RATE
        # Brief rise, stable electric hum and a rapid natural fade; no gunshot.
        attack = min(1.0, t / 0.008)
        tail = min(1.0, max(0.0, (duration - t) / 0.055))
        envelope = attack * tail * (0.65 + 0.35 * math.exp(-t * 7.0))
        frequency = 1020.0 - 410.0 * t / duration + 55.0 * math.sin(t * 35.0 + seed)
        phase += math.tau * frequency / RATE
        signal = (0.42 * math.sin(phase) + 0.16 * math.sin(phase * 2.02)
                  + 0.06 * rng.uniform(-1.0, 1.0)) * envelope
        samples.extend(struct.pack("<h", max(-32767, min(32767, round(signal * 24000)))))
    destination.mkdir(parents=True, exist_ok=True)
    with wave.open(str(destination / name), "wb") as audio:
        audio.setnchannels(1)
        audio.setsampwidth(2)
        audio.setframerate(RATE)
        audio.writeframes(samples)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=OUT)
    destination = parser.parse_args().output
    write_sample(destination, "laser-beam-a.wav", 2903)
    write_sample(destination, "laser-beam-b.wav", 2909)


if __name__ == "__main__":
    main()
