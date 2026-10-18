#!/usr/bin/env bash
# Automated device measurement helper for HK-013 (M-1).
# Usage: bash measure.sh > results.txt 2>&1
# Then paste results.txt contents into the chat.
set -u

echo "===== DEVICE INFO ====="
adb shell getprop ro.build.fingerprint
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk

echo ""
echo "===== COLD START ====="
adb shell am force-stop ir.dorehami
sleep 2
adb shell am start -W -n ir.dorehami/.MainActivity

echo ""
echo "===== GFXINFO (run a full match first, then this) ====="
adb shell dumpsys gfxinfo ir.dorehami

echo ""
echo "===== MEMINFO ====="
adb shell dumpsys meminfo ir.dorehami

echo ""
echo "===== DONE ====="
echo "Paste the above output into the chat."
