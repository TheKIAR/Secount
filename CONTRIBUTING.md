# Contributing to Secount

Secount is maintained as a personal product project. Contributions, fixes, and thoughtful technical feedback are welcome when they improve reliability, privacy, accessibility, or user experience.

## Development principles

- Keep shared logic platform-neutral where practical.
- Prefer small, testable components over giant UI functions.
- Do not commit generated release artifacts unless explicitly required.
- Never commit secrets or private user data.
- Add or update tests when changing business logic.
- Keep Android and desktop behavior consistent unless a platform-specific difference is intentional.

## Before opening a pull request

1. Run the shared tests.
2. Build the affected target.
3. Check the README if user-facing behavior changed.
4. Check that no credentials or private data were introduced.
5. Keep the change focused and explain any architectural trade-offs.
