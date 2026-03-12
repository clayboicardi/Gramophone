package org.akanework.gramophone.ui

import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.utils.BandConfig
import org.akanework.gramophone.logic.utils.EqConfig
import org.akanework.gramophone.logic.utils.EqEffectWrapper
import org.akanework.gramophone.logic.utils.FilterType
import org.akanework.gramophone.logic.utils.ParametricEqProcessor
import org.akanework.gramophone.logic.utils.ParametricEqProfileManager
import org.akanework.gramophone.ui.components.FrequencyResponseView

/**
 * Full-screen Activity for the 31-band parametric equalizer.
 * Text-based input interface — tap gain values to type exact dB.
 * Mutually exclusive with the hardware EQ (Simple mode).
 */
class ParametricEqActivity : AppCompatActivity() {

    private var processor: ParametricEqProcessor? = null
    private var currentConfig: EqConfig = EqConfig.createDefault31Band()
    private var selectedBandIndex: Int = -1
    private lateinit var prefs: SharedPreferences

    // View references
    private lateinit var frequencyResponseView: FrequencyResponseView
    private lateinit var bandsContainer: LinearLayout
    private lateinit var bandDetailPanel: LinearLayout
    private lateinit var bandDetailTitle: TextView
    private lateinit var detailGainValue: TextView
    private lateinit var detailQValue: TextView
    private lateinit var filterTypeDropdown: AutoCompleteTextView
    private lateinit var bandEnabledCheckbox: MaterialCheckBox
    private lateinit var preampValue: TextView
    private lateinit var enableSwitch: MaterialSwitch
    private lateinit var presetDropdown: AutoCompleteTextView
    private lateinit var clippingWarning: TextView
    private lateinit var bandsScroll: HorizontalScrollView

    private var suppressPresetChange = false
    private var accentColor = 0
    private var cutColor = 0
    private var defaultTextColor = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_parametric_eq)

        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        processor = ParametricEqProcessor.instance

        // Load saved config
        currentConfig = EqConfig.loadFromPrefs(prefs)

        initViews()
        // Cache colors for gain color coding
        accentColor = MaterialColors.getColor(
            this, androidx.appcompat.R.attr.colorPrimary, 0xFF6200EE.toInt()
        )
        cutColor = MaterialColors.getColor(
            this, android.R.attr.colorError, 0xFFB00020.toInt()
        )
        defaultTextColor = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorOnSurface, 0xFF000000.toInt()
        )
        setupToolbar()
        setupEnableSwitch()
        setupPresetDropdown()
        setupPreamp()
        setupBandGrid()
        setupBandDetail()
        setupActionButtons()
        setupModeToggle()

        // Disable hardware EQ when entering Advanced mode
        EqEffectWrapper.instance?.setEnabled(false)

        // Enable parametric if config says so
        if (currentConfig.enabled) {
            processor?.updateConfig(currentConfig)
        }

        updateFrequencyResponse()
        updateClippingWarning()
    }

    private fun initViews() {
        frequencyResponseView = findViewById(R.id.frequency_response_view)
        bandsContainer = findViewById(R.id.bands_container)
        bandDetailPanel = findViewById(R.id.band_detail_panel)
        bandDetailTitle = findViewById(R.id.band_detail_title)
        detailGainValue = findViewById(R.id.detail_gain_value)
        detailQValue = findViewById(R.id.detail_q_value)
        filterTypeDropdown = findViewById(R.id.filter_type_dropdown)
        bandEnabledCheckbox = findViewById(R.id.band_enabled_checkbox)
        preampValue = findViewById(R.id.preamp_value)
        enableSwitch = findViewById(R.id.eq_enable_switch)
        presetDropdown = findViewById(R.id.preset_dropdown)
        clippingWarning = findViewById(R.id.clipping_warning)
        bandsScroll = findViewById(R.id.bands_scroll)
    }

    override fun onPause() {
        super.onPause()
        // Persist config on any activity pause to handle unexpected kills
        currentConfig.saveToPrefs(prefs)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupEnableSwitch() {
        enableSwitch.isChecked = currentConfig.enabled
        enableSwitch.setOnCheckedChangeListener { _, checked ->
            currentConfig = currentConfig.copy(enabled = checked)
            applyConfig()
        }
    }

    // ── Preset / Profile Management ─────────────────────────────────────

    private fun setupPresetDropdown() {
        rebuildPresetDropdown()
    }

    private fun rebuildPresetDropdown() {
        val items = mutableListOf<String>()
        items.add(getString(R.string.eq_flat))
        // Built-in presets
        val builtInNames = ParametricEqProfileManager.BUILT_IN_PRESETS.keys.toList()
        items.addAll(builtInNames)
        // Saved profiles
        val profileNames = ParametricEqProfileManager.getProfileNames(prefs)
        if (profileNames.isNotEmpty()) {
            items.addAll(profileNames)
        }
        items.add(getString(R.string.eq_save_profile))
        if (profileNames.isNotEmpty()) {
            items.add(getString(R.string.eq_delete_profile))
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, items)
        presetDropdown.setAdapter(adapter)

        presetDropdown.setOnItemClickListener { _, _, position, _ ->
            if (suppressPresetChange) return@setOnItemClickListener
            val selected = items[position]
            when {
                position == 0 -> {
                    // Flat
                    currentConfig = EqConfig.createDefault31Band().copy(enabled = currentConfig.enabled)
                    applyConfig()
                    refreshAll()
                }
                selected in ParametricEqProfileManager.BUILT_IN_PRESETS -> {
                    // Built-in preset
                    val preset = ParametricEqProfileManager.BUILT_IN_PRESETS[selected] ?: return@setOnItemClickListener
                    currentConfig = preset.copy(enabled = currentConfig.enabled)
                    applyConfig()
                    refreshAll()
                }
                selected == getString(R.string.eq_save_profile) -> {
                    suppressPresetChange = true
                    presetDropdown.setText("", false)
                    suppressPresetChange = false
                    showSaveProfileDialog()
                }
                selected == getString(R.string.eq_delete_profile) -> {
                    suppressPresetChange = true
                    presetDropdown.setText("", false)
                    suppressPresetChange = false
                    showDeleteProfileDialog()
                }
                else -> {
                    // User-saved profile
                    loadProfile(selected)
                }
            }
        }
    }

    private fun loadProfile(name: String) {
        val config = ParametricEqProfileManager.loadProfile(prefs, name) ?: return
        currentConfig = config.copy(enabled = currentConfig.enabled)
        applyConfig()
        refreshAll()
        suppressPresetChange = true
        presetDropdown.setText(name, false)
        suppressPresetChange = false
    }

    private fun showSaveProfileDialog() {
        val inputLayout = TextInputLayout(this).apply {
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

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.eq_save_profile_title))
            .setView(inputLayout)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = editText.text?.toString()?.trim()
                if (!name.isNullOrEmpty()) {
                    ParametricEqProfileManager.saveProfile(prefs, name, currentConfig)
                    rebuildPresetDropdown()
                    suppressPresetChange = true
                    presetDropdown.setText(name, false)
                    suppressPresetChange = false
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()

        editText.requestFocus()
    }

    private fun showDeleteProfileDialog() {
        val profiles = ParametricEqProfileManager.getProfileNames(prefs)
        if (profiles.isEmpty()) return

        val names = profiles.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.eq_delete_profile_title))
            .setItems(names) { _, which ->
                val name = names[which]
                MaterialAlertDialogBuilder(this)
                    .setMessage(getString(R.string.eq_delete_profile_confirm, name))
                    .setPositiveButton(getString(R.string.yes)) { _, _ ->
                        ParametricEqProfileManager.deleteProfile(prefs, name)
                        rebuildPresetDropdown()
                    }
                    .setNegativeButton(getString(R.string.no), null)
                    .show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ── Preamp ──────────────────────────────────────────────────────────

    private fun setupPreamp() {
        updatePreampDisplay()

        preampValue.setOnClickListener {
            showPreampInputDialog()
        }

        findViewById<MaterialButton>(R.id.auto_preamp_button).setOnClickListener {
            autoPreamp()
        }
    }

    private fun updatePreampDisplay() {
        val db = currentConfig.preampDb
        preampValue.text = formatGain(db)
    }

    private fun showPreampInputDialog() {
        val inputLayout = TextInputLayout(this).apply {
            hint = getString(R.string.eq_preamp_hint)
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
            val db = currentConfig.preampDb
            setText(if (db == 0f) "0" else "%.1f".format(db).trimEnd('0').trimEnd('.'))
            selectAll()
        }
        inputLayout.addView(editText)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.eq_set_preamp_title))
            .setView(inputLayout)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val text = editText.text?.toString()?.trim() ?: return@setPositiveButton
                val db = text.toFloatOrNull() ?: return@setPositiveButton
                val clamped = db.coerceIn(-15f, 15f)
                currentConfig = currentConfig.copy(preampDb = clamped)
                applyConfig()
                updatePreampDisplay()
                updateClippingWarning()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()

        editText.requestFocus()
    }

    /**
     * Auto-preamp: compute the maximum positive gain across all enabled bands
     * and set preamp to the negative of that to prevent clipping.
     */
    private fun autoPreamp() {
        val maxGain = currentConfig.bands
            .filter { it.enabled }
            .maxOfOrNull { it.gainDb } ?: 0f
        val autoPreamp = if (maxGain > 0f) -maxGain else 0f
        currentConfig = currentConfig.copy(preampDb = autoPreamp)
        applyConfig()
        updatePreampDisplay()
        updateClippingWarning()
    }

    // ── Clipping Warning ────────────────────────────────────────────────

    private fun updateClippingWarning() {
        val maxGain = currentConfig.bands
            .filter { it.enabled }
            .maxOfOrNull { it.gainDb } ?: 0f
        val totalPeak = currentConfig.preampDb + maxGain
        clippingWarning.visibility = if (totalPeak > 0f) View.VISIBLE else View.GONE
    }

    // ── Band Grid ───────────────────────────────────────────────────────

    private fun setupBandGrid() {
        refreshBandGrid()
    }

    private fun refreshBandGrid() {
        bandsContainer.removeAllViews()

        for (i in currentConfig.bands.indices) {
            val band = currentConfig.bands[i]

            val bandColumn = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.eq_band_width),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding(4, 8, 4, 8)
            }

            // Frequency label (tappable to select without editing)
            val freqLabel = TextView(this).apply {
                text = formatFreq(band.frequencyHz)
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelSmall)
                background = getDrawable(android.R.drawable.list_selector_background)
                isClickable = true
                isFocusable = true

                if (i == selectedBandIndex) {
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(accentColor)
                }

                val bandIndex = i
                setOnClickListener {
                    selectBand(bandIndex)
                }
            }
            bandColumn.addView(freqLabel)

            // Gain value (tappable to edit, long-press to reset)
            val gainLabel = TextView(this).apply {
                text = formatGain(band.gainDb)
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium)
                setPadding(0, 8, 0, 8)
                background = getDrawable(android.R.drawable.list_selector_background)
                isClickable = true
                isFocusable = true
                isLongClickable = true

                // Color coding: boost=accent, cut=error, zero=default
                if (i == selectedBandIndex) {
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(accentColor)
                } else {
                    setTextColor(when {
                        band.gainDb > 0f -> accentColor
                        band.gainDb < 0f -> cutColor
                        else -> defaultTextColor
                    })
                }
                // Dim disabled bands
                if (!band.enabled) {
                    alpha = 0.4f
                }

                val bandIndex = i
                setOnClickListener {
                    selectBand(bandIndex)
                    showGainInputDialog(bandIndex)
                }
                // Long-press to reset individual band
                setOnLongClickListener {
                    updateBandConfig(bandIndex, BandConfig(
                        enabled = true, filterType = FilterType.PEAKING,
                        frequencyHz = band.frequencyHz, gainDb = 0f, q = BandConfig.DEFAULT_Q
                    ))
                    true
                }
            }
            bandColumn.addView(gainLabel)

            bandsContainer.addView(bandColumn)
        }

        // Scroll to selected band
        if (selectedBandIndex >= 0) {
            scrollToSelectedBand()
        }
    }

    /** Scroll the horizontal band grid to make the selected band visible */
    private fun scrollToSelectedBand() {
        if (selectedBandIndex < 0 || selectedBandIndex >= bandsContainer.childCount) return
        bandsContainer.post {
            val child = bandsContainer.getChildAt(selectedBandIndex) ?: return@post
            val scrollX = child.left - (bandsScroll.width / 2) + (child.width / 2)
            bandsScroll.smoothScrollTo(scrollX.coerceAtLeast(0), 0)
        }
    }

    /**
     * Select a band: update index, refresh grid highlighting, update detail panel,
     * and sync the frequency response view's selected band dot.
     */
    private fun selectBand(index: Int) {
        selectedBandIndex = index
        refreshBandGrid()
        updateSelectedBandDetail()
        frequencyResponseView.setSelectedBand(index)
    }

    private fun showGainInputDialog(bandIndex: Int) {
        val band = currentConfig.bands[bandIndex]
        val freqText = formatFreq(band.frequencyHz)

        val inputLayout = TextInputLayout(this).apply {
            hint = getString(R.string.eq_gain_hint)
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
            setText(if (band.gainDb == 0f) "0" else "%.1f".format(band.gainDb).trimEnd('0').trimEnd('.'))
            selectAll()
        }
        inputLayout.addView(editText)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.eq_set_gain_title, freqText))
            .setView(inputLayout)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val text = editText.text?.toString()?.trim() ?: return@setPositiveButton
                val db = text.toFloatOrNull() ?: return@setPositiveButton
                val clamped = db.coerceIn(-15f, 15f)
                updateBandConfig(bandIndex, band.copy(gainDb = clamped))
                updateClippingWarning()
                // Auto-advance to next band for fast sequential editing
                if (bandIndex + 1 < currentConfig.bands.size) {
                    selectBand(bandIndex + 1)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()

        editText.requestFocus()
    }

    // ── Selected Band Detail Panel ──────────────────────────────────────

    private fun setupBandDetail() {
        // Filter type dropdown
        val filterTypes = FilterType.entries.map { it.name.replace("_", " ") }
        val typeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, filterTypes)
        filterTypeDropdown.setAdapter(typeAdapter)

        filterTypeDropdown.setOnItemClickListener { _, _, position, _ ->
            if (selectedBandIndex < 0) return@setOnItemClickListener
            val band = currentConfig.bands[selectedBandIndex]
            val newType = FilterType.entries[position]
            updateBandConfig(selectedBandIndex, band.copy(filterType = newType))
        }

        // Band enabled checkbox
        bandEnabledCheckbox.setOnCheckedChangeListener { _, checked ->
            if (selectedBandIndex < 0) return@setOnCheckedChangeListener
            val band = currentConfig.bands[selectedBandIndex]
            updateBandConfig(selectedBandIndex, band.copy(enabled = checked))
        }

        // Gain value tap in detail panel
        detailGainValue.setOnClickListener {
            if (selectedBandIndex >= 0) {
                showGainInputDialog(selectedBandIndex)
            }
        }

        // Q value tap
        detailQValue.setOnClickListener {
            if (selectedBandIndex >= 0) {
                showQInputDialog(selectedBandIndex)
            }
        }
    }

    private fun updateSelectedBandDetail() {
        if (selectedBandIndex < 0 || selectedBandIndex >= currentConfig.bands.size) {
            bandDetailPanel.visibility = View.GONE
            return
        }
        bandDetailPanel.visibility = View.VISIBLE

        val band = currentConfig.bands[selectedBandIndex]
        bandDetailTitle.text = getString(R.string.eq_selected_band, formatFreq(band.frequencyHz))
        detailGainValue.text = formatGain(band.gainDb)
        detailQValue.text = "%.3f".format(band.q)

        suppressPresetChange = true
        filterTypeDropdown.setText(band.filterType.name.replace("_", " "), false)
        suppressPresetChange = false

        bandEnabledCheckbox.isChecked = band.enabled
    }

    private fun showQInputDialog(bandIndex: Int) {
        val band = currentConfig.bands[bandIndex]

        val inputLayout = TextInputLayout(this).apply {
            hint = getString(R.string.eq_q_hint)
            setPadding(
                resources.getDimensionPixelSize(R.dimen.tab_layout_content_padding),
                0,
                resources.getDimensionPixelSize(R.dimen.tab_layout_content_padding),
                0
            )
        }
        val editText = TextInputEditText(inputLayout.context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or
                    InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText("%.3f".format(band.q))
            selectAll()
        }
        inputLayout.addView(editText)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.eq_set_q_title))
            .setView(inputLayout)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val text = editText.text?.toString()?.trim() ?: return@setPositiveButton
                val q = text.toFloatOrNull() ?: return@setPositiveButton
                val clamped = q.coerceIn(0.1f, 30f)
                updateBandConfig(bandIndex, band.copy(q = clamped))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()

        editText.requestFocus()
    }

    // ── Action Buttons ──────────────────────────────────────────────────

    private fun setupActionButtons() {
        findViewById<MaterialButton>(R.id.reset_band_button).setOnClickListener {
            if (selectedBandIndex >= 0) {
                val band = currentConfig.bands[selectedBandIndex]
                updateBandConfig(
                    selectedBandIndex,
                    band.copy(
                        gainDb = 0f,
                        q = BandConfig.DEFAULT_Q,
                        filterType = FilterType.PEAKING,
                        enabled = true
                    )
                )
                updateClippingWarning()
            }
        }

        findViewById<MaterialButton>(R.id.reset_all_button).setOnClickListener {
            currentConfig = EqConfig.createDefault31Band().copy(enabled = currentConfig.enabled)
            selectedBandIndex = -1
            applyConfig()
            refreshAll()
        }
    }

    // ── Mode Toggle ─────────────────────────────────────────────────────

    private fun setupModeToggle() {
        findViewById<MaterialButton>(R.id.switch_to_simple_button).setOnClickListener {
            // Save preference
            prefs.edit().putString("eq_mode", "simple").apply()
            // Disable parametric
            processor?.updateConfig(currentConfig.copy(enabled = false))
            // Re-enable hardware EQ if it was previously enabled
            val wasEnabled = prefs.getBoolean("eq_enabled", false)
            if (wasEnabled) EqEffectWrapper.instance?.setEnabled(true)
            finish()
        }
    }

    // ── Config Application ──────────────────────────────────────────────

    private fun updateBandConfig(index: Int, newBand: BandConfig) {
        val newBands = currentConfig.bands.toMutableList()
        newBands[index] = newBand
        currentConfig = currentConfig.copy(bands = newBands)
        applyConfig()
        refreshBandGrid()
        updateSelectedBandDetail()
        updateClippingWarning()
    }

    private fun applyConfig() {
        processor?.updateConfig(currentConfig)
        currentConfig.saveToPrefs(prefs)
        updateFrequencyResponse()
    }

    private fun updateFrequencyResponse() {
        frequencyResponseView.updateConfig(currentConfig)
        frequencyResponseView.setSelectedBand(selectedBandIndex)
    }

    /** Refresh all UI elements after a config change */
    private fun refreshAll() {
        refreshBandGrid()
        updateSelectedBandDetail()
        updatePreampDisplay()
        updateClippingWarning()
    }

    // ── Formatting Helpers ──────────────────────────────────────────────

    private fun formatFreq(hz: Float): String {
        return when {
            hz >= 1000 -> {
                val khz = hz / 1000f
                if (khz == khz.toInt().toFloat()) "${khz.toInt()}k"
                else "%.1fk".format(khz)
            }
            hz == hz.toInt().toFloat() -> "${hz.toInt()}"
            else -> "%.1f".format(hz)
        }
    }

    private fun formatGain(db: Float): String {
        return when {
            db == 0f -> "0 dB"
            db > 0 -> "+%.1f dB".format(db).replace(Regex("\\.0 "), " ")
            else -> "%.1f dB".format(db).replace(Regex("\\.0 "), " ")
        }
    }
}
