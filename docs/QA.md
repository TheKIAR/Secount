# Secount QA Matrix

This is the verification checklist for the personal Secount product. Automated tests cover shared logic and synchronization; platform checks require the corresponding runtime.

## Automated

- [x] Shared Kotlin tests
- [x] Pairing and synchronization integration tests
- [x] Authenticated relay payload round-trip
- [x] Tampered relay payload rejection
- [x] Wrong-key rejection
- [x] Backup encryption round-trip
- [x] Deterministic malformed import and encrypted-payload inputs
- [x] Countdown leap-day and expiry cases
- [x] Monthly short-month rollover
- [x] Invalid time normalization
- [ ] CodeQL result reviewed and findings triaged
- [ ] Release workflow completed successfully for the current version tag

## Android

- [ ] Clean release APK install
- [ ] App start/resume after background
- [ ] PIN and biometric flows
- [ ] Countdown accuracy while backgrounded
- [ ] Notifications and notification channels
- [ ] Widget refresh and reboot behavior
- [ ] Pairing, offline retry and reconnection
- [ ] Photo attachment and cleanup
- [ ] Backup/restore on a real device

## Windows

- [ ] Clean-machine EXE installation
- [ ] JAR launch
- [ ] Window/icon behavior
- [ ] Notifications
- [ ] File storage and permissions
- [ ] Pairing and offline retry
- [ ] Upgrade/reinstall behavior

## Product QA

- [ ] Empty, loading, error and expired states
- [ ] Create/edit/delete countdowns
- [ ] Search/filter/sort
- [ ] Calendar/list views
- [ ] Secret reveal and reply flow
- [ ] Delivered/seen receipts
- [ ] Large event collections
- [ ] Restart, sleep/wake and timezone changes
- [ ] Accessibility and keyboard navigation
- [ ] Performance and memory sanity check

## Release sign-off

A release should not be called production-ready until automated CI is green and the Android/Windows runtime checks above have been exercised on real targets.
