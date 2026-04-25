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
- Phone OTA beta/stable: `M:\temp_android\mari_app\app\build\outputs\apk\release\app-release.apk`
- Phone local debug install only: `M:\temp_android\mari_app\app\build\outputs\apk\debug\app-debug.apk`
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

Phone OTA builds, including beta-track publishes, must normally be release-variant APKs:
- `applicationId`: `com.mari.app`
- `versionCode`: greater than the currently installed app
- APK signer: debug keystore. In this project, release currently uses the debug signing config for update compatibility.
- Release APKs must be able to reach the configured `MARI_API_BASE_URL`. The default URL is currently cleartext HTTP on the LAN (`http://192.168.1.10:8000/`), so the release variant must include a main-manifest network security config that permits cleartext traffic to `192.168.1.10`. Do not rely on `app/src/debug/AndroidManifest.xml`; debug-only network config is not packaged into release APKs.

Do not publish `app-debug.apk` to the in-app update feed for normal phone updates. It uses a separate package identity and cannot update the installed app.

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
- Phone beta OTA: `mari-phone-<version>-beta.apk`
- Phone stable: `mari-phone-<version>-stable.apk`
- Watch beta debug: `mari-watch-<version>-beta-debug.apk`
- Watch stable: `mari-watch-<version>-stable.apk`

For a phone beta `1.0.7` OTA build:

```bash
mkdir -p backend_api/data/app_updates/phone/beta/releases
cp /media/nelson/usb1-1T/driveM/temp_android/mari_app/app/build/outputs/apk/release/app-release.apk \
  backend_api/data/app_updates/phone/beta/mari-phone-1.0.7-beta.apk
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
  "package_name": "com.mari.app",
  "version_code": 8,
  "version_name": "1.0.7",
  "file_name": "mari-phone-1.0.7-beta.apk",
  "file_size_bytes": 11144116,
  "sha256": "sha256-of-apk",
  "released_at": "2026-04-25T00:01:38Z",
  "notification_title": "Mari 1.0.7 beta available",
  "notification_text": "A new beta build is ready to install.",
  "changelog": "- Fix release builds reaching the local HTTP update server\n- Keep the do not disturb time range picker readable in light and dark themes",
  "min_installed_version_code": 0
}
```

The release note schema is:

```json
{
  "version_code": 8,
  "version_name": "1.0.7",
  "released_at": "2026-04-25T00:01:38Z",
  "features": [],
  "upgrades": ["Supersedes phone beta 1.0.6 with versionCode 8"],
  "fixes": [
    "Fix release builds reaching the local HTTP update server by applying the LAN cleartext network security config",
    "Keep the do not disturb time range picker readable in light and dark themes"
  ]
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
curl -fsS --max-time 10 'http://127.0.0.1:8000/api/app-update/artifacts/beta/phone/mari-phone-1.0.7-beta.apk' | sha256sum
```

The downloaded SHA-256 must exactly match `latest.json`.

## 6. Source Control

Publishing APKs and feed metadata normally produces no tracked git changes because `backend_api/data/app_updates` is ignored.

Commit only source or guide changes. Do not force-add APKs or update feed JSON unless the user explicitly asks for published artifacts to be tracked.
