## Unreleased

### Security
- Added authenticated ENC2 payloads with HMAC-SHA-256 integrity protection.
- Kept ENC1 decryption compatibility for existing local data.
- Added tamper and wrong-key regression tests.
- Documented pairing-code security limitations.

### Engineering
- Added countdown/date edge-case regression coverage.
- Added a dedicated QA matrix for Android, Windows, synchronization, notifications and release sign-off.
- Made CI/release Windows artifact discovery version-independent.
- Updated Android SDK setup action to v4.

# Changelog

All notable Secount product changes are recorded here.

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
