# Mari App — Claude Instructions

## CRITICAL: No Local Installs Without Permission

**NEVER install, download, or execute package managers or system-level commands that modify the local machine without express user permission.**

This includes but is not limited to:
- `apt`, `apt-get`, `brew`, `snap`, `dnf`, `yum`
- `npm install -g`, `pip install` (system-level)
- `sdkmanager`, `avdmanager`, Android SDK components
- Any `sudo` command
- Downloading and running install scripts (`curl ... | bash`, etc.)

If a tool or dependency is missing, **ask the user** to install it rather than doing so yourself.

## CRITICAL: Build and Test on Windows via SSH, Never Locally

**NEVER run `./gradlew` build or test tasks on the local Raspberry Pi (arm64).** The Pi lacks the native libraries required (e.g. `conscrypt_openjdk_jni-linux-aarch_64` for Robolectric) and several tests fail with `UnsatisfiedLinkError` on this architecture.

All Gradle builds and tests must be executed on the Windows machine via SSH:

- Host: `marilinda@192.168.1.100`
- Repo on Windows: mirror path (ask the user if unsure)
- Example: `ssh marilinda@192.168.1.100 "cd <repo-path> && ./gradlew :app:testDebugUnitTest"`

This applies to every `gradlew` invocation — `:assembleDebug`, `:test*`, `:lint*`, `:check`, etc. Only read-only static analysis (grep, file reads) should be done locally.

ADB, APK install, and on-device verification also run on Windows (see memory `reference_adb_windows.md`).
