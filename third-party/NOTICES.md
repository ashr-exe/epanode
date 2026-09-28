# Third-party notices

The app's complete resolved runtime dependency inventory is `DEPENDENCIES.tsv`. `DEPENDENCIES.json` records artifact versions, original source URLs, POM license declarations, and the local source archive for each dependency. `sources/` contains source archives for 103 of the 104 resolved runtime artifacts; Guava's `listenablefuture:9999.0-empty-to-avoid-conflict-with-guava` is an intentionally empty conflict-avoidance artifact.

Primary components:

| Component | Version | License / copyright |
| --- | --- | --- |
| NewPipe Extractor | v0.26.5 | GPL-3.0-or-later; NewPipe contributors / NewPipe e.V. Full GPL text is ../LICENSE. No changes to upstream source. |
| AndroidX Compose, Activity, Lifecycle, Media3, WorkManager, SQLite support libraries | See inventory | Apache-2.0; The Android Open Source Project / AndroidX authors |
| Kotlin and kotlinx.coroutines | See inventory | Apache-2.0; JetBrains and contributors |
| OkHttp and Okio | See inventory | Apache-2.0; Square and contributors |
| Coil | 2.7.0 | Apache-2.0; Coil contributors |
| nanojson | e9d656ddb49a412a5a0a5d5ef20ca7ef09549996 | Apache-2.0; Copyright 2011 The nanojson Authors (source-file headers; the POM does not declare a license) |
| jsoup | 1.22.2 | MIT; Jonathan Hedley |
| Rhino / Rhino Engine | 1.8.1 | MPL-2.0; Mozilla and contributors. Unmodified source archives included. |
| Protocol Buffers Java Lite | 4.35.1 | BSD-3-Clause; Google LLC. License in protobuf-BSD.txt. |
| Guava, failureaccess, listenablefuture | See inventory | Apache-2.0; Google / Guava authors. Some leaf POMs inherit their license from a parent. |
| JSR-305 annotations | 3.0.2 | Apache-2.0 as declared by its published POM; individual source notices are retained in the archive. |

Full shared license texts are in this directory. Per-file notices and any additional upstream notices are retained inside the source archives. The inventory is generated with `./gradlew :app:dependencyInventory`; source archives and metadata can be refreshed with `python3 scripts/fetch-dependency-sources.py`.

Android SDK/emulator, Gradle, JDK, JUnit, Robolectric, and instrumentation runners are build/test tools, not included in the APK. The Gradle wrapper is provided under Apache-2.0. Android is a trademark of Google LLC. Epanode is an independent project and is not affiliated with Spotify, YouTube, or Google.
