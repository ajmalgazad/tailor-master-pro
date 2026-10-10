#!/bin/bash
# Runs inside the Android emulator job: installs the app + tests, runs them, collects screenshots.
set -x
mkdir -p device-out
adb wait-for-device
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r pk.tailormaster.pro.test/androidx.test.runner.AndroidJUnitRunner > device-out/instrument.txt 2>&1
cat device-out/instrument.txt | tail -60
adb pull /sdcard/Android/data/pk.tailormaster.pro/files/shots device-out/ || true
adb logcat -d -v brief AndroidRuntime:E TestRunner:I System.err:W '*:S' > device-out/logcat.txt || true
adb shell getprop ro.build.version.release > device-out/android-version.txt
adb shell getprop ro.product.model >> device-out/android-version.txt
exit 0
