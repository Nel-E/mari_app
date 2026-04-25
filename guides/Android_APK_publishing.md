# Android APK Publishing Guide

Use this guide when an agent needs to publish an already-built Mari APK to the local update API feed.

Mandatory rules:
- Keep `/media/code/mari_app` as the Linux source-of-truth.
- Inspect APKs from the Windows workspace: `M:\temp_android\mari_app`.
- Do not run Gradle on Linux. If a build is needed, follow `guides/Android_App_building.md`.
- Publish feed data under `/media/code/mari_app/backend_api/data/app_updates`.
- `backend_api/data/app_updates` is intentionally ignored by git; publishing changes the live feed but does not create tracked source changes.

## 1. Find Candidate APKs On Windows

List APKs in the Windows workspace and identify the newest output:

```bash
ssh marilinda@192.168.1.100 "cmd /c \"for /r M:\\temp_android\\mari_app %f in (*.apk) do @echo %~tf %~zf %f\""
```

Expected output locations:
- Phone debug beta: `M:\temp_android\mari_app\app\build\outputs\apk\debug\app-debug.apk`
- Phone release stable: `M:\temp_android\mari_app\app\build\outputs\apk\release\app-release.apk`
- Watch debug: `M:\temp_android\mari_app\wear\build\outputs\apk\debug\wear-debug.apk`
- Watch release: `M:\temp_android\mari_app\wear\build\outputs\apk\release\wear-release.apk`

## 2. Verify APK Metadata

Read Gradle output metadata first:

```bash
ssh marilinda@192.168.1.100 "cmd /c \"type M:\\temp_android\\mari_app\\app\\build\\outputs\\apk\\debug\\output-metadata.json\""
```

Then confirm the manifest package and version:

```bash
ssh marilinda@192.168.1.100 "cmd /c \"M:\\.android-studio\\Sdk\\build-tools\\36.1.0\\aapt.exe dump badging M:\\temp_android\\mari_app\\app\\build\\outputs\\apk\\debug\\app-debug.apk | findstr package\""
```

Verify signing before publishing:

```bash
ssh marilinda@192.168.1.100 "cmd /c \"M:\\.android-studio\\Sdk\\build-tools\\36.1.0\\apksigner.bat verify --print-certs M:\\temp_android\\mari_app\\app\\build\\outputs\\apk\\debug\\app-debug.apk\""
```

Debug/beta phone builds should normally be:
- `applicationId`: `com.mari.app.debug`
- `versionName`: ends with `-debug`
- APK signer: Android Debug certificate

Stable phone builds should normally be:
- `applicationId`: `com.mari.app`
- `versionName`: no `-debug` suffix
- APK signer: the configured release signer. In this project, release currently uses the debug signing config for update compatibility.

Do not publish if the package name, versionCode, versionName, component, or signing certificate is unexpected.

## 3. Choose Feed Path And File Name

Feed layout:

```text
backend_api/data/app_updates/
  phone/
    beta/
    stable/
  watch/
    beta/
    stable/
```

File naming convention:
- Phone beta debug: `mari-phone-<version>-beta-debug.apk`
- Phone stable: `mari-phone-<version>-stable.apk`
- Watch beta debug: `mari-watch-<version>-beta-debug.apk`
- Watch stable: `mari-watch-<version>-stable.apk`

For a phone beta `1.0.6-debug` build:

```bash
mkdir -p backend_api/data/app_updates/phone/beta/releases
cp /media/nelson/usb1-1T/driveM/temp_android/mari_app/app/build/outputs/apk/debug/app-debug.apk \
  backend_api/data/app_updates/phone/beta/mari-phone-1.0.6-beta-debug.apk
```

## 4. Write Metadata

Every publish must update:
- `<component>/<track>/latest.json`
- `<component>/<track>/releases/<sequence>__<version>.json`

The `latest.json` schema is:

```json
{
  "component": "phone",
  "track": "beta",
  "package_name": "com.mari.app.debug",
  "version_code": 7,
  "version_name": "1.0.6-debug",
  "file_name": "mari-phone-1.0.6-beta-debug.apk",
  "file_size_bytes": 22709335,
  "sha256": "sha256-of-apk",
  "released_at": "2026-04-25T00:01:38Z",
  "notification_title": "Mari 1.0.6 beta available",
  "notification_text": "A new beta debug build is ready to install.",
  "changelog": "- Publish beta debug build 1.0.6 for phone testing",
  "min_installed_version_code": 0
}
```

The release note schema is:

```json
{
  "version_code": 7,
  "version_name": "1.0.6-debug",
  "released_at": "2026-04-25T00:01:38Z",
  "features": ["Publish beta debug build 1.0.6 for phone testing"],
  "upgrades": ["Supersedes phone beta 1.0.5-debug with versionCode 7"],
  "fixes": []
}
```

Use UTC timestamps ending in `Z`. The APK `sha256` and `file_size_bytes` must be computed from the copied artifact.

## 5. Verify The Live API

The local API serves the feed from `/media/code/mari_app/backend_api/data/app_updates`.

Check latest metadata:

```bash
curl -fsS --max-time 3 'http://127.0.0.1:8000/api/app-update/latest?track=beta&component=phone'
```

Check release notes newer than the previous version:

```bash
curl -fsS --max-time 3 'http://127.0.0.1:8000/api/app-update/releases?track=beta&component=phone&after_version_code=6'
```

Check artifact download hash:

```bash
curl -fsS --max-time 10 'http://127.0.0.1:8000/api/app-update/artifacts/beta/phone/mari-phone-1.0.6-beta-debug.apk' | sha256sum
```

The downloaded SHA-256 must exactly match `latest.json`.

## 6. Source Control

Publishing APKs and feed metadata normally produces no tracked git changes because `backend_api/data/app_updates` is ignored.

Commit only source or guide changes. Do not force-add APKs or update feed JSON unless the user explicitly asks for published artifacts to be tracked.
