# Production Readiness

This is the release gate for Secount as a personal software product.

## Repository / code

- [x] Shared Kotlin/Compose architecture exists.
- [x] Android and Windows targets exist.
- [x] Security regression tests exist.
- [x] Authenticated ENC2 payload support exists.
- [x] QA matrix is tracked in docs/QA.md.
- [ ] Continue decomposing the remaining screen and state logic in App.kt.
- [ ] Complete a fresh full static/security review after each major release.
- [x] Add deterministic malformed-input coverage for import and encrypted payload parsers.

## Automated validation

- [x] Shared tests in CI.
- [x] Android release build in CI.
- [x] Desktop JAR/EXE build in CI.
- [x] SHA-256 release checksums.
- [x] CodeQL workflow.
- [x] Dependabot.
- [x] Confirm the latest post-change CI run is green.
- [x] Confirm the latest CodeQL run is clean.
- [x] Validate a fresh tagged release from a clean runner.

## Product validation

- [ ] Android install/update test on a physical device.
- [ ] Windows install/update test on a clean machine.
- [ ] Real notification delivery test.
- [ ] Android widget test after reboot and when the app is not running.
- [ ] Two-device pairing/sync test.
- [ ] Battery/background behavior observation.
- [ ] Long-running countdown observation across sleep/wake and timezone changes.

These last items require real operating-system environments and cannot be truthfully certified from repository tooling alone.
