import importlib.util
import os
from pathlib import Path
import shutil
import struct
import subprocess
import tempfile
import unittest
from zipfile import ZIP_DEFLATED, ZIP_STORED, ZipFile, ZipInfo

TOOLS = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("verify_elf_16kb", TOOLS / "verify_elf_16kb.py")
verify = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verify)


def with_abi(binary, abi):
    """Change only e_machine to exercise validation, not to simulate real Android code."""
    binary = bytearray(binary)
    binary[18:20] = struct.pack("<H", verify.ABI_MACHINES[abi])
    return bytes(binary)


class ElfAlignmentTests(unittest.TestCase):
    def test_parses_and_accepts_16k_and_64k_segments(self):
        output = """\
  LOAD  0x000000 0x000000 0x000000 0x001234 0x001234 R   0x4000
  LOAD  0x004000 0x014000 0x014000 0x004000 0x004000 R E 0x10000
"""
        segments = verify.load_segments(output)
        self.assertEqual(len(segments), 2)
        verify.validate_segments(segments)

    def test_rejects_4k_segment(self):
        with self.assertRaises(ValueError):
            verify.validate_segments([(0, 0, 0x1000)])

    def test_rejects_non_power_of_two_alignment(self):
        with self.assertRaises(ValueError):
            verify.validate_segments([(0, 0, 0x6000)])

    def test_rejects_noncongruent_offset(self):
        with self.assertRaises(ValueError):
            verify.validate_segments([(0x1000, 0, 0x4000)])

    def test_rejects_invalid_64k_congruence_even_if_16k_aligned(self):
        with self.assertRaises(ValueError):
            verify.validate_segments([(0x4000, 0, 0x10000)])

    def test_checks_elf_class_type_and_abi(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "lib.so"
            header = bytearray(struct.pack("<16sHHI", b"\x7fELF\x02\x01\x01", 3, 183, 1))
            path.write_bytes(header)
            verify.validate_elf_identity(path, "arm64-v8a")
            with self.assertRaises(ValueError):
                verify.validate_elf_identity(path, "x86_64")
            header[4] = 1
            path.write_bytes(header)
            with self.assertRaises(ValueError):
                verify.validate_elf_identity(path, "arm64-v8a")
            header[4] = 2
            header[16:18] = struct.pack("<H", 2)
            path.write_bytes(header)
            with self.assertRaises(ValueError):
                verify.validate_elf_identity(path, "arm64-v8a")
            path.write_bytes(b"not an elf")
            with self.assertRaises(ValueError):
                verify.validate_elf_identity(path, "arm64-v8a")

    def test_requires_load_segments(self):
        with self.assertRaises(ValueError):
            verify.load_segments("not an ELF program header")

    def test_rejects_malformed_segment(self):
        with self.assertRaises(ValueError):
            verify.load_segments("LOAD 0x0 not-a-number 0x0 0x1 0x1 R 0x4000")

    @unittest.skipUnless(shutil.which("cc") and shutil.which("readelf"), "compiler and readelf required")
    def test_real_shared_objects(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "sample.c"
            source.write_text("int example(void) { return 42; }\n")
            for page_size, expected in ((4096, False), (16384, True)):
                output = root / f"lib{page_size}.so"
                subprocess.run(
                    ["cc", "-shared", "-fPIC", f"-Wl,-z,max-page-size={page_size}", str(source), "-o", str(output)],
                    check=True,
                )
                result = subprocess.run(["python3", str(TOOLS / "verify_elf_16kb.py"), str(output)], capture_output=True)
                self.assertEqual(result.returncode == 0, expected)


@unittest.skipUnless(shutil.which("cc") and shutil.which("readelf"), "compiler and readelf required")
class TemplateReplacementTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.template = self.root / "template.zip"
        self.libs = self.root / "libs"
        self.output = self.root / "result.zip"
        self.names = [
            f"lib/{abi}/{name}"
            for abi in ("arm64-v8a", "x86_64")
            for name in ("libgodot_android.so", "libc++_shared.so")
        ]
        source = self.root / "sample.c"
        source.write_text("int f(void) { return 1; }\n")
        binary = self.root / "aligned.so"
        subprocess.run(["cc", "-shared", "-fPIC", "-Wl,-z,max-page-size=16384", str(source), "-o", str(binary)], check=True)
        for name in self.names:
            file = self.libs / name.removeprefix("lib/")
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_bytes(with_abi(binary.read_bytes(), name.split("/")[1]))
        with ZipFile(self.template, "w") as z:
            for name in self.names:
                z.writestr(name, b"old", compress_type=ZIP_DEFLATED)
            z.writestr("lib/armeabi-v7a/libgodot_android.so", b"unchanged", compress_type=ZIP_STORED)
            z.writestr("AndroidManifest.xml", b"manifest", compress_type=ZIP_DEFLATED)

    def run_replace(self):
        return subprocess.run(
            ["python3", str(TOOLS / "replace_android_template_libraries.py"), str(self.template), str(self.libs), str(self.output)],
            capture_output=True,
        )

    def test_only_requested_libraries_change(self):
        result = self.run_replace()
        self.assertEqual(result.returncode, 0, result.stderr)
        with ZipFile(self.output) as z:
            for name in self.names:
                self.assertEqual(z.read(name), (self.libs / name.removeprefix("lib/")).read_bytes())
                self.assertEqual(z.getinfo(name).compress_type, ZIP_DEFLATED)
            self.assertEqual(z.read("AndroidManifest.xml"), b"manifest")
            self.assertEqual(z.read("lib/armeabi-v7a/libgodot_android.so"), b"unchanged")
            self.assertEqual(z.getinfo("lib/armeabi-v7a/libgodot_android.so").compress_type, ZIP_STORED)

    def test_missing_rebuilt_library_fails(self):
        (self.libs / self.names[0].removeprefix("lib/")).unlink()
        self.assertNotEqual(self.run_replace().returncode, 0)
        self.assertFalse(self.output.exists())

    def test_unexpected_64_bit_library_fails(self):
        with ZipFile(self.template, "a") as z:
            z.writestr("lib/arm64-v8a/libunexpected.so", b"unsafe")
        self.assertNotEqual(self.run_replace().returncode, 0)
        self.assertFalse(self.output.exists())

    def test_duplicate_entry_fails(self):
        import warnings
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            with ZipFile(self.template, "a") as z:
                z.writestr(self.names[0], b"duplicate")
        self.assertNotEqual(self.run_replace().returncode, 0)
        self.assertFalse(self.output.exists())

    def test_rejects_wrong_abi_and_preserves_existing_output(self):
        file = self.libs / self.names[0].removeprefix("lib/")
        file.write_bytes(with_abi(file.read_bytes(), "x86_64"))
        self.output.write_bytes(b"keep existing output")
        result = self.run_replace()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn(b"Incorrect ELF architecture", result.stderr)
        self.assertEqual(self.output.read_bytes(), b"keep existing output")

    def test_rejects_old_4k_replacement(self):
        source = self.root / "sample.c"
        file = self.libs / self.names[0].removeprefix("lib/")
        subprocess.run(["cc", "-shared", "-fPIC", "-Wl,-z,max-page-size=4096", str(source), "-o", str(file)], check=True)
        file.write_bytes(with_abi(file.read_bytes(), "arm64-v8a"))
        result = self.run_replace()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn(b"LOAD alignment", result.stderr)
        self.assertFalse(self.output.exists())


@unittest.skipUnless(shutil.which("cc") and shutil.which("readelf"), "compiler and readelf required")
class ApkValidationTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.apk = self.root / "package.zip"
        self.aapt = self.root / "aapt"
        self.aapt.write_text('#!/bin/sh\necho "A: android:extractNativeLibs=(type 0x12)0xffffffff"\n')
        self.aapt.chmod(0o755)
        c = self.root / "sample.c"
        c.write_text("int f(void) { return 1; }\n")
        self.binaries = {}
        for size in (4096, 16384):
            file = self.root / f"lib{size}.so"
            subprocess.run(["cc", "-shared", "-fPIC", f"-Wl,-z,max-page-size={size}", str(c), "-o", str(file)], check=True)
            self.binaries[size] = file.read_bytes()

    def package(self, size=16384, compression=ZIP_DEFLATED, omit=None, wrong_abi=None, align_stored=False):
        with ZipFile(self.apk, "w") as z:
            z.writestr("AndroidManifest.xml", b"dummy")
            for abi in ("arm64-v8a", "x86_64"):
                for name in ("libgodot_android.so", "libc++_shared.so"):
                    item = f"lib/{abi}/{name}"
                    if item == omit:
                        continue
                    data = with_abi(self.binaries[size], "x86_64" if item == wrong_abi else abi)
                    info = ZipInfo(item)
                    info.compress_type = compression
                    if align_stored:
                        padding = (-(z.fp.tell() + 30 + len(item.encode()))) % 16384
                        if padding < 4:
                            padding += 16384
                        info.extra = struct.pack("<HH", 0xD935, padding - 4) + b"\0" * (padding - 4)
                    z.writestr(info, data)

    def run_check(self):
        return subprocess.run(
            ["python3", str(TOOLS / "verify_apk_16kb.py"), str(self.apk), str(self.aapt)], capture_output=True,
        )

    def test_accepts_compressed_16k_libraries_when_extraction_enabled(self):
        self.package()
        result = self.run_check()
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_rejects_old_4k_libraries(self):
        self.package(size=4096)
        self.assertNotEqual(self.run_check().returncode, 0)

    def test_rejects_missing_library(self):
        self.package(omit="lib/arm64-v8a/libgodot_android.so")
        self.assertNotEqual(self.run_check().returncode, 0)

    def test_rejects_library_for_the_wrong_abi(self):
        self.package(wrong_abi="lib/arm64-v8a/libgodot_android.so")
        result = self.run_check()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn(b"Incorrect ELF architecture", result.stderr)

    def test_rejects_compressed_libraries_without_extraction(self):
        self.package()
        self.aapt.write_text('#!/bin/sh\necho "A: android:extractNativeLibs=(type 0x12)0x0"\n')
        self.assertNotEqual(self.run_check().returncode, 0)

    def test_rejects_unaligned_stored_libraries(self):
        self.package(compression=ZIP_STORED)
        self.assertNotEqual(self.run_check().returncode, 0)

    def test_accepts_aligned_stored_libraries_without_extraction(self):
        self.package(compression=ZIP_STORED, align_stored=True)
        self.aapt.write_text('#!/bin/sh\necho "A: android:extractNativeLibs=(type 0x12)0x0"\n')
        result = self.run_check()
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_rejects_duplicate_apk_entries(self):
        import warnings
        self.package()
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            with ZipFile(self.apk, "a") as z:
                z.writestr("AndroidManifest.xml", b"duplicate")
        result = self.run_check()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn(b"Duplicate ZIP entries", result.stderr)


@unittest.skipUnless(shutil.which("cc") and hasattr(os, "sysconf") and os.sysconf("SC_PAGE_SIZE") in (4096, 16384), "supported host compiler and page size required")
class NativeLoaderTests(unittest.TestCase):
    def test_loads_sibling_runtime_before_library(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            probe = root / "probe"
            subprocess.run(["cc", "-Wall", "-Wextra", "-Werror", str(TOOLS / "android_16kb_load_probe.c"), "-ldl", "-o", str(probe)], check=True)
            runtime_source = root / "runtime.c"
            runtime_source.write_text("int from_runtime(void) { return 7; }\n")
            godot_source = root / "godot.c"
            godot_source.write_text("extern int from_runtime(void); int from_godot(void) { return from_runtime(); }\n")
            runtime = root / "libc++_shared.so"
            godot = root / "libgodot_android.so"
            for source, output in ((runtime_source, runtime), (godot_source, godot)):
                subprocess.run(["cc", "-shared", "-fPIC", str(source), "-o", str(output)], check=True)
            command = [str(probe), "./libgodot_android.so", str(os.sysconf("SC_PAGE_SIZE"))]
            result = subprocess.run(command, cwd=root, capture_output=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            runtime.unlink()
            result = subprocess.run(command, cwd=root, capture_output=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn(b"C++ runtime dlopen failed", result.stderr)


class LoaderHarnessTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.bin = self.root / "bin"
        self.bin.mkdir()
        adb = self.bin / "adb"
        adb.write_text('''#!/bin/sh
echo "$*" >> "$ADB_LOG"
case "$*" in
  'get-serialno') echo "$MOCK_SERIAL" ;;
  'shell getprop ro.kernel.qemu') echo 1 ;;
  'shell getprop ro.product.cpu.abi') echo x86_64 ;;
  'shell getconf PAGESIZE') echo "$MOCK_PAGE_SIZE" ;;
  'shell getprop ro.product.cpu.abilist64') echo x86_64,arm64-v8a ;;
  'shell getprop ro.enable.native.bridge.exec') echo "$MOCK_BRIDGE" ;;
  'shell cd '*/old*) exit "$MOCK_OLD_STATUS" ;;
esac
''')
        adb.chmod(0o755)
        self.probe = self.root / "probe"
        self.probe.write_text("probe")
        for directory in (self.root / "old", self.root / "new"):
            directory.mkdir()
            for name in ("libgodot_android.so", "libc++_shared.so"):
                (directory / name).write_text("fake library for mocked adb")
        self.log = self.root / "adb.log"

    def run_check(self, page="16384", expected="16384", abi="x86_64", serial="emulator-5554", bridge="1", old_status="1"):
        env = os.environ | {
            "PATH": f"{self.bin}:{os.environ['PATH']}", "ADB_LOG": str(self.log),
            "MOCK_PAGE_SIZE": page, "MOCK_SERIAL": serial, "MOCK_BRIDGE": bridge,
            "MOCK_OLD_STATUS": old_status,
        }
        return subprocess.run([
            "bash", str(TOOLS / "test_android_16kb_loader.sh"), str(self.probe),
            str(self.root / "old"), str(self.root / "new"), expected, abi,
        ], cwd=self.root, env=env, capture_output=True)

    def test_rebuilt_load_proceeds_after_expected_16k_control_failure(self):
        result = self.run_check()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("/new &&", self.log.read_text())

    def test_4k_control_must_load_before_testing_rebuilt_library(self):
        result = self.run_check(page="4096", expected="4096", old_status="1")
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("/new &&", self.log.read_text())

    def test_refuses_real_device_and_wrong_page_size(self):
        for args in ({"serial": "physical-device"}, {"page": "4096"}):
            with self.subTest(args=args):
                self.log.unlink(missing_ok=True)
                result = self.run_check(**args)
                self.assertNotEqual(result.returncode, 0)
                self.assertNotIn("rm -rf", self.log.read_text())

    def test_translated_arm_requires_native_bridge(self):
        result = self.run_check(abi="arm64-v8a", bridge="0")
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("rm -rf", self.log.read_text())
        self.log.unlink()
        result = self.run_check(abi="arm64-v8a", bridge="1")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn(b"translation layer", result.stdout)


class DevicePageSizeTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.apk = self.root / "game.apk"
        self.apk.write_bytes(b"fake apk for mocked adb")
        self.bin = self.root / "bin"
        self.bin.mkdir()
        adb = self.bin / "adb"
        adb.write_text('''#!/bin/sh
echo "$*" >> "$ADB_LOG"
case "$*" in
  'shell getconf PAGESIZE') echo "$MOCK_PAGE_SIZE" ;;
  'shell am start'*) echo 'Status: ok' ;;
  'shell pidof'*) echo '1234' ;;
  'shell getprop ro.build.version.sdk') echo '35' ;;
esac
''')
        adb.chmod(0o755)
        sleep = self.bin / "sleep"
        sleep.write_text("#!/bin/sh\nexit 0\n")
        sleep.chmod(0o755)
        self.log = self.root / "adb.log"

    def run_check(self, actual, expected, abi=""):
        env = os.environ | {
            "PATH": f"{self.bin}:{os.environ['PATH']}",
            "ADB_LOG": str(self.log),
            "MOCK_PAGE_SIZE": actual,
        }
        return subprocess.run(
            ["bash", str(TOOLS / "verify_standalone_apk_on_device.sh"), str(self.apk), abi, expected],
            cwd=self.root, env=env, capture_output=True,
        )

    def test_expected_page_size_still_installs_and_checks_requested_abi(self):
        result = self.run_check("16384", "16384", "arm64-v8a")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn(f"install --abi arm64-v8a {self.apk}", self.log.read_text())

    def test_wrong_page_size_fails_before_installation(self):
        result = self.run_check("4096", "16384")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn(b"Expected page size 16384", result.stderr)
        self.assertNotIn("install", self.log.read_text())


if __name__ == "__main__":
    unittest.main()
