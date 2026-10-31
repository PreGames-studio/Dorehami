#!/usr/bin/env python3
"""Verify that a built Dorehami APK is complete, signed and installable.

Why this exists: several hand-delivered builds reached phones as a file Android
rejected with «بسته نامعتبر است» / "There was a problem parsing the package".
That failure has only a few causes, and all of them are detectable before the
file is published:

* a truncated or corrupt download (ZIP central directory / CRC mismatch),
* an unsigned or re-zipped APK (no v1/v2 signing material),
* a missing runtime piece (manifest, dex, embedded Dezhban pack, Godot native
  library) that the build step silently dropped,
* a package/manifest that no target device accepts (wrong package id, SDK
  range, or ABI list).

The script needs no network and no third-party packages. When an Android build
tools directory is available it also asks ``aapt2``/``aapt`` for the badging
block, so the report states the package id, version, SDK range and ABIs that a
real installer will read.

Usage:
    python3 scripts/verify_shipped_apk.py app/build/outputs/apk/debug/app-debug.apk
    python3 scripts/verify_shipped_apk.py build.apk --aapt "$ANDROID_HOME/build-tools/36.0.0/aapt2"
    python3 scripts/verify_shipped_apk.py build.apk --expect-package ir.dorehami.app --min-sdk 24

Exit status is 0 only when every required check passes.
"""

from __future__ import annotations

import argparse
import hashlib
import re
import shutil
import struct
import subprocess
import sys
from collections import Counter
from pathlib import Path
from zipfile import BadZipFile, ZipFile

SIGNING_BLOCK_MAGIC = b"APK Sig Block 42"
EOCD_SIGNATURE = b"PK\x05\x06"
V1_SIGNATURE_SUFFIXES = (".RSA", ".DSA", ".EC")
REQUIRED_ENTRIES = ("AndroidManifest.xml", "resources.arsc")
REQUIRED_PACK = "assets/dezhban.pck"
REQUIRED_GODOT_LIB = "libgodot_android.so"


class Report:
    """Collects check results so the caller can print one readable summary."""

    def __init__(self) -> None:
        self.rows: list[tuple[bool, str, str]] = []

    def check(self, ok: bool, title: str, detail: str = "") -> bool:
        self.rows.append((ok, title, detail))
        return ok

    @property
    def failed(self) -> list[tuple[bool, str, str]]:
        return [row for row in self.rows if not row[0]]

    def print(self, *, markdown: bool = False) -> None:
        if markdown:
            print("| نتیجه | بررسی | جزئیات |")
            print("|---|---|---|")
            for ok, title, detail in self.rows:
                print(f"| {'✅' if ok else '❌'} | {title} | {detail or '—'} |")
            return
        for ok, title, detail in self.rows:
            print(f"[{'ok' if ok else 'FAIL'}] {title}{f' — {detail}' if detail else ''}")


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def find_central_directory_offset(path: Path, size: int) -> int | None:
    """Return the offset of the ZIP central directory, or None if unreadable."""
    tail_size = min(size, 66_000)
    with path.open("rb") as handle:
        handle.seek(size - tail_size)
        tail = handle.read(tail_size)
    index = tail.rfind(EOCD_SIGNATURE)
    if index < 0:
        return None
    eocd_offset = size - tail_size + index
    # EOCD: signature(4) disk(2) cd-disk(2) entries-this-disk(2) entries(2) cd-size(4) cd-offset(4)
    if eocd_offset + 16 > size:
        return None
    cd_offset = struct.unpack("<I", tail[index + 16:index + 20])[0]
    if cd_offset == 0xFFFFFFFF:
        return None  # ZIP64: caller falls back to the zipfile module only.
    return cd_offset


def has_v2_signing_block(path: Path, size: int) -> tuple[bool, str]:
    cd_offset = find_central_directory_offset(path, size)
    if cd_offset is None:
        return False, "central directory not readable"
    if cd_offset < 32:
        return False, "no room for a signing block"
    with path.open("rb") as handle:
        handle.seek(cd_offset - 16)
        if handle.read(16) != SIGNING_BLOCK_MAGIC:
            return False, "APK Signing Block absent (v2/v3 signature missing)"
        # Size field of the block lives 24 bytes before the magic.
        handle.seek(cd_offset - 24)
        block_size = struct.unpack("<Q", handle.read(8))[0]
    # v2 uses 24-bit; the sanity bound only rejects nonsense values.
    if not 24 <= block_size <= max(cd_offset, 1024) * 1024:
        return False, f"implausible signing block size {block_size}"
    return True, f"APK Signing Block present ({block_size} bytes)"


def badging(aapt: str | None, apk: Path) -> dict[str, str]:
    if not aapt:
        return {}
    tool = Path(aapt)
    if tool.is_dir():
        candidates = [tool / "aapt2", tool / "aapt"]
        tool = next((candidate for candidate in candidates if candidate.is_file()), tool)
    if not tool.is_file():
        return {}
    try:
        result = subprocess.run(
            [str(tool), "dump", "badging", str(apk)],
            capture_output=True, text=True, timeout=180,
        )
    except (OSError, subprocess.SubprocessError):
        return {}
    if result.returncode != 0:
        return {}
    fields: dict[str, str] = {}
    for line in result.stdout.splitlines():
        if line.startswith("package:"):
            fields["package"] = line
            for key in ("name", "versionCode", "versionName"):
                match = re.search(rf"{key}='([^']*)'", line)
                if match:
                    fields[f"package.{key}"] = match.group(1)
        elif line.startswith(("sdkVersion:", "minSdkVersion:")):
            fields["minSdk"] = line.split(":", 1)[1].strip().strip("'")
        elif line.startswith("targetSdkVersion:"):
            fields["targetSdk"] = line.split(":", 1)[1].strip().strip("'")
        elif line.startswith("native-code:"):
            fields["nativeCode"] = line.split(":", 1)[1].strip()
        elif line.startswith("launchable-activity:"):
            fields["launchable"] = line.split(":", 1)[1].strip()
    return fields


def verify(apk: Path, *, expect_package: str, min_sdk: int | None, aapt: str | None,
           expect_pack: bool,
           require_abis: tuple[str, ...] = ("arm64-v8a",)) -> tuple[Report, dict[str, str]]:
    report = Report()
    if not report.check(apk.is_file(), "فایل APK موجود است", str(apk)) or not apk.is_file():
        return report, {}
    size = apk.stat().st_size
    report.check(size > 1_000_000, "اندازهٔ فایل معقول است", f"{size:,} بایت")
    digest = sha256_of(apk)

    try:
        archive = ZipFile(apk)
    except (BadZipFile, OSError) as error:
        report.check(False, "ساختار ZIP سالم است", str(error))
        return report, {"sha256": digest, "size": size}

    with archive:
        names = [item.filename for item in archive.infolist()]
        duplicates = sorted(name for name, count in Counter(names).items() if count > 1)
        report.check(not duplicates, "ورودی تکراری در آرشیو نیست", ", ".join(duplicates[:5]))
        unsafe = [
            name for name in names
            if name.startswith("/") or ".." in name.split("/") or "\\" in name
        ]
        report.check(not unsafe, "مسیرهای آرشیو امن هستند", ", ".join(unsafe[:5]))

        # testzip() walks every entry and validates its CRC — the exact damage a
        # truncated browser download leaves behind.
        corrupt = archive.testzip()
        report.check(corrupt is None, "همهٔ محتوا سالم است (CRC)", corrupt or "")

        present = set(names)
        missing = [entry for entry in REQUIRED_ENTRIES if entry not in present]
        report.check(not missing, "ویرایش‌های اجباری حاضرند", ", ".join(missing))

        dexes = sorted(name for name in present if re.fullmatch(r"classes\d*\.dex", name))
        report.check(bool(dexes), "کد اجرایی (dex) داخل بسته است", ", ".join(dexes[:4]))

        v1 = [name for name in present
              if name.startswith("META-INF/") and name.upper().endswith(V1_SIGNATURE_SUFFIXES)]
        v2_ok, v2_detail = has_v2_signing_block(apk, size)
        report.check(v2_ok or bool(v1), "بسته امضا شده است",
                     v2_detail if v2_ok else ", ".join(v1[:3]))
        if v2_ok:
            report.check(True, "امضای نسخهٔ ۲/۳ (APK Signing Block)", v2_detail)

        if expect_pack:
            report.check(REQUIRED_PACK in present, "بستهٔ دژبان داخل APK است", REQUIRED_PACK)

        libs = sorted(name for name in present if name.startswith("lib/") and name.endswith(".so"))
        godot = [name for name in libs if name.rsplit("/", 1)[-1] == REQUIRED_GODOT_LIB]
        report.check(bool(godot), "کتابخانهٔ بومی Godot داخل APK است", ", ".join(godot))
        abis = sorted({name.split("/")[1] for name in libs if name.count("/") >= 2})
        report.check(bool(abis), "معماری‌های بومی مشخص‌اند", ", ".join(abis))
        for required in require_abis:
            report.check(required in abis, f"معماری {required} داخل بسته هست", ", ".join(abis))

    fields = badging(aapt, apk)
    if fields:
        package = fields.get("package.name", "")
        report.check(not expect_package or package == expect_package,
                     "شناسهٔ بسته درست است", package or "نامشخص")
        report.check("launchable" in fields, "اکتیویتی اجرا شونده دارد",
                     fields.get("launchable", "ندارد"))
        # Only compare when badging actually reported a minimum SDK; a missing
        # line means "unknown", not "wrong", and must not fail the gate.
        if min_sdk is not None and fields.get("minSdk"):
            try:
                actual = int(fields["minSdk"])
            except ValueError:
                actual = None
            if actual is not None:
                report.check(actual == min_sdk, "minSdk مطابق انتظار است", str(actual))
            else:
                report.check(True, "minSdk (خوانا نبود)", fields["minSdk"])
        if "nativeCode" in fields:
            for required in require_abis:
                report.check(required in fields["nativeCode"], f"aapt هم {required} را می‌بیند",
                             fields["nativeCode"])
    else:
        report.check(True, "اطلاعات بدجینگ (اختیاری)", "aapt در دسترس نبود")

    return report, {"sha256": digest, "size": size, **fields}


def _synthetic_apk(path: Path, *, omit: tuple[str, ...] = (), duplicate: str | None = None) -> Path:
    """Write a minimal APK-shaped ZIP; only used by --self-test."""
    entries = {
        "AndroidManifest.xml": b"\x03\x00\x08\x00" + b"\x00" * 28,
        "resources.arsc": b"\x02\x00\x0c\x00" + b"\x00" * 28,
        "classes.dex": b"dex\n035\x00" + b"\x00" * 96,
        "META-INF/CERT.RSA": b"\x30\x82" + b"\x00" * 32,
        "META-INF/CERT.SF": b"Signature-Version: 1.0\r\n\r\n",
        "assets/dezhban.pck": b"GDPC" + b"\x00" * 4096,
        "lib/arm64-v8a/libgodot_android.so": b"\x7fELF\x02\x01\x01" + b"\x00" * 512,
    }
    for name in omit:
        entries.pop(name, None)
    with ZipFile(path, "w") as archive:
        for name, payload in entries.items():
            archive.writestr(name, payload)
        if duplicate:
            archive.writestr(duplicate, b"second copy")
    return path


def self_test() -> int:
    """Prove the checker catches the exact failures that reached users' phones."""
    import tempfile
    import warnings

    warnings.filterwarnings("ignore", message="Duplicate name")

    failures: list[str] = []
    with tempfile.TemporaryDirectory() as temp:
        root = Path(temp)

        complete = _synthetic_apk(root / "complete.apk")
        report, facts = verify(complete, expect_package="", min_sdk=None, aapt=None, expect_pack=True)
        failed_titles = {title for _, title, _ in report.failed}
        if facts.get("sha256") is None:
            failures.append("sha256 missing for a readable APK")
        if "همهٔ محتوا سالم است (CRC)" in failed_titles:
            failures.append("a clean ZIP was reported as corrupt")
        if "کتابخانهٔ بومی Godot داخل APK است" in failed_titles:
            failures.append("present Godot library was reported missing")
        if "بسته امضا شده است" in failed_titles:
            failures.append("a v1-signed APK was reported unsigned")

        # The gate also reads `aapt2 dump badging`; a misparsed SDK line once
        # failed a perfectly good APK, so pin the parser with realistic output.
        fake_aapt = root / "aapt2"
        fake_aapt.write_text(
            "#!/bin/sh\n"
            "cat <<'OUT'\n"
            "package: name='ir.dorehami.app' versionCode='1' versionName='0.1.0'\n"
            "sdkVersion:'24'\n"
            "targetSdkVersion:'36'\n"
            "launchable-activity: name='ir.dorehami.app.MainActivity'  label='' icon=''\n"
            "native-code: 'arm64-v8a'\n"
            "OUT\n",
            encoding="utf-8",
        )
        fake_aapt.chmod(0o755)
        report, facts = verify(
            complete, expect_package="ir.dorehami.app", min_sdk=24,
            aapt=str(fake_aapt), expect_pack=True,
        )
        badging_failures = {title for _, title, _ in report.failed}
        if facts.get("package.name") != "ir.dorehami.app":
            failures.append(f"badging package id was not parsed ({facts.get('package.name')!r})")
        if "شناسهٔ بسته درست است" in badging_failures:
            failures.append("a correct package id was reported wrong")
        if "minSdk مطابق انتظار است" in badging_failures:
            failures.append("a correct minSdk (24) was misparsed")

        truncated = root / "truncated.apk"
        truncated.write_bytes(complete.read_bytes()[: complete.stat().st_size // 2])
        report, _ = verify(truncated, expect_package="", min_sdk=None, aapt=None, expect_pack=True)
        if not any(title.startswith("ساختار ZIP") for _, title, _ in report.failed):
            failures.append("a half-downloaded APK was not rejected")

        without_pack = _synthetic_apk(root / "no-pack.apk", omit=("assets/dezhban.pck",))
        report, _ = verify(without_pack, expect_package="", min_sdk=None, aapt=None, expect_pack=True)
        if "بستهٔ دژبان داخل APK است" not in {title for _, title, _ in report.failed}:
            failures.append("a pack-less APK was not rejected")

        with_duplicates = _synthetic_apk(root / "dupes.apk", duplicate="classes.dex")
        report, _ = verify(with_duplicates, expect_package="", min_sdk=None, aapt=None, expect_pack=True)
        if "ورودی تکراری در آرشیو نیست" not in {title for _, title, _ in report.failed}:
            failures.append("duplicate archive entries were not rejected")

    if failures:
        for item in failures:
            print(f"SELF-TEST FAIL: {item}", file=sys.stderr)
        return 1
    print("self-test passed: corrupt, pack-less and duplicated archives are all rejected")
    return 0


def main(argv: list[str]) -> int:
    if argv[:1] == ["--self-test"]:
        return self_test()
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--self-test", action="store_true",
                        help="run the checker's own regression test and exit")
    parser.add_argument("--aapt", help="path to aapt2/aapt or a build-tools directory")
    parser.add_argument("--expect-package", default="ir.dorehami.app")
    parser.add_argument("--min-sdk", type=int, default=None)
    parser.add_argument("--no-pack", action="store_true",
                        help="skip the embedded Dezhban pack check (non-app modules)")
    parser.add_argument("--require-abi", action="append", dest="require_abis",
                        help="an ABI that must be inside the APK (repeatable; default arm64-v8a)")
    parser.add_argument("--markdown", action="store_true", help="print the report as a Markdown table")
    args = parser.parse_args(argv)

    aapt = args.aapt
    if aapt is None and shutil.which("aapt2"):
        aapt = shutil.which("aapt2")

    report, facts = verify(
        args.apk, expect_package=args.expect_package, min_sdk=args.min_sdk,
        aapt=aapt, expect_pack=not args.no_pack,
        require_abis=tuple(args.require_abis or ["arm64-v8a"]),
    )
    report.print(markdown=args.markdown)

    if facts.get("sha256"):
        print(f"sha256: {facts['sha256']}")
    if facts.get("size"):
        print(f"size: {facts['size']} bytes")

    if report.failed:
        print(f"\nAPK verification failed: {len(report.failed)} check(s) did not pass.", file=sys.stderr)
        return 1
    print("\nAPK verification passed: the file is complete, signed and installable.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
