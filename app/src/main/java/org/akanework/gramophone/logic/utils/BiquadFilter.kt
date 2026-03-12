package org.akanework.gramophone.logic.utils

import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A single biquad filter section implementing 6 filter types from the Audio EQ Cookbook
 * (Robert Bristow-Johnson). Uses Transposed Direct Form II (TDF-II) for processing —
 * best for floating-point, only 2 state variables per channel.
 *
 * Coefficients are computed in Double (trig needs precision), processing in Float
 * (sufficient for audio).
 */
class BiquadFilter(private val maxChannels: Int = 2) {

    enum class Type {
        PEAKING, LOW_SHELF, HIGH_SHELF, LOW_PASS, HIGH_PASS, NOTCH
    }

    // Normalized coefficients (divided by a0)
    private var b0 = 1f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f

    // TDF-II state: 2 delay elements per channel
    private val z1 = FloatArray(maxChannels)
    private val z2 = FloatArray(maxChannels)

    // Store current sample rate for magnitudeAt()
    private var currentSampleRate = 44100.0

    /**
     * Compute filter coefficients for given parameters.
     * All formulas from Robert Bristow-Johnson's Audio EQ Cookbook.
     */
    fun configure(type: Type, sampleRate: Int, frequencyHz: Float, gainDb: Float, q: Float) {
        currentSampleRate = sampleRate.toDouble()
        val w0 = 2.0 * Math.PI * frequencyHz.toDouble() / currentSampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val A = 10.0.pow(gainDb.toDouble() / 40.0) // only for peaking and shelving
        val alpha = sinW0 / (2.0 * q.toDouble())

        val cb0: Double
        val cb1: Double
        val cb2: Double
        val ca0: Double
        val ca1: Double
        val ca2: Double

        when (type) {
            Type.PEAKING -> {
                cb0 = 1.0 + alpha * A
                cb1 = -2.0 * cosW0
                cb2 = 1.0 - alpha * A
                ca0 = 1.0 + alpha / A
                ca1 = -2.0 * cosW0
                ca2 = 1.0 - alpha / A
            }
            Type.LOW_SHELF -> {
                val sqrtA2alpha = 2.0 * sqrt(A) * alpha
                cb0 = A * ((A + 1.0) - (A - 1.0) * cosW0 + sqrtA2alpha)
                cb1 = 2.0 * A * ((A - 1.0) - (A + 1.0) * cosW0)
                cb2 = A * ((A + 1.0) - (A - 1.0) * cosW0 - sqrtA2alpha)
                ca0 = (A + 1.0) + (A - 1.0) * cosW0 + sqrtA2alpha
                ca1 = -2.0 * ((A - 1.0) + (A + 1.0) * cosW0)
                ca2 = (A + 1.0) + (A - 1.0) * cosW0 - sqrtA2alpha
            }
            Type.HIGH_SHELF -> {
                val sqrtA2alpha = 2.0 * sqrt(A) * alpha
                cb0 = A * ((A + 1.0) + (A - 1.0) * cosW0 + sqrtA2alpha)
                cb1 = -2.0 * A * ((A - 1.0) + (A + 1.0) * cosW0)
                cb2 = A * ((A + 1.0) + (A - 1.0) * cosW0 - sqrtA2alpha)
                ca0 = (A + 1.0) - (A - 1.0) * cosW0 + sqrtA2alpha
                ca1 = 2.0 * ((A - 1.0) - (A + 1.0) * cosW0)
                ca2 = (A + 1.0) - (A - 1.0) * cosW0 - sqrtA2alpha
            }
            Type.LOW_PASS -> {
                cb0 = (1.0 - cosW0) / 2.0
                cb1 = 1.0 - cosW0
                cb2 = (1.0 - cosW0) / 2.0
                ca0 = 1.0 + alpha
                ca1 = -2.0 * cosW0
                ca2 = 1.0 - alpha
            }
            Type.HIGH_PASS -> {
                cb0 = (1.0 + cosW0) / 2.0
                cb1 = -(1.0 + cosW0)
                cb2 = (1.0 + cosW0) / 2.0
                ca0 = 1.0 + alpha
                ca1 = -2.0 * cosW0
                ca2 = 1.0 - alpha
            }
            Type.NOTCH -> {
                cb0 = 1.0
                cb1 = -2.0 * cosW0
                cb2 = 1.0
                ca0 = 1.0 + alpha
                ca1 = -2.0 * cosW0
                ca2 = 1.0 - alpha
            }
        }

        // Normalize by a0
        b0 = (cb0 / ca0).toFloat()
        b1 = (cb1 / ca0).toFloat()
        b2 = (cb2 / ca0).toFloat()
        a1 = (ca1 / ca0).toFloat()
        a2 = (ca2 / ca0).toFloat()
    }

    /**
     * Process a single sample through TDF-II biquad for the given channel.
     */
    fun processSample(channel: Int, input: Float): Float {
        val output = b0 * input + z1[channel]
        z1[channel] = b1 * input - a1 * output + z2[channel]
        z2[channel] = b2 * input - a2 * output
        return output
    }

    /**
     * Clear delay line state for all channels. Call on seek/track change
     * to prevent artifacts from stale state.
     */
    fun clearState() {
        z1.fill(0f)
        z2.fill(0f)
    }

    /**
     * Copy all coefficients and state from another BiquadFilter.
     * Used for crossfade transitions when switching presets.
     */
    fun copyFrom(other: BiquadFilter) {
        this.b0 = other.b0; this.b1 = other.b1; this.b2 = other.b2
        this.a1 = other.a1; this.a2 = other.a2
        this.currentSampleRate = other.currentSampleRate
        other.z1.copyInto(this.z1, endIndex = minOf(other.z1.size, this.z1.size))
        other.z2.copyInto(this.z2, endIndex = minOf(other.z2.size, this.z2.size))
    }

    /**
     * Evaluate the filter's frequency response magnitude at a given frequency.
     * Used by UI to draw the EQ curve. Returns |H(e^jw)|.
     */
    fun magnitudeAt(freqHz: Double): Double {
        val w = 2.0 * Math.PI * freqHz / currentSampleRate
        val cosW = cos(w)
        val cos2W = cos(2.0 * w)
        val sinW = sin(w)
        val sin2W = sin(2.0 * w)

        val numReal = b0.toDouble() + b1.toDouble() * cosW + b2.toDouble() * cos2W
        val numImag = -(b1.toDouble() * sinW + b2.toDouble() * sin2W)
        val denReal = 1.0 + a1.toDouble() * cosW + a2.toDouble() * cos2W
        val denImag = -(a1.toDouble() * sinW + a2.toDouble() * sin2W)

        return sqrt(
            (numReal * numReal + numImag * numImag) /
                    (denReal * denReal + denImag * denImag)
        )
    }
}
