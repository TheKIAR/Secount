# Changelog

All notable Secount product changes are recorded here.

## Unreleased

### UI decomposition

- Extracted the app header, browse controls, settings drawer, and countdown list/empty states from App.kt into shared UI components.

## [1.1.2] - 2026-10-07

### Biometric authentication

- Require an Android Keystore-backed AES-GCM operation to complete biometric unlock.
- Enroll the encrypted proof through an authenticated prompt and reject invalidated or tampered proofs.
- Add regression coverage for biometric proof encryption and verification.

## [1.1.1] - 2026-10-07

### Product fixes

- Added the Secount brand mark to the shared Android and desktop app headers.
- Kept countdown delete actions clear of the persistent New countdown button.
- Configured signed Android releases through protected CI secrets and aligned artifact packaging with the current version.

### Security

- Added authenticated ENC2 payloads with HMAC-SHA-256 integrity protection.
- Kept ENC1 decryption compatibility for existing local data.
- Added tamper and wrong-key regression tests.
- Documented pairing-code security limitations.
- Pairing requests and pending codes expire after 7 days; incoming list capped.
- Added PIN lockout regression coverage.

### Engineering

- Extracted the home hero header, browser controls, startup checks, and pairing helpers from App.kt.
- Added deterministic malformed import and encrypted-payload regression coverage.
- Fixed Windows build output discovery for versioned JAR and EXE names.
- Added countdown/date edge-case regression coverage.
- Added a dedicated QA matrix for Android, Windows, synchronization, notifications and release sign-off.
- Made CI/release Windows artifact discovery version-independent.
- Updated Android SDK setup action to v4.
- Split UI into UiKit/Cards/Dialogs/UpdateCheck and logic into Crypto/EventJson.
- Fixed CI: CodeQL build step, screenshot retry, unsigned-APK handling.
- Added install, backup, and FAQ guides.

## Earlier releases

## [1.1.0] - 2026-10-06

### Product quality

- Production-focused release and repository cleanup.
- Clearer release and artifact strategy.
- Expanded security, architecture, testing, and operational documentation.
- Improved CI separation between validation, runtime media, and release packaging.
- Android release packaging is now part of the release pipeline.
- Added automated checksums for release artifacts.

### Documentation

- Added security policy.
- Added contribution guidelines.
- Added architecture documentation.
- Added release documentation.
- README repositioned Secount as a personal product project rather than coursework.

### Engineering direction

- Large UI and synchronization files are now explicitly tracked as refactoring targets.
- Security-sensitive code paths are documented as audit areas.
- CI is designed to validate before packaging releases.

## [1.0.0]

Initial public Secount release line.
