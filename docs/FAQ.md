# Secount FAQ

## Pairing & sync

**How do two devices connect?**
Each installation shows a six-letter code. Enter your partner's code on
your side and yours on theirs; the next sync links both. Codes (or the
`SECOUNT1:...` payload) can be shared by QR, copy-paste, or read aloud.

**My request never completes.**
Both sides must enter each other's code, then sync (the pairing dialog
syncs every few seconds while open). Unanswered requests expire after
7 days — just re-enter the code.

**How do we disconnect?**
Either side requests it; the link drops only when the partner agrees.
This prevents one offline device from orphaning the other's state.

**Is my data private on the relay?**
Countdowns, replies, and receipts travel encrypted (`ENC2` envelope,
key derived from both pairing codes) and tampered payloads are
rejected. The relay operator still learns *that* two devices sync, and
pairing codes themselves are short — treat them like passwords and
share them out-of-band.

**Sync conflicts?**
Last edit wins by timestamp; reply threads merge instead of
overwriting. Only the countdown's creator can change or delete it via
sync, so a partner can never rewrite your items.

## PIN & privacy

**Is a PIN required?**
No. Fresh installs open unlocked. Set one in the PIN dialog to lock on
launch and when returning from background.

**Wrong PIN?**
Five free attempts, then escalating lockouts (30s up to 10 minutes).
Biometrics (where available) unlock without the PIN.

**I forgot my backup password.**
It cannot be recovered — encrypted backups stay sealed. Your on-device
countdowns are unaffected.

## Notifications & behavior

**Reminders?**
Each countdown can remind 1 day and/or 7 days ahead, with Chime/Soft/
Silent sounds. Android needs notification permission; battery
optimizations can delay background delivery.

**Midnight/timezones?**
Countdowns are date-based and recompute every render. Crossing midnight
or changing timezones updates the display; recurring yearly/monthly/
weekly events roll to their next occurrence automatically.

## Install & data

**Where is my data?**
Desktop: `%USERPROFILE%\.secount`. Android: app-private storage.
See `INSTALL.md` and `BACKUP.md`.

**Upgrade safety?**
Install over the old version; old files always load (unknown fields
ignored, missing fields defaulted). Keep a backup before major jumps.

**The JAR won't start / APK won't install?**
See the troubleshooting section of `INSTALL.md` (Java 17+, signed APK,
unknown-apps permission).
