# Windows SSH Access Guide

Use this guide to connect to the Windows build host over SSH and run commands remotely.

Methodology:
- Sync the full `mari_app/` repo from `/media/code/mari_app` to `M:\temp_android\mari_app` on Windows before every build.
- Build the Android app on Windows at `M:\temp_android\mari_app`.
- Use Windows ADB with the test device for runtime checks (port changes — check `Developer options → Wireless debugging`).

Mandatory Windows temp root policy:
- Agents must create and use temporary folders only inside `M:\temp_android`.
- Do not create ad-hoc temp folders directly under `M:\`.

## Connect

```bash
ssh marilinda@192.168.1.100
```

## Run a one-off PowerShell command

```bash
ssh marilinda@192.168.1.100 "powershell -Command \"whoami\""
```

## Run a command in a specific directory

```bash
ssh marilinda@192.168.1.100 "cd M:\temp_android && dir"
```

## Sync source to Windows workspace

```bash
# Clear and recreate workspace
rm -rf /media/nelson/usb1-1T/driveM/temp_android/mari_app
mkdir -p /media/nelson/usb1-1T/driveM/temp_android/mari_app

# Sync via local mount (faster than SSH pipe)
tar --exclude='.gradle' --exclude='app/build' --exclude='wear/build' \
    --exclude='shared/build' --exclude='__pycache__' \
    -cf - -C /media/code mari_app \
| tar -xf - -C /media/nelson/usb1-1T/driveM/temp_android
```

Alternatively, sync via SSH pipe (fallback if local mount is unavailable):

```bash
tar --exclude='.gradle' --exclude='app/build' --exclude='wear/build' \
    --exclude='shared/build' --exclude='__pycache__' \
    -cf - -C /media/code mari_app \
| ssh marilinda@192.168.1.100 "powershell -NoProfile -Command \"
if (Test-Path 'M:\temp_android\mari_app') { Remove-Item -Recurse -Force 'M:\temp_android\mari_app' }
New-Item -ItemType Directory -Force -Path 'M:\temp_android\mari_app' | Out-Null
tar -xf - -C 'M:\temp_android\mari_app'
\""
```

Write `local.properties`:

```bash
echo 'sdk.dir=M:/.android-studio/Sdk' > /media/nelson/usb1-1T/driveM/temp_android/mari_app/local.properties
```

Or via SSH:

```bash
ssh marilinda@192.168.1.100 "powershell -Command \"Set-Content -NoNewline -Path 'M:\temp_android\mari_app\local.properties' -Value 'sdk.dir=M:/.android-studio/Sdk'\""
```

Build Android app in synced workspace (always use `--rerun-tasks` — never rely on Gradle cache):

```bash
ssh marilinda@192.168.1.100 "cmd /c \"cd /d M:\\temp_android\\mari_app && gradlew.bat --stop && gradlew.bat app:assembleDebug --no-daemon --max-workers=1 --rerun-tasks\""
```

## Copy a file from Windows to Linux

```bash
scp marilinda@192.168.1.100:/temp_android/mari_app/app/build/reports/tests/testDebugUnitTest/index.html /media/code/mari_app/build_reports/
```

Adjust paths as needed for the artifact you want to fetch.
