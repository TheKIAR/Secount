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

CI builds without a keystore produce `androidApp-release-unsigned.apk`; CI labels it `-android-unsigned.apk` so it is never mistaken for a production download. A version-tag production release **requires** a signed `androidApp-release.apk` and the release workflow refuses to publish without one. To enable signed releases, generate a release keystore once on a trusted machine, store it plus its passwords as GitHub Actions secrets, and wire them into `release.yml` — never commit the keystore or its passwords to the repository.

## Rollback

If a release is defective, publish a corrected patch release rather than rewriting an existing tag. Keep the previous known-good release available until the replacement is verified.
