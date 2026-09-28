# Verification report — Epanode 0.1.0

Verification date: 28 September 2026. This report describes the delivered APK and source, not a guarantee that all devices or provider links work. [Feature coverage](FEATURES.md) lists implemented behavior and known omissions.

## Results

| Check | Result | What it establishes |
| --- | --- | --- |
| Automated tests | **39 passed; 0 failed, 0 errors, 0 skipped** | Logic, Android-framework persistence, Compose workflows, playback item configuration, and two live provider checks |
| Android framework versions | API 29 and 35 | Robolectric tests exercise Android 10 and 15 framework behavior; these are not physical devices |
| Android lint | **0 errors, 32 warnings** | Static analysis passed; warnings remain, including API/style/dependency update suggestions |
| Optimized release build | Passed | Release compilation, resource packaging, and shrinking completed |
| Instrumentation test APK | Compiled | Device tests are provided, but could not be executed here |
| APK signature | Verified, v3, one signer | The delivered evaluation APK is signed with a development certificate |
| APK size | **6,227,291 bytes (5.94 MiB)** | Measured file size; this does not measure installed size or runtime memory |
| Fuzzy search benchmark | **226 ms for 10,000 fixture tracks** | One host-JVM measurement; not an Android latency or battery claim |

The final Gradle invocation completed successfully with `:app:testDebugUnitTest :app:lintDebug :app:assembleRelease :app:assembleDebugAndroidTest --continue`. Live checks were enabled with `EPANODE_LIVE_TESTS=1`. The environment used JDK 17, Gradle 8.13, Android platform 36, and build-tools 35.0.0. API 36 framework tests were not run.

## Functional coverage

| Suite | Passed | Coverage |
| --- | ---: | --- |
| MusicLogicTest | 15 | Tag priority, conservative filename cleanup, URI identity, deterministic recommendations, multilingual and typo-tolerant search, lyrics ranking, LRC offsets/timestamps, precise clip boundaries, URL classification, safe filenames, bounded large-library search |
| AndroidStoreTest | 16 | Eight scenarios on each of API 29 and 35: persistence after reopening, metadata overrides after rescans, playlist order and deduplication, transaction rollback on invalid backups, valid backup restoration, clip validation, scan pruning, download/retry/cancellation state |
| AndroidUiTest | 2 | One full Compose workflow on each API: navigation, typo search, liking, playlist creation and adding a song, saving a named clip, Best parts, Discover link entry, and Settings |
| PlaybackModelTest | 4 | Two scenarios on each API: Media3 clipping configuration and safe handling of invalid or missing clip data |
| CatalogLiveTest | 2 | Live music search and audio URL resolution with the first 4,096 audio bytes retrieved; public Spotify track metadata extraction |

UI tests use semantic actions and a controlled Compose clock. Screenshots were rendered from the real Android Compose view hierarchy using Robolectric native graphics. The pictured tracks are synthetic fixtures. These tests do not prove touch ergonomics, hardware decoding, audible loop seams, or system notification behavior.

The live search used “Kevin MacLeod Carefree” and resolved an M4A stream. The Spotify check exercised one public track. Full playlist imports, all regions, all provider URL variants, and completed on-device audio downloads were not verified by those two tests. Provider websites can change after this run.

## Nonfunctional checks and limits

- Search cost was exercised with 10,000 tracks. Search, scanning, and download work are dispatched away from the UI thread. No frame-time or cold-start measurements were taken on an Android device.
- Storage tests verify transactional backup rejection and preservation of local edits, playlist order, and explicit cancellation. Invalid paths, deceptive provider hosts, invalid clips, and bounded text inputs have automated coverage. This is not an external security audit or a fuzzing campaign.
- APK size and static lint were measured. The app uses bounded artwork caches and a 64 KB download buffer; these are implementation constraints, not measured peak RAM or energy consumption.
- No hardware CPU, RAM, battery, thermal, accessibility-service, or long-duration stability measurements are claimed.
- Automated tests ran against the debug variant. The optimized release built and its signature verified, but its runtime behavior has not been exercised on hardware.

## Device-testing blocker

No physical Android device was connected. An ARM64 Android emulator was installed and an API 35 virtual device created, but the emulator process crashed before Android booted. The host sandbox denied `sysctl hw.cachelinesize`; the emulator crashed with `SIGILL` in `init_cache_info`. A booted Android device was therefore unavailable for instrumentation, audio, Bluetooth, and power measurements. [Blocker record](test-results/emulator-blocker.txt).

The supplied instrumentation tests include synthetic WAV fixtures, UI/persistence journeys, playback repeat and clipping checks, and a best-parts queue check. They compiled successfully; **they have not passed a device run**.

## Required before treating this as a production release

1. Install the optimized APK on actual Android 10, 13, 15, and 16 devices. Exercise permission denial/revocation, first scan, a large library, removable storage, SAF folders, and rescans after moving/deleting files.
2. Run `./gradlew :app:connectedDebugAndroidTest` on a connected device, then manually repeat the core journeys with the optimized APK.
3. Listen to MP3, AAC/M4A, FLAC, Ogg, and WAV playback; seek near boundaries; repeat individual clips and playlist clips; assess audible seams and transitions. Exercise malformed and unsupported files.
4. Verify background and locked-screen playback, media notifications, headset controls, Bluetooth reconnects, calls/audio-focus loss, unplug events, process death, queue restore, speed, sleep timer, and equalizer support.
5. Complete direct, YouTube, YouTube Music, and public Spotify track/playlist imports. Interrupt network, change Wi-Fi/mobile constraints, cancel and retry, exhaust storage, reboot during a job, and inspect for duplicate tracks or orphaned partial files.
6. Measure cold start, UI frame timing, peak and steady RAM, CPU while idle and playing, and battery during a multi-hour local playback run. Repeat with a large library and screen off. No numerical resource target has yet been established by device measurement.
7. Check TalkBack, large fonts, display scaling, narrow screens, landscape, Unicode filenames, and multiple locales. Run a longer playback/import soak and review crash logs.

## Evidence

Hostnames and local workspace paths have been redacted from published logs; test outcomes are unchanged.

- [Final build and test log](test-results/build-and-tests.log)
- [Lint report](test-results/lint.txt)
- [Logic results](test-results/TEST-app.epanode.core.MusicLogicTest.xml)
- [Storage results](test-results/TEST-app.epanode.core.AndroidStoreTest.xml)
- [UI results](test-results/TEST-app.epanode.core.AndroidUiTest.xml)
- [Playback model results](test-results/TEST-app.epanode.core.PlaybackModelTest.xml)
- [Live provider results](test-results/TEST-app.epanode.core.CatalogLiveTest.xml)
- [APK signature verification](test-results/apk-signature.txt)
- [APK package inspection](test-results/apk-package.txt)
- [Home screenshot](../screenshots/home.png), [Best parts](../screenshots/best-parts.png), [Discover](../screenshots/discover.png)

Delivered APK SHA-256:

```text
362807a60af69e96bbf5f05ffd3f14b37d86ab5f5770566d2856edb6a351f71c
```

Development signing certificate SHA-256:

```text
36f0e70fe91e719558221b2cb622275e9c1b7dda516fd4eb34aff4edc2231e62
```
