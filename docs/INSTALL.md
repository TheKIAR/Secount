# Install Secount

## Android

1. Download the release APK from GitHub Releases (`Secount-<version>-Android.apk`).
  Do not install CI files named `-android-unsigned.apk`; Android rejects
  unsigned release APKs. Updating an existing installation also requires the
  same signing key as the installed copy.
2. Open the APK on the device (Android 8 / API 26 or newer).
3. Allow **Install unknown apps** for the browser/file manager when asked.
4. Launch **Secount** and create your first countdown.

Your data lives in the app's private storage with your photos; uninstalling
the app removes it. Back up first (see `BACKUP.md`).

## Windows

Two options from GitHub Releases:

- **Installer** (`Secount-<version>-Windows.exe`): run it and follow setup.
  The app appears with the Secount mark on the taskbar and Start menu.
- **JAR** (`Secount-<version>.jar`): needs a Java 17+ runtime.
  Run `java -jar Secount-<version>.jar`, or double-click `run.bat` in a
  development checkout (it picks the bundled JDK 17 automatically).

Desktop data lives in `%USERPROFILE%\.secount` (`events.json` plus a
`photos` folder). The folder is created on first launch.

## Upgrade

- Install the newer release over the old one; your countdowns carry over.
- `events.json` is forward- and backward-tolerant: unknown fields are
  ignored and missing fields fall back to safe defaults.
- If a release misbehaves, reinstall the previous known-good release and
  restore from your backup rather than editing data files by hand.

## Uninstall

- Android: uninstall normally; private app data is removed with it.
- Windows installer: uninstall via **Apps & features**. The `.secount`
  data folder is left behind — delete it manually for a full wipe.

## Troubleshooting

- **APK won't install**: confirm the file is the signed `-Android.apk`
  (not `-unsigned`), that Android 8+ is installed, and that installs from
  your browser are allowed. If Android reports a signature conflict, the
  APK was signed with a different key; back up app data before uninstalling
  the older copy.
- **JAR won't start**: `java -version` must report 17+. Machines with only
  Java 8 fail silently on double-click — use `run.bat` or install JDK 17.
- **Blank window / crash on desktop**: check
  `%USERPROFILE%\.secount\crash_log.txt`; the app offers to share it.
- **Version mismatch with your partner**: pairing works across versions,
  but update both sides so encryption envelopes and sync behavior match.
