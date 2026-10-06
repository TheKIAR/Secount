# Backup, Restore, Import, Export

Secount keeps one local truth: `events.json` (plus a `photos` folder).
Backup flows copy that truth out of — and back into — the app safely.

## Export backup (BACKUP section → Export backup)

- Produces a JSON array of your countdowns, identical in shape to
  `events.json`, so the file can be inspected or moved between Android
  and desktop by hand.
- With a password, the export is wrapped in an authenticated `ENC2`
  envelope (`BackupCrypto`): anyone holding the file learns nothing
  without the password, and tampered files are rejected instead of
  half-imported.

## Import backup (BACKUP section → Import backup)

- Paste/choose a previously exported file. Encrypted backups ask for
  the password; a wrong password fails closed with no changes applied.
- Entries with a blank title or unusable data are skipped and reported
  rather than creating broken countdowns.

## Manual file copy (advanced)

- Desktop: copy `%USERPROFILE%\.secount\events.json` (and `photos/`).
- Android: files live in app-private storage; prefer in-app export.
- The importer tolerates schema drift: unknown fields are ignored,
  missing fields get safe defaults, and malformed files never wipe
  the existing store on a failed load.

## Rules that protect you

- There is **no password recovery**: a lost backup password cannot be
  reset by anyone. Keep it somewhere safe.
- Never publish an encrypted backup alongside hints about its password.
- Restore merges by ID — re-importing the same file does not duplicate
  countdowns.
- Confirm before destructive operations in the app; there is no undo
  after a delete syncs to a paired partner.
