# Secount Threat Model

Secount is a personal cross-platform product. This document records the security boundary so implementation and future changes remain explicit.

## Assets
- Countdown/event data.
- Secret messages and replies.
- Pairing credentials and sync payloads.
- App PIN state.
- Optional encrypted backups.
- Photos attached to countdowns.
- Local preferences and platform data.

## Trust boundaries
1. **Local device** — trusted only to the extent that the OS account and filesystem are trusted.
2. **Secount application** — validates local input and protects secrets at rest where the platform implementation supports it.
3. **Pairing/relay path** — treated as untrusted transport. Authenticated ENC2 payloads protect message integrity/confidentiality for the supported pairing flow.
4. **GitHub CI/release infrastructure** — trusted to build and publish artifacts, but credentials must remain in repository/organization secrets.

## Main threats and mitigations

| Threat | Current mitigation | Remaining work |
|---|---|---|
| Tampered sync payload | ENC2 authenticated encryption/tag verification | Physical/network failure testing |
| Legacy payload exposure | ENC1 compatibility retained | Remove only after migration window |
| Pairing-code guessing | Short-lived pairing flow and confirmation | Stronger challenge/handshake design |
| Local PIN guessing | Hashed PIN and lock state | Rate limiting / lockout policy |
| Backup disclosure | Optional encrypted backup | Device-level storage validation |
| Secret leakage in logs | No intentional secret logging | Periodic log audit |
| Malformed imported data | Parser validation and tests | Fuzz/property testing |
| CI credential exposure | GitHub Actions permissions and secret hygiene | Periodic permissions review |

## Security invariants
- Never log secret-message plaintext.
- Never trust relay responses without authenticated verification.
- Reject malformed encrypted payloads rather than attempting partial recovery.
- Keep pairing credentials out of source control.
- Release workflows must not publish debug artifacts.
- Security changes require regression tests.

## Out of scope for automated repository validation
A repository/CI audit cannot prove behavior on every Android vendor, Windows installation, filesystem, notification implementation, or battery profile. Those require real-device/real-machine validation.
