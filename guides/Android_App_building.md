# Windows Android Build and Test Guide

Use this guide whenever you need to run Android/Kotlin builds and tests on the Windows host with Android SDK + JDK installed.

Methodology (mandatory):
- Keep source on Linux as source-of-truth at `/media/code/mari_app`.
- Sync the repo to the Windows workspace before every build.
- Build and run from the Windows workspace (`M:\temp_android\mari_app`), not from Linux artifacts.
- Run Android Gradle tests from the Windows workspace only, including targeted commands that use `--tests`.

Mandatory Windows temp root policy:
- All temporary Windows workspaces must be created under `M:\temp_android`.
- This project's workspace: `M:\temp_android\mari_app`.
- Do not create temp workspaces directly under `M:\` outside `M:\temp_android`.
- Do not create files outside `M:\temp_android`.

## 1. Sync the repo to the Windows workspace

**CRITICAL: Only sync the source tree — never stale build artifacts.**

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

## 2. Write local.properties

Write `local.properties` directly to the local mount:

```bash
echo 'sdk.dir=M:/.android-studio/Sdk' > /media/nelson/usb1-1T/driveM/temp_android/mari_app/local.properties
```

## 3. Build the APK

**CRITICAL: Use deterministic Gradle flags on this machine for every invocation.**

Gradle's incremental build cache can silently serve stale outputs even after source files change.
`--rerun-tasks` forces every task to re-execute from the current source, guaranteeing the built
APK reflects the latest code exactly.

For the Windows host, the safest default is:
- Stop any already-running Gradle daemons before the build
- Pass `--no-daemon` on the actual build command
- Force a single worker with `--max-workers=1`

Debug APK:

```bash
ssh marilinda@192.168.1.100 "cmd /c \"cd /d M:\\temp_android\\mari_app && gradlew.bat --stop && gradlew.bat app:assembleDebug --no-daemon --max-workers=1 --rerun-tasks\""
```

Release APK (only when explicitly requested):

```bash
ssh marilinda@192.168.1.100 "cmd /c \"cd /d M:\\temp_android\\mari_app && gradlew.bat --stop && gradlew.bat app:assembleRelease --no-daemon --max-workers=1 --rerun-tasks\""
```

APK output paths:
- Debug: `M:\temp_android\mari_app\app\build\outputs\apk\debug\app-debug.apk`
- Release: `M:\temp_android\mari_app\app\build\outputs\apk\release\app-release.apk`

## 4. Run Gradle tests with Windows JDK

**CRITICAL: Use the same deterministic flags for tests.**

The expected workflow is always:
1. Sync source to `M:\temp_android\mari_app`
2. Write `local.properties` in that workspace
3. Run `gradlew.bat` on Windows from `M:\temp_android\mari_app`

```bash
ssh marilinda@192.168.1.100 "cmd /c \"cd /d M:\\temp_android\\mari_app && gradlew.bat --stop && gradlew.bat app:testDebugUnitTest --no-daemon --max-workers=1 --rerun-tasks\""
```

Run shared module tests:

```bash
ssh marilinda@192.168.1.100 "cmd /c \"cd /d M:\\temp_android\\mari_app && gradlew.bat --stop && gradlew.bat shared:test --no-daemon --max-workers=1 --rerun-tasks\""
```

Run all tests:

```bash
ssh marilinda@192.168.1.100 "cmd /c \"cd /d M:\\temp_android\\mari_app && gradlew.bat --stop && gradlew.bat test --no-daemon --max-workers=1 --rerun-tasks\""
```

Example for a targeted test class:

```bash
ssh marilinda@192.168.1.100 "cmd /c \"cd /d M:\\temp_android\\mari_app && gradlew.bat --stop && gradlew.bat app:testDebugUnitTest --tests com.mari.app.SomeTest --no-daemon --max-workers=1 --rerun-tasks\""
```

## 5. Install APK on device

After a successful build, install the APK on the target device.

List connected devices:

```bash
ssh marilinda@192.168.1.100 "cmd /c \"\"M:\\.android-studio\\Sdk\\platform-tools\\adb.exe\" devices -l\""
```

Install debug APK (replace `<TARGET_SERIAL>` with the actual serial, e.g. `192.168.1.120:38545` or `emulator-5556`):

```bash
ssh marilinda@192.168.1.100 "cmd /c \"\"M:\\.android-studio\\Sdk\\platform-tools\\adb.exe\" -s \"<TARGET_SERIAL>\" install -r \"M:\\temp_android\\mari_app\\app\\build\\outputs\\apk\\debug\\app-debug.apk\"\""
```

If install fails due to signature mismatch (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`), uninstall first:

```bash
ssh marilinda@192.168.1.100 "cmd /c \"\"M:\\.android-studio\\Sdk\\platform-tools\\adb.exe\" -s \"<TARGET_SERIAL>\" uninstall com.mari.app\""
```

Then re-run the install command above.

Launch the app after install:

```bash
ssh marilinda@192.168.1.100 "cmd /c \"\"M:\\.android-studio\\Sdk\\platform-tools\\adb.exe\" -s \"<TARGET_SERIAL>\" shell am start -n com.mari.app/.MainActivity\""
```

## 6. Collect artifacts (optional)

```bash
scp marilinda@192.168.1.100:/temp_android/mari_app/app/build/reports/tests/testDebugUnitTest/index.html /media/code/mari_app/build_reports/
```

Adjust paths to match the artifact you need.

## 7. Optional cleanup

If needed, remove `M:\temp_android\mari_app` after archiving artifacts.

## Notes

- Never install JDK/SDK locally; the Windows box already has everything configured.
- Always use `gradlew.bat --stop` before the real task invocation on this host.
- Always use `--no-daemon --max-workers=1 --rerun-tasks` on every Gradle invocation — never omit them.
- If Gradle fails due to code issues, fix them locally, re-sync, and rebuild with the same flags.
- Keep an eye on long-running jobs; cancel with `Ctrl+C` on the Linux terminal if needed.
- Any visual testing must be done on the Windows-connected device `192.168.1.120`.

## Related guides

- For step-by-step visual UI testing (build/install/navigate/capture evidence) see:
  - `guides/windows_emulator_visual_testing.md`
- For SSH access details see:
  - `guides/windows_ssh_access.md`
