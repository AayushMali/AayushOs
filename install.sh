#!/bin/sh
# Builds, installs, and provisions Aayush OS (dev.aayush.os) on Pixel 7 via ADB
set -e
cd "$(dirname "$0")"

sh ./build.sh

echo "Installing out/aayush-os.apk onto Pixel 7..."
adb install -r out/aayush-os.apk

echo "Provisioning ADB-privileged permissions for dev.aayush.os..."
adb shell pm grant dev.aayush.os android.permission.WRITE_SECURE_SETTINGS 2>/dev/null || true
adb shell pm grant dev.aayush.os android.permission.READ_LOGS 2>/dev/null || true
adb shell pm grant dev.aayush.os android.permission.DUMP 2>/dev/null || true
adb shell pm grant dev.aayush.os android.permission.POST_NOTIFICATIONS 2>/dev/null || true
adb shell pm grant dev.aayush.os android.permission.READ_PHONE_STATE 2>/dev/null || true
adb shell pm grant dev.aayush.os android.permission.CAMERA 2>/dev/null || true
adb shell appops set dev.aayush.os SYSTEM_ALERT_WINDOW allow 2>/dev/null || true
adb shell appops set dev.aayush.os GET_USAGE_STATS allow 2>/dev/null || true
adb shell appops set dev.aayush.os WRITE_SETTINGS allow 2>/dev/null || true
adb shell appops set dev.aayush.os SCHEDULE_EXACT_ALARM allow 2>/dev/null || true
adb shell cmd notification allow_listener dev.aayush.os/dev.aayush.os.NowBarNotificationListener 2>/dev/null || true
adb shell settings put secure enabled_accessibility_services dev.aayush.os/dev.aayush.os.NowBarAccessibilityService 2>/dev/null || true
adb shell settings put secure accessibility_enabled 1 2>/dev/null || true

echo "Launching Aayush OS on Pixel 7..."
adb shell am start -n dev.aayush.os/.MainActivity
