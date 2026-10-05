# Security Policy

Secount is a personal cross-platform application. Security and privacy are treated as product requirements, not optional extras.

## Reporting a vulnerability

Please do not publish sensitive security details in a public issue. Contact the project owner privately through the contact channels listed on the owner's GitHub profile.

When reporting a security issue, include:

- affected version or commit
- platform (Android or Windows)
- reproduction steps
- expected and actual behavior
- security impact

Please do not include real private messages, personal data, pairing codes, PINs, or private backups in a report.

## Security scope

Security-sensitive areas include:

- local event and message storage
- app PIN and biometric gating
- pairing and synchronization
- backups and restore
- platform permissions
- release artifacts and CI workflows

## Safe handling

Never commit real secrets, credentials, private backups, production pairing codes, or personally identifying test data to the repository.

## Security implementation notes

- Pair and backup payloads now use an authenticated ENC2 envelope with HMAC-SHA-256 integrity protection.
- ENC1 payloads remain readable for backward compatibility with existing local data.
- Pairing still uses short human-entered codes and the public ntfy relay. The pairing code is not treated as a high-entropy cryptographic secret, so this is not equivalent to a fully audited end-to-end encrypted protocol.
- PIN verification uses salted SHA-256, constant-time comparisons, and progressive lockout after repeated failures.
- Do not place real credentials, private keys, production tokens, or personal backup data in the repository.