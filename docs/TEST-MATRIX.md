# Test Matrix

## Countdown
- [x] Normal future countdown
- [x] Due-today behavior
- [x] Expired/past behavior
- [x] Leap-day handling
- [x] Invalid hour/minute normalization
- [x] Monthly short-month rollover
- [ ] Sleep/wake on Android
- [ ] Timezone/DST change on real devices
- [ ] Long-running performance observation

## Events and storage
- [x] JSON round trip
- [x] Legacy defaults
- [x] Empty store
- [x] Special characters and Unicode
- [ ] Corrupted-file recovery matrix
- [ ] Large collection/performance benchmark
- [ ] Migration test for every future schema revision

## Security
- [x] Authenticated encrypted payload round trip
- [x] Tampered ciphertext rejection
- [x] Wrong-key rejection
- [x] Backup encryption round trip
- [x] Malformed encrypted payload handling
- [ ] PIN brute-force/rate-limit validation
- [ ] Property/fuzz testing

## Sync
- [x] Core sync regression coverage
- [ ] Two-device pairing
- [ ] Offline/reconnect
- [ ] Duplicate delivery
- [ ] Simultaneous edits/conflicts
- [ ] Restart during sync
- [ ] Real network interruption

## Platform
- [x] Desktop/shared automated tests
- [x] CI Android release build
- [x] CI Windows packaging
- [ ] Physical Android install
- [ ] Physical Android notification/widget validation
- [ ] Clean Windows machine install/update
