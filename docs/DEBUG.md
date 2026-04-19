# Phone ↔ Watch Sync — Debug Investigation

## Status: Crash Fix Applied (pending rebuild)

---

## Root Cause #1 — Sync Never Started (FIXED)

**Symptom:** Zero `MariSync` logs on watch; phone sent `TupleManifest` envelopes but watch received nothing.

**Cause:** `wear/build.gradle.kts` had `applicationId = "com.mari.wear"` instead of `"com.mari.app"`.
The Wearable Data Layer routes packets only between apps that share the same `applicationId`.

**Fix:** Changed `applicationId` in `wear/build.gradle.kts` to `"com.mari.app"`.
Uninstalled the old `com.mari.wear.debug` APK, installed the new `com.mari.app.debug` APK.

**Verification:** After fix, logcat confirmed end-to-end exchange:
- Phone → Watch: `TupleManifest` (4 tasks)
- Watch applied 4 tasks
- Watch → Phone: `DeltaBundle` (1 task back)

---

## Root Cause #2 — App Crash After Sync Working (FIXED)

**Symptom:** `java.lang.IllegalArgumentException: Buffer is closed` crash.

**Stack trace:**
```
java.lang.IllegalArgumentException: Buffer is closed.
    at com.google.android.gms.common.data.DataHolder.zae(...)
    at com.google.android.gms.wearable.internal.zzdm.getUri(...)
    at com.mari.app.sync.PhoneSyncService$onDataChanged$1$1.invokeSuspend(PhoneSyncService.kt:62)
```

**Cause:** `DataEventBuffer` is automatically released by GMS when `onDataChanged()` returns.
The code was accessing `item.uri` inside a `serviceScope.launch {}` block — after the buffer had already been closed.

**Affected files:**
- `app/src/main/kotlin/com/mari/app/sync/PhoneSyncService.kt`
- `wear/src/main/kotlin/com/mari/wear/sync/WatchSyncService.kt`

**Fix:** Capture `val uri = item.uri` synchronously before launching the coroutine, then use the captured `uri` value inside the `launch {}` block.

```kotlin
// Before (crash)
serviceScope.launch {
    handleEnvelope(SyncEnvelope.decode(payload))
    Wearable.getDataClient(this@PhoneSyncService).deleteDataItems(item.uri) // buffer closed here
}

// After (fixed)
val uri = item.uri  // captured before buffer closes
serviceScope.launch {
    handleEnvelope(SyncEnvelope.decode(payload))
    Wearable.getDataClient(this@PhoneSyncService).deleteDataItems(uri)
}
```

---

## Next Steps

1. Build both `app` and `wear` debug APKs
2. Install via ADB on Windows (`marilinda@192.168.1.100`)
   - Phone: `192.168.1.200:36581`
   - Watch: `192.168.1.45:46309`
3. Open Mari on phone, add a task, verify it appears on watch (and vice versa)
4. Monitor logcat (`adb logcat -s MariSync`) for clean exchange without crashes
