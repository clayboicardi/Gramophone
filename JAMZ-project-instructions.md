# Gramophone Fork — Project Instructions

## Who You're Working With

Clay is an analytically strong non-coder who directs AI to build solutions. He understands goals clearly but may lack technical vocabulary to express them precisely. Confirm you understand his end goal before executing. Fill in technical context he's missing rather than guessing at his intent.

Clay's development environment:
- **Planning and research:** Claude Chat (desktop app)
- **Code implementation:** Claude Code (desktop app, NOT CLI terminal)
- **Phone:** Pixel 10 Pro XL (arm64-v8a), USB-connected to Windows desktop for ADB
- **Desktop:** Windows, user path `C:\Users\chawo`, OneDrive Desktop at `C:\Users\chawo\OneDrive\Desktop\`
- **ADB location:** `C:\Users\chawo\Desktop\platform-tools\adb.exe`
- **Signing:** Self-signed debug builds for personal use (not publishing to any store)
- **GitHub:** Private repo, `github.com/[Clay's account]` — to be set up in first session

## The Project

This is a personal fork of [Gramophone](https://github.com/FoedusProgramme/Gramophone), a GPL-3.0 Android music player built with Kotlin, Jetpack Compose, media3, and Material Design 3. Clay is forking it to customize over time into his ideal FLAC player for a large curated music library (~39,500 tracks, ~349GB, meticulously tagged).

The upstream repo uses `org.akanework.gramophone` as the package name. Clay's fork should use a custom package name (to be decided) so it can coexist with the original on his phone.

### Why Gramophone Over Alternatives

Gramophone follows Android's standard patterns strictly — MediaStore for indexing, standard media3 playback, Material Design 3 components. This makes it the most AI-friendly codebase to modify incrementally. The tradeoff is that MediaStore has limitations with large libraries and complex metadata, which Clay plans to address by building a custom indexer over time.

## Codebase Architecture

```
Gramophone/
├── app/src/main/java/org/akanework/gramophone/
│   ├── logic/                    # Core app logic
│   │   ├── GramophoneApplication.kt
│   │   ├── GramophonePlaybackService.kt
│   │   ├── GramophoneExtensions.kt
│   │   ├── comparators/          # Sorting logic
│   │   ├── ui/                   # Logic-layer UI utilities
│   │   └── utils/                # Shared utilities
│   └── ui/                       # Presentation layer
│       ├── MainActivity.kt
│       ├── MediaControllerViewModel.kt
│       ├── Compose.kt
│       ├── Widget.kt
│       ├── adapters/             # List/grid adapters (Song, Album, Artist, Genre, Playlist, Folder, Date)
│       ├── components/           # Custom views (PlayerBottomSheet, LyricsView, SquigglyProgress, etc.)
│       └── fragments/            # Screen fragments (Search, Settings, ViewPager, etc.)
├── hificore/                     # High-fidelity audio module
├── media3/                       # Git submodule — custom media3 build
├── misc/
│   ├── audiofxstub/              # Audio effects stubs
│   ├── audiofxstub2/
│   ├── audiofxfwd/
│   └── alacdecoder/              # Built-in ALAC decoder
├── baselineprofile/              # Performance profiling
├── build.gradle.kts              # Root build config (Kotlin 2.3.0)
├── settings.gradle.kts           # Module includes + media3 dependency substitution
├── package.properties            # Required: releaseType=SelfBuilt
└── gradlew / gradlew.bat         # Gradle wrapper
```

### Key Technical Details

- **Language:** Kotlin (100%)
- **UI:** Mix of Jetpack Compose and traditional XML layouts with Fragments
- **Playback:** media3/ExoPlayer (included as git submodule with custom modifications)
- **Library indexing:** Android MediaStore (system media database)
- **Min SDK:** Android 5.0 (API 21), Compile SDK: 36
- **Build system:** Gradle with Kotlin DSL
- **Submodules:** media3 is a git submodule — must run `git submodule update --init --recursive` before building
- **Build prerequisite:** Create `package.properties` in repo root with `releaseType=SelfBuilt`
- **Signing:** Debug key for personal builds, no store signing needed
- **License:** GPL-3.0 — Clay can modify and distribute but must keep source available if distributed

### Features Already Present

- Material You theming with dynamic Monet colors
- LRC/TTML/SRT synced lyrics with karaoke support
- ReplayGain 2.0
- System/third-party equalizer support
- Folder browsing and filesystem navigation
- Natural sorting with multiple sort options
- Grid and list view modes
- Read-only playlist support
- Album cover color theming in Now Playing
- Built-in ALAC decoder
- SD card support
- Lyric widget

## Long-Term Roadmap

These are Clay's goals in rough priority order. Each is a separate phase, not a single session:

1. **Build pipeline:** Get the fork compiling and producing installable APKs (first session)
2. **Rebrand:** Custom app name, package name, icon, and color scheme
3. **UI customization:** Adjust layouts, typography, now-playing screen to Clay's preferences
4. **Custom indexer:** Replace MediaStore dependency with direct FLAC tag reading (JAudioTagger or similar) — this is the big one, unlocks full control over how Clay's metadata displays
5. **Playlist system:** Writable playlists, import/export support
6. **Metadata display refinements:** Multi-artist handling, album artist vs track artist, proper genre display
7. **Scrobbling integration:** Built-in Last.fm scrobbling or better integration with Pano Scrobbler

## Rules of Engagement

### Communication
- Be direct. Push Clay toward what's best even when he doesn't want to hear it.
- State your confidence level when uncertain. If one clarifying question would meaningfully improve the output, ask before diving in.
- When Clay describes a goal, confirm your understanding of the end state before executing.
- Fill in technical context Clay is missing rather than assuming he knows Kotlin/Android/Gradle specifics.

### Development Approach
- **One change at a time.** Each modification should be small enough to test independently. Never make multiple unrelated changes in a single session.
- **Build and verify after every change.** Never assume a change works — build the APK and confirm.
- **Explain what you changed and why** in plain English after each modification. Clay needs to understand his own codebase even though he didn't write the original.
- **Save progress frequently.** Commit after each working change with a clear message.
- **When unsure about Gramophone's architecture,** read the relevant source files before making changes. Don't guess how a component works.
- **Preserve upstream compatibility.** Structure changes so Clay can merge upstream Gramophone updates when desired. Use a clear branching strategy.

### Build and Deploy
- **Build command:** `./gradlew assembleDebug` (or `assembleRelease` with signing config)
- **APK output:** `app/build/outputs/apk/debug/app-debug.apk`
- **Install via ADB:** `"C:\Users\chawo\Desktop\platform-tools\adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk`
- **Always test on device** after building. Don't just confirm the build succeeded — install it and verify visually.

### Things to Never Do
- Don't modify the media3 submodule unless explicitly asked — it's a complex dependency with upstream implications
- Don't remove existing features without asking — even ones Clay hasn't mentioned using
- Don't change the minimum SDK version
- Don't introduce new external dependencies without discussing the tradeoff first
- Don't batch multiple changes into one commit — atomic commits only
