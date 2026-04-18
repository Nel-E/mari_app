# Windows Device Visual Testing Guide (Agent Runbook)

Use this guide to run visual tests for the Mari app from Linux by controlling a Windows-connected Android device over SSH + ADB.

## Scope

This guide covers:
- Syncing the current Linux codebase to a Windows workspace.
- Building the latest APK on the Windows host.
- Installing the APK to a target emulator/device.
- Navigating screens with ADB.
- Capturing evidence (screenshots + UI XML).
- Reporting pass/fail results in a reproducible way.

Mandatory Windows temp root policy:
- All agent-created temporary Windows workspaces must be under `M:\temp_android`.
- Valid workspace for this project: `M:\temp_android\mari_app`.
- Creating temp folders directly under `M:\` outside `M:\temp_android` is not allowed.

## Prerequisites

- Linux workspace has latest source changes at `/media/code/mari_app`.
- SSH access to Windows host (`marilinda@192.168.1.100`).
- Windows host has Android SDK and Gradle available.
- ADB available on Windows host at:
  - `M:\.android-studio\Sdk\platform-tools\adb.exe`
- Target emulator/device is visible in `adb devices -l`.

## 1. Sync local code to Windows build workspace

The Windows drive M: is locally mounted at `/media/nelson/usb1-1T/driveM`. Sync directly to the
local mount — no SSH required for this step.

```bash
# Clear stale workspace and recreate
rm -rf /media/nelson/usb1-1T/driveM/temp_android/mari_app
mkdir -p /media/nelson/usb1-1T/driveM/temp_android/mari_app

# Sync from Linux source
tar --exclude='.gradle' --exclude='app/build' --exclude='wear/build' \
    --exclude='shared/build' --exclude='__pycache__' \
    -cf - -C /media/code mari_app \
| tar -xf - -C /media/nelson/usb1-1T/driveM/temp_android
```

The workspace on Windows will be `M:\temp_android\mari_app\`.

## 2. Configure SDK path for Gradle

Write `local.properties` directly to the local mount:

```bash
echo 'sdk.dir=M:/.android-studio/Sdk' > /media/nelson/usb1-1T/driveM/temp_android/mari_app/local.properties
```

## 3. Build APK on Windows host

```bash
ssh marilinda@192.168.1.100 "cmd /c \"cd /d M:\\temp_android\\mari_app && gradlew.bat --stop && gradlew.bat app:assembleDebug --no-daemon --max-workers=1 --rerun-tasks\""
```

APK output path:
- `M:\temp_android\mari_app\app\build\outputs\apk\debug\app-debug.apk`

## 4. Select test target

List connected targets:

```bash
ssh marilinda@192.168.1.100 "cmd /c \"\"M:\\.android-studio\\Sdk\\platform-tools\\adb.exe\" devices -l\""
```

Pick the target serial that must be tested, for example:
- `192.168.1.120:38545`

If the device is not listed, connect it first:

```bash
ssh marilinda@192.168.1.100 "cmd /c \"\"M:\\.android-studio\\Sdk\\platform-tools\\adb.exe\" connect 192.168.1.120:38545\""
```

## 5. Install latest APK

```bash
ssh marilinda@192.168.1.100 "cmd /c \"\"M:\\.android-studio\\Sdk\\platform-tools\\adb.exe\" -s \"<TARGET_SERIAL>\" install -r \"M:\\temp_android\\mari_app\\app\\build\\outputs\\apk\\debug\\app-debug.apk\"\""
```

If signatures mismatch:

```bash
ssh marilinda@192.168.1.100 "cmd /c \"\"M:\\.android-studio\\Sdk\\platform-tools\\adb.exe\" -s \"<TARGET_SERIAL>\" uninstall com.mari.app\""
ssh marilinda@192.168.1.100 "cmd /c \"\"M:\\.android-studio\\Sdk\\platform-tools\\adb.exe\" -s \"<TARGET_SERIAL>\" install \"M:\\temp_android\\mari_app\\app\\build\\outputs\\apk\\debug\\app-debug.apk\"\""
```

## 6. Launch app and run visual checks

Launch:

```bash
ssh marilinda@192.168.1.100 "cmd /c \"\"M:\\.android-studio\\Sdk\\platform-tools\\adb.exe\" -s \"<TARGET_SERIAL>\" shell am start -n com.mari.app/.MainActivity\""
```

Capture current UI hierarchy:

```bash
ssh marilinda@192.168.1.100 "cmd /c \"\"M:\\.android-studio\\Sdk\\platform-tools\\adb.exe\" -s \"<TARGET_SERIAL>\" exec-out uiautomator dump /dev/tty\""
```

Tap interactions (example):

```bash
ssh marilinda@192.168.1.100 "powershell -NoProfile -Command \"
& 'M:\\.android-studio\\Sdk\\platform-tools\\adb.exe' -s '<TARGET_SERIAL>' shell input tap 85 138
Start-Sleep -Milliseconds 900
& 'M:\\.android-studio\\Sdk\\platform-tools\\adb.exe' -s '<TARGET_SERIAL>' shell input tap 300 935
\""
```

Use UI dump text assertions to confirm expected labels are present.

## 7. Capture evidence artifacts

Screenshot:

```bash
ssh marilinda@192.168.1.100 "cmd /c \"\"M:\\.android-studio\\Sdk\\platform-tools\\adb.exe\" -s \"<TARGET_SERIAL>\" exec-out screencap -p\"" > docs/evidence/screenshots/visual_test_<name>.png
```

UI XML:

```bash
ssh marilinda@192.168.1.100 "cmd /c \"\"M:\\.android-studio\\Sdk\\platform-tools\\adb.exe\" -s \"<TARGET_SERIAL>\" exec-out uiautomator dump /dev/tty\"" > docs/evidence/ui_dumps/visual_test_<name>.xml
```

## 8. Agent-required reporting format

Any agent performing visual tests must report:
- Commit SHA tested.
- APK path used.
- Target serial used.
- Exact commands executed for build/install/launch.
- Screen-by-screen expected vs observed results.
- Links to evidence files (`.png` + `.xml`).
- Final verdict: `PASS` or `FAIL`.
- If failed: first blocking step and reproducible command output.

## Agent rules (mandatory)

- Always build from the latest synced source before visual testing.
- Never claim a screen was verified without a matching screenshot and UI XML dump.
- Always verify device target explicitly with `adb devices -l` before install.
- If a system overlay blocks testing (permissions, Google setup, etc.), record it and dismiss it before continuing.
- If install fails, report the exact error (`INSTALL_FAILED_*`) and recovery action.
- If navigation uses tap coordinates, include the coordinates in the report.

## Common issues

- `adb not found`:
  - Use full path: `M:\.android-studio\Sdk\platform-tools\adb.exe`.
- Device appears `offline`:
  - Use only serials in `device` state.
- `INSTALL_FAILED_UPDATE_INCOMPATIBLE`:
  - Uninstall existing package (`com.mari.app`), then reinstall.
- `Shell does not have permission to access user ...` warning:
  - Usually non-blocking for install/launch; continue with explicit package/activity commands.

## Minimal smoke checklist

- App launches to `MainActivity`.
- Task list screen renders.
- Navigation between screens works.
- At least one screenshot and one UI XML captured for each critical screen.
