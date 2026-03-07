package org.akanework.gramophone.logic.utils

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import androidx.media3.common.util.Log
import androidx.preference.PreferenceManager

/**
 * Wraps Android's Equalizer, BassBoost, and Virtualizer audio effects.
 * Follows the EffectWrapper lifecycle pattern used by VolumeEffectWrapper and
 * DynamicsProcessingEffectWrapper.
 *
 * Persists user settings via SharedPreferences so EQ state survives app restarts.
 * Exposes a companion-object [instance] for UI access from EqualizerBottomSheet.
 */
class EqEffectWrapper(private val context: Context) : EffectWrapper<Equalizer>() {
    companion object {
        private const val TAG = "EqEffectWrapper"

        // Preference keys
        private const val PREF_EQ_ENABLED = "eq_enabled"
        private const val PREF_EQ_PRESET = "eq_preset"
        private const val PREF_EQ_BAND_PREFIX = "eq_band_"
        private const val PREF_BASS_BOOST = "eq_bass_boost"
        private const val PREF_VIRTUALIZER = "eq_virtualizer"

        /** Singleton for UI access. Set by PostAmpAudioSink, cleared on release. */
        @Volatile
        var instance: EqEffectWrapper? = null
            private set

        fun setInstance(wrapper: EqEffectWrapper?) {
            instance = wrapper
        }
    }

    // ── Equalizer state ─────────────────────────────────────────────────
    override var effect: Equalizer? = null
        private set
    override val hasControl: Boolean
        get() = effect?.hasControl() == true

    var numberOfBands: Short = 0
        private set
    var bandFrequencies: IntArray = intArrayOf()
        private set
    var bandLevelRange: ShortArray = shortArrayOf() // [min, max] in millibels
        private set
    var presetNames: List<String> = emptyList()
        private set
    var currentPreset: Short = -1 // -1 = Custom
        private set
    var bandLevels: ShortArray = shortArrayOf()
        private set

    // ── BassBoost state ─────────────────────────────────────────────────
    var bassBoost: BassBoost? = null
        private set
    var bassBoostStrength: Short = 0 // 0-1000
        private set

    // ── Virtualizer state ───────────────────────────────────────────────
    var virtualizer: Virtualizer? = null
        private set
    var virtualizerStrength: Short = 0 // 0-1000
        private set

    // ── Enabled state (user toggle) ─────────────────────────────────────
    var eqEnabled: Boolean = false
        private set

    // ── Preferences ─────────────────────────────────────────────────────
    private val prefs: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(context)
    }

    /** Listener for UI updates when effect state changes externally. */
    var onStateChanged: (() -> Unit)? = null

    // ── EffectWrapper lifecycle ──────────────────────────────────────────

    override fun maybeCreate() {
        if (audioSessionId == 0 || !created) return
        // Restore enabled state from prefs
        eqEnabled = prefs.getBoolean(PREF_EQ_ENABLED, false)
        try {
            effect = Equalizer(0, audioSessionId).also { eq ->
                numberOfBands = eq.numberOfBands
                if (numberOfBands <= 0) {
                    Log.w(TAG, "Device returned 0 EQ bands — EQ not supported")
                    eq.release()
                    effect = null
                    return
                }
                bandLevelRange = eq.bandLevelRange // [min, max] in millibels
                bandFrequencies = IntArray(numberOfBands.toInt()) { i ->
                    eq.getCenterFreq(i.toShort()) // milliHz
                }
                presetNames = try {
                    (0 until eq.numberOfPresets).map { i ->
                        eq.getPresetName(i.toShort())
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read presets", e)
                    emptyList()
                }
                bandLevels = ShortArray(numberOfBands.toInt())
                restoreState(eq)
                eq.enabled = eqEnabled
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create Equalizer", e)
            try { effect?.release() } catch (_: Throwable) {}
            effect = null
        }

        // BassBoost
        try {
            bassBoostStrength = prefs.getInt(PREF_BASS_BOOST, 0).toShort()
            bassBoost = BassBoost(0, audioSessionId).also { bb ->
                try {
                    bb.setStrength(bassBoostStrength)
                } catch (e: Exception) {
                    Log.w(TAG, "BassBoost setStrength failed", e)
                }
                bb.enabled = eqEnabled && bassBoostStrength > 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create BassBoost", e)
            try { bassBoost?.release() } catch (_: Throwable) {}
            bassBoost = null
        }

        // Virtualizer
        try {
            virtualizerStrength = prefs.getInt(PREF_VIRTUALIZER, 0).toShort()
            virtualizer = Virtualizer(0, audioSessionId).also { virt ->
                try {
                    virt.setStrength(virtualizerStrength)
                } catch (e: Exception) {
                    Log.w(TAG, "Virtualizer setStrength failed", e)
                }
                virt.enabled = eqEnabled && virtualizerStrength > 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create Virtualizer", e)
            try { virtualizer?.release() } catch (_: Throwable) {}
            virtualizer = null
        }

        Log.i(TAG, "EQ created: bands=$numberOfBands, presets=${presetNames.size}, " +
                "enabled=$eqEnabled, session=$audioSessionId")
    }

    override fun destroy() {
        try { effect?.release() } catch (_: Throwable) {}
        effect = null
        try { bassBoost?.release() } catch (_: Throwable) {}
        bassBoost = null
        try { virtualizer?.release() } catch (_: Throwable) {}
        virtualizer = null
    }

    // ── Public API for UI ───────────────────────────────────────────────

    fun setEnabled(enabled: Boolean) {
        eqEnabled = enabled
        try { effect?.enabled = enabled } catch (e: Exception) {
            Log.w(TAG, "EQ setEnabled failed", e)
        }
        try { bassBoost?.enabled = enabled && bassBoostStrength > 0 } catch (e: Exception) {
            Log.w(TAG, "BassBoost setEnabled failed", e)
        }
        try { virtualizer?.enabled = enabled && virtualizerStrength > 0 } catch (e: Exception) {
            Log.w(TAG, "Virtualizer setEnabled failed", e)
        }
        prefs.edit().putBoolean(PREF_EQ_ENABLED, enabled).apply()
    }

    fun setBandLevel(band: Short, level: Short) {
        try {
            effect?.setBandLevel(band, level)
        } catch (e: Exception) {
            Log.w(TAG, "setBandLevel($band, $level) failed", e)
        }
        if (band.toInt() in bandLevels.indices) {
            bandLevels[band.toInt()] = level
        }
        currentPreset = -1 // mark as Custom
        saveState()
    }

    fun usePreset(preset: Short) {
        try {
            effect?.usePreset(preset)
        } catch (e: Exception) {
            Log.w(TAG, "usePreset($preset) failed", e)
            return
        }
        currentPreset = preset
        // Read back actual band levels after preset applied
        effect?.let { eq ->
            bandLevels = ShortArray(numberOfBands.toInt()) { i ->
                try { eq.getBandLevel(i.toShort()) } catch (_: Exception) { 0 }
            }
        }
        saveState()
        onStateChanged?.invoke()
    }

    fun setBassBoostStrength(strength: Short) {
        bassBoostStrength = strength
        try {
            bassBoost?.setStrength(strength)
            bassBoost?.enabled = eqEnabled && strength > 0
        } catch (e: Exception) {
            Log.w(TAG, "BassBoost setStrength($strength) failed", e)
        }
        prefs.edit().putInt(PREF_BASS_BOOST, strength.toInt()).apply()
    }

    fun setVirtualizerStrength(strength: Short) {
        virtualizerStrength = strength
        try {
            virtualizer?.setStrength(strength)
            virtualizer?.enabled = eqEnabled && strength > 0
        } catch (e: Exception) {
            Log.w(TAG, "Virtualizer setStrength($strength) failed", e)
        }
        prefs.edit().putInt(PREF_VIRTUALIZER, strength.toInt()).apply()
    }

    fun resetToFlat() {
        val zero: Short = 0
        for (i in 0 until numberOfBands.toInt()) {
            setBandLevel(i.toShort(), zero)
        }
        setBassBoostStrength(0)
        setVirtualizerStrength(0)
        currentPreset = -1
        saveState()
        onStateChanged?.invoke()
    }

    // ── Persistence ─────────────────────────────────────────────────────

    private fun saveState() {
        prefs.edit().apply {
            putInt(PREF_EQ_PRESET, currentPreset.toInt())
            for (i in bandLevels.indices) {
                putInt("${PREF_EQ_BAND_PREFIX}$i", bandLevels[i].toInt())
            }
            apply()
        }
    }

    private fun restoreState(eq: Equalizer) {
        val savedPreset = prefs.getInt(PREF_EQ_PRESET, -1).toShort()
        if (savedPreset >= 0 && savedPreset < eq.numberOfPresets) {
            // Restore a hardware preset
            try {
                eq.usePreset(savedPreset)
                currentPreset = savedPreset
                bandLevels = ShortArray(numberOfBands.toInt()) { i ->
                    try { eq.getBandLevel(i.toShort()) } catch (_: Exception) { 0 }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to restore preset $savedPreset", e)
                restoreCustomBands(eq)
            }
        } else {
            restoreCustomBands(eq)
        }
    }

    private fun restoreCustomBands(eq: Equalizer) {
        currentPreset = -1
        for (i in 0 until numberOfBands.toInt()) {
            val level = prefs.getInt("${PREF_EQ_BAND_PREFIX}$i", 0).toShort()
            try {
                eq.setBandLevel(i.toShort(), level)
                // Read back actual value — hardware may clamp to its supported range
                bandLevels[i] = eq.getBandLevel(i.toShort())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to restore band $i level $level", e)
                bandLevels[i] = 0
            }
        }
    }
}
