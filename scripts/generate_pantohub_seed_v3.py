#!/usr/bin/env python3
"""Append the curated-bootstrap-v3 rows (seed-1001 .. seed-2500) to the Pantohub CSV seed.

Source chunks live in scripts/pantohub_seed_v3/chunkNN.txt, one word per line:

    text|DIFFICULTY|category-code|TYPE

    DIFFICULTY: E=EASY, M=MEDIUM, H=HARD
    TYPE:       W=WORD, A=ACTION, L=PLACE, P=PHRASE, I=IDIOM, R=PROVERB, S=PERSON

The script is deterministic: it reads the chunks in filename order, drops rows whose
normalized text already exists (in the CSV or earlier in the chunks), and takes the first
quota of each difficulty. It refuses to run unless the CSV still holds exactly the 1000
legacy rows, so it cannot be run twice by accident.
"""

import csv
import sys
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from validate_pantohub_seed import normalize_persian, has_persian_letter  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
SEED = ROOT / "games/pantohub/ui/src/main/assets/pantohub-words-fa.csv"
CHUNKS = ROOT / "scripts/pantohub_seed_v3"

LEGACY_COUNT = 1_000
QUOTAS = {"EASY": 600, "MEDIUM": 550, "HARD": 350}
SOURCE_VERSION = "curated-bootstrap-v3"
HEADERS = ["id", "text", "difficulty", "category", "type", "validated", "active", "sourceVersion"]

DIFFICULTY = {"E": "EASY", "M": "MEDIUM", "H": "HARD"}
TYPE = {"W": "WORD", "A": "ACTION", "L": "PLACE", "P": "PHRASE", "I": "IDIOM", "R": "PROVERB", "S": "PERSON"}
CATEGORY = {
    "an": "animals", "na": "nature", "ho": "home", "ob": "objects", "fo": "food",
    "ac": "actions", "pl": "places", "li": "literature", "jo": "jobs", "sp": "sports",
    "em": "emotions", "id": "idioms", "sc": "school", "te": "technology", "tr": "transport",
    "tv": "travel", "ci": "cinema", "hu": "humor", "cu": "culture", "daily_life": "daily_life",
}


def fail(message: str) -> None:
    raise SystemExit(f"generate_pantohub_seed_v3: {message}")


def read_chunks() -> list[tuple[str, str, str, str, str]]:
    rows = []
    for path in sorted(CHUNKS.glob("chunk*.txt")):
        for line_number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
            if not raw.strip():
                continue
            parts = raw.split("|")
            if len(parts) != 4:
                fail(f"{path.name}:{line_number}: expected 4 fields, found {len(parts)}")
            text, difficulty, category_code, type_code = (part.strip() for part in parts)
            if difficulty not in DIFFICULTY or type_code not in TYPE or category_code not in CATEGORY:
                fail(f"{path.name}:{line_number}: unknown code in {raw!r}")
            if not text or len(text) > 120 or any(ord(ch) < 32 for ch in text):
                fail(f"{path.name}:{line_number}: invalid text {text!r}")
            if not has_persian_letter(text) or not normalize_persian(text):
                fail(f"{path.name}:{line_number}: not Persian text {text!r}")
            rows.append((text, DIFFICULTY[difficulty], CATEGORY[category_code], TYPE[type_code], path.name))
    return rows


def main() -> None:
    with SEED.open(encoding="utf-8", newline="") as file:
        reader = csv.DictReader(file)
        if reader.fieldnames != HEADERS:
            fail("unexpected CSV headers")
        existing = list(reader)
    if len(existing) != LEGACY_COUNT:
        fail(f"expected {LEGACY_COUNT} legacy rows before appending, found {len(existing)}")

    seen = {normalize_persian(row["text"]) for row in existing}
    pools: dict[str, list[tuple[str, str, str, str]]] = {level: [] for level in QUOTAS}
    skipped = Counter()
    for text, difficulty, category, phrase_type, source in read_chunks():
        key = normalize_persian(text)
        if key in seen:
            skipped["duplicate"] += 1
            continue
        seen.add(key)
        pools[difficulty].append((text, category, phrase_type, source))

    selected = []
    for level, quota in QUOTAS.items():
        if len(pools[level]) < quota:
            fail(f"not enough unique {level} rows: have {len(pools[level])}, need {quota}")
        selected.extend((level, *item) for item in pools[level][:quota])

    next_number = LEGACY_COUNT + 1
    lines = []
    for level, text, category, phrase_type, _source in selected:
        lines.append([f"seed-{next_number}", text, level, category, phrase_type, "true", "true", SOURCE_VERSION])
        next_number += 1

    raw_bytes = SEED.read_bytes()
    if not raw_bytes.endswith(b"\n"):
        raw_bytes += b"\n"
    with SEED.open("w", encoding="utf-8", newline="") as file:
        file.write(raw_bytes.decode("utf-8"))
        writer = csv.writer(file, lineterminator="\n")
        writer.writerows(lines)

    print(f"Appended {len(lines)} rows (seed-{LEGACY_COUNT + 1} .. seed-{next_number - 1}).")
    print(f"Skipped as duplicates: {skipped['duplicate']}")
    print("Available unique per difficulty:", {k: len(v) for k, v in pools.items()})


if __name__ == "__main__":
    main()
