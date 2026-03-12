# JAMZ YouTube Music Streaming — Research Summary (CORRECTED)
## Date: March 8, 2026

### Decision: YouTube Music via NewPipe Extractor (NOT Spotify)

**Why not Spotify:** Spotify sent a C&D to Spotube in early 2025 for using their API to bypass Premium. Using Spotify's metadata API + alternate audio sources = TOS violation. Avoid entirely.

**Why YouTube Music:** Clay's wife's Spotify catalog is available on YouTube Music. No API key needed. No account needed. No TOS risk for personal use.

---

### Primary Library: NewPipe Extractor v0.26.0
- **Source:** JitPack (`com.github.teamnewpipe:NewPipeExtractor:v0.26.0`)
- **Released:** February 22, 2026 (latest stable)
- **License:** GPL-3.0 (compatible with JAMZ's GPL-3.0)
- **Capabilities:** YouTube + YouTube Music search, audio stream URL extraction, playlist extraction
- **YT Music support:** Built-in since 2020 — search filters for Songs, Videos, Albums, Playlists via `YoutubeMusicSearchExtractor`
- **Requires custom Downloader implementation** — abstract class `org.schabi.newpipe.extractor.downloader.Downloader` must be subclassed. Reference: NewPipe app's `DownloaderImpl.java` uses OkHttp.
- **Initialization:** Must call `NewPipe.init(downloaderInstance)` once at app startup before any extraction calls.

#### Proguard rules required:
```
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
```

#### Core library desugaring REQUIRED (minSdk 21 < 33):
JAMZ does NOT currently have desugaring enabled. Must add to `app/build.gradle.kts`:
```kotlin
compileOptions {
    isCoreLibraryDesugaringEnabled = true
}
// In dependencies:
coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.4")
```

#### JitPack content filter must be expanded:
`settings.gradle.kts` already has JitPack but filtered to only `com.github.philburk`. Must add `com.github.teamnewpipe`:
```kotlin
maven("https://jitpack.io") {
    content {
        includeGroup("com.github.philburk")
        includeGroup("com.github.teamnewpipe")
    }
}
```

#### aboutLibraries strict license check:
`app/build.gradle.kts` has `strictMode = FAIL` with an allowlist that does NOT include GPL-3.0. Must add:
```kotlin
allowedLicenses.addAll("Apache-2.0", "MIT", "BSD-2-Clause", "BSD-3-Clause", "LGPL-3.0-only",
    "The GNU Lesser General Public License, Version 3.0", "GPL-3.0-only",
    "The GNU General Public License, Version 3.0")
```

#### OkHttp dependency needed:
NewPipe's Downloader uses OkHttp. JAMZ doesn't currently have it. Add:
```kotlin
implementation("com.squareup.okhttp3:okhttp:4.12.0")
```

---

### Reference Architecture: OuterTune
- **Repo:** github.com/OuterTune/OuterTune (GPL-3.0)
- **What it is:** Fork of InnerTune — Material 3 local player + YouTube Music client
- **Tech stack:** Kotlin, Jetpack Compose, ExoPlayer, custom innertube API wrapper
- **Key features:** Mixed local+streaming queue, custom tag extractor (not MediaStore), YT Music search/browse
- **Credits Gramophone** in their README ("for emotional support, and a legendary lyrics parser")
- **Architecture docs:** deepwiki.com/OuterTune/OuterTune
- **NOTE:** OuterTune uses a custom innertube API wrapper, NOT NewPipe Extractor for YT Music queries. We chose NewPipe Extractor for community maintenance and battle-tested reliability.

---

### media3/ExoPlayer Integration
JAMZ already uses media3 ExoPlayer with a custom submodule (dependency substitution in `settings.gradle.kts`). HTTP audio URLs work natively via `ProgressiveMediaSource`. The playback service (`GramophonePlaybackService.kt`) extends `MediaLibraryService` and creates the ExoPlayer instance with a custom `PostAmpAudioSink` for audio effects.

**Integration point:** The service already uses `DefaultDataSource.Factory` which supports HTTP URIs. A streamed track's audio URL can be set as the `MediaItem` URI, and ExoPlayer will handle HTTP streaming through the same pipeline (including EQ, visualizer, etc.).

---

### Implementation Phases
1. **Session 1 (this prompt):** Add dependency, build Downloader, proof-of-concept (search YT Music → extract audio URL → play via ExoPlayer on device)
2. **Session 2:** UI integration (streaming search alongside local library)
3. **Session 3:** Mixed queue support (local FLAC + streamed tracks in same queue)
4. **Session 4:** Polish (quality selection, loading states, error handling, caching)

---

### Risks
- YouTube changes internal API → NewPipe Extractor team actively maintains (v0.26.0 is 2 weeks old, community responds to breakages within days)
- Stream URLs expire → resolve just-in-time before playback, re-resolve on error
- Audio quality varies → prefer high-bitrate audio streams (opus/m4a, 128-256kbps)
- APK size increase → ~3-5MB from NewPipe Extractor + Rhino JS engine
- Legal gray area → personal use only, not published to any store

---

### Key Code References in JAMZ
- `GramophonePlaybackService.kt` — main playback service, MediaLibraryService, creates ExoPlayer
- `PostAmpAudioSink.kt` — custom audio sink with EQ/visualizer effects (streams go through same pipeline)
- `SearchFragment.kt` — existing search UI (Phase 3: sectioned search with ConcatAdapter)
- `GramophoneApplication.kt` — Application class (init NewPipe.init() here)
- `app/build.gradle.kts` — applicationId `com.clayboi.player`, namespace `org.akanework.gramophone`, compileSdk 36, minSdk 21
- `settings.gradle.kts` — JitPack repo (content-filtered), media3 submodule dependency substitution
- `app/proguard-rules.pro` — existing keep rules for EQ, visualizer, JNI

### Current Git State
- Branch: `beta`, 11 commits ahead of origin/beta (NOT pushed)
- Working tree: clean (except this research file)
- 12 custom commits total from rebrand through Phase 5 audit
