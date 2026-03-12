# Session 1: Core DSP Engine — BiquadFilter + ParametricEqProcessor + Pipeline Wiring

## What You're Building
A 31-band parametric equalizer DSP engine that processes audio in real-time through Media3's AudioProcessor pipeline. By the end of this session, the app should play music through the new processor (in pass-through mode) without any audible difference — proving the pipeline wiring works.

## Context: What JAMZ Is
JAMZ is a personal Android music player fork of Gramophone (GPL-3.0). It's a Kotlin Android app using Media3/ExoPlayer for audio playback. The owner (Clay) is not a developer — he directs AI to build.

## What Already Exists
- **5-band hardware EQ** via `EqEffectWrapper.kt` — wraps Android's `Equalizer` audiofx, attached to audio session ID
- **ReplayGainAudioProcessor** — a `BaseAudioProcessor` that applies volume normalization to PCM audio. This is your **primary code template**.
- **VisualizerProcessor** — taps audio for FFT spectrum display via `TeeAudioProcessor`
- **GramophoneRenderFactory** — wires the audio processor chain: `[ReplayGainAP, TeeAudioProcessor(Visualizer)]`
- **PostAmpAudioSink** — manages hardware audio effects (DynamicsProcessing, Volume, EQ) via audio session ID

## Architecture Overview
```
Current:  PCM → [ReplayGain] → [Visualizer Tee] → AudioTrack
After:    PCM → [ReplayGain] → [ParametricEQ] → [Visualizer Tee] → AudioTrack
```
The ParametricEQ sits between ReplayGain (which normalizes volume) and the Visualizer (which should show the EQ'd signal).

---

## Files to Create

### 1. `app/src/main/java/org/akanework/gramophone/logic/utils/BiquadFilter.kt`

A single biquad filter section implementing 6 filter types from the Audio EQ Cookbook.

**Key design decisions:**
- **Transposed Direct Form II** (TDF-II): best for floating-point, only 2 state variables per channel
- **Coefficient computation in Double** (trig needs precision), **processing in Float** (sufficient for audio)
- **`magnitudeAt()`** method evaluates the filter's frequency response at any frequency — needed later for the UI curve

**Filter types to implement:**
- `PEAKING` — parametric band boost/cut at center frequency with adjustable Q
- `LOW_SHELF` — boost/cut below a frequency
- `HIGH_SHELF` — boost/cut above a frequency
- `LOW_PASS` — remove content above cutoff
- `HIGH_PASS` — remove content below cutoff
- `NOTCH` — remove content at center frequency

**All coefficient formulas (from Robert Bristow-Johnson's Audio EQ Cookbook):**

Common variables:
```
w0 = 2 * PI * freq / sampleRate
A = 10^(gainDb / 40)       // only for peaking and shelving
alpha = sin(w0) / (2 * Q)  // for peaking, LP, HP, notch
// For shelving filters, alpha uses a different formula (see below)
```

PEAKING:
```
b0 =  1 + alpha * A        a0 =  1 + alpha / A
b1 = -2 * cos(w0)          a1 = -2 * cos(w0)
b2 =  1 - alpha * A        a2 =  1 - alpha / A
```

LOW_SHELF (alpha = sin(w0)/2 * sqrt((A + 1/A) * (1/S - 1) + 2), where S=1 for steepest):
```
// For shelf filters, compute alpha differently:
// alpha_shelf = sin(w0) / 2 * sqrt( (A + 1/A) * (1/Q - 1) + 2 )
// OR use the standard alpha = sin(w0)/(2*Q) and accept Q controls the transition band width
// Using standard alpha for simplicity (Q controls shelf slope):
sqrtA2alpha = 2 * sqrt(A) * alpha
b0 = A * ((A+1) - (A-1)*cos(w0) + sqrtA2alpha)
b1 = 2*A * ((A-1) - (A+1)*cos(w0))
b2 = A * ((A+1) - (A-1)*cos(w0) - sqrtA2alpha)
a0 = (A+1) + (A-1)*cos(w0) + sqrtA2alpha
a1 = -2 * ((A-1) + (A+1)*cos(w0))
a2 = (A+1) + (A-1)*cos(w0) - sqrtA2alpha
```

HIGH_SHELF:
```
sqrtA2alpha = 2 * sqrt(A) * alpha
b0 = A * ((A+1) + (A-1)*cos(w0) + sqrtA2alpha)
b1 = -2*A * ((A-1) + (A+1)*cos(w0))
b2 = A * ((A+1) + (A-1)*cos(w0) - sqrtA2alpha)
a0 = (A+1) - (A-1)*cos(w0) + sqrtA2alpha
a1 = 2 * ((A-1) - (A+1)*cos(w0))
a2 = (A+1) - (A-1)*cos(w0) - sqrtA2alpha
```

LOW_PASS:
```
b0 = (1 - cos(w0)) / 2     a0 = 1 + alpha
b1 = 1 - cos(w0)            a1 = -2 * cos(w0)
b2 = (1 - cos(w0)) / 2     a2 = 1 - alpha
```

HIGH_PASS:
```
b0 = (1 + cos(w0)) / 2     a0 = 1 + alpha
b1 = -(1 + cos(w0))         a1 = -2 * cos(w0)
b2 = (1 + cos(w0)) / 2     a2 = 1 - alpha
```

NOTCH:
```
b0 = 1                      a0 = 1 + alpha
b1 = -2 * cos(w0)           a1 = -2 * cos(w0)
b2 = 1                      a2 = 1 - alpha
```

After computing, normalize all by dividing by a0: `b0' = b0/a0`, etc.

**TDF-II processing (per sample, per channel):**
```
output = b0 * input + z1[channel]
z1[channel] = b1 * input - a1 * output + z2[channel]
z2[channel] = b2 * input - a2 * output
return output
```

**magnitudeAt() for UI curve — evaluates |H(e^jω)|:**
```
w = 2 * PI * freqHz / sampleRate
numReal = b0 + b1*cos(w) + b2*cos(2w)
numImag = -(b1*sin(w) + b2*sin(2w))
denReal = 1 + a1*cos(w) + a2*cos(2w)
denImag = -(a1*sin(w) + a2*sin(2w))
magnitude = sqrt((numReal² + numImag²) / (denReal² + denImag²))
```

---

### 2. `app/src/main/java/org/akanework/gramophone/logic/utils/ParametricEqConfig.kt`

Data model for the EQ configuration. Must be **immutable** (data classes) because it's passed across threads via `AtomicReference`.

**Contents:**
- `enum class FilterType { PEAKING, LOW_SHELF, HIGH_SHELF, LOW_PASS, HIGH_PASS, NOTCH }`
- `data class BandConfig(val enabled: Boolean, val filterType: FilterType, val frequencyHz: Float, val gainDb: Float, val q: Float)`
- `data class EqConfig(val enabled: Boolean, val preampDb: Float, val bands: List<BandConfig>)`
- Companion with `ISO_31_FREQUENCIES` array and `createDefault31Band()` factory
- `toJson()` / `fromJson()` using `org.json.JSONObject` / `JSONArray`
- `saveToPrefs()` / `loadFromPrefs()` using SharedPreferences key `"parametric_eq_config"`

**Standard 31-band ISO 1/3-octave frequencies:**
```kotlin
val ISO_31_FREQUENCIES = floatArrayOf(
    20f, 25f, 31.5f, 40f, 50f, 63f, 80f, 100f, 125f, 160f,
    200f, 250f, 315f, 400f, 500f, 630f, 800f, 1000f, 1250f, 1600f,
    2000f, 2500f, 3150f, 4000f, 5000f, 6300f, 8000f, 10000f, 12500f, 16000f,
    20000f
)
```
Default Q = 4.318 (1/3 octave bandwidth). Default gain = 0 dB. Default type = PEAKING.

---

### 3. `app/src/main/java/org/akanework/gramophone/logic/utils/ParametricEqProcessor.kt`

Media3 `BaseAudioProcessor` that chains 31 biquad filters. **Follow the ReplayGainAudioProcessor pattern exactly.**

**Key patterns from ReplayGainAudioProcessor to replicate:**
1. `onConfigure()` returns output `AudioFormat` — use `C.ENCODING_PCM_FLOAT` output
2. Use `ToFloatPcmAudioProcessor` (from media3 library) to convert non-float input to float
3. `queueInput()`: get frameCount from `inputBuffer.remaining() / inputAudioFormat.bytesPerFrame`, allocate output via `replaceOutputBuffer(size)`, process, call `outputBuffer.flip()`
4. Use `pending*` fields set in configure, committed in `onFlush()` — avoids race between configure and active playback
5. `onReset()` releases resources

**Thread-safe config handoff:**
```kotlin
private val pendingConfig = AtomicReference<EqConfig?>(null)

// Called from UI thread (main thread)
fun updateConfig(config: EqConfig) {
    pendingConfig.set(config)
}

// Called at start of queueInput() on audio thread
private fun checkPendingConfig() {
    pendingConfig.getAndSet(null)?.let { applyConfig(it) }
}
```

**Processing loop:**
```kotlin
for (frame in 0 until frameCount) {
    for (ch in 0 until channelCount) {
        var sample = floatInput.getFloat()  // read one float sample
        sample *= preampLinear              // apply preamp
        for (band in bands) {
            sample = band.processSample(ch, sample)  // cascade through all 31 filters
        }
        outputBuffer.putFloat(sample)       // write to output
    }
}
```

**Singleton pattern (same as EqEffectWrapper):**
```kotlin
companion object {
    @Volatile var instance: ParametricEqProcessor? = null
        private set
    fun setInstance(p: ParametricEqProcessor?) { instance = p }
}
```

**When disabled** (config.enabled = false): still convert to float (to keep format consistent) but skip the biquad loop — just copy samples with no processing.

**On flush**: clear all filter state (`band.clearState()` for each band) to prevent artifacts from stale delay lines on seek/track change. Also apply any pending config.

---

## File to Modify

### 4. `app/src/main/java/org/akanework/gramophone/logic/utils/exoplayer/GramophoneRenderFactory.kt`

In the `buildAudioSink()` method, the processor chain is created:

```kotlin
// CURRENT (around line 108-115):
val teeProcessor = TeeAudioProcessor(VisualizerProcessor())
builder.setAudioProcessorChain(object : AudioProcessorChain {
    override fun getAudioProcessors(inputFormat: Format): Array<out AudioProcessor> {
        rgAp.setRootFormat(inputFormat)
        return arrayOf(rgAp, teeProcessor)
    }
    // ... other overrides unchanged ...
})
```

**Change to:**
```kotlin
val teeProcessor = TeeAudioProcessor(VisualizerProcessor())
val parametricEqProcessor = ParametricEqProcessor()
ParametricEqProcessor.setInstance(parametricEqProcessor)
builder.setAudioProcessorChain(object : AudioProcessorChain {
    override fun getAudioProcessors(inputFormat: Format): Array<out AudioProcessor> {
        rgAp.setRootFormat(inputFormat)
        return arrayOf(rgAp, parametricEqProcessor, teeProcessor)
    }
    // ... other overrides unchanged ...
})
```

Also need to add the import: `import org.akanework.gramophone.logic.utils.ParametricEqProcessor`

**Important:** Also clean up the singleton in `PostAmpAudioSink.release()`. Add to the `release()` method:
```kotlin
ParametricEqProcessor.setInstance(null)
```
This goes alongside the existing `EqEffectWrapper.setInstance(null)` line.

And add the import to PostAmpAudioSink.kt: `import org.akanework.gramophone.logic.utils.ParametricEqProcessor`

---

## Build & Verify

```bash
# Build
./gradlew :app:assembleDebug

# Install
adb -s 58081FDCQ000EB install -r app/build/outputs/apk/debug/JAMZ-*.apk

# Launch
adb -s 58081FDCQ000EB shell am start -n com.clayboi.player.debug/org.akanework.gramophone.ui.MainActivity
```

**Verification:** Play any song. It should sound exactly the same as before — the parametric EQ processor starts with `enabled = false` (no config loaded yet), so it passes audio through unchanged. If playback breaks, stutters, or sounds different, something is wrong with the pipeline wiring.

**Optional verification:** Add a temporary log in `ParametricEqProcessor.queueInput()`:
```kotlin
Log.d("ParametricEQ", "Processing ${frameCount} frames, ${channelCount}ch, ${sampleRate}Hz")
```
Check logcat to confirm the processor is receiving audio data.

## Commit
```
feat: add parametric EQ DSP engine (BiquadFilter + ParametricEqProcessor)

- BiquadFilter: 6 filter types (peaking, shelves, LP/HP, notch) using
  TDF-II biquad from Audio EQ Cookbook, with magnitudeAt() for UI curve
- ParametricEqConfig: immutable 31-band data model with ISO 1/3-octave
  frequencies, JSON serialization, SharedPreferences persistence
- ParametricEqProcessor: Media3 BaseAudioProcessor with AtomicReference
  config handoff, ToFloatPcmAudioProcessor conversion, 31-band cascade
- Wired into audio pipeline between ReplayGain and Visualizer
```

## Hard Rules (from CLAUDE.md)
- Build with `:app:assembleDebug` (not bare `assembleDebug`)
- Don't modify the media3 submodule
- Don't introduce new external dependencies
- Don't batch multiple changes into one commit
- JAVA_HOME must be Temurin JDK 21
- `package.properties` must exist with `releaseType=SelfBuilt`
- Read source files before modifying them
