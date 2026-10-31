import importlib.util
from pathlib import Path
import struct
import tempfile
import unittest
from zipfile import ZIP_DEFLATED, ZipFile

TOOLS = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("patch_android_manifest_config", TOOLS / "patch_android_manifest_config.py")
manifest = importlib.util.module_from_spec(spec)
spec.loader.exec_module(manifest)


def length8(number):
    if number < 0x80:
        return bytes([number])
    return bytes([0x80 | (number >> 8), number & 0xFF])


def make_xml(*, utf8=True, target="com.godot.game.GodotApp", flags=manifest.GODOT_43_CONFIG,
             duplicate_target=False, include_config=True, raw_name=True):
    """Build a small compiled XML fixture, including an unrelated activity."""
    strings = [manifest.ANDROID_NAMESPACE, "activity", "name", "configChanges", target,
               "org.example.OtherActivity", "x" * 150, "متن فارسی"]
    raw_strings = bytearray()
    offsets = []
    for text in strings:
        offsets.append(len(raw_strings))
        encoded16 = text.encode("utf-16-le")
        if utf8:
            encoded = text.encode("utf-8")
            raw_strings.extend(length8(len(encoded16) // 2))
            raw_strings.extend(length8(len(encoded)))
            raw_strings.extend(encoded + b"\0")
        else:
            raw_strings.extend(struct.pack("<H", len(encoded16) // 2))
            raw_strings.extend(encoded16 + b"\0\0")
    raw_strings.extend(b"\0" * (-len(raw_strings) % 4))
    string_start = 28 + 4 * len(strings)
    pool_size = string_start + len(raw_strings)
    pool = struct.pack("<HHIIIIII", 1, 28, pool_size, len(strings), 0,
                       manifest.UTF8_FLAG if utf8 else 0, string_start, 0)
    pool += struct.pack("<" + "I" * len(offsets), *offsets) + raw_strings

    def attr(name_index, raw, value_type, data):
        return struct.pack("<IIIHBBI", 0, name_index, raw, 8, 0, value_type, data)

    def activity(name_index, config):
        attrs = [attr(2, name_index if raw_name else manifest.NO_INDEX, manifest.TYPE_STRING, name_index)]
        if config is not None:
            attrs.append(attr(3, manifest.NO_INDEX, manifest.TYPE_INT_HEX, config))
        size = 36 + 20 * len(attrs)
        node = struct.pack("<HHIII", manifest.START_ELEMENT_TYPE, 16, size, 1, manifest.NO_INDEX)
        node += struct.pack("<IIHHHHHH", manifest.NO_INDEX, 1, 20, 20, len(attrs), 0, 0, 0)
        node += b"".join(attrs)
        end = struct.pack("<HHIIIII", 0x0103, 16, 24, 1, manifest.NO_INDEX, manifest.NO_INDEX, 1)
        return node + end

    body = pool + activity(5, 0x80) + activity(4, flags if include_config else None)
    if duplicate_target:
        body += activity(4, flags)
    return struct.pack("<HHI", manifest.XML_TYPE, 8, 8 + len(body)) + body


class CompiledManifestTests(unittest.TestCase):
    def test_backports_flags_for_both_string_encodings_without_other_changes(self):
        for utf8 in (True, False):
            with self.subTest(utf8=utf8):
                raw = make_xml(utf8=utf8)
                offset, old = manifest.activity_config_offset(raw)
                self.assertEqual(old, 0x1FF0)
                updated = manifest.patch_manifest(raw)
                self.assertEqual(len(raw), len(updated))
                self.assertEqual(updated[:offset], raw[:offset])
                self.assertEqual(updated[offset + 4:], raw[offset + 4:])
                self.assertEqual(manifest.verify_manifest(updated), old | manifest.BACKPORTED_CONFIG)
                self.assertEqual(manifest.patch_manifest(updated), updated)

    def test_supports_short_activity_name_and_typed_string_without_raw_value(self):
        raw = make_xml(target=".GodotApp", raw_name=False)
        self.assertEqual(manifest.verify_manifest(manifest.patch_manifest(raw)),
                         manifest.GODOT_43_CONFIG | manifest.BACKPORTED_CONFIG)

    def test_rejects_unpatched_manifest_during_verification(self):
        with self.assertRaisesRegex(ValueError, "Missing Android"):
            manifest.verify_manifest(make_xml())

    def test_requires_exactly_one_target_with_expected_original_flags(self):
        for raw in (make_xml(target=".DifferentActivity"), make_xml(duplicate_target=True),
                    make_xml(include_config=False), make_xml(flags=0x80)):
            with self.subTest(size=len(raw)), self.assertRaises(ValueError):
                manifest.patch_manifest(raw)

    def test_rejects_truncated_or_invalid_documents(self):
        raw = make_xml()
        for bad in (b"", raw[:-1], b"wrong" + raw[5:], raw[:10] + b"\xff\xff" + raw[12:]):
            with self.subTest(size=len(bad)), self.assertRaises((ValueError, struct.error)):
                manifest.patch_manifest(bad)

    def test_rejects_invalid_attribute_value_type_and_string_index(self):
        raw = bytearray(make_xml())
        offset, _ = manifest.activity_config_offset(raw)
        raw[offset - 1] = manifest.TYPE_STRING
        with self.assertRaises(ValueError):
            manifest.patch_manifest(raw)
        raw = bytearray(make_xml())
        # The original target name string is referenced by its own attribute;
        # corrupt both the raw and typed string indexes.
        pos = raw.find(struct.pack("<IIIHBBI", 0, 2, 4, 8, 0, manifest.TYPE_STRING, 4))
        self.assertGreater(pos, 0)
        struct.pack_into("<I", raw, pos + 8, 9999)
        with self.assertRaises(ValueError):
            manifest.patch_manifest(raw)


class TemplateTests(unittest.TestCase):
    def test_patches_only_manifest_and_preserves_other_zip_entries(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source, output = root / "source.apk", root / "output.apk"
            with ZipFile(source, "w") as apk:
                apk.comment = b"template comment"
                apk.writestr("AndroidManifest.xml", make_xml(), compress_type=ZIP_DEFLATED)
                apk.writestr("lib/x86_64/libgodot_android.so", b"unaltered library", compress_type=ZIP_DEFLATED)
            manifest.patch_template(source, output)
            with ZipFile(source) as before, ZipFile(output) as after:
                self.assertEqual(after.comment, before.comment)
                self.assertEqual(after.namelist(), before.namelist())
                self.assertEqual(after.read("lib/x86_64/libgodot_android.so"), b"unaltered library")
                self.assertEqual(after.getinfo("AndroidManifest.xml").compress_type, ZIP_DEFLATED)
                manifest.verify_manifest(after.read("AndroidManifest.xml"))

    def test_bad_input_does_not_damage_existing_output_and_cannot_overwrite_input(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source, output = root / "source.apk", root / "output.apk"
            with ZipFile(source, "w") as apk:
                apk.writestr("AndroidManifest.xml", b"bad xml")
            output.write_bytes(b"keep me")
            with self.assertRaises(ValueError):
                manifest.patch_template(source, output)
            self.assertEqual(output.read_bytes(), b"keep me")
            with self.assertRaises(ValueError):
                manifest.patch_template(source, source)

    def test_rejects_duplicate_or_missing_manifest(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source, output = root / "source.apk", root / "output.apk"
            with ZipFile(source, "w") as apk:
                apk.writestr("other", b"data")
            with self.assertRaises(ValueError):
                manifest.patch_template(source, output)
            with self.assertWarns(UserWarning):
                with ZipFile(source, "w") as apk:
                    apk.writestr("AndroidManifest.xml", make_xml())
                    apk.writestr("AndroidManifest.xml", make_xml())
            with self.assertRaises(ValueError):
                manifest.patch_template(source, output)


if __name__ == "__main__":
    unittest.main()
