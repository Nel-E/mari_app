# Phase 6 — Watch Auto-Update

**Depends on:** Phase 4 (API), Phase 5 (phone already resolves updates and has installer primitives).
**Unblocks:** nothing.

## Goal

When the phone detects a newer **watch** APK in the user's selected track, it fetches the APK, streams it to the paired watch via the Wear Data Layer, and the watch installs it silently (`PackageInstaller` session with `USER_ACTION_NOT_REQUIRED` on API ≥ 31). Status flows back phone-ward for user visibility. Ports the architecture from `bfi_dev/android_app/app/.../appupdate/watch/` + `bfi_dev/android_app/wear/.../update/`.

## Rationale

User requirement: "It must also automatically update the watch app as well."

## Files (new/modified)

### New (phone app)
- `app/src/main/kotlin/com/mari/app/appupdate/watch/WatchAppUpdateManager.kt`
- `app/src/main/kotlin/com/mari/app/appupdate/watch/WatchAppUpdateCheckWorker.kt`
- `app/src/main/kotlin/com/mari/app/appupdate/watch/WatchAppUpdateScheduler.kt`
- `app/src/main/kotlin/com/mari/app/appupdate/watch/PhoneWatchUpdateListenerService.kt`
- `app/src/main/kotlin/com/mari/app/data/repository/WatchAppUpdateLocalStore.kt`
- `shared/src/main/kotlin/com/mari/shared/wear/WearUpdateContract.kt` (message paths, payload schemas shared between phone and watch)
- `app/src/main/kotlin/com/mari/app/ui/screens/settings/WatchUpdateSection.kt`

### New (wear module)
- `wear/src/main/kotlin/com/mari/wear/update/WearWatchUpdateInstaller.kt`
- `wear/src/main/kotlin/com/mari/wear/update/WearWatchUpdateInstallReceiver.kt`
- `wear/src/main/kotlin/com/mari/wear/update/WearWatchUpdatePromptNotifier.kt`
- `wear/src/main/kotlin/com/mari/wear/update/WearWatchUpdateStatusReporter.kt`
- `wear/src/main/kotlin/com/mari/wear/data/WearWatchUpdateStore.kt`
- `wear/src/main/kotlin/com/mari/wear/update/WearUpdateListenerService.kt`

### Modified
- `app/src/main/AndroidManifest.xml` — no new permissions (phone already has `REQUEST_INSTALL_PACKAGES` for itself).
- `wear/src/main/AndroidManifest.xml` — `REQUEST_INSTALL_PACKAGES` and new listener/receiver registration.
- `app/build.gradle.kts` & `wear/build.gradle.kts` — kotlinx-serialization (or Moshi) for the payload adapters.

## Contract (`WearUpdateContract`)

```kotlin
object WearUpdateContract {
    const val PATH_UPDATE_PACKAGE = "/mari/update/package"        // DataClient, phone -> watch, with APK Asset
    const val PATH_UPDATE_STATUS  = "/mari/update/status"         // MessageClient, watch -> phone, JSON bytes
    const val PATH_STATUS_REQUEST = "/mari/update/status_request" // MessageClient, phone -> watch
}

@Serializable
data class WearUpdatePackagePayload(
    val requestId: String,
    val packageName: String,
    val versionCode: Int,
    val versionName: String,
    val fileName: String,
    val sha256: String,
)

@Serializable
enum class WearUpdateState { IDLE, RECEIVING, VERIFYING, INSTALLING, SUCCEEDED, FAILED }

@Serializable
data class WearUpdateStatusPayload(
    val requestId: String?,
    val packageName: String,
    val versionCode: Int,
    val versionName: String,
    val state: WearUpdateState,
    val statusMessage: String? = null,
    val targetVersionCode: Int? = null,
    val targetVersionName: String? = null,
    val reportedAtEpochMs: Long,
)
```

## Phone-side Flow (`WatchAppUpdateManager`)

1. Triggered by `WatchAppUpdateCheckWorker` (its own schedule, same track as phone).
2. Call API: `getLatest(track=<user>, component="watch")`.
3. Query paired watch via `MessageClient.sendMessage(nodeId, PATH_STATUS_REQUEST, empty)`; wait up to 12s for a `WearUpdateStatusPayload` response delivered through `PhoneWatchUpdateListenerService.onMessageReceived(...)`.
4. If `latest.versionCode <= watch.versionCode` → nothing to do.
5. Else:
   - Download APK to phone cache via existing `AppUpdateInstaller.downloadAndVerify`.
   - Create `DataClient` request on `PATH_UPDATE_PACKAGE` with:
     - JSON metadata payload.
     - `Asset.createFromUri(FileProvider uri)` for the APK bytes.
   - Await watch status messages on `PATH_UPDATE_STATUS`; if `SUCCEEDED` with matching `versionCode` → mark success; if `FAILED` within timeout → surface error.
6. Suppress repeat transfers while `state.isTargetVersionInFlight(latest.versionCode)`.

## Watch-side Flow

`WearUpdateListenerService` splits Data Layer responsibilities correctly:

1. `onDataChanged(...)` handles `PATH_UPDATE_PACKAGE`:
   - Parse metadata.
   - If `currentStatus.versionCode >= payload.versionCode` → reply `IDLE` / "already up to date".
   - Else copy `Asset` bytes to `cacheDir/watch_updates/<fileName>`.
   - Verify SHA-256.
   - `PackageInstaller` session:
     ```kotlin
     val params = PackageInstaller.SessionParams(MODE_FULL_INSTALL).apply {
         setAppPackageName(payload.packageName)
         if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(USER_ACTION_NOT_REQUIRED)
     }
     ```
   - `commit()` with a `PendingIntent` to `WearWatchUpdateInstallReceiver`.
2. `onMessageReceived(...)` handles `PATH_STATUS_REQUEST`:
   - Reply with current `WearUpdateStatusPayload`.

Report every state change via `WearWatchUpdateStatusReporter` using `MessageClient` on `PATH_UPDATE_STATUS`.

## Signing Requirement

`USER_ACTION_NOT_REQUIRED` **requires** the new APK to be signed with the same key as the installed watch package. Our CI/publish scripts (Phase `08_release_workflow.md`) must sign beta and stable watch APKs with the **same** keystore. First-time install on a fresh watch will require user confirmation; subsequent updates are silent.

## Settings UI — `WatchUpdateSection`

- Toggle: **Auto-update watch**.
- Row: **Watch current version** — from cached last status.
- Row: **Last watch update check**.
- Button: **Check watch now**.
- When failure: error text with retry.

## Tests (RED before GREEN)

1. `WatchAppUpdateManagerTest` (fake API, fake MessageClient/DataClient, fake installer):
   - Watch older than available → transfer initiated.
   - Watch equal/newer → no transfer.
   - Status-request timeout → transfer still attempted after fallback window OR aborted (decide and test — recommendation: abort and surface "watch not reachable").
   - Dedup: in-flight target version is skipped.
2. `WearWatchUpdateInstallerTest` (JVM test of pure helpers; `PackageInstaller` part covered by a thin wrapper interface):
   - SHA mismatch → `FAILED` status emitted.
   - Missing asset → `FAILED` status emitted.
3. `WearUpdateContractTest`:
   - JSON round-trip for both payloads; backward-compat with extra fields ignored.
   - Message paths (`PATH_STATUS_REQUEST`, `PATH_UPDATE_STATUS`) are handled via `MessageClient`; package transfer stays on `DataClient`.
4. `WatchAppUpdateSchedulerTest`:
   - Periodic + manual enqueue mirror the phone scheduler tests.
5. Instrumented (optional, on-device): a mock `packageInstaller` wrapper verifying `setRequireUserAction(USER_ACTION_NOT_REQUIRED)` is called when `SDK_INT >= 31`.

## Validation Gate

- [ ] All unit tests green.
- [ ] Manual end-to-end on real phone + real watch (same keystore):
  - Install older watch APK.
  - Publish newer watch APK to `stable`.
  - Enable "Auto-update watch" on phone.
  - Trigger **Check watch now** → watch receives APK → silent install → phone shows `SUCCEEDED` with new version.
- [ ] Repeat with beta track.
- [ ] Disconnect watch (turn off Bluetooth) → phone surfaces "watch not reachable" without hanging.
- [ ] Corrupt SHA in `latest.json` → watch reports `FAILED` with "checksum mismatch".
- [ ] First-time install on a fresh watch prompts user; subsequent install is silent.

## Exit Criteria

Watch auto-update works end-to-end, silent on update, visible status flows back to phone.
Commit sequence:
1. `feat(wear): update contract and payload models`
2. `feat(appupdate): watch update manager, scheduler, and check worker`
3. `feat(wear): watch-side installer, status reporter, and prompt notifier`
4. `feat(ui): watch update settings section`

---

## Implementation Progress

- [ ] `not implemented` `WearUpdateContract` object (path constants) + `WearUpdatePackagePayload` + `WearUpdateStatusPayload` + `WearUpdateState` enum
- [ ] `not implemented` `WatchAppUpdateManager` (status request, versionCode compare, DataClient transfer, SUCCEEDED/FAILED handling)
- [ ] `not implemented` `WatchAppUpdateCheckWorker` + `WatchAppUpdateScheduler`
- [ ] `not implemented` `WatchAppUpdateLocalStore` (caches last watch status)
- [ ] `not implemented` `WearWatchUpdateInstaller` (Asset copy, SHA verify, PackageInstaller session, USER_ACTION_NOT_REQUIRED on API 31+)
- [ ] `not implemented` `WearWatchUpdateInstallReceiver` (PendingIntent target for PackageInstaller callback)
- [ ] `not implemented` `WearWatchUpdateStatusReporter` (reports state changes back to phone via MessageClient)
- [ ] `not implemented` `PhoneWatchUpdateListenerService` + `WearUpdateListenerService` wired for message/data update paths
- [ ] `not implemented` `wear/AndroidManifest.xml` updated with `REQUEST_INSTALL_PACKAGES` and update listener/receiver registration
- [ ] `not implemented` `WatchUpdateSection` composable in phone's settings
- [ ] `not implemented` `WatchAppUpdateManagerTest`, `WearWatchUpdateInstallerTest`, `WearUpdateContractTest`, `WatchAppUpdateSchedulerTest` — all green
- [ ] `not implemented` Manual end-to-end: older watch APK → publish newer → silent install → phone shows SUCCEEDED

## Functional Requirements / Key Principles

- The watch installer calls `setRequireUserAction(USER_ACTION_NOT_REQUIRED)` only on API ≥ 31; on older APIs the user is prompted via `WearWatchUpdatePromptNotifier`.
- `USER_ACTION_NOT_REQUIRED` only succeeds when the new APK is signed with the same keystore as the installed watch package; the publish workflow must use the shared release keystore for all tracks.
- SHA-256 is verified on the watch before `PackageInstaller.commit()`; a mismatch emits `WearUpdateState.FAILED` with message "checksum mismatch" and deletes the cached APK bytes.
- The phone waits up to 12 seconds for a `PATH_STATUS_REQUEST` reply; if no reply arrives, the transfer is aborted and the phone surfaces "watch not reachable" without hanging.
- Control/status traffic uses `MessageClient`; APK bytes use `DataClient` with an `Asset`. Do not try to receive `PATH_STATUS_REQUEST` or `PATH_UPDATE_STATUS` through `onDataChanged`.
- Duplicate transfers for the same `targetVersionCode` are suppressed while a transfer or install is in-flight.
- Every state transition (`IDLE → RECEIVING → VERIFYING → INSTALLING → SUCCEEDED / FAILED`) is reported back to the phone via `PATH_UPDATE_STATUS` so the settings UI always reflects current progress.
- The first install on a factory-reset watch requires user confirmation; all subsequent updates with the same keystore are silent.
