# Secount ♥ Countdown Studio

**Se**cret + **Count** — a modern countdown app where moments can stay private until zero.

Secount is a cross-platform **Kotlin + Compose Multiplatform** app for Windows and Android. The same shared UI and event model power both platforms.

> Birthdays • Exams • Weddings • Holidays • Trips • Work deadlines • App launches • Game launches • Private surprises

## ✨ Real runtime preview

This is captured from the **running Secount desktop application**, not a mockup.

### 🏠 Home screen

![Secount home screen](https://raw.githubusercontent.com/TheKIAR/Secount/main/assets/runtime-screenshot.png)

GitHub Actions automatically launches the current desktop app normally and captures a fresh home-screen screenshot.

## 💗 Why Secount?

Secount combines a normal countdown with a private-message experience. You can create a countdown for anything, optionally arm a secret message, and reveal it only when the countdown reaches zero.

### Core experience

- **Modern Material UI** with soft surfaces, rounded cards and clear hierarchy.
- **Valentine-first visual language** with a warm pink/rose palette, while keeping the interface clean and approachable.
- **Live countdowns** with days, hours, minutes and seconds.
- **Mine / Inbox** views for personal countdowns and partner surprises.
- **Letter-style secret reveal** that writes the message character by character.
- **Explicit OPEN MESSAGE step** before a secret is revealed.
- **Replies and conversation threads** with You / Partner labels.
- **Delivered / Seen receipts** with timestamps.
- **Search, filters and sorting** for larger countdown collections.
- **List and Calendar views**.
- **Per-countdown categories, icons and accent colors**.
- **Photo attachments** with synced thumbnails.
- **6-letter pairing codes + QR / copy-paste pairing**.
- **Offline retry and sync** for edits, replies, deletes and receipts.
- **App PIN + biometric unlock** where supported.
- **Backup and restore**, including optional encrypted backups.
- **Windows + Android** from one shared Kotlin/Compose codebase.

## 🔐 Private secret-message flow

1. Create a countdown.
2. Enable **Secret message at zero**.
3. Write the private message.
4. Keep using the app normally — the secret remains sealed.
5. At zero, the recipient gets the secret-message prompt.
6. **OPEN MESSAGE** starts the letter-style reveal.
7. The recipient can reply inside the same conversation.

The secret text is also automatically armed when text is entered, so accidentally forgetting the toggle does not leave the message behind as an ordinary public message.

## 🤝 Partner pairing

Two Secount installations can connect using a six-letter pairing code.

- Share your code or QR payload.
- Enter your partner's code.
- Both sides confirm the connection.
- Countdown edits and deletes sync between paired devices.
- Partner surprises remain hidden until their target day.
- Disconnect requires agreement from both sides.

## 🎨 UI & themes

The default **Valentine** theme is designed to feel romantic without looking like a novelty card:

- Soft warm background
- High-contrast typography
- Rounded Material surfaces
- Accent-colored countdown identity
- Compact status pills
- Clear primary actions
- Dark-mode support
- Additional themes: Midnight Android, Ocean, Sunset, Forest, Lavender and Porcelain

## 📱 Platforms

### Windows

`Secount.exe` is the packaged Windows application. The bundled runtime means users do not need to install Java separately.

You can also run the desktop JAR with:

```bat
run.bat
```

### Android

`Secount-debug.apk` is the test APK and requires Android 8.0 / API 26 or newer.

## 🛠️ Tech stack

- **Kotlin**
- **Jetpack Compose / Material 3**
- **Compose Multiplatform**
- **Gradle**
- **Android**
- **Desktop JVM**
- **ZXing** for QR generation
- **GitHub Actions** for tests, builds and home-screen runtime capture

## 🧱 Project structure

```text
android/
├── shared/       # Shared logic + Compose UI
├── androidApp/   # Android entry point
└── desktopApp/  # Windows / desktop entry point
```

The shared module contains the event model, storage, pairing/sync logic, PIN protection and the complete Compose UI.

## ▶ Build locally

### Windows desktop

```bat
build.bat
```

This builds the desktop JAR and Windows executable.

### Android APK

```bat
android\build-apk.bat
```

### Shared tests

```bat
gradle -p android :shared:desktopTest
```

## ⚙️ Continuous integration

GitHub Actions automatically:

- Runs the shared-logic test suite.
- Builds `Secount.jar`.
- Builds `Secount.exe`.
- Builds `Secount-debug.apk`.
- Captures the **current normal home screen** from the running desktop application.
- Commits the fresh home-screen media back to the repository using `github-actions[bot]`.

## 👨‍💻 Author

**Md. Ragib Ashhab**  
CSE Student • Java / Python • AI • Computer Graphics • Software Projects

- 🌐 Portfolio: [ragibashhab.netlify.app](https://ragibashhab.netlify.app/)
- 🔗 Linktree: [linktr.ee/RagibAshhab](https://linktr.ee/RagibAshhab)
- 💼 LinkedIn: [md-ragib-ashhab-768a19240](https://www.linkedin.com/in/md-ragib-ashhab-768a19240/)
- 🐙 GitHub: [TheKIAR](https://github.com/TheKIAR)

---

*Building practical software, turning coursework into projects, and improving one release at a time.*
