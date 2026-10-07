# Release Process

Secount releases are built from Git tags. The repository source remains the source of truth; generated binaries are distributed as release artifacts instead of being continuously committed to the root directory.

## Release checklist

1. Update `versionName` / desktop package version.
2. Update `CHANGELOG.md`.
3. Run the shared tests locally.
4. Push the version commit.
5. Create and push a tag such as `v1.1.0`.
6. GitHub Actions validates the project and builds Windows, desktop JAR, and Android release artifacts.
7. Checksums are generated for every release artifact.
8. The GitHub Release contains the artifacts and generated checksums.

## Artifact policy

Release artifacts should be named with the version and platform. Debug APKs are for development and should not be presented as production downloads.

## Android signing

Android signing is read from `SECOUNT_UPLOAD_STORE_FILE`, `SECOUNT_UPLOAD_STORE_PASSWORD`, `SECOUNT_UPLOAD_KEY_ALIAS`, and `SECOUNT_UPLOAD_KEY_PASSWORD`. GitHub Actions reads the keystore from `SECOUNT_UPLOAD_KEYSTORE_BASE64` and the three credential secrets, writes it to the runner's temporary directory, and removes it with the runner. Never commit the keystore or print credentials in logs.

The same keystore must sign every update for an installed Android app. Keep a secure offline backup of the keystore and credentials. If CI signing is not configured, ordinary CI may produce a clearly labeled unsigned APK; Android will reject it as a production install. The tagged release workflow fails closed if signing secrets are missing.
The same keystore must sign every update for an installed Android app. Keep a secure offline backup of the keystore and credentials. If CI signing is not configured, ordinary CI may produce a clearly labeled unsigned APK; Android will reject it as a production install. The tagged release workflow fails closed if signing secrets are missing.

## Rollback

If a release is defective, publish a corrected patch release rather than rewriting an existing tag. Keep the previous known-good release available until the replacement is verified.
