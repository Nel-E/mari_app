# Remote ADB Access to Windows-Connected Device

## Accessing the Windows device from Linux

Control the device by running ADB over SSH on the Windows host.

Methodology reminder:
- Build APK on Windows from synced codebase (`M:\temp_android\mari_app`).
- Do not use Linux-built APKs for final validation evidence.

Mandatory Windows temp root policy:
- All temporary Windows workspaces must be created under `M:\temp_android`.
- This project's workspace: `M:\temp_android\mari_app`.
- Do not create temp workspaces directly under `M:\` outside `M:\temp_android`.

Examples:
```
ssh marilinda@192.168.1.100 "cmd /c \"\"M:\\.android-studio\\Sdk\\platform-tools\\adb.exe\" devices -l\""
ssh marilinda@192.168.1.100 "cmd /c \"\"M:\\.android-studio\\Sdk\\platform-tools\\adb.exe\" connect 192.168.1.120:38545\""
ssh marilinda@192.168.1.100 "cmd /c \"\"M:\\.android-studio\\Sdk\\platform-tools\\adb.exe\" -s 192.168.1.120:38545 shell am start -n com.mari.app/.MainActivity\""
ssh marilinda@192.168.1.100 "cmd /c \"\"M:\\.android-studio\\Sdk\\platform-tools\\adb.exe\" -s 192.168.1.120:38545 exec-out uiautomator dump /dev/tty\""
```

Capture screenshots and pull UI dumps:
```
ssh marilinda@192.168.1.100 "cmd /c \"\"M:\\.android-studio\\Sdk\\platform-tools\\adb.exe\" -s 192.168.1.120:38545 exec-out screencap -p\"" > docs/evidence/screenshots/mari_home.png
ssh marilinda@192.168.1.100 "cmd /c \"\"M:\\.android-studio\\Sdk\\platform-tools\\adb.exe\" -s 192.168.1.120:38545 exec-out uiautomator dump /dev/tty\"" > docs/evidence/ui_dumps/mari_home.xml
```

For details of Windows SSH access see `guides/windows_ssh_access.md`.

## Notes / Common Issues
- Use `adb connect 192.168.1.120:38545` before install/launch when the phone is not listed.
- Use only serials in `device` state.
