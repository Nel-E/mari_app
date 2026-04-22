# Release Workflow — Branches, Versioning, Publish Scripts

**Depends on:** Phases 4, 5, 6 working.
**Unblocks:** ongoing releases.

## Goal

One codebase, two git branches, one backend, one channel with two tracks (`stable` + `beta`). This document defines **how to cut a release** and **how to publish** so that phones and watches pick it up correctly.

## Branch Model

| Branch | Track published to | `versionName` suffix | Purpose |
|---|---|---|---|
| `beta` | `beta` | `-beta` | Active development; testable builds. |
| `release` | `stable` | (none) | User-visible stable builds. |

- Both branches build from the **same `app/build.gradle.kts`**.
- `main` remains the integration branch. PRs merge from feature → `main`, then `main` → `beta` → `release` via fast-forward when ready.
- No CI-only branch needed. Publishing is local.

## Version Scheme

Borrowed from `app_m` (6-digit derived `versionCode`):

1. Strip any suffix from `versionName` (`-beta` etc.).
2. Split on `.`; pad with trailing zeros to exactly **6** numeric components.
3. Concatenate → `versionCode` (integer).

| `versionName` | Padded | `versionCode` |
|---|---|---|
| `1.0.1.0-beta` | `1,0,1,0,0,0` | `101000` |
| `1.0.1.4-beta` | `1,0,1,4,0,0` | `101400` |
| `1.0.1.4` | `1,0,1,4,0,0` | `101400` |
| `1.1.0` | `1,1,0,0,0,0` | `110000` |

> **Note:** beta and stable of the *same* marketing version share the same `versionCode`. That is fine because they are **different tracks** — the phone filters by track before comparing. The `versionCode` ordering only needs to be correct *within* a track.

## Publish Scripts

### `scripts/publish_phone.sh`

Arguments:
```
--apk-path <path>
--track <stable|beta>
--version-code <int>
--version-name <text>
--notification-title <text>
--notification-text <text>
[--changelog <text>]
[--released-at <ISO8601>]
[--artifact-dir <dir>]            # defaults to $MARI_UPDATES_ROOT/phone/<track>/
[--min-installed-version-code <int>]
```

Actions:
1. Verify APK exists; compute `file_size_bytes` and `sha256`.
2. Verify `versionCode` in `latest.json` (if any) is **strictly less** than the new one. Abort otherwise.
3. Copy APK to `<artifact-dir>/mari-phone-<versionName>.apk`.
4. Write `<artifact-dir>/latest.json` atomically (tmpfile + rename).
5. Write `<artifact-dir>/releases/NNNN__<versionName>.json` with features/upgrades/fixes (placeholder — editor opens if empty).
6. Print the SHA, size, and final URLs.

### `scripts/publish_watch.sh`

Identical shape, different target dir (`<root>/watch/<track>/`) and different `component` value.

### Make scripts idempotent

- Re-running with the same inputs is a no-op (detects the file exists with the same SHA and exits 0).
- Re-running with *different* bytes at the same version → aborts (user must bump).

## End-to-End Release Flow

1. On `beta` branch, finish work:
   - `./gradlew :app:lint :shared:test :app:test :wear:test` all green.
   - Bump `versionName` + `versionCode` in `app/build.gradle.kts` (and `wear/build.gradle.kts` if watch changed).
   - Update `releases/NNNN__<ver>.json` templates.
   - Commit: `chore(release): bump to <versionName>`.
2. Build APKs (foreground, see `guides/apk_publish_guide.md` pattern for mari):
   - Phone: `./gradlew :app:assembleBetaDebug` (or `:app:assembleRelease` for stable).
   - Watch: `./gradlew :wear:assembleBetaDebug` (or `:wear:assembleRelease`).
   - **Both APKs must be signed with the same keystore** — required for silent watch updates (`USER_ACTION_NOT_REQUIRED`).
3. Publish phone:
   ```bash
   scripts/publish_phone.sh \
     --apk-path app/build/outputs/apk/beta/debug/app-beta-debug.apk \
     --track beta \
     --version-code 101400 \
     --version-name 1.0.1.4-beta \
     --notification-title "Mari 1.0.1.4-beta available" \
     --notification-text "Deadline reminders and daily nudge."
   ```
4. Publish watch (same shape, `--track beta`).
5. On-device verification:
   - Phone running `1.0.1.3-beta` with beta track enabled picks up `1.0.1.4-beta` within one check cycle.
   - Watch picks up its update when triggered.
6. When stable: merge `beta` → `release`, drop the `-beta` suffix in `versionName` (keep `versionCode` aligned), build release APKs, publish with `--track stable`, commit: `chore(release): publish stable <versionName>`.

## Backend Deployment Ops

- RPi: `cd /home/mari/mari_app_backend && git pull && docker compose up -d --build mari-api`.
- Data dir: `/home/mari/mari_updates/` — **never** wiped by compose (bind mount).
- Rotate `MARI_API_TOKEN`:
  1. Update `.env` on RPi.
  2. Update phone's `local.properties` `MARI_API_TOKEN` and rebuild APK.
  3. Publish the new APK via a one-off workaround (bearer-free build OR push via ADB) so phones can upgrade and fetch future updates. Document this as a "token rotation requires a bridging build."

## Checklists

### Per-release (beta)

- [ ] Branch is `beta`.
- [ ] `versionName` bumped with `-beta` suffix.
- [ ] `versionCode` re-derived.
- [ ] Tests pass (`:shared:test`, `:app:test`, `:wear:test`).
- [ ] Keystore is the shared release keystore.
- [ ] `publish_phone.sh` ran green with SHA logged.
- [ ] `publish_watch.sh` ran green with SHA logged.
- [ ] `latest.json` on RPi reachable via `curl`.
- [ ] Phone on beta track picks up the update.
- [ ] Watch on beta track picks up the update.

### Per-release (stable)

- [ ] Branch is `release`.
- [ ] `versionName` has no suffix.
- [ ] All `beta` commits merged and validated.
- [ ] `publish_phone.sh --track stable` green.
- [ ] `publish_watch.sh --track stable` green.
- [ ] Phone on stable track picks up the update.
- [ ] Watch on stable track picks up the update.

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Watch silent install breaks after key rotation | Document that the shared keystore must never change without a coordinated manual-update release. |
| Token leaked from `local.properties` into public repo | `.gitignore` `local.properties`; grep CI for token pattern. |
| `latest.json` published with wrong SHA | Publish script computes SHA from the copied APK, not from user input. |
| Phone stuck on old track after user toggles | `reenqueueOnTrackChange` clears `lastNotifiedVersionCode` and runs a manual check immediately. |
| Version ordering collision between tracks | Filter by track **before** comparing `versionCode`. Covered in `AppUpdateRepositoryTest`. |
| RPi restart loses published data | Bind mount outside the container; snapshot `/home/mari/mari_updates/` in your backup routine. |

## Done Definition

Plan is complete when:
- [ ] All 8 plan files checked in on a feature branch.
- [ ] Phases 0–6 implemented and validation gates passed.
- [ ] `scripts/publish_phone.sh` and `scripts/publish_watch.sh` live and tested.
- [ ] First beta release published from `beta` branch and installed on a real phone + watch from the RPi API.
- [ ] First stable release published from `release` branch and installed.

---

## Implementation Progress

- [ ] `not implemented` `scripts/publish_phone.sh` — idempotent, verifies versionCode ordering, computes SHA from copied file
- [ ] `not implemented` `scripts/publish_watch.sh` — same shape, targets `watch/<track>/`
- [ ] `not implemented` `beta` branch created from `main`
- [ ] `not implemented` `release` branch created from `beta` (fast-forward on first stable cut)
- [ ] `not implemented` `versionCode` derivation documented and applied in `app/build.gradle.kts` and `wear/build.gradle.kts`
- [ ] `not implemented` First beta release published (phone + watch APKs) and installed on device
- [ ] `not implemented` First stable release published and installed on device
- [ ] `not implemented` RPi `docker compose` deployment verified with healthcheck green

## Functional Requirements / Key Principles

- `publish_phone.sh` and `publish_watch.sh` are idempotent: re-running with identical inputs is a no-op (same SHA detected, exits 0); re-running with different bytes at the same version aborts with an error.
- SHA-256 is computed from the file after it is copied to the artifact directory, not from user-supplied input, preventing metadata/file mismatches.
- `versionCode` must be strictly greater than the value in the existing `latest.json`; the script aborts if not.
- Beta and stable builds of the same marketing version share the same `versionCode` by design; they are on different tracks and track-filtered before version comparison.
- Phone and watch APKs for all tracks must be signed with the same shared release keystore; changing the keystore requires a coordinated manual-update bridging release.
- Token rotation requires a bridging build: a one-off APK built and side-loaded while the old token is still valid, so subsequent fetch requests use the new token.
- The RPi bind mount is `:ro` for the API container; only publish scripts write to the host path, never the API process.
