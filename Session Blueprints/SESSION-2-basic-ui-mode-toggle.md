# Session 2: Advanced EQ Activity + Mode Toggle + 31-Band Text Input UI

## Prerequisites
Session 1 must be complete. The following files should already exist:
- `app/src/main/java/org/akanework/gramophone/logic/utils/BiquadFilter.kt`
- `app/src/main/java/org/akanework/gramophone/logic/utils/ParametricEqConfig.kt`
- `app/src/main/java/org/akanework/gramophone/logic/utils/ParametricEqProcessor.kt`
- `GramophoneRenderFactory.kt` should already wire `ParametricEqProcessor` into the pipeline

Verify by reading these files before starting.

## What You're Building
A full-screen Activity for the 31-band parametric EQ with a **text-based input interface** — no sliders. Users see a grid of frequency labels with tappable gain values, tap any gain to type an exact dB value. A read-only frequency response curve below updates reactively as values change. Users can toggle between "Simple" (existing 5-band hardware EQ bottom sheet) and "Advanced" (this new 31-band screen).

## Context: What JAMZ Is
JAMZ is a Kotlin Android music player (fork of Gramophone, GPL-3.0). Uses mixed Jetpack Compose + XML layouts with Fragments. The existing 5-band EQ is an XML-based `BottomSheetDialogFragment` at `app/src/main/java/org/akanework/gramophone/ui/components/EqualizerBottomSheet.kt`.

## UI Layout (Full-Screen Activity)

```
┌──────────────────────────────────────────┐
│ ← Advanced Equalizer            [ON/OFF] │  ← Toolbar with back + enable switch
│                                          │
│ Preset: [▼ Flat                ]         │  ← Dropdown: profiles + Save/Delete
│ Preamp: [0 dB]  [Auto]                  │  ← Tappable preamp value + auto button
│                                          │
│ ┌──────────────────────────────────────┐ │
│ │   Frequency Response Curve           │ │  ← Read-only Canvas, ~160dp tall
│ │   (updates reactively, no touch)     │ │     Log X: 20Hz→20kHz
│ │   Shows combined response of all 31  │ │     Linear Y: -15→+15 dB
│ │   bands as a smooth curve            │ │     Grid at 0, ±5, ±10, ±15 dB
│ └──────────────────────────────────────┘ │
│                                          │
│  ┌──────── Band Gains ─────────────────┐ │  ← Horizontal scroll
│  │  20    25   31.5   40    50    63   │ │     Row 1: frequency labels
│  │ [0dB] [0dB] [0dB] [0dB] [0dB] [0dB]│ │     Row 2: tappable gain values
│  │  80   100   125   160   200   250   │ │     Repeat for all 31 bands
│  │ [0dB] [0dB] [0dB] [0dB] [0dB] [0dB]│ │     (or single scrolling row)
│  │ ...                                  │ │
│  └──────────────────────────────────────┘ │
│                                          │
│ ── Selected: 1000 Hz ──────────────────  │  ← Appears when a band is tapped
│ Gain:  [+3.0 dB]    Q: [4.318]          │     Tappable values open input dialogs
│ Type:  [▼ Peaking]   [Enabled ✓]        │     Filter type dropdown
│                                          │
│    [Reset Band]  [Reset All]  [Save]     │
│                                          │
│    [Switch to Simple EQ]                 │  ← Mode toggle button
└──────────────────────────────────────────┘
```

**Key UX decisions (from Clay):**
- **Full-screen Activity** (not bottom sheet) — more room for 31 bands
- **Text-based input only** — no sliders anywhere. Tap a gain value → dialog → type exact dB
- **Frequency response curve is read-only** — visual feedback only, no dragging/interaction
- **Bass Boost and Virtualizer are hidden** in Advanced mode (parametric gives full control)
- When Advanced mode is active, the hardware EQ is disabled. When switching back to Simple, hardware EQ re-enables.

---

## Files to Create

### 1. `app/src/main/java/org/akanework/gramophone/ui/ParametricEqActivity.kt`

A new Activity that hosts the parametric EQ UI. Use a simple XML layout with a Toolbar and a ComposeView for the main content, OR use pure XML with programmatic views (matching the existing EqualizerBottomSheet pattern).

**Recommended approach:** Since the existing EQ UI is XML/View-based, keep this consistent. Use XML layout + programmatic view creation for the band grid (similar to how EqualizerBottomSheet creates band sliders dynamically).

**Activity structure:**
```kotlin
class ParametricEqActivity : AppCompatActivity() {
    private var processor: ParametricEqProcessor? = null
    private var currentConfig: EqConfig = EqConfig.createDefault31Band()
    private var selectedBandIndex: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_parametric_eq)

        processor = ParametricEqProcessor.instance

        // Load saved config or create default
        currentConfig = ParametricEqConfig.loadFromPrefs(
            PreferenceManager.getDefaultSharedPreferences(this)
        ) ?: EqConfig.createDefault31Band()

        setupToolbar()
        setupPresetDropdown()
        setupPreamp()
        setupFrequencyResponseCurve()
        setupBandGrid()
        setupSelectedBandDetail()
        setupModeToggle()

        // Apply config to processor
        applyConfig()
    }
}
```

### 2. `app/src/main/res/layout/activity_parametric_eq.xml`

XML layout with:
- `MaterialToolbar` with title + enable switch
- `TextInputLayout` for preset dropdown
- Preamp row (TextView for value, Button for "Auto")
- A `View` or `FrameLayout` placeholder for the frequency response curve (custom View)
- `HorizontalScrollView` > `LinearLayout` for the band grid
- `LinearLayout` for the selected band detail panel
- Buttons row at the bottom
- "Switch to Simple EQ" button

### 3. `app/src/main/java/org/akanework/gramophone/ui/components/FrequencyResponseView.kt`

Custom `View` that draws the frequency response curve.

**Drawing logic:**
```kotlin
class FrequencyResponseView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private var bands: List<BiquadFilter> = emptyList()
    private var sampleRate: Int = 48000
    private val curvePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = /* theme accent color */
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { ... }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        // Semi-transparent accent color for area under curve
    }

    fun updateBands(filters: List<BiquadFilter>, sampleRate: Int) {
        this.bands = filters
        this.sampleRate = sampleRate
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val minFreq = 20.0
        val maxFreq = 20000.0
        val minDb = -15.0
        val maxDb = 15.0

        // Draw grid lines at 0, ±5, ±10, ±15 dB
        for (db in listOf(-15, -10, -5, 0, 5, 10, 15)) {
            val y = dbToY(db.toDouble(), h, minDb, maxDb)
            canvas.drawLine(0f, y, w, y, gridPaint)
            // Draw dB label
        }

        // Draw frequency grid at 100, 1k, 10k Hz
        for (freq in listOf(100.0, 1000.0, 10000.0)) {
            val x = freqToX(freq, w, minFreq, maxFreq)
            canvas.drawLine(x, 0f, x, h, gridPaint)
        }

        // Compute combined magnitude at each pixel
        val path = Path()
        val fillPath = Path()
        val zeroY = dbToY(0.0, h, minDb, maxDb)

        for (px in 0..w.toInt()) {
            val freq = xToFreq(px.toFloat(), w, minFreq, maxFreq)
            var magnitude = 1.0
            for (band in bands) {
                if (band.enabled) {
                    magnitude *= band.magnitudeAt(freq, sampleRate)
                }
            }
            val db = 20.0 * log10(magnitude)
            val y = dbToY(db, h, minDb, maxDb)
            if (px == 0) {
                path.moveTo(px.toFloat(), y)
                fillPath.moveTo(px.toFloat(), zeroY)
                fillPath.lineTo(px.toFloat(), y)
            } else {
                path.lineTo(px.toFloat(), y)
                fillPath.lineTo(px.toFloat(), y)
            }
        }
        fillPath.lineTo(w, zeroY)
        fillPath.close()

        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(path, curvePaint)
    }

    // Log-scale X axis: 20Hz → 20kHz
    private fun freqToX(freq: Double, w: Float, minF: Double, maxF: Double): Float {
        return (ln(freq / minF) / ln(maxF / minF) * w).toFloat()
    }
    private fun xToFreq(x: Float, w: Float, minF: Double, maxF: Double): Double {
        return minF * (maxF / minF).pow(x / w)
    }
    // Linear Y axis: -15dB → +15dB (inverted: top = +15)
    private fun dbToY(db: Double, h: Float, minDb: Double, maxDb: Double): Float {
        return ((maxDb - db) / (maxDb - minDb) * h).toFloat()
    }
}
```

---

## Files to Modify

### 4. `AndroidManifest.xml`

Register the new activity:
```xml
<activity
    android:name=".ui.ParametricEqActivity"
    android:theme="@style/Theme.YourApp"
    android:parentActivityName=".ui.MainActivity" />
```

Find the manifest file and add the activity entry. Use the same theme as existing activities.

### 5. Modify EQ menu action to support mode toggle

Currently, the Equalizer menu item opens `EqualizerBottomSheet`. Read the code that handles the "Equalizer" menu click (likely in `MainActivity.kt` or a Fragment) and change it to:

```kotlin
// Check mode preference
val prefs = PreferenceManager.getDefaultSharedPreferences(context)
val eqMode = prefs.getString("eq_mode", "simple")
if (eqMode == "parametric") {
    startActivity(Intent(context, ParametricEqActivity::class.java))
} else {
    EqualizerBottomSheet().show(supportFragmentManager, "eq")
}
```

### 6. Add mode toggle in `EqualizerBottomSheet.kt`

Add a "Switch to Advanced EQ" button at the bottom of the existing EQ bottom sheet. When tapped:
```kotlin
// Save mode preference
PreferenceManager.getDefaultSharedPreferences(requireContext())
    .edit().putString("eq_mode", "parametric").apply()
// Close bottom sheet and open Advanced EQ
dismiss()
startActivity(Intent(requireContext(), ParametricEqActivity::class.java))
```

Add a corresponding string resource: `<string name="eq_switch_to_advanced">Switch to Advanced EQ</string>`

### 7. Add a "Switch to Simple EQ" button in `ParametricEqActivity`

When tapped:
```kotlin
PreferenceManager.getDefaultSharedPreferences(this)
    .edit().putString("eq_mode", "simple").apply()
// Disable parametric, re-enable hardware EQ
processor?.updateConfig(currentConfig.copy(enabled = false))
EqEffectWrapper.instance?.setEnabled(true)
finish()
```

---

## Band Grid: Text-Based Input UI

The core UI is a grid of tappable gain values. For 31 bands in a horizontal scroll:

```kotlin
private fun createBandGrid(container: LinearLayout) {
    val config = currentConfig

    for (i in config.bands.indices) {
        val band = config.bands[i]

        val bandColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                resources.getDimensionPixelSize(R.dimen.eq_band_width), // reuse existing 56dp
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(4, 8, 4, 8)
        }

        // Frequency label
        val freqLabel = TextView(this).apply {
            text = formatFreq(band.frequencyHz)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelSmall)
        }
        bandColumn.addView(freqLabel)

        // Gain value (tappable)
        val gainLabel = TextView(this).apply {
            text = formatGain(band.gainDb)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium)
            // Highlight selected band
            if (i == selectedBandIndex) {
                setTypeface(null, Typeface.BOLD)
                setTextColor(/* accent color */)
            }
            setOnClickListener {
                selectedBandIndex = i
                showGainInputDialog(i)
                updateSelectedBandDetail()
                refreshBandGrid() // refresh to highlight
            }
        }
        bandColumn.addView(gainLabel)

        container.addView(bandColumn)
    }
}
```

**Gain input dialog (reuse the pattern from EqualizerBottomSheet's tap-to-edit):**
```kotlin
private fun showGainInputDialog(bandIndex: Int) {
    val band = currentConfig.bands[bandIndex]
    val freqText = formatFreq(band.frequencyHz)

    // TextInputLayout with TextInputEditText
    // Numeric input, signed, decimal
    // Title: "Set {freqText}"
    // Hint: "dB value (-15 to +15)"
    // On OK: validate, clamp to -15..+15, update config, apply

    // After updating:
    updateBandConfig(bandIndex, band.copy(gainDb = newGain))
}

private fun updateBandConfig(index: Int, newBand: BandConfig) {
    val newBands = currentConfig.bands.toMutableList()
    newBands[index] = newBand
    currentConfig = currentConfig.copy(bands = newBands)
    applyConfig()
    refreshUI()
}

private fun applyConfig() {
    processor?.updateConfig(currentConfig)
    ParametricEqConfig.saveToPrefs(
        PreferenceManager.getDefaultSharedPreferences(this), currentConfig
    )
    frequencyResponseView.updateBands(/* ... */)
}
```

---

## Mode Toggle: Mutual Exclusion

When entering Advanced mode:
```kotlin
// Disable hardware EQ
EqEffectWrapper.instance?.setEnabled(false)
// Enable parametric processor
processor?.updateConfig(currentConfig.copy(enabled = true))
```

When entering Simple mode (or leaving ParametricEqActivity):
```kotlin
// Disable parametric processor
processor?.updateConfig(currentConfig.copy(enabled = false))
// Re-enable hardware EQ (only if it was previously enabled in user prefs)
val wasEnabled = PreferenceManager.getDefaultSharedPreferences(this)
    .getBoolean("eq_enabled", false)
if (wasEnabled) EqEffectWrapper.instance?.setEnabled(true)
```

---

## New String Resources

Add to `app/src/main/res/values/strings.xml`:
```xml
<string name="advanced_equalizer">Advanced Equalizer</string>
<string name="eq_switch_to_advanced">Switch to Advanced EQ</string>
<string name="eq_switch_to_simple">Switch to Simple EQ</string>
<string name="eq_preamp">Preamp</string>
<string name="eq_auto_preamp">Auto</string>
<string name="eq_selected_band">Selected: %s</string>
<string name="eq_gain">Gain</string>
<string name="eq_quality">Q</string>
<string name="eq_filter_type">Type</string>
<string name="eq_reset_band">Reset Band</string>
<string name="eq_reset_all">Reset All</string>
<string name="eq_mode_key" translatable="false">eq_mode</string>
```

---

## Build & Verify

```bash
./gradlew :app:assembleDebug
adb -s 58081FDCQ000EB install -r app/build/outputs/apk/debug/JAMZ-*.apk
adb -s 58081FDCQ000EB shell am start -n com.clayboi.player.debug/org.akanework.gramophone.ui.MainActivity
```

**Test checklist:**
1. Open overflow menu → Equalizer → existing Simple EQ bottom sheet appears
2. Tap "Switch to Advanced EQ" → full-screen Advanced EQ opens
3. All 31 frequency labels visible in horizontal scroll
4. Tap a gain value → input dialog → type "+3" → OK → gain updates
5. Frequency response curve shows the boost at that frequency
6. Play music → hear the EQ change in real-time
7. Tap "Switch to Simple EQ" → returns to app, hardware EQ re-enabled
8. Reopen Equalizer → opens Simple mode (preference remembered)
9. Kill and reopen app → Advanced EQ settings persist

## Commit
```
feat: add Advanced EQ activity with 31-band text input UI

- Full-screen ParametricEqActivity with text-based gain input for all
  31 ISO 1/3-octave bands (tap gain value → type exact dB)
- Read-only FrequencyResponseView showing combined EQ curve
- Mode toggle between Simple (hardware 5-band) and Advanced (parametric
  31-band) with mutual exclusion of audio effects
- Per-band detail panel for Q factor and filter type
- Preset dropdown with save/load/delete profiles
```

## Hard Rules (from CLAUDE.md)
- Build with `:app:assembleDebug` (not bare `assembleDebug`)
- Don't modify the media3 submodule
- Don't introduce new external dependencies
- Don't batch multiple changes into one commit
- Read source files before modifying them
- One change at a time, build and verify after each
