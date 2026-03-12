# Session 3: Frequency Response Curve Polish + Auto-Preamp + Profile System

## Prerequisites
Sessions 1 and 2 must be complete:
- `BiquadFilter.kt`, `ParametricEqConfig.kt`, `ParametricEqProcessor.kt` exist and are wired into the pipeline
- `ParametricEqActivity.kt` exists with 31-band text input grid and basic `FrequencyResponseView`
- Mode toggle between Simple and Advanced EQ works

Read the existing files before starting to understand current state.

## What You're Building
This session polishes the frequency response curve visualization, adds auto-preamp calculation, implements the profile save/load system for parametric EQ configs, and adds band highlighting (selected band is visually indicated on both the curve and the grid).

---

## Changes

### 1. Polish FrequencyResponseView

Read the existing `FrequencyResponseView.kt`. Enhance it with:

**Band dot indicators:**
- Draw a small filled circle at each band's (frequency, gain) position on the curve
- The selected band's dot should be larger and use the accent color
- Non-selected dots use a dimmer color

```kotlin
// After drawing the curve path, draw band dots
for (i in bandConfigs.indices) {
    val band = bandConfigs[i]
    if (!band.enabled) continue
    val x = freqToX(band.frequencyHz.toDouble(), w, 20.0, 20000.0)
    val y = dbToY(band.gainDb.toDouble(), h, -15.0, 15.0)
    val radius = if (i == selectedBandIndex) 12f else 6f
    val paint = if (i == selectedBandIndex) selectedDotPaint else dotPaint
    canvas.drawCircle(x, y, radius, paint)
}
```

**Frequency axis labels:**
- Draw text labels at bottom: "20", "100", "1k", "10k", "20k"
- Small vertical tick marks at each labeled frequency

**dB axis labels:**
- Draw text labels on the left: "+15", "+10", "+5", "0", "-5", "-10", "-15"

**0 dB reference line:**
- Draw the 0 dB line slightly thicker/more prominent than other grid lines

**Gradient fill:**
- Fill area between the curve and the 0 dB line with a semi-transparent gradient
- Boost areas (above 0 dB) use accent color fill
- Cut areas (below 0 dB) can use a different tint or same color but inverted

**Performance note:**
- `magnitudeAt()` is called for each pixel × each band = ~360 pixels × 31 bands = ~11K evaluations per draw
- Each evaluation is ~20 floating-point ops = ~220K ops total per frame
- This is negligible, no need for caching unless you notice lag on redraw

**Add a `setSelectedBand(index: Int)` method** that stores the selected index and calls `invalidate()`.

---

### 2. Auto-Preamp Calculation

When the user has positive gain on any band, the combined signal can clip. Auto-preamp reduces the preamp by the maximum boost to prevent this.

**Simple algorithm:**
```kotlin
private fun calculateAutoPreamp(config: EqConfig): Float {
    val maxGain = config.bands
        .filter { it.enabled }
        .maxOfOrNull { it.gainDb } ?: 0f
    // If max gain is positive, apply negative preamp to compensate
    return if (maxGain > 0f) -maxGain else 0f
}
```

**Wire to the "Auto" button in ParametricEqActivity:**
```kotlin
autoPreampButton.setOnClickListener {
    val autoDb = calculateAutoPreamp(currentConfig)
    currentConfig = currentConfig.copy(preampDb = autoDb)
    preampValueText.text = formatGain(autoDb)
    applyConfig()
}
```

**Also run auto-preamp suggestion after any gain change** — show a warning icon or text hint if preamp + max band gain > 0 dB (risk of clipping), but don't auto-apply (let user decide).

---

### 3. Parametric EQ Profile System

The existing `EqEffectWrapper` already has `saveProfile()`/`loadProfile()`/`deleteProfile()` for the simple 5-band EQ. The parametric EQ needs its own separate profile storage because the data shape is different (31 bands with freq/gain/Q/type vs 5 band levels).

**Add to ParametricEqConfig.kt (or create a ParametricEqProfileManager helper):**

```kotlin
object ParametricEqProfileManager {
    private const val PREF_PROFILE_NAMES = "parametric_eq_profile_names"
    private const val PREF_PROFILE_PREFIX = "parametric_eq_profile_"

    fun getProfileNames(prefs: SharedPreferences): List<String> {
        return (prefs.getStringSet(PREF_PROFILE_NAMES, null) ?: emptySet()).sorted()
    }

    fun saveProfile(prefs: SharedPreferences, name: String, config: EqConfig) {
        val names = (prefs.getStringSet(PREF_PROFILE_NAMES, null) ?: emptySet()).toMutableSet()
        names.add(name)
        prefs.edit()
            .putStringSet(PREF_PROFILE_NAMES, names)
            .putString("$PREF_PROFILE_PREFIX$name", config.toJson().toString())
            .apply()
    }

    fun loadProfile(prefs: SharedPreferences, name: String): EqConfig? {
        val json = prefs.getString("$PREF_PROFILE_PREFIX$name", null) ?: return null
        return EqConfig.fromJson(JSONObject(json))
    }

    fun deleteProfile(prefs: SharedPreferences, name: String) {
        val names = (prefs.getStringSet(PREF_PROFILE_NAMES, null) ?: emptySet()).toMutableSet()
        names.remove(name)
        prefs.edit()
            .putStringSet(PREF_PROFILE_NAMES, names)
            .remove("$PREF_PROFILE_PREFIX$name")
            .apply()
    }
}
```

**Preset dropdown in ParametricEqActivity:**
- Items: "Flat" (default) → saved profiles → "Save Current..." → "Delete Profile..."
- Same pattern as the simple EQ dropdown from `EqualizerBottomSheet.kt`
- "Flat" loads `EqConfig.createDefault31Band()`
- Saved profiles load from `ParametricEqProfileManager`

**Built-in presets to consider (optional, nice-to-have):**
- "Bass Boost": +6dB at 60-100 Hz, gentle slope
- "Treble Boost": +4dB above 8kHz
- "V-Shape": boost lows and highs, cut mids
- "Vocal Clarity": boost 2-4kHz range

These can just be hardcoded `EqConfig` instances — nice for first-time users.

---

### 4. Band Selection Sync Between Grid and Curve

When user taps a gain value in the grid:
1. `selectedBandIndex` updates
2. The band grid refreshes (selected band's text becomes bold/accented)
3. `FrequencyResponseView.setSelectedBand(index)` is called → curve redraws with highlighted dot
4. The selected band detail panel at the bottom updates (shows that band's Q, type, etc.)

When user taps the frequency label (not the gain) of a band:
- Same selection behavior, but no input dialog — just selects the band
- This lets users browse bands without editing

---

### 5. Selected Band Detail Panel — Full Parametric Controls

When a band is selected, show below the grid:

```
── Selected: 1000 Hz ────────────────────────
Gain: [+3.0 dB]    Q: [4.318]    [Enabled ✓]
Type: [▼ Peaking]
```

Each value is tappable:
- **Gain**: opens same numeric input dialog as the grid
- **Q**: opens numeric input dialog, range 0.1 to 30.0
  - Hint text: "Q factor (0.1=wide, 4.3=1/3oct, 10+=narrow)"
- **Type**: dropdown with filter types: Peaking, Low Shelf, High Shelf, Low Pass, High Pass, Notch
- **Enabled**: checkbox/switch to enable/disable individual band

**Q input dialog:**
```kotlin
private fun showQInputDialog(bandIndex: Int) {
    // Same pattern as gain dialog
    // Input: TYPE_CLASS_NUMBER | TYPE_NUMBER_FLAG_DECIMAL
    // Validate: clamp to 0.1..30.0
    // On OK: updateBandConfig(bandIndex, band.copy(q = newQ))
}
```

**Filter type dropdown:**
```kotlin
private fun showFilterTypeDropdown(bandIndex: Int) {
    val types = FilterType.values()
    val names = types.map { it.name.replace("_", " ").lowercase().capitalize() }
    MaterialAlertDialogBuilder(this)
        .setTitle("Filter Type")
        .setItems(names.toTypedArray()) { _, which ->
            updateBandConfig(bandIndex, currentConfig.bands[bandIndex].copy(filterType = types[which]))
        }
        .show()
}
```

---

## New String Resources

```xml
<string name="eq_clipping_warning">Clipping risk: preamp + max gain exceeds 0 dB</string>
<string name="eq_q_hint">Q factor (0.1=wide, 4.3=1/3 oct, 10+=narrow)</string>
<string name="eq_flat">Flat</string>
```

---

## Build & Verify

```bash
./gradlew :app:assembleDebug
adb -s 58081FDCQ000EB install -r app/build/outputs/apk/debug/JAMZ-*.apk
```

**Test checklist:**
1. Open Advanced EQ → frequency response curve shows flat line at 0 dB
2. Tap 1000 Hz gain → type "+6" → curve shows peak at 1 kHz
3. Band dot at 1 kHz is highlighted on curve
4. Selected band detail shows Gain: +6 dB, Q: 4.318, Type: Peaking
5. Tap Q value → type "1.0" → curve shows wider peak
6. Change filter type to "Low Shelf" → curve changes shape
7. Tap "Auto" preamp → preamp changes to -6 dB
8. Save profile "My EQ" → shows in dropdown
9. Tap "Flat" → all gains reset → load "My EQ" → settings restored
10. Delete "My EQ" → removed from dropdown
11. Exit and reopen → settings persist

## Commit
```
feat: polish frequency response curve, auto-preamp, EQ profiles

- Enhanced FrequencyResponseView with band dots, axis labels, gradient
  fill, and selected band highlighting
- Auto-preamp calculation prevents clipping from boosted bands
- Parametric EQ profile save/load/delete system (separate from simple
  EQ profiles due to different data shape)
- Full per-band parametric controls: Q factor, filter type, enable
```

## Hard Rules
- Build with `:app:assembleDebug`
- Don't modify the media3 submodule
- Don't introduce new external dependencies
- Read source files before modifying
- One change at a time, build and verify after each
