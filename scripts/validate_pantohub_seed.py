#!/usr/bin/env python3
"""Validate the curated offline Pantohub CSV seed without third-party packages."""

import csv
import unicodedata
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SEED = ROOT / "games/pantohub/ui/src/main/assets/pantohub-words-fa.csv"
EXPECTED_COUNT = 2_500
EXPECTED_DIFFICULTY_COUNTS = {"EASY": 1_000, "MEDIUM": 900, "HARD": 600}
EXPECTED_CATEGORIES = {
    "animals", "nature", "home", "objects", "food", "jobs", "sports", "actions",
    "emotions", "places", "transport", "cinema", "literature", "idioms", "technology",
    "school", "travel", "humor", "culture", "daily_life",
}
EXPECTED_TYPES = {"WORD", "PHRASE", "IDIOM", "PROVERB", "ACTION", "PERSON", "PLACE"}
HEADERS = ["id", "text", "difficulty", "category", "type", "validated", "active", "sourceVersion"]


def fail(message: str) -> None:
    raise SystemExit(f"Pantohub seed validation failed: {message}")


def normalize_persian(text: str) -> str:
    """Keep this aligned with WordBankRules.normalizePersian in the Kotlin core."""
    text = unicodedata.normalize("NFKC", text)
    text = (
        text.replace("ي", "ی")
        .replace("ى", "ی")
        .replace("ك", "ک")
        .replace("\u0640", " ")
        .replace("\u200c", " ")
        .replace("\u200f", " ")
        .replace("\u200e", " ")
        .lower()
    )
    normalized = []
    for char in text:
        code = ord(char)
        if 0x064B <= code <= 0x065F or code == 0x0670 or 0x06D6 <= code <= 0x06ED:
            continue
        # WordBankRules removes punctuation. Treating every Unicode punctuation mark as such
        # is intentionally conservative: it can flag more duplicates, never fewer.
        if unicodedata.category(char).startswith("P") or char in "،؛؟!…«»٫٬":
            normalized.append(" ")
        else:
            normalized.append(char)
    return " ".join("".join(normalized).split())


def has_persian_letter(text: str) -> bool:
    for char in text:
        code = ord(char)
        in_persian_block = 0x0600 <= code <= 0x06FF or 0x0750 <= code <= 0x077F or 0x08A0 <= code <= 0x08FF
        if in_persian_block and unicodedata.category(char).startswith("L"):
            return True
    return False


def main() -> None:
    try:
        with SEED.open(encoding="utf-8", newline="") as file:
            reader = csv.DictReader(file)
            if reader.fieldnames != HEADERS:
                fail(f"unexpected CSV headers: {reader.fieldnames!r}")
            rows = list(reader)
    except (OSError, csv.Error) as error:
        fail(str(error))

    if len(rows) != EXPECTED_COUNT:
        fail(f"expected {EXPECTED_COUNT} records, found {len(rows)}")

    ids = set()
    texts = {}
    difficulty_counts = Counter()
    for line_number, row in enumerate(rows, start=2):
        if None in row or any(row.get(field) is None for field in HEADERS):
            fail(f"malformed columns at CSV line {line_number}")
        word_id = row["id"].strip()
        text = row["text"].strip()
        difficulty = row["difficulty"].strip()
        category = row["category"].strip()
        phrase_type = row["type"].strip()
        source_version = row["sourceVersion"].strip()

        if not word_id or word_id in ids:
            fail(f"blank or repeated ID at CSV line {line_number}: {word_id!r}")
        ids.add(word_id)
        if not text or len(text) > 120 or any(unicodedata.category(char) == "Cc" for char in text):
            fail(f"blank, overlong, or control-containing text at CSV line {line_number}")
        if not has_persian_letter(text) or not normalize_persian(text):
            fail(f"text is not a valid Persian word/phrase at CSV line {line_number}: {text!r}")
        key = normalize_persian(text)
        if key in texts:
            fail(f"duplicate normalized text at CSV line {line_number}: {text!r} and {texts[key]!r}")
        texts[key] = text
        if difficulty not in EXPECTED_DIFFICULTY_COUNTS:
            fail(f"unknown difficulty at CSV line {line_number}: {difficulty!r}")
        difficulty_counts[difficulty] += 1
        if category not in EXPECTED_CATEGORIES:
            fail(f"unknown category at CSV line {line_number}: {category!r}")
        if phrase_type not in EXPECTED_TYPES:
            fail(f"unknown phrase type at CSV line {line_number}: {phrase_type!r}")
        if row["validated"].strip().lower() != "true" or row["active"].strip().lower() != "true":
            fail(f"bootstrap record is not active and reviewed at CSV line {line_number}")
        if line_number <= 101:
            expected_version = "curated-bootstrap-v1"
        elif line_number <= 1001:
            expected_version = "curated-bootstrap-v2"
        else:
            expected_version = "curated-bootstrap-v3"
        if source_version != expected_version:
            fail(f"unexpected source version at CSV line {line_number}: {source_version!r}")
        expected_id = f"seed-{line_number - 1:03d}"
        if word_id != expected_id:
            fail(f"expected stable ID {expected_id!r} at CSV line {line_number}, found {word_id!r}")

    if dict(difficulty_counts) != EXPECTED_DIFFICULTY_COUNTS:
        fail(f"unexpected difficulty distribution: {dict(difficulty_counts)!r}")
    print(
        f"Pantohub seed OK: {len(rows)} unique Persian records "
        f"(easy {difficulty_counts['EASY']}, medium {difficulty_counts['MEDIUM']}, hard {difficulty_counts['HARD']})."
    )


if __name__ == "__main__":
    main()
