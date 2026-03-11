package org.akanework.gramophone.ui.components

import android.content.Intent
import android.os.Bundle
import android.text.InputType
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
import androidx.preference.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.utils.EqEffectWrapper
import org.akanework.gramophone.ui.ParametricEqActivity

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
    private val bandValueLabels = mutableListOf<TextView>()

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

        // ── Presets + Profiles dropdown ──
        val dropdownItems = buildDropdownItems(eq)
        val dropdownAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            dropdownItems.map { it.label }
        )
        presetDropdown.setAdapter(dropdownAdapter)
        updatePresetDisplay(presetDropdown, eq)

        presetDropdown.setOnItemClickListener { _, _, position, _ ->
            if (suppressPresetChange) return@setOnItemClickListener
            val item = dropdownItems.getOrNull(position) ?: return@setOnItemClickListener
            when (item.type) {
                DropdownItemType.HARDWARE_PRESET -> {
                    eq.usePreset(item.index.toShort())
                    refreshBandSliders(eq)
                    bassBoostSlider.value = eq.bassBoostStrength.toFloat()
                    virtualizerSlider.value = eq.virtualizerStrength.toFloat()
                }
                DropdownItemType.SAVED_PROFILE -> {
                    eq.loadProfile(item.label)
                    refreshBandSliders(eq)
                    bassBoostSlider.value = eq.bassBoostStrength.toFloat()
                    virtualizerSlider.value = eq.virtualizerStrength.toFloat()
                    suppressPresetChange = true
                    presetDropdown.setText(item.label, false)
                    suppressPresetChange = false
                }
                DropdownItemType.CUSTOM -> { /* already custom, no-op */ }
                DropdownItemType.SAVE_ACTION -> {
                    // Reset dropdown text (don't show "Save Current…")
                    updatePresetDisplay(presetDropdown, eq)
                    showSaveProfileDialog(eq, presetDropdown, bassBoostSlider, virtualizerSlider)
                }
                DropdownItemType.DELETE_ACTION -> {
                    updatePresetDisplay(presetDropdown, eq)
                    showDeleteProfileDialog(eq, presetDropdown, bassBoostSlider, virtualizerSlider)
                }
            }
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
            updatePresetDisplay(presetDropdown, eq)
        }

        // ── Switch to Advanced EQ button ──
        view.findViewById<MaterialButton>(R.id.switch_to_advanced_button)?.setOnClickListener {
            PreferenceManager.getDefaultSharedPreferences(requireContext())
                .edit().putString("eq_mode", "parametric").apply()
            dismiss()
            startActivity(Intent(requireContext(), ParametricEqActivity::class.java))
        }

        // ── Initial enabled state ──
        setControlsEnabled(view, eq.eqEnabled)

        // ── Listen for external state changes (e.g. preset applied) ──
        eq.onStateChanged = {
            if (isAdded) {
                requireActivity().runOnUiThread {
                    refreshBandSliders(eq)
                    updatePresetDisplay(presetDropdown, eq)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        wrapper?.onStateChanged = null
        bandSliders.clear()
        bandValueLabels.clear()
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

            // Current dB value label (tappable for precise input)
            val currentLevel = eq.bandLevels.getOrElse(i) { 0 }.toFloat()
            val valueLabel = TextView(requireContext()).apply {
                text = formatDbPrecise(currentLevel)
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium)
                setPadding(0, 4, 0, 4)
            }
            bandValueLabels.add(valueLabel)

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
                value = currentLevel
                val bandIndex = i
                addOnChangeListener { _, value, fromUser ->
                    // Always update the value label (whether from user or programmatic)
                    if (bandIndex in bandValueLabels.indices) {
                        bandValueLabels[bandIndex].text = formatDbPrecise(value)
                    }
                    if (fromUser && !suppressSliderCallbacks) {
                        eq.setBandLevel(bandIndex.toShort(), value.toInt().toShort())
                        markAsCustom()
                    }
                }
            }
            sliderWrapper.addView(slider)
            bandSliders.add(slider)
            bandLayout.addView(sliderWrapper)

            // Value label between slider and freq label — tap to type exact dB
            valueLabel.setOnClickListener {
                showBandInputDialog(eq, i, minLevel, maxLevel)
            }
            bandLayout.addView(valueLabel)

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
                if (i in bandValueLabels.indices) {
                    bandValueLabels[i].text = formatDbPrecise(level)
                }
            }
        } finally {
            suppressSliderCallbacks = false
        }
    }

    // ── Dropdown item model ────────────────────────────────────────────

    private enum class DropdownItemType {
        HARDWARE_PRESET, SAVED_PROFILE, CUSTOM, SAVE_ACTION, DELETE_ACTION
    }

    private data class DropdownItem(
        val label: String,
        val type: DropdownItemType,
        val index: Int = -1
    )

    /** Builds the full dropdown list: hardware presets → saved profiles → Custom → Save → Delete */
    private fun buildDropdownItems(eq: EqEffectWrapper): List<DropdownItem> {
        val items = mutableListOf<DropdownItem>()
        // Hardware presets
        eq.presetNames.forEachIndexed { i, name ->
            items.add(DropdownItem(name, DropdownItemType.HARDWARE_PRESET, i))
        }
        // Saved profiles
        for (name in eq.getProfileNames()) {
            items.add(DropdownItem(name, DropdownItemType.SAVED_PROFILE))
        }
        // Custom
        items.add(DropdownItem(getString(R.string.custom), DropdownItemType.CUSTOM))
        // Actions
        items.add(DropdownItem(getString(R.string.eq_save_profile), DropdownItemType.SAVE_ACTION))
        if (eq.getProfileNames().isNotEmpty()) {
            items.add(DropdownItem(getString(R.string.eq_delete_profile), DropdownItemType.DELETE_ACTION))
        }
        return items
    }

    /** Refreshes the dropdown adapter and sets the displayed text to current state. */
    private fun rebuildDropdown(
        eq: EqEffectWrapper,
        dropdown: AutoCompleteTextView,
        bassBoostSlider: Slider,
        virtualizerSlider: Slider
    ) {
        val items = buildDropdownItems(eq)
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            items.map { it.label }
        )
        dropdown.setAdapter(adapter)
        // Re-wire click handler with new items
        dropdown.setOnItemClickListener { _, _, position, _ ->
            if (suppressPresetChange) return@setOnItemClickListener
            val item = items.getOrNull(position) ?: return@setOnItemClickListener
            when (item.type) {
                DropdownItemType.HARDWARE_PRESET -> {
                    eq.usePreset(item.index.toShort())
                    refreshBandSliders(eq)
                    bassBoostSlider.value = eq.bassBoostStrength.toFloat()
                    virtualizerSlider.value = eq.virtualizerStrength.toFloat()
                }
                DropdownItemType.SAVED_PROFILE -> {
                    eq.loadProfile(item.label)
                    refreshBandSliders(eq)
                    bassBoostSlider.value = eq.bassBoostStrength.toFloat()
                    virtualizerSlider.value = eq.virtualizerStrength.toFloat()
                    suppressPresetChange = true
                    dropdown.setText(item.label, false)
                    suppressPresetChange = false
                }
                DropdownItemType.CUSTOM -> { /* no-op */ }
                DropdownItemType.SAVE_ACTION -> {
                    updatePresetDisplay(dropdown, eq)
                    showSaveProfileDialog(eq, dropdown, bassBoostSlider, virtualizerSlider)
                }
                DropdownItemType.DELETE_ACTION -> {
                    updatePresetDisplay(dropdown, eq)
                    showDeleteProfileDialog(eq, dropdown, bassBoostSlider, virtualizerSlider)
                }
            }
        }
        updatePresetDisplay(dropdown, eq)
    }

    private fun updatePresetDisplay(dropdown: AutoCompleteTextView, eq: EqEffectWrapper) {
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

    // ── Profile dialogs ──────────────────────────────────────────────

    private fun showSaveProfileDialog(
        eq: EqEffectWrapper,
        dropdown: AutoCompleteTextView,
        bassBoostSlider: Slider,
        virtualizerSlider: Slider
    ) {
        val inputLayout = TextInputLayout(requireContext()).apply {
            hint = getString(R.string.eq_profile_name_hint)
            setPadding(
                resources.getDimensionPixelSize(R.dimen.tab_layout_content_padding),
                0,
                resources.getDimensionPixelSize(R.dimen.tab_layout_content_padding),
                0
            )
        }
        val editText = TextInputEditText(inputLayout.context)
        inputLayout.addView(editText)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.eq_save_profile_title))
            .setView(inputLayout)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = editText.text?.toString()?.trim()
                if (!name.isNullOrEmpty()) {
                    eq.saveProfile(name)
                    rebuildDropdown(eq, dropdown, bassBoostSlider, virtualizerSlider)
                    suppressPresetChange = true
                    dropdown.setText(name, false)
                    suppressPresetChange = false
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()

        editText.requestFocus()
    }

    private fun showDeleteProfileDialog(
        eq: EqEffectWrapper,
        dropdown: AutoCompleteTextView,
        bassBoostSlider: Slider,
        virtualizerSlider: Slider
    ) {
        val profiles = eq.getProfileNames()
        if (profiles.isEmpty()) return

        val names = profiles.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.eq_delete_profile_title))
            .setItems(names) { _, which ->
                val name = names[which]
                MaterialAlertDialogBuilder(requireContext())
                    .setMessage(getString(R.string.eq_delete_profile_confirm, name))
                    .setPositiveButton(getString(R.string.yes)) { _, _ ->
                        eq.deleteProfile(name)
                        rebuildDropdown(eq, dropdown, bassBoostSlider, virtualizerSlider)
                    }
                    .setNegativeButton(getString(R.string.no), null)
                    .show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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

    /** Marks the dropdown as "Custom" after manual band adjustment. */
    private fun markAsCustom() {
        view?.let { v ->
            val dropdown = v.findViewById<AutoCompleteTextView>(R.id.preset_dropdown)
            suppressPresetChange = true
            dropdown?.setText(getString(R.string.custom), false)
            suppressPresetChange = false
        }
    }

    /** Opens a dialog to type an exact dB value for a band. */
    private fun showBandInputDialog(
        eq: EqEffectWrapper,
        bandIndex: Int,
        minMillibels: Float,
        maxMillibels: Float
    ) {
        val currentDb = eq.bandLevels.getOrElse(bandIndex) { 0 } / 100f
        val minDb = minMillibels / 100f
        val maxDb = maxMillibels / 100f
        val freqText = formatFreq(eq.bandFrequencies.getOrElse(bandIndex) { 0 })

        val inputLayout = TextInputLayout(requireContext()).apply {
            hint = getString(R.string.eq_enter_db_hint, minDb, maxDb)
            setPadding(
                resources.getDimensionPixelSize(R.dimen.tab_layout_content_padding),
                0,
                resources.getDimensionPixelSize(R.dimen.tab_layout_content_padding),
                0
            )
        }
        val editText = TextInputEditText(inputLayout.context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or
                    InputType.TYPE_NUMBER_FLAG_SIGNED or
                    InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(if (currentDb == 0f) "0" else "%.1f".format(currentDb).trimEnd('0').trimEnd('.'))
            selectAll()
        }
        inputLayout.addView(editText)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.eq_set_band_title, freqText))
            .setView(inputLayout)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val text = editText.text?.toString()?.trim() ?: return@setPositiveButton
                val db = text.toFloatOrNull() ?: return@setPositiveButton
                val clamped = db.coerceIn(minDb, maxDb)
                val millibels = (clamped * 100).toInt()
                // Round to nearest 100 (stepSize on slider)
                val rounded = ((millibels + 50) / 100) * 100
                val finalMillibels = rounded.toFloat().coerceIn(minMillibels, maxMillibels)

                eq.setBandLevel(bandIndex.toShort(), finalMillibels.toInt().toShort())
                if (bandIndex in bandSliders.indices) {
                    bandSliders[bandIndex].value = finalMillibels
                }
                if (bandIndex in bandValueLabels.indices) {
                    bandValueLabels[bandIndex].text = formatDbPrecise(finalMillibels)
                }
                markAsCustom()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()

        // Auto-show keyboard
        editText.requestFocus()
    }

    /** Converts millibel value to dB display string (e.g. 1500 → "+15 dB") */
    private fun formatDb(millibels: Float): String {
        val db = millibels / 100f
        return if (db >= 0) "+${db.toInt()} dB" else "${db.toInt()} dB"
    }

    /** Precise dB display for value labels (e.g. 600 → "+6 dB", 0 → "0 dB") */
    private fun formatDbPrecise(millibels: Float): String {
        val db = millibels / 100f
        return when {
            db > 0 -> "+${db.toInt()} dB"
            db < 0 -> "${db.toInt()} dB"
            else -> "0 dB"
        }
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
