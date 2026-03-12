# JAMZ Parametric EQ — Complete Handoff Document
## Sessions 1–4 Audit & Codebase Context

**Date:** 2026-03-11
**Author:** Claude (AI pair programmer)
**For:** Fresh Claude Code session performing codebase audit, cleanup, and refinement
**Branch:** `beta`
**Commit range:** `f0016163..f5e05d42` (4 commits, 2,019 lines added across 14 files)

---

## Table of Contents
1. [Project Context](#1-project-context)
2. [What Was Built](#2-what-was-built)
3. [Commit History](#3-commit-history)
4. [Session-by-Session Breakdown](#4-session-by-session-breakdown)
5. [Blueprint Deviations — All Sessions](#5-blueprint-deviations--all-sessions)
6. [File Inventory](#6-file-inventory)
7. [Architecture & Data Flow](#7-architecture--data-flow)
8. [Known Issues & Technical Debt](#8-known-issues--technical-debt)
9. [Testing Status](#9-testing-status)
10. [Audit Checklist](#10-audit-checklist)

---

## 1. Project Context

**JAMZ** is a personal Android music player fork of [Gramophone](https://github.com/FoedusProgramme/Gramophone) (GPL-3.0). The owner (Clay) has a large curated FLAC library (~39,500 tracks, ~349GB) and wants audiophile-grade EQ.

**Pre-existing EQ:** Gramophone had a simple 5-band hardware equalizer using Android's built-in `Equalizer` AudioEffect (via `EqualizerBottomSheet`). This is limited, device-dependent, and only works with specific audio sessions.

**Goal:** Add a software-based 31-band parametric EQ that:
- Works with any audio format/device (processes PCM in the Media3 pipeline)
- Gives precise control (exact dB input, Q factor, filter type per band)
- Has a frequency response curve visualization
- Supports save/load profiles and built-in presets
- Coexists with the simple EQ via a mode toggle

**Build system:** Kotlin, Gradle KTS, Media3 as git submodule, min SDK 21, compile SDK 36.

---

## 2. What Was Built

A complete 31-band parametric equalizer system consisting of:

| Layer | Component | Lines | Description |
|-------|-----------|-------|-------------|
| DSP | `BiquadFilter.kt` | 168 | Single biquad filter section (6 types, TDF-II) |
| DSP | `ParametricEqProcessor.kt` | 303 | Media3 AudioProcessor chaining 31 biquads |
| Data | `ParametricEqConfig.kt` | 217 | Config model, persistence, profiles, presets |
| UI | `ParametricEqActivity.kt` | 693 | Full-screen Activity with band grid + controls |
| UI | `FrequencyResponseView.kt` | 291 | Custom canvas View drawing frequency response |
| UI | `activity_parametric_eq.xml` | 277 | Material 3 layout |
| Glue | `GramophoneRenderFactory.kt` | +5 | Pipeline wiring |
| Glue | `EqualizerBottomSheet.kt` | +12 | Mode toggle button |
| Glue | `AndroidManifest.xml` | +5 | Activity registration |
| Glue | `strings.xml` | +28 | String resources |

**Total new code:** ~2,019 lines across 14 files.

---

## 3. Commit History

```
f0016163  Session 1: feat: add parametric EQ DSP engine with 31-band biquad processing
911e000e  Session 2: feat: add Advanced EQ activity with 31-band text input UI
d51a2f3f  Session 3: feat: polish frequency response curve, auto-preamp, EQ profiles
f5e05d42  Session 4: feat: built-in EQ presets, crossfade transitions, edge case polish
```

Each session built on the previous. Sessions 1-3 ran in one conversation. Session 4 ran in a continuation after context compaction.

---

## 4. Session-by-Session Breakdown

### Session 1: Core DSP Engine
**Blueprint:** `Session Blueprints/SESSION-1-core-dsp-engine.md`
**Commit:** `f0016163`

**What was built:**
- `BiquadFilter.kt` — Transposed Direct Form II biquad filter with 6 types (peaking, low/high shelf, low/high pass, notch). Coefficients computed in Double precision (trig needs it), processing in Float (sufficient for audio). Implements Robert Bristow-Johnson's Audio EQ Cookbook formulas. Includes `magnitudeAt()` for UI curve evaluation.
- `ParametricEqConfig.kt` — Immutable data model with `EqConfig`, `BandConfig`, `FilterType` enum. 31 ISO 1/3-octave bands (20 Hz – 20 kHz). JSON serialization. SharedPreferences persistence. Default Q of 4.318 (1/3 octave bandwidth).
- `ParametricEqProcessor.kt` — Media3 `BaseAudioProcessor` that chains 31 biquad filters. Uses `ToFloatPcmAudioProcessor` for input conversion. `AtomicReference<EqConfig?>` for thread-safe UI→audio config handoff. Clears filter state on flush. Always outputs `ENCODING_PCM_FLOAT`.
- Wired into `GramophoneRenderFactory.kt` between ReplayGain and Visualizer Tee.
- Added singleton cleanup in `PostAmpAudioSink.kt`.

**Verification:** App played music unchanged with EQ disabled by default. No audio glitches.

### Session 2: Activity + UI
**Blueprint:** `Session Blueprints/SESSION-2-basic-ui-mode-toggle.md`
**Commit:** `911e000e`

**What was built:**
- `ParametricEqActivity.kt` — Full-screen Activity with:
  - MaterialToolbar with enable/disable switch
  - Preset dropdown (Flat + built-in presets)
  - Preamp control (tap to edit, numeric dialog)
  - FrequencyResponseView (read-only curve)
  - HorizontalScrollView with 31 band columns (frequency label + gain label)
  - Selected band detail panel (gain, Q, filter type, enabled toggle)
  - Reset band / Reset all buttons
  - "Switch to Simple EQ" button
- `activity_parametric_eq.xml` — Material 3 layout (CoordinatorLayout + AppBarLayout + NestedScrollView)
- `FrequencyResponseView.kt` — Custom View drawing combined frequency response with log X axis (20Hz–20kHz), linear Y (±15 dB), grid lines, curve with gradient fill, band dot indicators
- Registered in `AndroidManifest.xml`
- Added "Switch to Advanced EQ" button in `EqualizerBottomSheet` + mode toggle preference (`eq_mode`)
- Updated `FullBottomSheet.kt` to route to correct EQ based on mode preference

### Session 3: Curve Polish + Profiles
**Blueprint:** `Session Blueprints/SESSION-3-frequency-response-curve.md`
**Commit:** `d51a2f3f`

**What was built:**
- Enhanced `FrequencyResponseView`: selected band dot larger/brighter, gradient fill split into boost (above 0dB) and cut (below 0dB) regions with different alphas
- `ParametricEqProfileManager` (inside `ParametricEqConfig.kt`): save/load/delete profiles via SharedPreferences, profile names stored as StringSet
- Auto-preamp calculation: finds max potential gain across all bands, sets preamp to negative of that value to prevent clipping
- Clipping warning indicator (shows when preamp + max band gain > 0 dB)
- Per-band controls wired up: Q input (0.1–30.0), filter type dropdown, enabled checkbox
- Tap-to-edit gain values with numeric dialog (clamped ±15 dB)

### Session 4: Presets + Polish
**Blueprint:** `Session Blueprints/SESSION-4-polish-and-presets.md`
**Commit:** `f5e05d42`

**What was built:**
- **7 built-in presets** with refined gain curves: Flat, Bass Boost, Treble Boost, V-Shape, Vocal Clarity, Loudness (with -4dB preamp), Reduce Sibilance
- **Crossfade transitions** in `ParametricEqProcessor`: detects multi-band preset switches (>3 bands changed), applies cos/sin energy-preserving crossfade over ~10ms to prevent clicks
- **`copyFrom()` on BiquadFilter**: copies coefficients + delay state for crossfade snapshots
- **Sample rate change handling**: `onConfigure()` detects format changes and recalculates all coefficients
- **`onPause()` persistence**: saves config when activity pauses (handles unexpected kills)
- **Gain color coding**: green (boost), red (cut), default (zero) — uses Material theme colors
- **Long-press to reset band**: resets individual band to 0 dB / PEAKING / default Q
- **Auto-advance**: after editing a band's gain, automatically selects next band
- **Scroll to selected band**: `HorizontalScrollView` smoothly scrolls to center the selected band
- **maxChannels ≥ 8**: supports surround audio formats

---

## 5. Blueprint Deviations — All Sessions

### Session 1 Deviations
| Blueprint Spec | Actual Implementation | Reason |
|---|---|---|
| Blueprint shows `ParametricEqProcessor` calling `computeCoefficients()` | Used `BiquadFilter.configure()` directly | `computeCoefficients()` doesn't exist as a separate method — `configure()` IS the coefficient computation |
| Blueprint mentions `isActive` override | Not overridden; `pendingActive` flag + `onConfigure` returning float format handles this | BaseAudioProcessor's default `isActive` already works correctly when output format differs from input |

### Session 2 Deviations
| Blueprint Spec | Actual Implementation | Reason |
|---|---|---|
| Blueprint shows separate `EqualizerModeToggle` component | Mode toggle is a simple preference check + button in both UIs | A separate component was over-engineered for what amounts to a SharedPreference flip |
| Blueprint shows complex intent routing in FullBottomSheet | Simple `if/else` on `eq_mode` preference value | The routing logic is 10 lines, not worth a separate abstraction |
| Blueprint shows `onBandSelected` callback from FrequencyResponseView | Curve is read-only; band selection happens via tap on grid labels | Touch handling on the curve would conflict with scroll gestures and adds complexity for minimal UX benefit |

### Session 3 Deviations
| Blueprint Spec | Actual Implementation | Reason |
|---|---|---|
| Blueprint shows `ProfileManager` as a separate class file | Implemented as `ParametricEqProfileManager` object inside `ParametricEqConfig.kt` | Keeps all config/persistence logic in one file; the manager is only ~60 lines |
| Blueprint shows profile import/export via JSON files | Not implemented | Over-scoped for initial release; SharedPreferences profiles are sufficient |
| Blueprint shows per-band solo/mute buttons | Only per-band enabled checkbox | Solo requires additional state management and is rarely used; enabled/disabled covers the primary use case |

### Session 4 Deviations
| Blueprint Spec | Actual Implementation | Reason |
|---|---|---|
| Blueprint uses `withBandGains` extension method on EqConfig | Kept Session 3's `createPreset` helper, added `preampDb` parameter | Avoids introducing a new pattern when the existing helper works fine. Same functional result. |
| Blueprint's crossfade loop is simpler (no mid-buffer handling) | Added handling for crossfade ending mid-buffer | Blueprint's version would produce silence/garbage for remaining samples in a buffer if crossfade ends partway through. Real-world buffers are ~1024-4096 samples; a 10ms crossfade at 48kHz is ~480 samples, so mid-buffer completion is common. |
| Blueprint section 3c: show "Play a song to initialize" for null processor | Not added | The activity already handles null processor gracefully — `processor?.updateConfig()` is a no-op when null. A message would be confusing since the EQ config is stored in SharedPreferences regardless. |
| Blueprint section 3e: restore config in `onFlush()` | Not modified | The existing `checkPendingConfig()` call in `onFlush()` already picks up any pending config. The UI re-applies config on resume via `onPause()`→save, `onCreate()`→load pattern. |
| Blueprint's `copyFrom` copies `enabled` field | Adapted — BiquadFilter has no `enabled` field | `enabled` is on `BandConfig`, not `BiquadFilter`. The crossfade logic copies `bandConfigs` list separately. |
| Blueprint uses `com.google.android.material.R.attr.colorError` | Changed to `android.R.attr.colorError` | Material's `colorError` attr wasn't resolving in this project's Material Components version. `android.R.attr.colorError` works universally on API 26+. |

---

## 6. File Inventory

### New Files (created in Sessions 1–4)

| File | Lines | Session | Purpose |
|------|-------|---------|---------|
| `logic/utils/BiquadFilter.kt` | 168 | 1, 4 | Biquad filter DSP (6 types, TDF-II) |
| `logic/utils/ParametricEqConfig.kt` | 217 | 1, 3, 4 | Data model, persistence, profiles, presets |
| `logic/utils/ParametricEqProcessor.kt` | 303 | 1, 4 | Media3 AudioProcessor with crossfade |
| `ui/ParametricEqActivity.kt` | 693 | 2, 3, 4 | Full-screen parametric EQ Activity |
| `ui/components/FrequencyResponseView.kt` | 291 | 2, 3 | Custom frequency response curve View |
| `res/layout/activity_parametric_eq.xml` | 277 | 2 | Material 3 layout |

### Modified Files

| File | Changes | Session | What Changed |
|------|---------|---------|-------------|
| `logic/utils/exoplayer/GramophoneRenderFactory.kt` | +5 lines | 1 | Instantiate + wire ParametricEqProcessor into pipeline |
| `logic/utils/PostAmpAudioSink.kt` | +1 line | 1 | Clear singleton on release |
| `ui/components/EqualizerBottomSheet.kt` | +12 lines | 2 | "Switch to Advanced EQ" button |
| `ui/components/FullBottomSheet.kt` | ~10 lines | 2 | Route to correct EQ based on mode preference |
| `ui/fragments/SearchFragment.kt` | 1 line | (unrelated) | Album artist search fix |
| `AndroidManifest.xml` | +5 lines | 2 | Register ParametricEqActivity |
| `res/values/strings.xml` | +28 lines | 2, 4 | String resources for EQ UI + presets |
| `res/layout/fragment_equalizer.xml` | +10 lines | 2 | "Switch to Advanced" button in simple EQ |

---

## 7. Architecture & Data Flow

### Audio Processing Pipeline
```
Raw PCM → ReplayGainAudioProcessor → ParametricEqProcessor → TeeAudioProcessor (Visualizer) → AudioTrack
```

### Thread Model
```
UI Thread                          Audio Thread
─────────                          ────────────
ParametricEqActivity               ParametricEqProcessor.queueInput()
  ↓ updateConfig(EqConfig)           ↓ checkPendingConfig()
  ↓ AtomicReference.set()     →      ↓ AtomicReference.getAndSet(null)
                                     ↓ applyConfig() → rebuild filters
                                     ↓ process samples through biquad cascade
```

### Data Persistence
```
SharedPreferences ("parametric_eq_config")
  ↕ JSON serialization
EqConfig (immutable data class)
  ↕ AtomicReference handoff
ParametricEqProcessor (audio thread state)

SharedPreferences ("parametric_eq_profile_names" StringSet)
SharedPreferences ("parametric_eq_profile_<name>" JSON string)
  ↕
ParametricEqProfileManager (save/load/delete)
```

### Crossfade Flow (preset switches)
```
1. UI calls processor.updateConfig(newConfig)
2. Audio thread picks up in checkPendingConfig()
3. applyConfig() detects >3 bands changed → needsCrossfade=true
4. Old filters copied via copyFrom(), old preamp/bandConfigs saved
5. New filters configured with new coefficients, state cleared
6. For ~10ms (480 frames @ 48kHz):
   - Each sample processed through BOTH old and new filter chains
   - Output = oldSample * cos(t*π/2) + newSample * sin(t*π/2)
7. After crossfade completes, old filters cleared, normal processing resumes
```

### Mode Toggle Flow
```
EqualizerBottomSheet ("Switch to Advanced")
  → SharedPreferences "eq_mode" = "advanced"
  → startActivity(ParametricEqActivity)

ParametricEqActivity ("Switch to Simple")
  → SharedPreferences "eq_mode" = "simple"
  → Disable parametric EQ processor
  → finish()
```

---

## 8. Known Issues & Technical Debt

### Critical
1. **ANR on EQ access via bottom sheet path** — When navigating Play Song → Expand Player → Tap Equalizer, persistent ANR dialogs appear. Root cause is SharedPreferences disk I/O on main thread during the bottom sheet → activity transition. This is a **pre-existing issue** not introduced by the parametric EQ, but it blocks the normal user flow to the EQ. Fix: move SharedPreferences reads to a background thread or use DataStore.

### Should Fix
2. **`exported="false"` on ParametricEqActivity** — Currently set correctly, but during testing it had to be temporarily set to `exported="true"` to bypass the ANR. This is reverted and committed correctly, but the ANR root cause (item 1) still needs fixing.

3. **No input validation on profile names** — User can save profiles with empty names, names containing special characters, or duplicate names. Should add validation in the save dialog.

4. **Large Activity class** — `ParametricEqActivity.kt` is 693 lines. Consider extracting:
   - Profile management into a separate `ProfileDialogHelper`
   - Band grid building into a `BandGridAdapter` or custom ViewGroup
   - Dialog creation into utility methods

5. **`colorError` fallback** — Using `android.R.attr.colorError` instead of Material's `colorError`. Works on API 26+ but the app targets min SDK 21. Should verify behavior on API 21-25 or add a fallback color.

### Nice to Have
6. **Frequency response curve not interactive** — Blueprint originally spec'd band selection from curve taps. Current implementation only allows selection from the band grid.

7. **No undo/redo** — No way to undo individual band changes or revert to previous state.

8. **Missing accessibility** — Band grid items don't have proper content descriptions. Screen reader users can't operate the EQ.

9. **Sample rate change not tested** — Code handles it in `onConfigure()` but wasn't verified with actual different-sample-rate tracks during testing.

10. **Crossfade audible verification** — Crossfade was code-verified and visually confirmed (preset switch was smooth) but couldn't be audibly verified for click-free transitions through screenshot-based testing.

11. **Simple EQ not disabled when Advanced is active** — The mode toggle sets a preference, but the hardware `Equalizer` AudioEffect may still be active in the background. Should explicitly release the hardware EQ when switching to Advanced mode.

---

## 9. Testing Status

### Session 4 On-Device Test Results (Pixel 10 Pro XL)

| # | Test | Result | Notes |
|---|------|--------|-------|
| 1 | Dropdown shows all 7 built-in presets | PASS | Flat, Bass Boost, Treble Boost, V-Shape, Vocal Clarity, Loudness, Reduce Sibilance |
| 2 | Bass Boost loads correct gains | PASS | +6, +6, +5.5, +5, +4.5, +4, +3, +2 dB verified |
| 3 | Preset switch smooth (V-Shape) | PASS | Visual confirmation, no visible glitch |
| 4 | Save custom profile "My V-Shape" | PASS | Appears in dropdown below built-ins |
| 5 | Flat resets all to 0 dB | PASS | All bands reset |
| 6 | Load saved profile "My V-Shape" | PASS | V-Shape gains restored correctly |
| 7 | Sample rate change handling | NOT TESTED | Requires different-bitrate tracks, code review looks correct |
| 8 | Config persists after force-stop | PASS | SharedPreferences dump confirmed |
| 9 | Long-press band resets to 0 dB | PASS | 20 Hz band (+5 dB) → 0 dB on long-press |
| 10 | Gain color coding | PASS | Green (boost), red (cut), default (zero) |

### What Was NOT Tested
- Audio output correctness (no actual listening test — all verification was via screenshots/snapshots)
- Crossfade audible smoothness (only visual confirmation)
- Multi-channel audio (>2 channels)
- Edge cases: very low sample rates, mono audio, DSD
- Memory usage / long-running stability
- Simple ↔ Advanced mode toggle (ANR blocked this path)
- Profile deletion
- Q and filter type changes on audio output

---

## 10. Audit Checklist

Use this checklist for the codebase audit session:

### Code Quality
- [ ] Review `ParametricEqActivity.kt` for extraction opportunities (693 lines is large)
- [ ] Check all `TODO` comments in the codebase
- [ ] Verify proper resource cleanup (filter arrays, listeners)
- [ ] Check for potential memory leaks (static singleton `ParametricEqProcessor.instance`)
- [ ] Review thread safety: is the `AtomicReference` pattern correct and complete?
- [ ] Check all casts and null safety (especially `currentConfig!!` in `onConfigure`)

### DSP Correctness
- [ ] Verify biquad coefficient formulas against Audio EQ Cookbook
- [ ] Check `magnitudeAt()` formula matches the processing formula
- [ ] Verify crossfade doesn't introduce DC offset or phase issues
- [ ] Check that `clearState()` is called at all necessary points
- [ ] Verify `ToFloatPcmAudioProcessor` integration (is `floatInput` consumed correctly?)
- [ ] Check buffer position management in `queueInput()` — are all bytes consumed?

### UI/UX
- [ ] Verify all string resources are properly used (no hardcoded strings)
- [ ] Check color contrast for accessibility
- [ ] Verify scroll behavior with many bands selected
- [ ] Test preset dropdown with many custom profiles
- [ ] Verify `onPause()`/`onCreate()` lifecycle handling
- [ ] Check that `suppressPresetChange` flag doesn't cause state bugs

### Integration
- [ ] Verify `GramophoneRenderFactory` creates processor in correct pipeline position
- [ ] Check `PostAmpAudioSink` singleton cleanup
- [ ] Verify mode toggle properly disables/enables each EQ system
- [ ] Check `AndroidManifest.xml` — `exported="false"` is correct
- [ ] Verify `EqualizerBottomSheet` mode toggle button works

### Edge Cases
- [ ] What happens if SharedPreferences JSON is corrupted?
- [ ] What happens with 0 Hz or Nyquist frequency bands?
- [ ] What happens if `outputAudioFormat` is `NOT_SET` when config arrives?
- [ ] What happens if channel count changes mid-stream?
- [ ] What happens if all bands are disabled?

### Performance
- [ ] Profile CPU usage of 31 biquad filters per sample
- [ ] Check memory allocation in `queueInput()` hot path
- [ ] Verify no object allocation in the audio processing loop
- [ ] Check if `replaceOutputBuffer` is called correctly

---

## Appendix: Key Design Decisions

1. **Why TDF-II (Transposed Direct Form II)?** — Best for floating-point: only 2 state variables per channel, good numerical properties, industry standard for software EQ.

2. **Why 31 bands?** — ISO 1/3-octave spacing is the professional audio standard. 10 or 15 bands would leave gaps; 31 gives full frequency spectrum control.

3. **Why text input instead of sliders?** — Owner specifically wanted precise dB control. Sliders on a phone screen for 31 bands would be unusable. Tap-to-edit with numeric input is more precise and works well in a horizontal scroll grid.

4. **Why always output float?** — Keeps format consistent regardless of EQ state. Avoids format changes when toggling EQ on/off, which would cause pipeline reconfiguration and potential glitches.

5. **Why crossfade only for >3 band changes?** — Single-band tweaks are typically small enough not to cause audible clicks. Preset switches change many bands simultaneously, which can cause discontinuities. The threshold of 3 balances protection against clicks with avoiding unnecessary processing overhead.

6. **Why cos/sin crossfade?** — Energy-preserving: `cos²(t) + sin²(t) = 1` at all points. Linear crossfade would have a -3dB dip at the midpoint. Cos/sin maintains constant energy.

7. **Why SharedPreferences over Room/DataStore?** — The EQ config is a single JSON blob, not relational data. SharedPreferences is simpler, already used elsewhere in the app, and has no dependency overhead. DataStore would be better for the ANR issue (async by default) but wasn't worth the migration complexity for this feature.
