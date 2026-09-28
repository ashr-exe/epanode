# Verification report — Epanode 0.1.0

Verification date: 28 September 2026. This report describes the delivered APK and source, not a guarantee that all devices or provider links work. [Feature coverage](FEATURES.md) lists implemented behavior and known omissions.

## Results

| Check | Result | What it establishes |
| --- | --- | --- |
| Local automated tests | **39 passed; 0 failed, 0 errors, 0 skipped** | Logic, Android-framework persistence, Compose workflows, playback item configuration, and two live provider checks |
| Android framework versions | API 29 and 35 | Robolectric tests exercise Android 10 and 15 framework behavior; these are not physical devices |
| Android lint | **0 errors, 33 warnings** | Static analysis passed; warnings remain, including API/style/dependency update suggestions |
| Optimized release build | Passed | Release compilation, resource packaging, and shrinking completed |
| Android 15 instrumentation | **3 passed; 0 failed** | Real emulator touch workflow, clip-position wrapping, highlight queue advancement, and persistence |
| GitHub Android CI and CodeQL | Passed | Hosted build, tests, lint, and Kotlin/Java security analysis completed |
| APK signature | Verified, v3, one signer | The delivered evaluation APK is signed with a development certificate |
| APK size | **6,227,291 bytes (5.94 MiB)** | Measured file size; this does not measure installed size or runtime memory |
| Fuzzy search benchmark | **243 ms for 10,000 fixture tracks** | One host-JVM measurement; not an Android latency or battery claim |

The final Gradle invocation completed successfully with `:app:testDebugUnitTest :app:lintDebug :app:assembleRelease :app:assembleDebugAndroidTest --continue`. Live checks were enabled with `EPANODE_LIVE_TESTS=1`. The environment used JDK 17, Gradle 8.13, Android platform 36, and build-tools 35.0.0. API 36 framework tests were not run.

## Functional coverage

| Suite | Passed | Coverage |
| --- | ---: | --- |
| MusicLogicTest | 15 | Tag priority, conservative filename cleanup, URI identity, deterministic recommendations, multilingual and typo-tolerant search, lyrics ranking, LRC offsets/timestamps, precise clip boundaries, URL classification, safe filenames, bounded large-library search |
| AndroidStoreTest | 16 | Eight scenarios on each of API 29 and 35: persistence after reopening, metadata overrides after rescans, playlist order and deduplication, transaction rollback on invalid backups, valid backup restoration, clip validation, scan pruning, download/retry/cancellation state |
| AndroidUiTest | 2 | One full Compose workflow on each API: navigation, typo search, liking, playlist creation and adding a song, saving a named clip, Best parts, Discover link entry, and Settings |
| PlaybackModelTest | 4 | Two scenarios on each API: Media3 clipping configuration and safe handling of invalid or missing clip data |
| CatalogLiveTest | 2 | Live music search and audio URL resolution with the first 4,096 audio bytes retrieved; public Spotify track metadata extraction |

The two Robolectric UI tests use semantic actions and a controlled Compose clock. Screenshots were rendered from the real Android Compose view hierarchy using Robolectric native graphics. The pictured tracks are synthetic fixtures. Those host tests do not prove touch ergonomics, hardware decoding, audible loop seams, or system notification behavior. The separate instrumentation suite uses actual touch injection on a 320 × 640 Android 15 emulator and exercises the Media3 audio engine with synthetic WAV files.

The live search used “Kevin MacLeod Carefree” and resolved an M4A stream. The Spotify check exercised one public track. Full playlist imports, all regions, all provider URL variants, and completed on-device audio downloads were not verified by those two tests. Provider websites can change after this run.

## Nonfunctional checks and limits

- Search cost was exercised with 10,000 tracks. Search, scanning, and download work are dispatched away from the UI thread. No frame-time or cold-start measurements were taken on an Android device.
- Storage tests verify transactional backup rejection and preservation of local edits, playlist order, and explicit cancellation. Invalid paths, deceptive provider hosts, invalid clips, and bounded text inputs have automated coverage. This is not an external security audit or a fuzzing campaign.
- APK size and static lint were measured. The app uses bounded artwork caches and a 64 KB download buffer; these are implementation constraints, not measured peak RAM or energy consumption.
- No hardware CPU, RAM, battery, thermal, accessibility-service, or long-duration stability measurements are claimed.
- Automated tests ran against the debug variant. The optimized release built and its signature verified, but its runtime behavior has not been exercised on hardware.

## Hosted Android verification

[Android CI run 36393359892](https://github.com/ashr-exe/epanode/actions/runs/36393359892) and [CodeQL run 36393359903](https://github.com/ashr-exe/epanode/actions/runs/36393359903) passed for application/test commit `77c6d5f8b3ea2345d8239f1ab74617fb07bd61f9`. The release documentation and evidence were refreshed afterward without changing application code, dependencies, or tests. Hosted unit tests omit the two optional live-provider checks; the local 39-test run above includes them.

All three `EpanodeFunctionalTest` cases passed on the hosted API 35 Google APIs x86_64 emulator:

1. Local WAV playback, typo search, liking, playlist creation, naming/saving/playing a clip, full player, lyrics, and queue navigation using injected touch events.
2. Persistent edits after rescans, playlist deduplication, backup restoration, and invalid clip rejection.
3. Actual playback position wrapping twice in a one-second clip, exact clip duration, and automatic advancement to the next song's best part with repeat-all enabled.

The device runs exposed compact-screen feedback overlap and test synchronization issues. Song menus now expand fully and scroll, feedback has a dismiss control, playing a search result dismisses the keyboard, and the mini-player has an explicit accessible label. Tests wait for the platform keyboard and dismiss visible confirmation messages before touching covered controls. The loop test measures position wrapping because Media3's remote controller suppresses same-item transition callbacks.

No physical Android phone was connected. The local ARM64 emulator remained blocked by a host sandbox crash before boot (`sysctl hw.cachelinesize` denied, `SIGILL` in `init_cache_info`); the hosted emulator provided the instrumentation run instead. [Local blocker record](test-results/emulator-blocker.txt). Audible quality, physical audio outputs, Bluetooth, and power measurements remain unverified.

## Required before treating this as a production release

1. Install the optimized APK on actual Android 10, 13, 15, and 16 devices. Exercise permission denial/revocation, first scan, a large library, removable storage, SAF folders, and rescans after moving/deleting files.
2. Repeat `./gradlew :app:connectedDebugAndroidTest` on physical devices, then manually repeat the core journeys with the optimized APK. The completed CI emulator run uses the debug variant.
3. Listen to MP3, AAC/M4A, FLAC, Ogg, and WAV playback; seek near boundaries; repeat individual clips and playlist clips; assess audible seams and transitions. Exercise malformed and unsupported files.
4. Verify background and locked-screen playback, media notifications, headset controls, Bluetooth reconnects, calls/audio-focus loss, unplug events, process death, queue restore, speed, sleep timer, and equalizer support.
5. Complete direct, YouTube, YouTube Music, and public Spotify track/playlist imports. Interrupt network, change Wi-Fi/mobile constraints, cancel and retry, exhaust storage, reboot during a job, and inspect for duplicate tracks or orphaned partial files.
6. Measure cold start, UI frame timing, peak and steady RAM, CPU while idle and playing, and battery during a multi-hour local playback run. Repeat with a large library and screen off. No numerical resource target has yet been established by device measurement.
7. Check TalkBack, large fonts, display scaling, narrow screens, landscape, Unicode filenames, and multiple locales. Run a longer playback/import soak and review crash logs.

## Evidence

Hostnames and local workspace paths have been redacted from published logs; test outcomes are unchanged.

- [Hosted verification summary](test-results/ci-summary.json)
- [Instrumentation log excerpt](test-results/android-instrumentation.txt)
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
669771c31a9c62a51e16ad59cb8ab7ebe35f250914e254662ebf919253c20cbe
```

Development signing certificate SHA-256:

```text
36f0e70fe91e719558221b2cb622275e9c1b7dda516fd4eb34aff4edc2231e62
```
