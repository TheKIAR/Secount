# Sync and Pairing

Secount uses a peer-oriented pairing flow for supported countdown sharing, replies, deletes and receipts.

## Flow
1. One installation creates a short pairing code.
2. The second installation enters/scans the pairing payload.
3. Both sides confirm the relationship.
4. Sync exchanges supported state through the relay/transport path.
5. Payloads are authenticated before trusted state is applied.
6. Temporary/offline failures are retried by the application.
7. Unanswered pairing requests (incoming list, outgoing pending code) expire after 7 days; the incoming list is capped at the 20 newest.

## Failure model
The sync layer must assume:
- the network can disappear at any time;
- a request can be duplicated;
- a response can arrive late;
- a relay can return malformed data;
- both devices can edit related data;
- either device can restart during synchronization.

## Conflict policy
When changing synchronization logic, preserve these invariants:
- IDs remain stable across retries.
- A duplicate delivery must not create a second countdown.
- A delete must close an open editor/secret view for the deleted item.
- Partner-only surprises remain hidden until their target date.
- A failed sync must not destroy the last known local state.
- Authentication failures are treated as rejected data, not recoverable plaintext.

## Manual verification
Use two installations and test:
- successful pairing;
- wrong/expired code;
- offline pairing;
- reconnect;
- edit on either side;
- delete on either side;
- reply and seen receipt;
- duplicate sync;
- restart during sync;
- recovery after network loss;
- disconnect request/confirmation.

Automated regression coverage belongs in commonTest; device-to-device behavior remains an integration/manual validation responsibility.
