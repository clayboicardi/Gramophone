package org.akanework.gramophone.logic.utils

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Immutable data model for the 31-band parametric EQ configuration.
 * All classes are data classes for safe passing across threads via AtomicReference.
 */

enum class FilterType {
    PEAKING, LOW_SHELF, HIGH_SHELF, LOW_PASS, HIGH_PASS, NOTCH;

    fun toBiquadType(): BiquadFilter.Type = when (this) {
        PEAKING -> BiquadFilter.Type.PEAKING
        LOW_SHELF -> BiquadFilter.Type.LOW_SHELF
        HIGH_SHELF -> BiquadFilter.Type.HIGH_SHELF
        LOW_PASS -> BiquadFilter.Type.LOW_PASS
        HIGH_PASS -> BiquadFilter.Type.HIGH_PASS
        NOTCH -> BiquadFilter.Type.NOTCH
    }
}

data class BandConfig(
    val enabled: Boolean,
    val filterType: FilterType,
    val frequencyHz: Float,
    val gainDb: Float,
    val q: Float
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("enabled", enabled)
        put("filterType", filterType.name)
        put("frequencyHz", frequencyHz.toDouble())
        put("gainDb", gainDb.toDouble())
        put("q", q.toDouble())
    }

    companion object {
        fun fromJson(json: JSONObject): BandConfig = BandConfig(
            enabled = json.optBoolean("enabled", true),
            filterType = try {
                FilterType.valueOf(json.optString("filterType", "PEAKING"))
            } catch (_: IllegalArgumentException) {
                FilterType.PEAKING
            },
            frequencyHz = json.optDouble("frequencyHz", 1000.0).toFloat(),
            gainDb = json.optDouble("gainDb", 0.0).toFloat(),
            q = json.optDouble("q", DEFAULT_Q.toDouble()).toFloat()
        )

        /** Default Q for 1/3 octave bandwidth */
        const val DEFAULT_Q = 4.318f
    }
}

data class EqConfig(
    val enabled: Boolean,
    val preampDb: Float,
    val bands: List<BandConfig>
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("enabled", enabled)
        put("preampDb", preampDb.toDouble())
        val bandsArray = JSONArray()
        for (band in bands) {
            bandsArray.put(band.toJson())
        }
        put("bands", bandsArray)
    }

    fun saveToPrefs(prefs: SharedPreferences) {
        prefs.edit()
            .putString(PREFS_KEY, toJson().toString())
            .apply()
    }

    companion object {
        const val PREFS_KEY = "parametric_eq_config"

        /** Standard 31-band ISO 1/3-octave center frequencies */
        val ISO_31_FREQUENCIES = floatArrayOf(
            20f, 25f, 31.5f, 40f, 50f, 63f, 80f, 100f, 125f, 160f,
            200f, 250f, 315f, 400f, 500f, 630f, 800f, 1000f, 1250f, 1600f,
            2000f, 2500f, 3150f, 4000f, 5000f, 6300f, 8000f, 10000f, 12500f, 16000f,
            20000f
        )

        /** Create a default 31-band config with all bands at 0 dB, disabled */
        fun createDefault31Band(): EqConfig = EqConfig(
            enabled = false,
            preampDb = 0f,
            bands = ISO_31_FREQUENCIES.map { freq ->
                BandConfig(
                    enabled = true,
                    filterType = FilterType.PEAKING,
                    frequencyHz = freq,
                    gainDb = 0f,
                    q = BandConfig.DEFAULT_Q
                )
            }
        )

        fun fromJson(jsonStr: String): EqConfig {
            val json = JSONObject(jsonStr)
            val bandsArray = json.getJSONArray("bands")
            val bands = (0 until bandsArray.length()).map { i ->
                BandConfig.fromJson(bandsArray.getJSONObject(i))
            }
            return EqConfig(
                enabled = json.optBoolean("enabled", false),
                preampDb = json.optDouble("preampDb", 0.0).toFloat(),
                bands = bands
            )
        }

        fun loadFromPrefs(prefs: SharedPreferences): EqConfig {
            val jsonStr = prefs.getString(PREFS_KEY, null) ?: return createDefault31Band()
            return try {
                fromJson(jsonStr)
            } catch (_: Exception) {
                createDefault31Band()
            }
        }
    }
}
