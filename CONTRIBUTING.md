# Contributing

Keep Epanode focused on fast, accessible listening from a local music library. Prefer Android platform/Media3 capabilities, bounded background work, and small dependencies with compatible licenses.

Use JDK 17 and the SDK described in README.md. Before submitting a change, run `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease`. Run instrumentation on an emulator or device for UI, playback, permission, or storage changes. Online provider tests are opt-in with `EPANODE_LIVE_TESTS=1`; do not hide provider failures as passing tests.

Keep test media synthetic or clearly licensed. Do not commit private libraries, credentials, signing keys, device identifiers, generated build files, or local SDK paths. Update feature coverage and testing limits accurately. Contributions are under the repository's GPL-3.0-or-later license. New dependencies need a license review and updated inventory/notices/source archives.
