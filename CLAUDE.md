# JAMZ!!! — Claude Code Project Guide

## What This Is
Personal Android music player fork of [Gramophone](https://github.com/FoedusProgramme/Gramophone) (GPL-3.0). Built for a large curated FLAC library (~39,500 tracks, ~349GB) with meticulous metadata. Owner is Clay — an analytically strong non-coder who directs AI to build.

## Identity
- **App ID:** `com.clayboi.player` (debug: `com.clayboi.player.debug`)
- **Namespace:** `org.akanework.gramophone` (kept for upstream merge compatibility)
- **GitHub:** `clayboicardi/JAMZ` (branch: `beta`)
- **Language:** Kotlin 100%, mixed Jetpack Compose + XML layouts with Fragments
- **Build:** Gradle Kotlin DSL, media3 as git submodule, min SDK 21, compile SDK 36

## Environment
- **Project path:** `C:\Users\chawo\Projects\GRAMOPHONE_CUSTOM\`
- **ADB:** `C:\Users\chawo\Desktop\platform-tools\adb.exe`
- **Primary device:** Pixel 10 Pro XL, serial `58081FDCQ000EB`
- **Secondary device:** Pixel 7 Pro (family member testing)
- **JAVA_HOME:** Temurin JDK 21 (NOT JBR — JBR causes TLS/SSL issues with Gradle)
- **Android SDK:** `C:\Users\chawo\AppData\Local\Android\Sdk`

## Build & Deploy
```bash
# Build
./gradlew :app:assembleDebug

# Install (always specify device serial)
adb -s 58081FDCQ000EB install -r app/build/outputs/apk/debug/app-debug.apk

# Launch
adb -s 58081FDCQ000EB shell am start -n com.clayboi.player.debug/org.akanework.gramophone.ui.MainActivity

# Submodule init (first time or after clean)
git submodule update --init --recursive

# Build prerequisite
# Ensure package.properties exists in repo root with: releaseType=SelfBuilt
```

## Architecture Quick Reference
```
app/src/main/java/org/akanework/gramophone/
├── logic/                     # Core app logic
│   ├── GramophoneApplication.kt
│   ├── GramophonePlaybackService.kt
│   ├── GramophoneExtensions.kt
│   ├── comparators/           # Sorting
│   ├── ui/                    # Logic-layer UI utilities
│   └── utils/                 # Shared utilities
└── ui/                        # Presentation layer
    ├── MainActivity.kt
    ├── MediaControllerViewModel.kt
    ├── Compose.kt
    ├── adapters/              # List/grid adapters
    ├── components/            # Custom views (PlayerBottomSheet, LyricsView, etc.)
    └── fragments/             # Screen fragments
```

## Workflow Rules
1. **One change at a time.** Each modification small enough to test independently.
2. **Build and verify after every change.** Install APK and confirm on device.
3. **Read source files before modifying.** Never guess how a component works.
4. **Commit after each working change** with a clear descriptive message.
5. **Explain what changed and why** in plain English.
6. **Edit logs** go in `Edit Logs/` directory — read existing logs at session start, write new one at session end.

## Tool Usage Priorities
1. **API/library lookups:** Context7 first, then web search
2. **Upstream Gramophone reference:** GitMCP to search `FoedusProgramme/Gramophone`, or fetch raw GitHub URLs (`https://raw.githubusercontent.com/FoedusProgramme/Gramophone/refs/heads/beta/[filepath]`)
3. **Complex planning:** Sequential Thinking MCP before writing code
4. **On-device testing:** android-mcp for screenshots and interaction after APK install
5. **GitHub remote ops:** GitHub MCP for commits, branches, PRs

## Hard Rules — Never Do These
- Don't modify the media3 submodule unless explicitly asked
- Don't remove existing features without asking
- Don't change minimum SDK version
- Don't introduce new external dependencies without discussing tradeoffs
- Don't batch multiple changes into one commit
- Don't use bare `assembleDebug` — always `:app:assembleDebug`

## Current State (Update This Section Each Phase)
**Phase:** 5+ — YouTube Music streaming integration
**Next feature:** NewPipe Extractor v0.26.0 integration for YT Music search/playback
**Reference architecture:** OuterTune (local + streaming hybrid, Kotlin/ExoPlayer)
**Research doc:** `JAMZ-streaming-research.md` in project root
**Completed features:** Custom branding, 5-band hardware EQ, 31-band parametric EQ, visualizer, spring animations, sectioned search, blurred album art backgrounds

## Parametric EQ Architecture
The 31-band parametric EQ was built in Sessions 1-4 (see `Session Blueprints/HANDOFF-EQ-SESSIONS-1-4.md`).
- **DSP:** `BiquadFilter.kt` → `ParametricEqProcessor.kt` (Media3 BaseAudioProcessor, TDF-II biquads)
- **Pipeline position:** ReplayGain → ParametricEQ → Visualizer Tee → AudioTrack
- **Config:** `ParametricEqConfig.kt` (immutable data classes, JSON serialization, SharedPreferences)
- **UI:** `ParametricEqActivity.kt` + `FrequencyResponseView.kt` + `activity_parametric_eq.xml`
- **Mode toggle:** `eq_mode` preference: `"simple"` = hardware EQ, `"parametric"` = software EQ
- **Thread safety:** `AtomicReference<EqConfig?>` for UI→audio thread handoff
- **Crossfade:** cos/sin energy-preserving crossfade (~10ms) when switching presets (>3 bands changed)
- **ProGuard:** Keep rules in `app/proguard-rules.pro` for all EQ classes
- **ANR fix:** `allowDiskAccessInStrictMode` wraps all disk I/O in onCreate + `window.decorView.post {}` defers 31-band grid creation and BiquadFilter instantiation to after activity transition completes
- **StrictMode pattern:** Any new Activity must wrap SharedPreferences and layout inflation disk I/O with `allowDiskAccessInStrictMode` (from `GramophoneExtensions.kt`), otherwise debug StrictMode dialogs cascade into ANR

## Key Learnings (Hard-Won)
- Gradle: `:app:assembleDebug` not bare `assembleDebug`
- JAVA_HOME: Must be Temurin JDK 21, not JBR (TLS issues)
- Configuration cache: Keep enabled
- Upstream source reading: Raw GitHub URLs only (API auth fails, blob URLs return HTML)
- Large code insertions: 200+ lines work best as single edit operations
- Git submodule: Must init before first build
- package.properties: Must exist with `releaseType=SelfBuilt`
