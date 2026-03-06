# JAMZ!!! (Gramophone Fork) — Session Handoff

## Quick Reference
- **Project path:** `C:\Users\chawo\Projects\GRAMOPHONE_CUSTOM\`
- **Repo:** Fork of [FoedusProgramme/Gramophone](https://github.com/FoedusProgramme/Gramophone) (GPL-3.0)
- **Clay's remote:** `github.com/clayboicardi/Gramophone`
- **Current branch:** `beta` (6 commits ahead of `origin/beta`, NOT pushed)
- **Feature branch:** `phase3-core-fixes` (merged into beta, safe to delete)
- **App name:** JAMZ!!! by Clayboi
- **applicationId:** `com.clayboi.player` (debug: `com.clayboi.player.debug`)
- **namespace:** `org.akanework.gramophone` (unchanged for upstream merge compatibility)

---

## Build & Deploy Commands

```bash
# Build (bash shell — MUST use Temurin JDK 21, NOT JetBrains JBR)
cd "C:/Users/chawo/Projects/GRAMOPHONE_CUSTOM" && \
JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-21.0.10.7-hotspot" \
ANDROID_HOME="C:/Users/chawo/AppData/Local/Android/Sdk" \
./gradlew :app:assembleDebug

# APK output (filename includes version+commit hash):
# app/build/outputs/apk/debug/Gramophone-<version>.<hash>-debug.apk

# Deploy to device
"C:\Users\chawo\Desktop\platform-tools\adb.exe" -s 58081FDCQ000EB install -r <apk-path>

# Launch
"C:\Users\chawo\Desktop\platform-tools\adb.exe" -s 58081FDCQ000EB shell am start -n com.clayboi.player.debug/org.akanework.gramophone.ui.MainActivity
```

**Build prereqs (gitignored, already present):**
- `package.properties` — contains `releaseType=SelfBuilt`
- `local.properties` — contains `sdk.dir=C:\Users\chawo\AppData\Local\Android\Sdk`
- Submodules: `media3` (composite build) + `hificore/src/main/cpp/libusb-cmake`

**Known build gotchas:**
- MUST use `:app:assembleDebug` (not bare `assembleDebug`) — matches upstream CI
- MUST leave configuration cache enabled — `resourceplaceholders` plugin breaks without it
- JetBrains Runtime (Android Studio JBR) has TLS handshake failures with Google CDN — use Temurin JDK 21

---

## Git State (as of March 5, 2026)

```
Branch: beta (6 commits ahead of origin/beta, NOT PUSHED)

*   38ed9aff  Merge phase3-core-fixes
|\
| * 0c8e57b9  feat: tappable Artist/Album Artist toggle in list header
| * 76a86b15  feat: sectioned search with Artists, Albums, and Songs sections
| * b68a3c2d  fix: harden card flip tag info for edge cases
| * cf087656  fix: shuffle all tracks individually instead of shuffling album order
|/
*   e85c2d0c  feat: Phase 2 - swipe-to-skip, bounce animation, card flip tag info, dark theme
*   4a7b1f8f  Apply JAMZ!!! color scheme: dark purple/grey + bright green  ← on origin/beta
*   503be8c7  Rename app display name to JAMZ!!!
*   06a0303d  Change applicationId to com.clayboi.player
```

**Total delta from origin/beta:** 22 files changed, +1695 insertions, -80 deletions

**Working tree:** Clean (only untracked: `Edit Logs/` directory with session notes)

---

## What Was Built — Phase by Phase

### Phase 1: Identity (on origin/beta already)
- Renamed app to "JAMZ!!!" (launcher label, about screen)
- Changed applicationId to `com.clayboi.player`
- Applied custom color scheme: dark purple/grey surfaces (#1A1A1E) + bright green primary (#69FF5C) + muted purple secondary
- Disabled Material You dynamic colors (custom palette always used)
- Content-based album art coloring still works independently

### Phase 2: Now Playing Enhancements (commit e85c2d0c)
14 files, +1460/-41 lines. Single squashed commit.

**Features added:**
1. **Swipe-to-skip** — horizontal swipe on album art skips forward/backward. Uses `GestureDetector` on the cover art `ImageView` in `FullBottomSheet.kt`. Threshold: 100dp, velocity: 200dp/s.

2. **Bounce animation on skip** — album art does a subtle scale bounce (1.0 → 0.92 → 1.0) via `ObjectAnimator` when swiping or pressing skip buttons. Direction-aware: swipe left bounces left, swipe right bounces right.

3. **Card flip tag info** — tapping album art flips the card 180° (Y-axis rotation) to reveal track metadata on the back: title, artist, album, album artist, genre, year, track#, disc#, codec, sample rate, bit depth, MusicBrainz ID. Uses `ObjectAnimator` with `AccelerateDecelerateInterpolator`. Back face is a dark card with scrollable tag rows.

4. **FlacTagManager** — new utility class (`logic/utils/FlacTagManager.kt`, 321 lines) that reads FLAC metadata (Vorbis comments + STREAMINFO) directly from the file using `RandomAccessFile`. Extracts codec, sample rate, bit depth, and MusicBrainz Recording ID without any external library dependency.

5. **TagDetailFragment** — new fragment for viewing full tag details (accessible from a "Details" button on the card back). Shows all tags in a scrollable list with copy-to-clipboard support.

6. **Dark theme color overhaul** — `values-night/colors.xml` rewritten with custom palette. All surfaces, cards, and controls use the dark purple/grey + green scheme.

**Key files created:**
- `app/src/main/java/.../logic/utils/FlacTagManager.kt` — FLAC tag reader
- `app/src/main/java/.../ui/fragments/TagDetailFragment.kt` — tag detail view
- `app/src/main/res/layout/player_tag_info.xml` — card back layout
- `app/src/main/res/layout/fragment_tag_detail.xml` — tag detail layout
- `app/src/main/res/layout/tag_detail_row.xml` — individual tag row

**Key files modified:**
- `app/src/main/java/.../ui/components/FullBottomSheet.kt` — swipe, bounce, card flip logic (+323 lines)
- `app/src/main/java/.../ui/MainActivity.kt` — TagDetailFragment navigation
- `app/src/main/res/layout/full_player.xml` + `layout-w600dp-land/full_player.xml` — card flip container
- `app/src/main/res/values/strings.xml` — tag info labels
- `app/build.gradle.kts` — AndroidManifest merger strategy for exported activities

### Phase 3: Core Functionality Fixes (4 commits, merged via --no-ff)

**Fix 1 (cf087656): Album shuffle → track shuffle**
- **Problem:** "Shuffle All" on album lists shuffled album ORDER but played tracks sequentially within each album. ExoPlayer's shuffle was disabled while albums were pre-shuffled.
- **Fix:** Set `shuffleModeEnabled = true`, flatten all songs without pre-shuffling, let ExoPlayer handle true track-level randomization.
- **Also fixed:** "Play All" for AlbumAdapter — removed stray `.shuffled()` call.
- **File:** `BaseDecorAdapter.kt` lines 173-206

**Fix 2 (b68a3c2d): Card flip tag info edge cases**
- **Problem:** Card flip tag info could show raw null values or crash with missing metadata.
- **Fix:** Added null/blank guards in `populateTagInfo()` — missing fields show "Unknown" for genre, "N/A" for MBID, empty string for year.
- **File:** `FullBottomSheet.kt`

**Fix 3 (76a86b15): Sectioned search results**
- **Problem:** Search only returned songs. No way to find artists or albums.
- **Fix:** Rewrote `SearchFragment.kt` to filter all three data sources (`artistListFlow`, `albumListFlow`, `songListFlow`). Uses `ConcatAdapter` with `setIsolateViewTypes(true)` to chain section headers + filtered adapters. Each section shows up to 5 results; empty sections hidden.
- **Created:** `SearchSectionHeaderAdapter.kt`, `search_section_header.xml`
- **Modified:** `SearchFragment.kt` (full rewrite), `ArtistAdapter.kt` (constructor params for search context), `CustomGridLayoutManager.kt` (span lookup for new adapter), `GridPaddingDecoration.kt` (padding exclusion), `FileOpUtils.kt` (adapter type ID 17)

**Fix 4 (0c8e57b9): Tappable Artist/Album Artist toggle**
- **Problem:** Album Artist toggle was buried as a checkbox in the sort overflow menu — undiscoverable.
- **Fix:** Counter text in list header now shows "▾" indicator and is tappable. Toggles between "4022 Artists ▾" and "257 Album Artists ▾" with proper data source switching and SharedPreferences persistence.
- **Architecture:** Added `onCounterBound()` template method hook to `BaseDecorAdapter` (matches existing `onSortButtonPressed()` pattern). `ArtistDecorAdapter` overrides it. Click listener cleanup added in `onViewRecycled`.
- **Modified:** `BaseDecorAdapter.kt`, `ArtistAdapter.kt`, `strings.xml` (added `album_artists` plurals)

---

## Architecture Notes for Future Work

### Adapter System
- **BaseAdapter<T>** — generic RecyclerView adapter for all content types (songs, albums, artists, etc.)
- **BaseDecorAdapter<T>** — header/decorator adapter (1 item) showing count, sort button, play/shuffle buttons
  - Template methods: `onSortButtonPressed()`, `onExtraMenuButtonPressed()`, `onCounterBound()`
  - Each content adapter has its own DecorAdapter subclass
- **ConcatAdapter** — used to chain DecorAdapter + ContentAdapter (and in search: multiple sections)
  - `setIsolateViewTypes(true)` is CRITICAL when combining multiple BaseAdapters (they share view types)
- **CustomGridLayoutManager** — grid layout with span size lookup. Any new adapter type needs recognition here AND in `GridPaddingDecoration`

### Data Flow
- `FlowReader` exposes `artistListFlow`, `albumArtistListFlow`, `albumListFlow`, `songListFlow` as `Flow<List<T>?>`
- Adapters observe these flows via `liveDataAgent` (a `MutableStateFlow` that can be hot-swapped)
- Album Artist toggle works by swapping `liveDataAgent.value` between `artistListFlow` and `albumArtistListFlow`
- Search filters all three flows with `filter { }` operators

### Card Flip System
- Album art `ImageView` is wrapped in a `FrameLayout` container
- Front face: album art image
- Back face: `player_tag_info.xml` (dark card with scrollable tag rows)
- Flip uses `ObjectAnimator` on `rotationY` (0→90, pause to swap visibility, 90→180)
- `FlacTagManager` reads FLAC metadata directly — no MediaStore dependency for codec/bitrate
- `TagDetailFragment` accessible from "Details" button on card back

### Color System
- Dynamic colors (Material You) disabled via `DynamicColorsOptions` override
- Custom palette in `values-night/colors.xml`: dark purple/grey surfaces + bright green primary
- Content-based coloring (album art → player colors) is independent and still works

---

## Device & Environment
- **Phone:** Pixel 10 Pro XL, device serial `58081FDCQ000EB`
- **ADB path:** `C:\Users\chawo\Desktop\platform-tools\adb.exe`
- **Music library:** `/storage/emulated/0/Music/tiddl/` (~764GB FLAC, 672 artists)
- **All tracks have ALBUMARTIST tags** — verified via audit of 50 random tracks (50/50)
- **OS:** Windows 11 Home 25H2
- **Shell:** Use bash syntax in Claude Code (NOT PowerShell)

---

## What Has NOT Been Pushed

**IMPORTANT:** `beta` is 6 commits ahead of `origin/beta`. These changes are LOCAL ONLY:
- Phase 2 commit (e85c2d0c)
- All 4 Phase 3 commits + merge commit

Clay has not requested a push. Do NOT push without explicit confirmation.

---

## Potential Next Steps (not started)

These are ideas surfaced during the audit — Clay has not requested any of them:

1. **Push to remote** — 6 local commits on `beta` not yet pushed to `origin/beta`
2. **Phase 4 ideas:**
   - Queue management improvements (Gramophone's queue is basic)
   - Playlist creation/editing enhancements
   - Now Playing screen: progress bar styling, lyrics integration
   - Genre/folder browsing improvements
   - Export/backup settings
3. **Upstream merge** — `origin/beta` may have upstream updates to merge. The unchanged namespace (`org.akanework.gramophone`) was kept specifically to make this easier.
4. **Delete `phase3-core-fixes` branch** — already merged, safe to remove
5. **Tag editing** — FlacTagManager currently read-only; could add write support

---

## Edit Logs
- `Edit Logs/Edits_phase3_3-5-26.txt` — detailed Phase 3 changes
- Phase 2 edit log was written in the previous session (may have been in `gramophone-session2-handoff.md` which was deleted during cleanup — the Phase 2 commit message and this handoff serve as the record)

---

## Tool Usage Notes for Next Session

- **Build command must use bash shell** — gradle wrapper is `./gradlew` (bash), not `gradlew.bat`
- **APK filename is versioned** — don't hardcode `Gramophone-debug.apk`, list the `app/build/outputs/apk/debug/` directory to find the actual filename (e.g. `Gramophone-1.0.17.0c8e57b9-debug.apk`)
- **ADB device ID:** `58081FDCQ000EB` — use `-s 58081FDCQ000EB` flag
- **android-mcp tools** available for on-device testing (Snapshot, Click, Type, Swipe, etc.)
- **File encoding:** Use `sys.stdout.reconfigure(encoding='utf-8')` in Python scripts (artist/album names have special characters)
- **Read edit logs first** at session start per MEMORY.md convention
