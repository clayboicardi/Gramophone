# Session 4: Polish, Built-In Presets, Preset Crossfade, Edge Cases

## Prerequisites
Sessions 1-3 must be complete:
- Full DSP engine (BiquadFilter, ParametricEqProcessor) wired into pipeline
- ParametricEqActivity with 31-band text input grid
- FrequencyResponseView with band dots and axis labels
- Auto-preamp, per-band Q/type/enable controls
- Profile save/load/delete system

Read all existing files before starting.

## What You're Building
Final polish session: built-in EQ presets, smooth coefficient transitions when switching presets, edge case handling, and overall UX refinement.

---

## Changes

### 1. Built-In EQ Presets

Add hardcoded preset `EqConfig` instances that appear in the dropdown before user-saved profiles. These give users useful starting points.

**Add to ParametricEqConfig.kt (companion object):**

```kotlin
val BUILT_IN_PRESETS: Map<String, EqConfig> = mapOf(
    "Flat" to createDefault31Band(),

    "Bass Boost" to createDefault31Band().withBandGains(mapOf(
        20f to 6f, 25f to 6f, 31.5f to 5.5f, 40f to 5f, 50f to 4.5f,
        63f to 4f, 80f to 3f, 100f to 2f, 125f to 1f, 160f to 0.5f
    )),

    "Treble Boost" to createDefault31Band().withBandGains(mapOf(
        4000f to 1f, 5000f to 2f, 6300f to 3f, 8000f to 4f,
        10000f to 4.5f, 12500f to 5f, 16000f to 5f, 20000f to 5f
    )),

    "V-Shape" to createDefault31Band().withBandGains(mapOf(
        20f to 5f, 25f to 5f, 31.5f to 4.5f, 40f to 4f, 50f to 3.5f,
        63f to 3f, 80f to 2f, 100f to 1f,
        2000f to -2f, 2500f to -2.5f, 3150f to -2f,
        8000f to 1f, 10000f to 2f, 12500f to 3f, 16000f to 4f, 20000f to 5f
    )),

    "Vocal Clarity" to createDefault31Band().withBandGains(mapOf(
        160f to -1f, 200f to -1.5f, 250f to -2f, 315f to -1.5f,
        2000f to 2f, 2500f to 3f, 3150f to 3.5f, 4000f to 3f, 5000f to 2f
    )),

    "Loudness" to createDefault31Band().withBandGains(mapOf(
        20f to 4f, 25f to 4f, 31.5f to 3.5f, 40f to 3f, 50f to 2f,
        63f to 1f,
        6300f to 1f, 8000f to 2f, 10000f to 3f, 12500f to 3.5f,
        16000f to 4f, 20000f to 4f
    )).copy(preampDb = -4f),

    "Reduce Sibilance" to createDefault31Band().withBandGains(mapOf(
        5000f to -2f, 6300f to -4f, 8000f to -5f, 10000f to -3f, 12500f to -1f
    ))
)

// Helper extension
private fun EqConfig.withBandGains(gains: Map<Float, Float>): EqConfig {
    val newBands = bands.map { band ->
        val gain = gains.entries.minByOrNull {
            kotlin.math.abs(it.key - band.frequencyHz)
        }
        if (gain != null && kotlin.math.abs(gain.key - band.frequencyHz) < 1f) {
            band.copy(gainDb = gain.value)
        } else {
            band
        }
    }
    return copy(bands = newBands)
}
```

**Update the preset dropdown in ParametricEqActivity:**
Dropdown order should be:
1. Built-in presets (Flat, Bass Boost, Treble Boost, V-Shape, Vocal Clarity, Loudness, Reduce Sibilance)
2. Separator or visual distinction
3. User-saved profiles
4. "Save Current..."
5. "Delete Profile..." (only if user profiles exist)

When a built-in preset is selected, apply its config and auto-calculate preamp.

---

### 2. Smooth Preset Transitions (Coefficient Crossfade)

When switching between presets (where many bands change at once), abrupt coefficient changes can cause a brief click. Implement a simple crossfade in the processor.

**Add to ParametricEqProcessor.kt:**

```kotlin
private var crossfadeFramesRemaining = 0
private var crossfadeTotalFrames = 0
private val oldBands = Array(31) { BiquadFilter(maxChannels = 8) }
private var isCrossfading = false

private fun applyConfig(config: EqConfig) {
    val oldConfig = currentConfig
    currentConfig = config
    enabled = config.enabled
    preampLinear = 10f.pow(config.preampDb / 20f)

    // If multiple bands changed (preset switch), use crossfade
    val changedBandCount = if (oldConfig != null) {
        config.bands.indices.count { i ->
            i < oldConfig.bands.size && config.bands[i].gainDb != oldConfig.bands[i].gainDb
        }
    } else 0

    if (changedBandCount > 3 && oldConfig != null) {
        // Copy current filter state to old filters for crossfade
        for (i in bands.indices) {
            oldBands[i].copyFrom(bands[i])  // need to implement copyFrom()
        }
        // Apply new coefficients to main filters
        for (i in config.bands.indices) {
            if (i >= bands.size) break
            val bc = config.bands[i]
            bands[i].enabled = bc.enabled
            if (bc.enabled) {
                bands[i].computeCoefficients(bc.filterType, bc.frequencyHz, bc.gainDb, bc.q, sampleRate)
            }
        }
        // Clear state on new filters (start fresh for crossfade)
        for (band in bands) band.clearState()
        // Setup crossfade: ~10ms at current sample rate
        crossfadeTotalFrames = (sampleRate * 0.01).toInt().coerceIn(128, 2048)
        crossfadeFramesRemaining = crossfadeTotalFrames
        isCrossfading = true
    } else {
        // Small change: just update coefficients directly (no audible artifact)
        for (i in config.bands.indices) {
            if (i >= bands.size) break
            val bc = config.bands[i]
            bands[i].enabled = bc.enabled
            if (bc.enabled) {
                bands[i].computeCoefficients(bc.filterType, bc.frequencyHz, bc.gainDb, bc.q, sampleRate)
            }
        }
        isCrossfading = false
    }
}
```

**In the processing loop, when crossfading:**
```kotlin
if (isCrossfading && crossfadeFramesRemaining > 0) {
    // Process through both old and new filter chains
    var oldSample = inputSample * preampLinear
    var newSample = inputSample * preampLinear
    for (band in oldBands) { oldSample = band.processSample(ch, oldSample) }
    for (band in bands) { newSample = band.processSample(ch, newSample) }

    // Crossfade: cos/sin for energy preservation
    val t = 1.0f - (crossfadeFramesRemaining.toFloat() / crossfadeTotalFrames)
    val fadeIn = sin(t * PI.toFloat() / 2f)
    val fadeOut = cos(t * PI.toFloat() / 2f)
    outputSample = oldSample * fadeOut + newSample * fadeIn

    if (ch == channelCount - 1) crossfadeFramesRemaining--
    if (crossfadeFramesRemaining <= 0) {
        isCrossfading = false
        for (band in oldBands) band.clearState()
    }
} else {
    // Normal processing
    var sample = inputSample * preampLinear
    for (band in bands) { sample = band.processSample(ch, sample) }
    outputSample = sample
}
```

**Add `copyFrom()` to BiquadFilter.kt:**
```kotlin
fun copyFrom(other: BiquadFilter) {
    this.b0 = other.b0; this.b1 = other.b1; this.b2 = other.b2
    this.a1 = other.a1; this.a2 = other.a2
    this.enabled = other.enabled
    other.z1.copyInto(this.z1)
    other.z2.copyInto(this.z2)
}
```

---

### 3. Edge Case Handling

**3a. Sample rate changes:**
When the sample rate changes (e.g., switching from 44.1kHz to 48kHz track), `onConfigure()` is called again. Coefficients MUST be recalculated for the new sample rate. Add:

```kotlin
override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
    val newSampleRate = inputAudioFormat.sampleRate
    val newChannelCount = inputAudioFormat.channelCount
    val sampleRateChanged = newSampleRate != sampleRate
    sampleRate = newSampleRate
    channelCount = newChannelCount

    if (sampleRateChanged && currentConfig != null) {
        // Recalculate all coefficients for new sample rate
        applyConfig(currentConfig!!)
    }

    // ... rest of configure ...
}
```

**3b. Channel count > 2:**
BiquadFilter must support more than 2 channels (surround, etc.). The `maxChannels` parameter in BiquadFilter's constructor should be set high enough (8 is safe for all formats including 7.1).

**3c. Empty/null processor instance:**
The UI (ParametricEqActivity) must handle `ParametricEqProcessor.instance` being null. This can happen if:
- Audio hasn't started playing yet (pipeline not initialized)
- Audio session was released

Show a message like "Play a song to initialize the equalizer" if the processor is null, and disable the enable switch.

**3d. Config persistence on activity destroy:**
Save config in `onPause()` (not just when values change) to handle unexpected kills:
```kotlin
override fun onPause() {
    super.onPause()
    ParametricEqConfig.saveToPrefs(prefs, currentConfig)
}
```

**3e. Restore config on processor recreation:**
When PostAmpAudioSink creates a new audio session (track change, etc.), the processor is reconfigured via `onConfigure()` → `onFlush()`. The saved config should be restored. Add to ParametricEqProcessor:
```kotlin
override fun onFlush(streamMetadata: StreamMetadata) {
    for (band in bands) band.clearState()
    // If no pending config, try to restore from prefs
    if (pendingConfig.get() == null && currentConfig == null) {
        // Load from a static reference or context
        // (or let the UI re-apply on resume)
    }
    pendingConfig.getAndSet(null)?.let { applyConfig(it) }
}
```

---

### 4. UX Refinements

**4a. Gain value color coding:**
- Positive gains: show in green/accent color (boost)
- Negative gains: show in red/warning color (cut)
- Zero: show in default text color

```kotlin
gainLabel.setTextColor(when {
    band.gainDb > 0 -> accentColor
    band.gainDb < 0 -> cutColor
    else -> defaultColor
})
```

**4b. Long-press on band to reset individual band:**
```kotlin
gainLabel.setOnLongClickListener {
    updateBandConfig(i, BandConfig(
        enabled = true, filterType = FilterType.PEAKING,
        frequencyHz = band.frequencyHz, gainDb = 0f, q = EqConfig.DEFAULT_Q
    ))
    true
}
```

**4c. Keyboard navigation:**
After typing a gain value and pressing OK, automatically select the next band. This makes it fast to set up all 31 bands in sequence.

**4d. Scroll to selected band:**
When a band is selected from the detail panel (or programmatically), scroll the horizontal band grid to make that band visible.

---

## Build & Verify

```bash
./gradlew :app:assembleDebug
adb -s 58081FDCQ000EB install -r app/build/outputs/apk/debug/JAMZ-*.apk
```

**Test checklist:**
1. Open Advanced EQ → dropdown shows built-in presets
2. Select "Bass Boost" → gains update, curve shows bass boost, music sounds bassier
3. Select "V-Shape" → smooth transition (no click/pop)
4. Save as "My V-Shape" → appears in dropdown
5. Select "Flat" → all gains reset smoothly
6. Load "My V-Shape" → restored
7. Play a 44.1kHz track, then a 48kHz track → EQ continues working (sample rate change handled)
8. Kill app → reopen → Advanced EQ → settings persist
9. Long-press a band → resets to 0 dB
10. Positive gains show in accent color, negative in cut color

## Commit
```
feat: built-in EQ presets, crossfade transitions, edge case polish

- 6 built-in presets (Bass Boost, Treble Boost, V-Shape, Vocal Clarity,
  Loudness, Reduce Sibilance) in preset dropdown
- Smooth cos/sin crossfade when switching presets (prevents click/pop)
- Handle sample rate changes, null processor, config persistence
- Gain color coding, long-press to reset band, auto-advance after edit
```

## Hard Rules
- Build with `:app:assembleDebug`
- Don't modify the media3 submodule
- Don't introduce new external dependencies
- Read source files before modifying
- One change at a time, build and verify after each
