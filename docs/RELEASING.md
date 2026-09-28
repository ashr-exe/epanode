# Releases

The first release is a manually published **0.1.0 evaluation prerelease**. Its optimized APK is signed with the development certificate recorded in TESTING.md. It must not be described as a production-certified release. The signing key is not in this repository or the source archive.

## Future signed releases

The `Publish signed release` workflow is manual. It checks out an existing version tag, tests/lints/builds the app, signs and verifies the optimized APK, checks its version, packages corresponding source, writes checksums, and publishes a GitHub Release. It is not triggered merely by pushing a tag.

Before using it, configure these repository Actions secrets with the release owner's signing key:

- `ANDROID_KEYSTORE_BASE64`: base64-encoded Android keystore.
- `ANDROID_KEYSTORE_PASSWORD`: keystore password.
- `ANDROID_KEY_ALIAS`: signing key alias.
- `ANDROID_KEY_PASSWORD`: key password.

These secrets are intentionally not embedded in source. **They are not configured by the initial repository setup.** A missing secret stops the manual workflow before building. Keep a separate secure backup of the signing key; later Android updates must use the same signing identity. Moving from the evaluation development certificate to a new production certificate generally requires exporting your data, uninstalling the evaluation app, and installing the new one.

For each release:

1. Update `versionCode` and `versionName` in `app/build.gradle.kts`, CHANGELOG.md, feature coverage, and any applicable test results.
2. Run local verification, review dependency/license changes, and get Android CI green for the exact commit. Complete the relevant physical-device checks from TESTING.md.
3. Write user-facing notes in `docs/releases/<version>.md`, including honest verification limits.
4. Create and push an annotated `v<version>` tag. Do not move a published release tag.
5. Run the manual publishing workflow with that tag. Keep the prerelease option selected until production acceptance is complete.
6. Verify the Release page, asset checksums, signature certificate, and installation/update behavior.

The regular Android CI workflow includes Android 15 emulator tests using [Android Emulator Runner](https://github.com/ReactiveCircus/android-emulator-runner). CI build artifacts are temporary diagnostics; Releases are the durable download location. The unsigned optimized APK produced by CI is not directly installable.
