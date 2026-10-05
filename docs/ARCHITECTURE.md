# Secount Architecture

Secount is a personal cross-platform product built with Kotlin and Compose Multiplatform. The architecture is intentionally shared across Android and desktop while keeping platform-specific integrations isolated.

## Layers

```text
UI / Compose
    |
    v
Application state + user actions
    |
    +--> Event model / event store
    +--> Countdown and date logic
    +--> Pairing / synchronization
    +--> PIN / privacy controls
    +--> Backup / restore
    |
    v
Platform adapters
    +--> Android
    +--> Desktop JVM
```

## Current modules

- `android/shared` — shared event model, persistence, pairing/sync, protection, tests, and Compose UI.
- `android/androidApp` — Android application entry point, manifest, resources, and widget integration.
- `android/desktopApp` — desktop entry point and Windows packaging.

## Refactoring boundaries

The largest current shared files are deliberately treated as refactoring targets. Future changes should extract focused components for:

- home/countdown presentation
- event creation and editing
- secret-message reveal
- pairing and sync UI
- settings and security UI
- reusable Material components

Business logic should remain independently testable and should not depend on Compose rendering.

## Data and synchronization principles

1. Local state should remain usable when offline.
2. Synchronization should be retryable and idempotent.
3. User-visible conflict behavior should be deterministic.
4. Sensitive data must not be written to logs.
5. Backup/restore must preserve data integrity and fail safely.

## Platform principle

Android and desktop should share the same product model and user experience. Platform adapters are used only where operating-system APIs differ, such as biometrics, file dialogs, notifications, and native packaging.

## Security boundary

Relay traffic is treated as untrusted. Pairing validates the sender identity before applying synchronized events, and payload encryption now includes authentication so tampered ciphertext is rejected. Local PIN protection and backup protection are separate concerns and must not be assumed to provide account-level identity or server-side access control.

## Current technical debt

ui/App.kt remains intentionally large and is the next major decomposition target. This document describes the desired boundaries; it does not claim that every boundary has already been extracted into a separate module.