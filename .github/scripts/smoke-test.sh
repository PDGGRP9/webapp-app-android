#!/usr/bin/env bash
set -euo pipefail

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
PACKAGE="com.pdg.braceletconnecte"

adb install -r "$APK_PATH"
adb logcat -c
adb shell am start -n "${PACKAGE}/.MainActivity"
sleep 8

if adb logcat -d | grep -q "FATAL EXCEPTION.*${PACKAGE}"; then
  echo "App crashed on launch:"
  adb logcat -d | grep -A 30 "FATAL EXCEPTION.*${PACKAGE}"
  exit 1
fi

echo "App launched without crashing."
