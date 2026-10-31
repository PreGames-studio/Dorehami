import os
from pathlib import Path
import subprocess
import tempfile
import unittest

TOOLS = Path(__file__).resolve().parents[1]
SCRIPT = TOOLS / "diagnose_android_recreation.sh"

MOCK_ADB = r'''#!/bin/bash
printf '%s\n' "$*" >> "$MOCK_LOG"
case "$*" in
  'get-serialno') echo "${MOCK_SERIAL:-emulator-5554}" ;;
  'shell getprop ro.kernel.qemu') echo "${MOCK_QEMU:-1}" ;;
  'shell getconf PAGESIZE') echo "${MOCK_PAGE:-4096}" ;;
  'shell pm list packages com.gf2227.dezhban')
    if [[ ${MOCK_INSTALLED:-0} == 1 ]]; then echo 'package:com.gf2227.dezhban'; fi ;;
  'shell am start '* ) printf 'Status: ok\n' ;;
  'shell pidof com.gf2227.dezhban')
    if [[ -f "$MOCK_STATE/died" ]]; then exit 1; fi
    echo 1234 ;;
  'shell cmd locale set-app-locales '* | 'shell settings put system font_scale 1.2')
    if [[ ${MOCK_DIE:-0} == 1 ]]; then touch "$MOCK_STATE/died"; fi ;;
esac
exit 0
'''


class RecreationHarnessTests(unittest.TestCase):
    def run_script(self, scenario="locale", rounds="3", **overrides):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            adb = root / "adb"
            adb.write_text(MOCK_ADB)
            adb.chmod(0o755)
            sleep = root / "sleep"
            sleep.write_text("#!/bin/sh\nexit 0\n")
            sleep.chmod(0o755)
            apk = root / "game.apk"
            apk.write_bytes(b"test apk")
            log = root / "adb.log"
            env = dict(os.environ, PATH=f"{root}:{os.environ['PATH']}", MOCK_LOG=str(log), MOCK_STATE=str(root), **overrides)
            result = subprocess.run(["bash", str(SCRIPT), str(apk), "4096", scenario, rounds],
                                    env=env, text=True, capture_output=True, check=False)
            return result, log.read_text() if log.exists() else ""

    def test_three_locale_changes_keep_the_same_process(self):
        result, log = self.run_script()
        self.assertEqual(result.returncode, 0, result.stderr)
        changes = [line for line in log.splitlines() if "set-app-locales" in line]
        self.assertEqual(len(changes), 3)
        self.assertTrue(changes[0].endswith("fa-IR"))
        self.assertTrue(changes[1].endswith("en-US"))
        self.assertTrue(changes[2].endswith("fa-IR"))
        self.assertIn("survived locale change 3/3", result.stdout)

    def test_font_changes_are_restored_after_success_and_failure(self):
        result, log = self.run_script("font")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(log.count("shell settings put system font_scale 1.2"), 2)
        self.assertIn("shell settings put system font_scale 0.9", log)
        self.assertIn("shell settings put system font_scale 1.0", log)
        result, log = self.run_script("font", MOCK_DIE="1")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("shell settings put system font_scale 1.0", log)

    def test_refuses_a_physical_device_or_wrong_page_size_before_install(self):
        for overrides in ({"MOCK_SERIAL": "physical-serial"}, {"MOCK_QEMU": "0"}, {"MOCK_PAGE": "16384"}):
            with self.subTest(overrides=overrides):
                result, log = self.run_script(**overrides)
                self.assertEqual(result.returncode, 2)
                self.assertNotIn("install ", log)

    def test_refuses_to_modify_an_existing_installation(self):
        result, log = self.run_script(MOCK_INSTALLED="1")
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("install ", log)
        self.assertNotIn("set-app-locales", log)

    def test_invalid_rounds_rejected_before_adb(self):
        result, log = self.run_script(rounds="4")
        self.assertEqual(result.returncode, 2)
        self.assertEqual(log, "")

    def test_dying_process_causes_failure(self):
        result, log = self.run_script(MOCK_DIE="1")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("did not survive", result.stderr)
        self.assertEqual(log.count("set-app-locales"), 1)


if __name__ == "__main__":
    unittest.main()
