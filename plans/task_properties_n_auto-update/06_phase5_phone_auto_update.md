# Phase 5 — Phone Auto-Update

**Depends on:** Phase 4 (API must be serving `latest.json`).
**Unblocks:** Phase 6 (watch reuses the same fetch/install pieces via phone).

## Goal

The phone app periodically (and at startup, and on demand) checks the publish API for a newer APK in the user's selected **track** (`stable` or `beta`), downloads it, verifies SHA-256, and asks the user to install it via `FileProvider` + `ACTION_VIEW`. Ports the proven architecture from `bfi_dev/android_app/appupdate/`.

Published OTA artifacts must be release-signed APKs with the same application id as the installed app. Debug builds with an application-id suffix are for local development only and must never be published to the OTA feed.

## Rationale

User requirement: "create auto-update… see how bfi_dev implements it… must also automatically update the watch." Plus the track toggle: "user can choose to update only when I build release versions."

## Files (new/modified)

### New
- `app/src/main/kotlin/com/mari/app/appupdate/AppUpdateCheckWorker.kt`
- `app/src/main/kotlin/com/mari/app/appupdate/AppUpdateScheduler.kt`
- `app/src/main/kotlin/com/mari/app/appupdate/AppUpdateInstaller.kt`
- `app/src/main/kotlin/com/mari/app/appupdate/AppUpdateNotifier.kt`
- `app/src/main/kotlin/com/mari/app/data/remote/AppUpdateApiService.kt`
- `app/src/main/kotlin/com/mari/app/data/remote/dto/AppUpdateDtos.kt`
- `app/src/main/kotlin/com/mari/app/data/repository/AppUpdateRepositoryImpl.kt`
- `app/src/main/kotlin/com/mari/app/data/repository/AppUpdateLocalStore.kt`
- `app/src/main/kotlin/com/mari/app/domain/repository/AppUpdateRepository.kt`
- `app/src/main/kotlin/com/mari/app/domain/model/AppUpdateModels.kt`
- `app/src/main/kotlin/com/mari/app/ui/screens/settings/AppUpdateSection.kt`
- `app/src/main/kotlin/com/mari/app/ui/screens/update/UpdateAvailableScreen.kt`
- `app/src/main/res/xml/file_provider_paths.xml`
- Tests for repository, worker, installer, scheduler, local store.

### Modified
- `app/src/main/AndroidManifest.xml` — add `FileProvider`, `REQUEST_INSTALL_PACKAGES`, `INTERNET`, `ACCESS_NETWORK_STATE`.
- `app/src/main/kotlin/com/mari/app/MariApp.kt` (or the Application subclass) — enqueue startup + periodic checks.
- `app/src/main/kotlin/com/mari/app/di/NetworkModule.kt` (new if missing) — Retrofit + OkHttp with the `X-Mari-Token` interceptor.
- `app/build.gradle.kts` — Retrofit, Moshi/kotlinx-serialization converter, OkHttp logging.
- `app/src/main/kotlin/com/mari/app/ui/screens/settings/SettingsScreen.kt` — include `AppUpdateSection`.

## Detailed Steps

### 1. Models

```kotlin
data class AppUpdateInfo(
    val track: String,                // "release" | "debug"
    val component: String,            // "phone"
    val packageName: String,
    val versionCode: Int,
    val versionName: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val sha256: String,
    val releasedAt: String,
    val notificationTitle: String,
    val notificationText: String,
    val changelog: String?,
    val downloadUrl: String,
    val minInstalledVersionCode: Int? = null,
)

data class AppUpdateLocalState(
    val autoCheckEnabled: Boolean = true,
    val track: UpdateTrack = UpdateTrack.RELEASE,     // user toggle
    val checkIntervalHours: Int = 6,
    val lastCheckAt: String? = null,
    val availableUpdate: AppUpdateInfo? = null,
    val releaseNotes: List<AppUpdateReleaseNote> = emptyList(),
    val lastNotifiedVersionCode: Int? = null,
    val lastAcknowledgedVersionCode: Int? = null,
)

enum class UpdateTrack(val wire: String) { RELEASE("release"), DEBUG("debug") }
```

### 2. Retrofit service

```kotlin
interface AppUpdateApiService {
    @GET("api/app-update/latest")
    suspend fun getLatest(
        @Query("track") track: String,
        @Query("component") component: String = "phone",
    ): AppUpdateLatestDto

    @GET("api/app-update/releases")
    suspend fun getReleases(
        @Query("track") track: String,
        @Query("component") component: String,
        @Query("after_version_code") after: Int,
    ): AppUpdateReleasesResponseDto

    @Streaming
    @GET("api/app-update/artifacts/{track}/{component}/{file_name}")
    suspend fun downloadArtifact(
        @Path("track") track: String,
        @Path("component") component: String,
        @Path("file_name", encoded = true) fileName: String,
    ): Response<ResponseBody>
}
```

### 3. Repository

`AppUpdateRepositoryImpl` combines API + local state. Compares `response.versionCode` > `BuildConfig.VERSION_CODE`. Downgrade blocked. Respects `minInstalledVersionCode` if present. Returns a decided `AppUpdateCheckResult` (update available? should notify?).

The selected track only chooses which remote feed to query. `versionCode` must still be globally monotonic across **all** published builds, so a user who installs debug can later upgrade to release.

### 4. Local store

`AppUpdateLocalStore` uses DataStore Preferences. Expose a `StateFlow<AppUpdateLocalState>` for settings-screen observation.

### 5. Worker

`AppUpdateCheckWorker` (CoroutineWorker):
- Input: `track` (defaults to stored track), `forceNotify: Boolean`.
- If `!forceNotify && !autoCheckEnabled` → `Result.success()` (no-op).
- Calls repository, updates local state, and if `shouldNotify`, calls `AppUpdateNotifier.notifyAvailable(update)`.
- Network constraint required.

### 6. Scheduler

`AppUpdateScheduler` mirrors `bfi_dev`:
- `enqueuePeriodic(intervalHours)` — `PeriodicWorkRequest`, `UPDATE` policy.
- `enqueueOnStartup()` — `OneTimeWorkRequest`, `REPLACE` policy, silent.
- `enqueueManual()` — `OneTimeWorkRequest`, `REPLACE`, `forceNotify = true`.
- `reenqueuePeriodic(newInterval)` — called when user changes interval.
- `reenqueueOnTrackChange(newTrack)` — cancel + re-enqueue, clears `lastNotifiedVersionCode` so the user is re-prompted for the first debug or first release version on the other track.

### 7. Installer

Port verbatim from `bfi_dev/AppUpdateInstaller.kt`:
- `downloadAndVerify(track, fileName, expectedSha256): Result<File>`.
- `canRequestInstallPackages()`.
- `buildInstallIntent(apk)` — `FileProvider` + `application/vnd.android.package-archive`.
- `buildInstallPermissionSettingsIntent()` — routes user to grant "Install unknown apps".

### 8. Notifier

`AppUpdateNotifier`:
- Notification with tap → `UpdateAvailableScreen`.
- Action: `Install now` (starts download inside a foreground service OR navigates to screen).
- Dedup via `lastNotifiedVersionCode`.

### 9. FileProvider

`file_provider_paths.xml`:
```xml
<paths>
    <cache-path name="updates" path="app_updates/" />
</paths>
```

Manifest:
```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data android:name="android.support.FILE_PROVIDER_PATHS"
               android:resource="@xml/file_provider_paths"/>
</provider>
```

### 10. Settings UI — `AppUpdateSection`

- Toggle: **Check for updates automatically**.
- Toggle: **Receive debug builds** (switches `track` between STABLE and BETA).
- Interval picker: 1h / 6h / 12h / 24h (clamped to `>= 1h`).
- Row: **Last checked** — ISO timestamp.
- Row: **Current version** — `BuildConfig.VERSION_NAME (VERSION_CODE)`.
- Button: **Check now** — calls `enqueueManual()`.
- Visible when `availableUpdate != null`: **Update available — v<name>** → opens `UpdateAvailableScreen`.

### 11. UpdateAvailableScreen

- Shows release notes.
- Download progress bar.
- On `downloadAndVerify` success → opens install intent (grants permission first if missing).
- On SHA mismatch or download failure → error banner + retry.

### 12. Application startup

In `MariApp.onCreate`:
```kotlin
appUpdateScheduler.enqueuePeriodic()
appUpdateScheduler.enqueueOnStartup()
```

### 13. Network module

- Base URL from `BuildConfig.MARI_API_BASE_URL` (set in `build.gradle.kts`; defaults to the RPi LAN address, can be overridden in `local.properties`).
- OkHttp interceptor adds `X-Mari-Token: BuildConfig.MARI_API_TOKEN` (also from gradle/local.properties).
- Timeouts: connect 10s, read 60s (artifacts are large).
- Logging interceptor only in debug variant.

### 14. Track change handling

When the user toggles "Receive debug builds":
- `localStore` updates `track`.
- `scheduler.reenqueueOnTrackChange(newTrack)`.
- `availableUpdate` cleared; next check resolves the new track.

## Tests (RED before GREEN)

1. `AppUpdateRepositoryTest` (fake API + fake local store):
   - Stable track, server returns higher version → `availableUpdate` set, `shouldNotify = true`.
   - Beta track, server returns beta-suffixed version with higher `versionCode` → update offered.
   - Device currently on beta, server returns newer stable with higher `versionCode` after track switch → update offered.
   - Server returns same version → no update.
   - Server returns lower version → no update (downgrade protection).
   - `minInstalledVersionCode` above current → filtered out.
2. `AppUpdateCheckWorkerTest`:
   - `autoCheckEnabled = false` + `forceNotify = false` → no network call.
   - `forceNotify = true` → fetches regardless; notifier invoked on update.
   - Dedup: same version already in `lastNotifiedVersionCode` → no notification.
3. `AppUpdateSchedulerTest`:
   - `enqueuePeriodic` schedules with clamped interval (min 1h).
   - `reenqueueOnTrackChange` cancels and re-enqueues with fresh state.
4. `AppUpdateInstallerTest`:
   - Download succeeds and SHA matches → returns file.
   - SHA mismatch → file deleted, `Result.failure`.
   - HTTP error → propagates failure with truncated body.
5. `AppUpdateLocalStoreTest`:
   - Track change persists across instance re-create.
6. `AppUpdateSectionViewModelTest`:
   - Toggle auto-check on → scheduler.enqueuePeriodic called.
   - Toggle auto-check off → scheduler.cancel called.
   - Track change triggers `reenqueueOnTrackChange`.

## Validation Gate

- [ ] All new tests green.
- [ ] Install current (older) release APK on device. Publish a newer stable release APK. Within one periodic cycle the phone shows a notification. Tap → download → install.
- [ ] Toggle **Receive debug builds**. Publish a newer beta release APK. Phone offers the beta.
- [ ] Start on beta, then switch back to stable and publish a newer stable build with a higher global `versionCode`. Phone offers the stable build.
- [ ] Turn off auto-check. Publish newer APK. No notification. Tap **Check now** → notified immediately.
- [ ] Corrupt the published APK (edit a byte) and **preserve** the old SHA in `latest.json` → installer reports SHA mismatch, no install intent fired.
- [ ] First-time install flow: grant "Install unknown apps" prompt appears exactly once, subsequent updates do not re-prompt.
- [ ] `./gradlew :app:connectedAndroidTest` (if instrumented tests added) passes.

## Exit Criteria

Phone auto-update works end-to-end against the RPi API on LAN, track toggle works, SHA verification blocks tampered builds.
Commit sequence:
1. `feat(appupdate): models, DTOs, and repository scaffolding`
2. `feat(appupdate): Retrofit API service and local store`
3. `feat(appupdate): check worker, scheduler, and notifier`
4. `feat(appupdate): installer with SHA-256 verification and FileProvider`
5. `feat(ui): app update settings section and UpdateAvailableScreen`
6. `feat(appupdate): wire startup and periodic checks in application onCreate`

---

## Implementation Progress

- [ ] `not implemented` `AppUpdateInfo`, `AppUpdateLocalState`, `UpdateTrack` domain models
- [ ] `not implemented` `AppUpdateLatestDto`, `AppUpdateReleasesResponseDto` DTOs
- [ ] `not implemented` `AppUpdateApiService` Retrofit interface (latest, releases, streaming artifact)
- [ ] `not implemented` `AppUpdateLocalStore` DataStore Preferences + `StateFlow<AppUpdateLocalState>`
- [ ] `not implemented` `AppUpdateRepositoryImpl` (compares versionCode, downgrade protection, minInstalledVersionCode)
- [ ] `not implemented` `AppUpdateCheckWorker` CoroutineWorker (forceNotify, autoCheckEnabled guard)
- [ ] `not implemented` `AppUpdateScheduler` (enqueuePeriodic, enqueueOnStartup, enqueueManual, reenqueueOnTrackChange)
- [ ] `not implemented` `AppUpdateInstaller` (downloadAndVerify, FileProvider intent, permission settings intent)
- [ ] `not implemented` `AppUpdateNotifier` (notification with tap → UpdateAvailableScreen, dedup via lastNotifiedVersionCode)
- [ ] `not implemented` `file_provider_paths.xml` + `FileProvider` in `AndroidManifest.xml`
- [ ] `not implemented` `AppUpdateSection` composable in `SettingsScreen`
- [ ] `not implemented` `UpdateAvailableScreen` (release notes, download progress, install intent)
- [ ] `not implemented` `NetworkModule` with OkHttp + `X-Mari-Token` interceptor, timeouts, logging interceptor (debug only)
- [ ] `not implemented` `MariApp.onCreate` wires `enqueuePeriodic()` + `enqueueOnStartup()`
- [ ] `not implemented` `AppUpdateRepositoryTest`, `AppUpdateCheckWorkerTest`, `AppUpdateSchedulerTest`, `AppUpdateInstallerTest`, `AppUpdateLocalStoreTest` — all green

## Functional Requirements / Key Principles

- The app checks for updates periodically (default 6h), on startup, and on-demand; all three paths go through the same worker.
- Downgrade is always blocked: `response.versionCode <= BuildConfig.VERSION_CODE` produces no update, regardless of track.
- Track selection chooses the remote feed; it does **not** relax the global monotonic `versionCode` requirement across beta and stable.
- SHA-256 is verified against the value in `latest.json` after download; a mismatch deletes the cached file and returns `Result.failure` — no install intent is fired.
- The track toggle (`STABLE` / `BETA`) triggers `reenqueueOnTrackChange`, clears `lastNotifiedVersionCode`, and immediately enqueues a fresh check on the new track.
- Auto-check can be disabled in settings; when disabled, only manual "Check now" triggers a network call.
- The notification deduplicates by `lastNotifiedVersionCode`; the user is not re-notified for the same version unless they switch tracks.
- `X-Mari-Token` is read from `BuildConfig.MARI_API_TOKEN` (set via `local.properties`); it is never stored in source control.
- OTA artifacts are release-signed APKs with the same application id as the installed package; debug/applicationId-suffixed builds are unsupported for OTA.
- Download progress is visible in `UpdateAvailableScreen`; the install intent is only fired after a successful SHA-verified download.
