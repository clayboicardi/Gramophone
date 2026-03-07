package org.akanework.gramophone.ui.components

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.util.Log
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputLayout
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.utils.EqEffectWrapper

/**
 * Bottom sheet presenting the built-in equalizer, bass boost, and virtualizer controls.
 * Reads/writes to [EqEffectWrapper.instance] which is managed by PostAmpAudioSink.
 *
 * Band sliders are created dynamically (device-dependent count, typically 5).
 * Uses the Material Slider rotation="270" trick for vertical orientation.
 */
class EqualizerBottomSheet : BottomSheetDialogFragment() {

    companion object {
        private const val TAG = "EqualizerBottomSheet"
    }

    private var wrapper: EqEffectWrapper? = null
    private var suppressPresetChange = false
    private var suppressSliderCallbacks = false
    private val bandSliders = mutableListOf<Slider>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_equalizer, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        wrapper = EqEffectWrapper.instance

        val eq = wrapper
        val enableSwitch = view.findViewById<MaterialSwitch>(R.id.eq_enable_switch)
        val presetLayout = view.findViewById<TextInputLayout>(R.id.preset_layout)
        val presetDropdown = view.findViewById<AutoCompleteTextView>(R.id.preset_dropdown)
        val bandsScroll = view.findViewById<View>(R.id.bands_scroll)
        val bandsContainer = view.findViewById<LinearLayout>(R.id.bands_container)
        val unsupportedText = view.findViewById<TextView>(R.id.eq_unsupported_text)
        val bassBoostRow = view.findViewById<View>(R.id.bass_boost_row)
        val bassBoostSlider = view.findViewById<Slider>(R.id.bass_boost_slider)
        val virtualizerRow = view.findViewById<View>(R.id.virtualizer_row)
        val virtualizerSlider = view.findViewById<Slider>(R.id.virtualizer_slider)
        val resetButton = view.findViewById<View>(R.id.reset_button)

        // ── No wrapper or 0 bands → show unsupported ──
        if (eq == null || eq.numberOfBands <= 0) {
            Log.w(TAG, "EQ unavailable: wrapper=${eq != null}, bands=${eq?.numberOfBands}")
            unsupportedText.visibility = View.VISIBLE
            bandsScroll.visibility = View.GONE
            presetLayout.visibility = View.GONE
            bassBoostRow.visibility = View.GONE
            virtualizerRow.visibility = View.GONE
            resetButton.visibility = View.GONE
            enableSwitch.isEnabled = false
            return
        }

        // ── Enable switch ──
        enableSwitch.isChecked = eq.eqEnabled
        enableSwitch.setOnCheckedChangeListener { _, checked ->
            eq.setEnabled(checked)
            setControlsEnabled(view, checked)
        }

        // ── Presets ──
        val presetNames = mutableListOf<String>()
        presetNames.addAll(eq.presetNames)
        presetNames.add(getString(R.string.custom))
        val presetAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            presetNames
        )
        presetDropdown.setAdapter(presetAdapter)
        updatePresetDisplay(presetDropdown, eq, presetNames)

        if (eq.presetNames.isEmpty()) {
            presetLayout.visibility = View.GONE
        }

        presetDropdown.setOnItemClickListener { _, _, position, _ ->
            if (suppressPresetChange) return@setOnItemClickListener
            if (position < eq.presetNames.size) {
                eq.usePreset(position.toShort())
                // Refresh band sliders to match preset values
                refreshBandSliders(eq)
            }
            // Last item = "Custom" — do nothing, already in custom mode
        }

        // ── Band sliders (dynamic) ──
        createBandSliders(eq, bandsContainer)

        // ── Bass Boost ──
        if (eq.bassBoost == null) {
            bassBoostRow.visibility = View.GONE
        } else {
            bassBoostSlider.value = eq.bassBoostStrength.toFloat()
            bassBoostSlider.addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    eq.setBassBoostStrength(value.toInt().toShort())
                }
            }
        }

        // ── Virtualizer ──
        if (eq.virtualizer == null) {
            virtualizerRow.visibility = View.GONE
        } else {
            virtualizerSlider.value = eq.virtualizerStrength.toFloat()
            virtualizerSlider.addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    eq.setVirtualizerStrength(value.toInt().toShort())
                }
            }
        }

        // ── Reset button ──
        resetButton.setOnClickListener {
            eq.resetToFlat()
            refreshBandSliders(eq)
            bassBoostSlider.value = 0f
            virtualizerSlider.value = 0f
            updatePresetDisplay(presetDropdown, eq, presetNames)
        }

        // ── Initial enabled state ──
        setControlsEnabled(view, eq.eqEnabled)

        // ── Listen for external state changes (e.g. preset applied) ──
        eq.onStateChanged = {
            if (isAdded) {
                requireActivity().runOnUiThread {
                    refreshBandSliders(eq)
                    updatePresetDisplay(presetDropdown, eq, presetNames)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        wrapper?.onStateChanged = null
        bandSliders.clear()
    }

    // ── Band slider creation ────────────────────────────────────────────

    private fun createBandSliders(eq: EqEffectWrapper, container: LinearLayout) {
        val bandCount = eq.numberOfBands.toInt()
        val range = eq.bandLevelRange // [min, max] millibels
        val minLevel = if (range.size >= 2) range[0].toFloat() else -1500f
        val maxLevel = if (range.size >= 2) range[1].toFloat() else 1500f

        for (i in 0 until bandCount) {
            val bandLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.eq_band_width),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            // Top label: +dB max
            val topLabel = TextView(requireContext()).apply {
                text = formatDb(maxLevel)
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelSmall)
            }
            bandLayout.addView(topLabel)

            // Vertical slider via rotation trick
            val sliderHeight = resources.getDimensionPixelSize(R.dimen.eq_slider_height)
            val sliderWrapper = FrameLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    sliderHeight,  // width = height of slider (swapped by rotation)
                    sliderHeight
                )
            }
            val slider = Slider(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(
                    sliderHeight,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
                rotation = 270f
                valueFrom = minLevel
                valueTo = maxLevel
                stepSize = 100f
                value = eq.bandLevels.getOrElse(i) { 0 }.toFloat()
                val bandIndex = i
                addOnChangeListener { _, value, fromUser ->
                    if (fromUser && !suppressSliderCallbacks) {
                        eq.setBandLevel(bandIndex.toShort(), value.toInt().toShort())
                        // Mark as custom preset in dropdown
                        view?.let { v ->
                            val dropdown = v.findViewById<AutoCompleteTextView>(R.id.preset_dropdown)
                            suppressPresetChange = true
                            dropdown?.setText(getString(R.string.custom), false)
                            suppressPresetChange = false
                        }
                    }
                }
            }
            sliderWrapper.addView(slider)
            bandSliders.add(slider)
            bandLayout.addView(sliderWrapper)

            // Bottom label: -dB min
            val bottomLabel = TextView(requireContext()).apply {
                text = formatDb(minLevel)
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelSmall)
            }
            bandLayout.addView(bottomLabel)

            // Frequency label
            val freqLabel = TextView(requireContext()).apply {
                text = formatFreq(eq.bandFrequencies.getOrElse(i) { 0 })
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium)
            }
            bandLayout.addView(freqLabel)

            container.addView(bandLayout)
        }
    }

    private fun refreshBandSliders(eq: EqEffectWrapper) {
        suppressSliderCallbacks = true
        try {
            for (i in bandSliders.indices) {
                val level = eq.bandLevels.getOrElse(i) { 0 }.toFloat()
                bandSliders[i].value = level
            }
        } finally {
            suppressSliderCallbacks = false
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun updatePresetDisplay(
        dropdown: AutoCompleteTextView,
        eq: EqEffectWrapper,
        presetNames: List<String>
    ) {
        suppressPresetChange = true
        val preset = eq.currentPreset.toInt()
        val name = if (preset >= 0 && preset < eq.presetNames.size) {
            eq.presetNames[preset]
        } else {
            getString(R.string.custom)
        }
        dropdown.setText(name, false)
        suppressPresetChange = false
    }

    private fun setControlsEnabled(root: View, enabled: Boolean) {
        root.findViewById<View>(R.id.bands_scroll)?.alpha = if (enabled) 1f else 0.4f
        root.findViewById<View>(R.id.bass_boost_row)?.alpha = if (enabled) 1f else 0.4f
        root.findViewById<View>(R.id.virtualizer_row)?.alpha = if (enabled) 1f else 0.4f
        root.findViewById<View>(R.id.preset_layout)?.isEnabled = enabled
        root.findViewById<View>(R.id.reset_button)?.isEnabled = enabled

        for (slider in bandSliders) {
            slider.isEnabled = enabled
        }
        root.findViewById<Slider>(R.id.bass_boost_slider)?.isEnabled = enabled
        root.findViewById<Slider>(R.id.virtualizer_slider)?.isEnabled = enabled
    }

    /** Converts millibel value to dB display string (e.g. 1500 → "+15 dB") */
    private fun formatDb(millibels: Float): String {
        val db = millibels / 100f
        return if (db >= 0) "+${db.toInt()} dB" else "${db.toInt()} dB"
    }

    /** Converts milliHz to readable frequency (e.g. 60000 → "60 Hz", 14000000 → "14 kHz") */
    private fun formatFreq(milliHz: Int): String {
        val hz = milliHz / 1000
        return if (hz >= 1000) {
            val khz = hz / 1000f
            if (khz == khz.toInt().toFloat()) "${khz.toInt()} kHz"
            else "%.1f kHz".format(khz)
        } else {
            "$hz Hz"
        }
    }
}
