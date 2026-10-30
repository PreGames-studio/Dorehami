import os
from pathlib import Path
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "diagnose_existing_apk_install.sh"


class ExistingApkInstallTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.calls = self.root / "calls.txt"
        self.old = self.root / "old.apk"
        self.new = self.root / "new.apk"
        self.old.write_bytes(b"old")
        self.new.write_bytes(b"new")
        adb = self.root / "adb"
        adb.write_text(
            """#!/bin/sh
printf 'adb %s\\n' "$*" >> "$CALLS"
case "$*" in
  'get-serialno') echo "${SERIAL:-emulator-5554}" ;;
  'shell getprop ro.kernel.qemu') echo "${QEMU:-1}" ;;
  'shell pm list packages com.gf2227.dezhban')
    if [ "${PRESENT:-0}" = 1 ]; then echo 'package:com.gf2227.dezhban'; fi ;;
  'install -r '*)
    if [ "${REJECT_UPDATE:-0}" = 1 ]; then
      echo 'Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]' >&2
      exit 1
    fi
    echo Success ;;
  install*|uninstall*) echo Success ;;
  *) echo "Unexpected adb invocation: $*" >&2; exit 2 ;;
esac
""",
            encoding="utf-8",
        )
        adb.chmod(0o755)
        bash = self.root / "bash"
        bash.write_text(
            "#!/bin/sh\nprintf 'ui %s\\n' \"$*\" >> \"$CALLS\"\nexit 0\n",
            encoding="utf-8",
        )
        bash.chmod(0o755)

    def run_probe(self, **extra):
        env = os.environ.copy()
        env.update(PATH=f"{self.root}:{env['PATH']}", CALLS=str(self.calls), **extra)
        result = subprocess.run(
            ["/bin/bash", str(SCRIPT), str(self.old), str(self.new), "16384"],
            env=env,
            capture_output=True,
            text=True,
        )
        calls = self.calls.read_text(encoding="utf-8") if self.calls.exists() else ""
        return result, calls

    def test_rejects_physical_device_without_install_or_uninstall(self):
        result, calls = self.run_probe(SERIAL="physical-123", QEMU="0")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Refusing", result.stderr)
        self.assertNotIn("adb install ", calls)
        self.assertNotIn("adb uninstall ", calls)

    def test_refuses_to_modify_emulator_with_preexisting_package(self):
        result, calls = self.run_probe(PRESENT="1")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("existing", result.stderr)
        self.assertNotIn("adb uninstall ", calls)

    def test_reports_rejected_update_then_checks_fresh_visible_install(self):
        result, calls = self.run_probe(REJECT_UPDATE="1")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("INSTALL_FAILED_UPDATE_INCOMPATIBLE", result.stdout)
        self.assertIn("Update rejected", result.stdout)
        self.assertIn("adb uninstall com.gf2227.dezhban", calls)
        self.assertIn("ui ", calls)
        self.assertIn("diagnose_apk_installer_ui.sh", calls)
        self.assertIn("16384", calls)

    def test_reports_accepted_update(self):
        result, calls = self.run_probe()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("Update accepted", result.stdout)
        self.assertIn("ui ", calls)


if __name__ == "__main__":
    unittest.main()
