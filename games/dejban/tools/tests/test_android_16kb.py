import importlib.util
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

TOOLS = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("verify_elf_16kb", TOOLS / "verify_elf_16kb.py")
verify = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verify)


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


if __name__ == "__main__":
    unittest.main()
