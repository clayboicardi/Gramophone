# JAMZ!!! (Gramophone Fork) — Phase 4 Feature Development Handoff

## Document Purpose
This is a comprehensive handoff prompt for continuing development of JAMZ!!!, Clay's custom fork of Gramophone. It captures all prior work, the current codebase state, research findings, and a fully planned feature roadmap with granular implementation details.

---

## Project Identity

| Field | Value |
|-------|-------|
| **App Name** | JAMZ!!! by Clayboi |
| **Launcher Label** | JAMZ!!! |
| **applicationId** | `com.clayboi.player` (debug: `com.clayboi.player.debug`) |
| **namespace** | `org.akanework.gramophone` (unchanged for upstream merge compatibility) |
| **Upstream** | [FoedusProgramme/Gramophone](https://github.com/FoedusProgramme/Gramophone) (GPL-3.0) |
| **Fork** | `github.com/clayboicardi/Gramophone`, branch `beta` |
| **Path** | `C:\Users\chawo\Projects\GRAMOPHONE_CUSTOM\` |
| **Color Scheme** | Dark purple/grey surfaces (#1A1A1E) + bright green primary (#69FF5C) + muted purple secondary |
| **Dynamic Colors** | Material You wallpaper colors OFF; custom palette always used |
| **Content-Based Color** | ON — album art extracts player accent colors independently |

---

## Build & Deploy

```bash
# Build (bash shell, NOT PowerShell)
cd "C:/Users/chawo/Projects/GRAMOPHONE_CUSTOM" && \
  JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-21.0.10.7-hotspot" \
  ANDROID_HOME="C:/Users/chawo/AppData/Local/Android/Sdk" \
  ./gradlew :app:assembleDebug

# Deploy to Pixel 10 Pro XL
"C:\Users\chawo\Desktop\platform-tools\adb.exe" -s 58081FDCQ000EB install -r app/build/outputs/apk/debug/Gramophone-*.apk

# Launch
adb -s 58081FDCQ000EB shell am start -n com.clayboi.player.debug/org.akanework.gramophone.ui.MainActivity
```

**Critical build notes:**
- MUST use Temurin JDK 21 (JetBrains Runtime has TLS failures with Google CDN)
- MUST use `:app:assembleDebug` (not bare `assembleDebug`)
- MUST leave configuration cache enabled (resourceplaceholders plugin requires it)
- `minSdk = 21` (Android 5.0) — affects API availability for blur, visualizer, etc.
- APK output: `app/build/outputs/apk/debug/Gramophone-*.apk` (~89MB)

---

## What Has Been Built (Phases 1-3)

### Phase 1 — Branding & Identity
- Renamed app to JAMZ!!! by Clayboi across launcher, toolbar, about screen
- Changed applicationId to `com.clayboi.player`
- Custom color palette: dark purple/grey + bright green (#69FF5C)
- Disabled Material You dynamic colors, kept content-based album art colors

### Phase 2 — Core Features
- **Artist/Album Artist toggle** — switches between 4022 artists and 257 album artists in the library
- **Search with sectioned results** — Artists, Albums, Songs sections with auto-hiding empty sections via ConcatAdapter + setIsolateViewTypes(true)
- **Card flip animation** — tap album art in Now Playing to flip and see tag metadata (ObjectAnimator on rotationY)
- **Swipe-to-skip** — left=next, right=previous on album art (GestureDetector, 100dp threshold)
- **TagDetailFragment** — full-screen FLAC metadata viewer (title, artist, album, genre, year, format, sample rate, bitrate, BPM)
- **FlacTagManager** — reads FLAC metadata via ealvatag library (Vorbis comments + STREAMINFO)

### Phase 3 — Polish (committed as `3eccc1a4`)
- **Search result limits** — 5 items max per section (`.map { it.take(maxPerSection) }`), headers still show full counts
- **Coroutine lifecycle fix** — TagDetailFragment changed from orphaned `CoroutineScope(Dispatchers.IO)` to `viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO)`
- **BPM row conditional** — only shown in TagDetailFragment when BPM tag actually exists (`if (!bpm.isNullOrBlank())`)

### Git State (as of Phase 3 completion)
- Branch: `beta`, 7 commits ahead of `origin/beta`
- Working tree: clean (2 modified files committed)
- NOT pushed to remote

---

## What Already Exists in JAMZ!!! (feature audit)

| Feature | Status | Location |
|---------|--------|----------|
| Now Playing UI | FULL | `FullBottomSheet.kt` (1,666 lines) |
| Play/pause/skip/shuffle/loop | FULL | FullBottomSheet.kt |
| Playback speed control | FULL | FullBottomSheet.kt (dialog) |
| Sleep timer | FULL | FullBottomSheet.kt (Material TimePicker) |
| Queue management | FULL | `PlaylistQueueSheet.kt` (drag-to-reorder) |
| Synced lyrics | FULL | `SemanticLyrics.kt`, `NewLyricsView.kt` (LRC/TTML/SRT, word-level karaoke) |
| Audio format detection | FULL | `AudioFormatDetector.kt` |
| Content-based colors | FULL | DynamicColorsOptions from album art |
| Audio effects infra | PARTIAL | `EffectWrapper.kt`, `PostAmpAudioSink.kt` (Volume + DynamicsProcessing wrappers exist) |
| **Visualizer** | **NONE** | Not implemented anywhere |
| **Equalizer UI** | **NONE** | No EQ interface or AudioEffect integration |
| **Crossfade** | **NONE** | Only Coil image crossfade, no audio crossfade |
| **Blurred background** | **NONE** | Solid dark surface background only |

---

## Phase 4 Feature Roadmap

### Tier System

| Tier | Meaning |
|------|---------|
| **S** | Highest visual impact, transforms the app's identity |
| **A** | High-value features that complete the premium feel |
| **B** | Polish and differentiation features |
| **C** | Future-phase candidates, high effort or scope risk |

### Build Order (S + A tier, sequential)

```
1. S1: Blurred Album Art Background     (2-3 hours)
2. A3: Swipe to Queue                   (2-3 hours)
3. S2: Audio Visualizer                 (3-5 hours)
4. A1: Spring Physics Animations        (2-3 hours)
5. A2: Equalizer UI                     (4-6 hours)
                                   TOTAL: 13-20 hours
```

**Rationale for this order:**
- S1 first: transforms the Now Playing screen foundation; all subsequent features layer on top
- A3 second: quick win, builds momentum, completely independent
- S2 third: designed to look good over the new blurred background
- A1 fourth: polish pass applied to ALL existing + new animations at once
- A2 last: largest standalone feature, benefits from stable visual framework

---

## S-TIER FEATURE SPECS

### S1: Blurred Album Art Background

**Goal:** Full-bleed blurred version of current album art as the Now Playing background, with a dark scrim overlay for text readability. Every premium player does this (Spotify, Apple Music, Poweramp). It's the single most impactful cosmetic change.

**Constraint:** minSdk 21 rules out `RenderEffect` (API 31+). Must use a backwards-compatible approach.

**Approach:** Downscale + StackBlur (pure Kotlin, no native code, no RenderScript)
1. Load artwork bitmap via Coil3 (same URI already used for album cover)
2. Downscale to ~100x100px (tiny bitmap = fast blur + minimal memory)
3. Apply StackBlur algorithm (radius 25, O(n) complexity)
4. Display in a full-match_parent ImageView behind all content
5. Overlay a semi-transparent dark scrim (#B3000000 = 70% black)
6. Crossfade (300ms alpha animation) when track changes

**StackBlur source:** Mario Klingemann's algorithm, widely used (see [kikoso/android-stackblur](https://github.com/kikoso/android-stackblur)). Adapt the core algorithm to a pure Kotlin function — no library dependency needed. Alternatively, simple downscale-to-tiny + upscale-with-bilinear-filtering creates a natural blur effect.

**Files to create:**
- `app/src/main/java/org/akanework/gramophone/logic/utils/BlurUtil.kt` — StackBlur algorithm as a `Bitmap.blur(radius: Int): Bitmap` extension function

**Files to modify:**
- `app/src/main/res/layout/full_player.xml` — add `ImageView` (id: `blurred_background`) as FIRST child of root layout + `View` (id: `blur_scrim`) with `#B3000000` background
- `app/src/main/res/layout-w600dp-land/full_player.xml` — same changes for landscape
- `app/src/main/java/org/akanework/gramophone/ui/components/FullBottomSheet.kt` — in the metadata update area (~line 886 where `artworkUri` is loaded), also load blurred version into background ImageView. Add crossfade animation between old and new blurred bitmaps on track change.

**Track change animation approach:**
- Use two ImageViews for the background (front/back buffer)
- Load new blurred art into the hidden one
- Animate alpha crossfade (300ms) then swap references
- Prevents jarring snap between songs

**Testing:** On-device — verify blur renders, verify track change crossfade is smooth, verify text/controls remain readable over any album art color.

---

### S2: Audio Visualizer

**Goal:** Real-time audio spectrum visualization on the Now Playing screen. The "wow factor" feature that makes JAMZ!!! feel alive.

**Recommended style:** Bar spectrum strip positioned below the seekbar, or subtle wave behind album art. Bars using the primary green (#69FF5C) over the blurred background = stunning.

**Android API:** `android.media.audiofx.Visualizer` — captures FFT data from the audio output.

**Permission:** Requires `RECORD_AUDIO` (dangerous permission, runtime request needed). Should be OPT-IN: user enables visualizer in settings, then permission is requested. If denied, visualizer simply hidden.

**Audio session access:** Gramophone already has this solved:
- `ViewPagerFragment.kt:101` — `activity.getPlayer()?.audioSessionId`
- `GramophonePlaybackService.kt:761` — `onAudioSessionIdChanged()` callback
- `EffectWrapper.kt` — pattern for managing audio session lifecycle

**Implementation:**
1. Create `VisualizerView.kt` — custom View, override `onDraw()`:
   - Receive FFT byte array from Visualizer callback
   - Compute magnitude for each frequency band (e.g., 32 bands)
   - Draw bars using `Canvas.drawRoundRect()` with the primary green color
   - Smooth animation: lerp between previous and current magnitudes
2. Initialize `android.media.audiofx.Visualizer(audioSessionId)`:
   - `setCaptureSize(256)` — 128 or 256 for performance
   - `setDataCaptureListener()` at ~30fps (not 60 — saves battery)
   - `setEnabled(true)` when Now Playing is visible
3. Lifecycle management (CRITICAL):
   - Release Visualizer when fragment view is destroyed (`viewLifecycleOwner` observer)
   - Pause capture when app is backgrounded
   - Handle audio session ID changes (re-create Visualizer with new ID)
   - Use the same lifecycle patterns as the coroutine fix we already made in Phase 3

**Files to create:**
- `app/src/main/java/org/akanework/gramophone/ui/components/VisualizerView.kt` — custom Canvas-based View
- Possibly: `app/src/main/java/org/akanework/gramophone/logic/utils/VisualizerManager.kt` — manages Visualizer instance lifecycle

**Files to modify:**
- `app/src/main/res/layout/full_player.xml` — add `VisualizerView` element
- `app/src/main/res/layout-w600dp-land/full_player.xml` — landscape
- `app/src/main/java/org/akanework/gramophone/ui/components/FullBottomSheet.kt` — initialize VisualizerManager, bind to lifecycle
- `AndroidManifest.xml` — add `<uses-permission android:name="android.permission.RECORD_AUDIO"/>`
- Settings fragment — add visualizer on/off toggle

**Color integration:** Bars should use #69FF5C (primary green) by default, or optionally adapt to the content-based album art accent color for a cohesive look.

**Performance budget:** Visualizer capture at 30fps + Canvas invalidation is lightweight. The VisualizerView should NOT allocate objects in onDraw() (pre-allocate Paint, RectF, arrays in init).

---

## A-TIER FEATURE SPECS

### A1: Spring Physics Animations

**Goal:** Replace linear/easing animations with spring physics across the app. Makes interactions feel alive, tactile, and premium. This is the animation system behind Material 3 Expressive (Android 16).

**Library:** `androidx.dynamicanimation:dynamicanimation` (check if already transitive via Material Components; if not, add to build.gradle.kts)

**Where to apply springs:**

| Element | Current Animation | Spring Upgrade |
|---------|------------------|----------------|
| Play/Pause button | Instant state change | Scale spring: 1.0 → 0.85 on press, spring back to 1.0 with overshoot |
| Card flip | ObjectAnimator rotationY, linear | SpringAnimation on rotationY with DAMPING_RATIO_MEDIUM_BOUNCY |
| Album art on Now Playing open | Fade in | Spring scale from 0.8 → 1.0 |
| Skip swipe feedback | Instant snap | Spring translation: art follows finger, springs to final position |
| FAB press (TagDetailFragment) | Ripple only | Scale spring on press/release |
| Queue item reorder | Default ItemAnimator | Spring settle after drag release |

**Code pattern:**
```kotlin
// Example: play/pause button spring
val springScaleX = SpringAnimation(playButton, DynamicAnimation.SCALE_X, 1f).apply {
    spring.stiffness = SpringForce.STIFFNESS_MEDIUM
    spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
}
val springScaleY = SpringAnimation(playButton, DynamicAnimation.SCALE_Y, 1f).apply {
    spring.stiffness = SpringForce.STIFFNESS_MEDIUM
    spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
}

playButton.setOnTouchListener { v, event ->
    when (event.action) {
        MotionEvent.ACTION_DOWN -> {
            springScaleX.animateToFinalPosition(0.85f)
            springScaleY.animateToFinalPosition(0.85f)
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            springScaleX.animateToFinalPosition(1f)
            springScaleY.animateToFinalPosition(1f)
            if (event.action == MotionEvent.ACTION_UP) v.performClick()
        }
    }
    true
}
```

**Files to modify:**
- `app/build.gradle.kts` — add dynamicanimation dependency (if not already transitive)
- `app/src/main/java/org/akanework/gramophone/ui/components/FullBottomSheet.kt` — play/pause spring, card flip spring, album art entrance spring, skip gesture spring
- `app/src/main/java/org/akanework/gramophone/ui/fragments/TagDetailFragment.kt` — FAB press spring

**Risk:** Very low. Springs are additive — they enhance existing interactions without changing logic. If any spring feels wrong, just adjust stiffness/damping constants.

---

### A2: Equalizer UI

**Goal:** 5-band graphic equalizer with presets, plus bass boost and virtualizer. Accessible from the Now Playing screen.

**Critical discovery:** Gramophone already has audio effects infrastructure:
- `EffectWrapper.kt` — generic pattern for wrapping AudioEffect instances with audio session lifecycle management
- `PostAmpAudioSink.kt` — manages audioSessionId changes, already attaches DynamicsProcessing and Volume effects
- `GramophonePlaybackService.kt:761` — dispatches audio session changes

**The EQ implementation can follow the exact same EffectWrapper pattern** that Volume and DynamicsProcessing use. This is not building from scratch — it's extending an existing, proven architecture.

**Architecture:**
- `EqEffectWrapper.kt` — extends the EffectWrapper pattern for Equalizer, BassBoost, Virtualizer AudioEffect instances
- `EqFragment.kt` — UI fragment with vertical sliders for each band
- Settings persistence in SharedPreferences (selected preset + custom band levels + bass/virtualizer levels)

**UI Design:**
```
┌─────────────────────────────────────┐
│  Equalizer                    [ON]  │
├─────────────────────────────────────┤
│  Preset: [  Rock           ▼]      │
├─────────────────────────────────────┤
│                                     │
│  +15dB ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─   │
│     │   │       │           │       │
│     │   █       │       █   │       │
│   0 ─ ─█─ ─ ─ ─█─ ─ ─ ─█─ ─█─ ─  │
│     █   █   █   █       █   █       │
│     █   █   █   █   █   █   █       │
│  -15dB ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─   │
│   60   230  910  3.6k  14k  Hz     │
│                                     │
├─────────────────────────────────────┤
│  Bass Boost    ════════█══          │
│  Virtualizer   ═══█══════          │
└─────────────────────────────────────┘
```

**Presets:** Flat, Bass Boost, Rock, Pop, Jazz, Classical, Hip-Hop, Vocal, Custom
- Built-in presets via `Equalizer.usePreset(index)`
- "Custom" stores per-band levels from manual slider adjustment

**Files to create:**
- `app/src/main/java/org/akanework/gramophone/logic/utils/EqEffectWrapper.kt` — Equalizer/BassBoost/Virtualizer lifecycle management (follows EffectWrapper pattern)
- `app/src/main/java/org/akanework/gramophone/ui/fragments/EqFragment.kt` — UI with sliders
- `app/src/main/res/layout/fragment_equalizer.xml` — layout with vertical SeekBars

**Files to modify:**
- `app/src/main/java/org/akanework/gramophone/ui/components/FullBottomSheet.kt` — add EQ button to Now Playing controls, navigate to EqFragment
- `app/src/main/res/layout/full_player.xml` — add EQ button icon
- `app/src/main/res/values/strings.xml` — EQ-related strings

**Key files to study (existing patterns):**
- `app/src/main/java/org/akanework/gramophone/logic/utils/EffectWrapper.kt` — THE pattern to follow
- `app/src/main/java/org/akanework/gramophone/logic/utils/PostAmpAudioSink.kt` — audio session management
- `app/src/main/java/org/akanework/gramophone/logic/GramophonePlaybackService.kt` — onAudioSessionIdChanged()

**Device compatibility risk:** Some devices/ROMs have broken Equalizer implementations. Wrap AudioEffect creation in try-catch and show a "EQ not supported on this device" message if it fails.

---

### A3: Swipe to Queue

**Goal:** In any song list, swipe RIGHT on a song row to instantly add it to the playback queue. Visual feedback: green (#69FF5C) background with queue icon slides into view, row snaps back, haptic buzz confirms.

**Implementation:** Standard `ItemTouchHelper.SimpleCallback` pattern on RecyclerView.

**Swipe visual:**
- Background: solid #69FF5C
- Icon: `ic_playlist_add` (or equivalent queue icon) in dark #1A1A1E, 24dp, left-aligned with 16dp margin
- Only RIGHT swipe enabled (LEFT reserved for future "remove" or other action)
- After action completes, row slides back to original position (not removed)

**Action on swipe:**
1. Get MediaItem at swiped adapter position
2. Call `controller.addMediaItem(mediaItem)` to append to end of queue
3. `adapter.notifyItemChanged(position)` to snap row back
4. `view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)` for tactile feedback
5. Show Snackbar: "Added to queue" with UNDO action
6. UNDO: `controller.removeMediaItem(lastAddedIndex)`

**Where to apply:**
- SongAdapter items in library tabs (primary use case)
- SearchFragment song results
- Album detail song list
- NOT in the queue list itself (that uses drag-to-reorder)

**Files to create:**
- `app/src/main/java/org/akanework/gramophone/ui/components/SwipeToQueueCallback.kt`

**Files to modify:**
- Song list fragments (or a shared base setup) — attach `ItemTouchHelper` with the callback to RecyclerViews that show songs

**Accessibility note:** Swipe should supplement, not replace, existing long-press/popup menu "Add to queue" option (which may already exist in Gramophone's popup menus).

---

## B-TIER FEATURES (build after S+A)

### B1: Wavy/Animated Seekbar
Replace standard Material Slider with a wavy line (Android 13+ squiggly style). Custom View overriding `onDraw()` to draw a sine wave path. Wave amplitude could pulse gently with playback. Effort: 3-4 hours. See [mahozad/wavy-slider](https://github.com/mahozad/wavy-slider) for reference.

### B2: Waveform Seekbar (alternative to B1 — pick one)
Show actual audio waveform as seekbar background using [massoudss/waveformSeekBar](https://github.com/massoudss/waveformSeekBar). Needs async waveform extraction from FLAC files + caching. Effort: 6-8 hours. More functional but heavier than B1.

### B3: Queue Animations
Spring-physics ItemAnimator on queue RecyclerView. Active track gets green (#69FF5C) left-border highlight. Gentle pulse animation on track change. Spring settle after drag-to-reorder. Effort: 2-3 hours.

### B4: Smart Shuffle
Custom `ShuffleOrder` for Media3 that avoids same-artist back-to-back, spaces recently played tracks, optionally weights by genre similarity. Needs metadata access during shuffle computation. Effort: 4-6 hours. Important for 764GB library with thousands of tracks.

---

## C-TIER FEATURES (future phases)

### C1: Crossfade Between Tracks
Smooth audio blend between ending/starting tracks. Media3 doesn't natively support this — needs dual ExoPlayer instances or custom AudioProcessor. High complexity (10-15 hours), many edge cases. Defer.

### C2: Playback Stats / Listening History
Room database logging play events. Stats screen with top artists, songs, genres, total listening time. Good differentiator — no local player does this well. Effort: 6-8 hours. Scope creep risk.

### C3: Synced Lyrics Editor
Tap-along timestamping to create LRC files for unsynced lyrics. Leverages existing LRC playback and FlacTagManager file I/O. Unique feature for FLAC community. Effort: 8-12 hours.

---

## Key Architecture Files Reference

| File | Purpose | Lines |
|------|---------|-------|
| `FullBottomSheet.kt` | Now Playing UI — all controls, animations, metadata display | ~1,666 |
| `PostAmpAudioSink.kt` | Audio session lifecycle, effect attachment | ~475 |
| `EffectWrapper.kt` | Generic AudioEffect wrapper pattern (follow this for EQ) | ~280 |
| `GramophonePlaybackService.kt` | Playback service, audio session change dispatch | ~764 |
| `SemanticLyrics.kt` | Lyrics parsing (LRC/TTML/SRT) | ~80KB |
| `NewLyricsView.kt` | Lyrics visualization with word-level highlighting | Large |
| `SearchFragment.kt` | Sectioned search with ConcatAdapter | ~219 |
| `TagDetailFragment.kt` | FLAC metadata viewer | ~182 |
| `FlacTagManager.kt` | FLAC tag read/write via ealvatag | Utility |
| `AudioFormatDetector.kt` | Bitrate/sample rate/codec detection | ~846 |
| `full_player.xml` | Now Playing layout (portrait) | Layout |
| `layout-w600dp-land/full_player.xml` | Now Playing layout (landscape) | Layout |
| `colors.xml` | Theme colors (#69FF5C primary green) | Values |
| `ViewPagerFragment.kt:101` | Example of accessing audioSessionId from UI | Reference |

---

## Testing Protocol

For each feature:
1. Build APK: run the build command above
2. Deploy to Pixel 10 Pro XL: `adb -s 58081FDCQ000EB install -r <apk>`
3. Launch: `adb shell am start -n com.clayboi.player.debug/org.akanework.gramophone.ui.MainActivity`
4. On-device verification using android-mcp (device ID `58081FDCQ000EB`)
5. Document results in edit log at `Edit Logs/`
6. Commit after each verified feature (not before)

---

## Edit Log Convention

- Location: `C:\Users\chawo\Projects\GRAMOPHONE_CUSTOM\Edit Logs\`
- Naming: `Edits_<phase>_<date>.txt`
- Content: what changed, how, why, files modified, test results, git state
- Always read existing logs at session start; always write a new log at session end

---

## MCP Tools — Available Servers & Usage

You have access to a full suite of MCP servers. **Use these proactively** — default to the purpose-built MCP tool over manual alternatives (bash, curl, raw git commands). If an MCP exists for the task, use it.

### Direct MCP Servers (Claude App / Claude Code native)

These are first-class MCPs loaded directly into the session:

| Server | Use For |
|--------|---------|
| **Desktop Commander** | Read/write/edit files on Clay's Windows machine, run processes, search files. **Primary tool for all local file operations.** |
| **Windows-MCP** | Execute PowerShell commands, interact with Windows desktop/apps (click, type, screenshot), launch applications. Use for any Windows-native operations. |
| **android-mcp** | Connect to Clay's Pixel 10 Pro XL (`58081FDCQ000EB`), take device snapshots, click/type/swipe on device, read notifications. **Use for on-device testing after APK install.** |
| **Filesystem** | Container filesystem access (reads within the Linux sandbox environment). |
| **Context7** | Look up current library/framework documentation (Kotlin, Jetpack Compose, media3, Gradle, etc.). **Always prefer this over training knowledge for API details.** |
| **Shadcn UI** | Component library reference (if building any web-based tooling). |
| **PDF (By Anthropic)** | Display and list PDFs. |
| **PDF Tools - Analyze** | PDF analysis and extraction. |

### MCP_DOCKER Sub-Servers

These are accessed through the MCP_DOCKER gateway. All are available:

| Server | Use For |
|--------|---------|
| **GitHub Official** | All GitHub repo operations: read/create issues, PRs, branches, files, commits, releases, code search. **Primary tool for all git remote operations on `clayboicardi/Gramophone`.** |
| **Git (Reference)** | Local git operations: init, add, commit, checkout, diff, log, status, show. **Use for local repo management.** |
| **GitMCP** | Search and read documentation/code from any GitHub repository. Use to look up upstream Gramophone code or any reference repo. |
| **Playwright** | Full browser automation: navigate, click, type, screenshot, evaluate JS, fill forms. Use for web-based testing or research. |
| **DuckDuckGo** | Web search. Use for researching Android APIs, StackOverflow solutions, library docs not in Context7. |
| **Fetch (Reference)** | Fetch URL content as markdown. Use to read web pages, documentation, or API responses. |
| **Markdownify** | Convert files to markdown: webpages, PDFs, DOCX, XLSX, PPTX, images, audio, YouTube transcripts. |
| **Sequential Thinking** | Structured multi-step reasoning for complex problems. Use when planning architecture changes, debugging tricky issues, or making design decisions. |
| **Memory (Reference)** | Persistent knowledge graph: store entities, observations, and relations across sessions. Use to track project state, decisions, and context that should persist. |
| **SQLite** | Local structured data storage and queries. Use if any feature needs persistent local data (e.g., build logs, test results). |
| **Python Refactoring Assistant** | Analyze Python files for refactoring opportunities. (Less relevant for Kotlin project, but available.) |
| **Docker Hub** | Search Docker images, check repositories and tags. |
| **Time (Reference)** | Get current time, convert between timezones. |
| **Next.js DevTools** | Next.js documentation and runtime interaction. (Not relevant to this project unless building web tooling.) |
| **Excalidraw** | Create hand-drawn style diagrams and visual sketches. Use for architecture diagrams or feature planning visuals. |
| **Miro** | Collaborative visual boards. |
| **API Gateway** | Execute arbitrary API endpoints with custom parameters. |

### MCP Usage Rules for This Project

1. **File operations on Clay's machine** → Desktop Commander (`read_file`, `write_file`, `edit_block`, `list_directory`, `start_search`). Never use bash_tool for Windows filesystem access.
2. **Shell commands on Windows** → Windows-MCP (`Shell` for PowerShell). Use for gradle builds, ADB commands, and anything requiring Clay's local environment variables.
3. **On-device testing** → android-mcp. After installing APK, use `Snapshot` to see device state, `Click`/`Swipe`/`Type` to interact, verify features visually.
4. **GitHub operations** → GitHub Official MCP. Push commits, create branches, manage PRs on `clayboicardi/Gramophone`.
5. **Local git** → Git (Reference) MCP for `git status`, `git add`, `git commit`, `git diff`, etc. within the repo at `C:\Users\chawo\Projects\GRAMOPHONE_CUSTOM\`.
6. **Looking up Android/Kotlin/Gradle/media3 APIs** → Context7 first, then DuckDuckGo + Fetch if Context7 doesn't have it.
7. **Complex planning** → Sequential Thinking for multi-step architecture decisions before writing code.
8. **Reading upstream Gramophone code** → GitMCP to search `FoedusProgramme/Gramophone` for reference implementations.

---

## Session Rules (from CLAUDE.md)

- Never run destructive commands without explicit confirmation
- Use CMD syntax on Windows, bash for gradle builds
- `sys.stdout.reconfigure(encoding='utf-8')` in Python scripts (special characters in artist/album names)
- Clay is not a developer — take the lead, do then explain, be blunt and direct
- High confidence threshold — if unsure, say so
- Keep tasks sized for completion within ~4 hours of productive energy (ADHD)
