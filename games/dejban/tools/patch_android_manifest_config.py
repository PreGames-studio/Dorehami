#!/usr/bin/env python3
"""Backport Godot's Android activity locale-change fix to a 4.3 APK template.

Godot 4.3's exported activity can be recreated on a locale/layout-direction
change while its native engine is still in the same process. The engine cannot
be safely reinitialized there. Godot upstream added those two configChanges
flags in https://github.com/godotengine/godot/pull/103419. A reproducible font
scale change has the same effect, so the game also handles that configuration.

The stock export template contains compiled Android XML, not text XML. This
script changes only the integer value of that one attribute, validates the
surrounding structure, and writes the new template atomically. Godot signs the
final export using the existing signing configuration; this script does not
sign any APK.
"""
from __future__ import annotations

from collections import Counter
from copy import copy
from pathlib import Path
import shutil
import struct
import sys
import tempfile
from zipfile import BadZipFile, ZipFile

XML_TYPE = 0x0003
STRING_POOL_TYPE = 0x0001
START_ELEMENT_TYPE = 0x0102
UTF8_FLAG = 0x00000100
NO_INDEX = 0xFFFFFFFF
TYPE_STRING = 0x03
TYPE_INT_DEC = 0x10
TYPE_INT_HEX = 0x11
ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
GODOT_ACTIVITIES = {".GodotApp", "com.godot.game.GodotApp"}
# Existing Godot 4.3 flags: orientation, keyboard, screen size, density, etc.
GODOT_43_CONFIG = 0x1FF0
# AOSP ActivityInfo constants. Locale/layoutDirection are upstream's backport;
# fontScale additionally prevents the same demonstrated recreation on text-size changes.
BACKPORTED_CONFIG = 0x0004 | 0x2000 | 0x40000000


def _u16(data: bytes, offset: int) -> int:
    if offset < 0 or offset + 2 > len(data):
        raise ValueError("Truncated Android XML")
    return struct.unpack_from("<H", data, offset)[0]


def _u32(data: bytes, offset: int) -> int:
    if offset < 0 or offset + 4 > len(data):
        raise ValueError("Truncated Android XML")
    return struct.unpack_from("<I", data, offset)[0]


def _chunk(data: bytes, offset: int, end: int) -> tuple[int, int, int]:
    if offset + 8 > end:
        raise ValueError("Truncated Android XML chunk header")
    kind, header_size, size = struct.unpack_from("<HHI", data, offset)
    if header_size < 8 or size < header_size or offset + size > end:
        raise ValueError("Invalid Android XML chunk size")
    return kind, header_size, size


def _length8(data: bytes, offset: int, end: int) -> tuple[int, int]:
    if offset >= end:
        raise ValueError("Truncated UTF-8 string length")
    value = data[offset]
    offset += 1
    if value & 0x80:
        if offset >= end:
            raise ValueError("Truncated UTF-8 string length")
        value = ((value & 0x7F) << 8) | data[offset]
        offset += 1
    return value, offset


def _length16(data: bytes, offset: int, end: int) -> tuple[int, int]:
    if offset + 2 > end:
        raise ValueError("Truncated UTF-16 string length")
    value = _u16(data, offset)
    offset += 2
    if value & 0x8000:
        if offset + 2 > end:
            raise ValueError("Truncated UTF-16 string length")
        value = ((value & 0x7FFF) << 16) | _u16(data, offset)
        offset += 2
    return value, offset


def _read_string_pool(data: bytes, start: int, header_size: int, size: int) -> list[str]:
    if header_size < 28:
        raise ValueError("Invalid Android XML string pool header")
    count, styles, flags, strings_start, _ = struct.unpack_from("<IIIII", data, start + 8)
    end = start + size
    offsets_start = start + header_size
    offsets_end = offsets_start + 4 * (count + styles)
    string_data = start + strings_start
    if offsets_end > end or string_data < offsets_end or string_data > end:
        raise ValueError("Invalid Android XML string pool offsets")
    strings: list[str] = []
    for index in range(count):
        pos = string_data + _u32(data, offsets_start + 4 * index)
        if pos >= end:
            raise ValueError("Android XML string outside its pool")
        if flags & UTF8_FLAG:
            char_len, pos = _length8(data, pos, end)
            byte_len, pos = _length8(data, pos, end)
            if pos + byte_len >= end or data[pos + byte_len] != 0:
                raise ValueError("Truncated Android XML UTF-8 string")
            try:
                text = data[pos:pos + byte_len].decode("utf-8")
            except UnicodeError as error:
                raise ValueError("Invalid Android XML UTF-8 string") from error
            if len(text.encode("utf-16-le")) // 2 != char_len:
                raise ValueError("Invalid Android XML UTF-8 string length")
        else:
            char_len, pos = _length16(data, pos, end)
            byte_len = 2 * char_len
            if pos + byte_len + 2 > end or data[pos + byte_len:pos + byte_len + 2] != b"\0\0":
                raise ValueError("Truncated Android XML UTF-16 string")
            try:
                text = data[pos:pos + byte_len].decode("utf-16-le")
            except UnicodeError as error:
                raise ValueError("Invalid Android XML UTF-16 string") from error
        strings.append(text)
    return strings


def _string(strings: list[str], index: int) -> str:
    if index >= len(strings):
        raise ValueError("Invalid Android XML string index")
    return strings[index]


def activity_config_offset(data: bytes) -> tuple[int, int]:
    """Return the location and value of GodotApp's unique configChanges flag."""
    kind, root_header, root_size = _chunk(data, 0, len(data))
    if kind != XML_TYPE or root_header != 8 or root_size != len(data):
        raise ValueError("Expected a complete compiled Android XML document")
    strings: list[str] | None = None
    found: list[tuple[int, int]] = []
    cursor = root_header
    while cursor < root_size:
        kind, header_size, size = _chunk(data, cursor, root_size)
        if kind == STRING_POOL_TYPE:
            if strings is not None:
                raise ValueError("Multiple Android XML string pools")
            strings = _read_string_pool(data, cursor, header_size, size)
        elif kind == START_ELEMENT_TYPE:
            if strings is None or header_size != 16 or size < 36:
                raise ValueError("Invalid Android XML start element")
            element_name = _string(strings, _u32(data, cursor + 20))
            attr_start = _u16(data, cursor + 24)
            attr_size = _u16(data, cursor + 26)
            attr_count = _u16(data, cursor + 28)
            attrs = cursor + 16 + attr_start
            if attr_start < 20 or attr_size < 20 or attrs + attr_size * attr_count > cursor + size:
                raise ValueError("Invalid Android XML attributes")
            if element_name == "activity":
                values: dict[str, tuple[int, int, int, int]] = {}
                for i in range(attr_count):
                    attr = attrs + i * attr_size
                    namespace = _u32(data, attr)
                    if namespace == NO_INDEX or _string(strings, namespace) != ANDROID_NAMESPACE:
                        continue
                    name = _string(strings, _u32(data, attr + 4))
                    if name not in {"name", "configChanges"}:
                        continue
                    if name in values or _u16(data, attr + 12) != 8 or data[attr + 14] != 0:
                        raise ValueError(f"Invalid or duplicate android:{name} attribute")
                    values[name] = (attr + 16, data[attr + 15], _u32(data, attr + 8), _u32(data, attr + 16))
                name_value = values.get("name")
                if name_value is not None:
                    _, value_type, raw_index, typed_value = name_value
                    if value_type != TYPE_STRING:
                        raise ValueError("Expected a string android:name")
                    name_index = typed_value if raw_index == NO_INDEX else raw_index
                    if _string(strings, name_index) in GODOT_ACTIVITIES:
                        config = values.get("configChanges")
                        if config is None or config[1] not in (TYPE_INT_DEC, TYPE_INT_HEX):
                            raise ValueError("Missing or invalid GodotApp configChanges")
                        found.append((config[0], config[3]))
        cursor += size
    if cursor != root_size or len(found) != 1:
        raise ValueError("Expected exactly one GodotApp activity with configChanges")
    offset, flags = found[0]
    if flags & GODOT_43_CONFIG != GODOT_43_CONFIG:
        raise ValueError(f"Unexpected Godot 4.3 activity flags: 0x{flags:08x}")
    return offset, flags


def patch_manifest(data: bytes) -> bytes:
    offset, old = activity_config_offset(data)
    result = bytearray(data)
    struct.pack_into("<I", result, offset, old | BACKPORTED_CONFIG)
    return bytes(result)


def verify_manifest(data: bytes) -> int:
    _, flags = activity_config_offset(data)
    if flags & BACKPORTED_CONFIG != BACKPORTED_CONFIG:
        raise ValueError(f"Missing Android locale/layout-direction/font-scale flags: 0x{flags:08x}")
    return flags


def patch_template(template: Path, output: Path) -> None:
    if template.resolve() == output.resolve():
        raise ValueError("The output must differ from the input template")
    with ZipFile(template) as source:
        items = source.infolist()
        counts = Counter(item.filename for item in items)
        duplicates = [name for name, count in counts.items() if count != 1]
        if duplicates or counts.get("AndroidManifest.xml") != 1:
            raise ValueError("Template has duplicate entries or no unique AndroidManifest.xml")
        manifest = patch_manifest(source.read("AndroidManifest.xml"))
        verify_manifest(manifest)
        with tempfile.NamedTemporaryFile(dir=output.parent, prefix=".dezhban-manifest-", delete=False) as temporary:
            temp_path = Path(temporary.name)
        try:
            with ZipFile(temp_path, "w", allowZip64=True) as result:
                result.comment = source.comment
                for item in items:
                    with result.open(copy(item), "w") as destination:
                        if item.filename == "AndroidManifest.xml":
                            destination.write(manifest)
                        else:
                            with source.open(item) as original:
                                shutil.copyfileobj(original, destination)
            temp_path.replace(output)
        finally:
            temp_path.unlink(missing_ok=True)


def main() -> int:
    try:
        if len(sys.argv) == 3 and sys.argv[1] == "--verify":
            with ZipFile(Path(sys.argv[2])) as apk:
                if apk.namelist().count("AndroidManifest.xml") != 1:
                    raise ValueError("APK has no unique AndroidManifest.xml")
                flags = verify_manifest(apk.read("AndroidManifest.xml"))
            print(f"GodotApp Android configuration flags verified: 0x{flags:08x}.")
        elif len(sys.argv) == 3:
            patch_template(Path(sys.argv[1]), Path(sys.argv[2]))
            print("Updated Godot activity locale, layout-direction, and font-scale handling.")
        else:
            print(f"Usage: {sys.argv[0]} TEMPLATE.apk OUTPUT.apk | --verify APK", file=sys.stderr)
            return 2
    except (BadZipFile, OSError, ValueError, struct.error) as error:
        print(f"Could not process AndroidManifest.xml: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
