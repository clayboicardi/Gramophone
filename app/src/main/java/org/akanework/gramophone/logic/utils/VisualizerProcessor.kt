/*
 *     Copyright (C) 2024 Akane Foundation
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Gramophone is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.akanework.gramophone.logic.utils

import androidx.media3.common.C
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sin

/**
 * Taps into ExoPlayer's audio pipeline via [TeeAudioProcessor], computes a 512-point FFT,
 * and produces 32 logarithmically-spaced frequency band magnitudes for visualization.
 *
 * Data flow: ExoPlayer audio thread calls [handleBuffer] with raw PCM → accumulate 512 mono
 * samples → Hanning window → radix-2 FFT → log-frequency band grouping → dB normalization →
 * store in [magnitudes]. UI thread reads via [getMagnitudes] on each frame.
 *
 * Zero allocations in the hot path — all arrays are pre-allocated in init.
 */
class VisualizerProcessor : TeeAudioProcessor.AudioBufferSink {

    companion object {
        @Volatile
        var instance: VisualizerProcessor? = null
            private set

        const val BAND_COUNT = 32
        private const val FFT_SIZE = 512
    }

    /** When false, [handleBuffer] returns immediately (saves CPU when visualizer is off). */
    @Volatile
    var active = false

    private var sampleRate = 44100
    private var channelCount = 2
    private var encoding = C.ENCODING_PCM_16BIT

    // Sample accumulation buffer
    private val sampleBuffer = FloatArray(FFT_SIZE)
    private var sampleCount = 0

    // FFT working arrays (pre-allocated, reused every frame)
    private val fftReal = FloatArray(FFT_SIZE)
    private val fftImag = FloatArray(FFT_SIZE)

    // Pre-computed Hanning window coefficients
    private val window = FloatArray(FFT_SIZE) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / FFT_SIZE))).toFloat()
    }

    // Pre-computed logarithmic bin-to-band mapping
    private val binToBand = IntArray(FFT_SIZE / 2).also { arr ->
        val numBins = FFT_SIZE / 2
        for (i in 1 until numBins) {
            arr[i] = (ln(i.toFloat()) / ln(numBins.toFloat()) * BAND_COUNT)
                .toInt().coerceIn(0, BAND_COUNT - 1)
        }
    }

    // Output magnitudes (0..1 range), protected by lock
    private val magnitudes = FloatArray(BAND_COUNT)
    private val lock = Any()

    // Working arrays for band computation (pre-allocated)
    private val bandEnergies = FloatArray(BAND_COUNT)
    private val bandCounts = IntArray(BAND_COUNT)

    init {
        instance = this
    }

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        this.sampleRate = sampleRateHz
        this.channelCount = channelCount
        this.encoding = encoding
        sampleCount = 0
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        if (!active) return

        val bytesPerSample = when (encoding) {
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_FLOAT -> 4
            C.ENCODING_PCM_32BIT -> 4
            else -> return
        }
        val frameSize = bytesPerSample * channelCount
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        while (buffer.remaining() >= frameSize) {
            var mono = 0f
            for (ch in 0 until channelCount) {
                mono += when (encoding) {
                    C.ENCODING_PCM_16BIT -> buffer.short.toFloat() / 32768f
                    C.ENCODING_PCM_FLOAT -> buffer.float
                    C.ENCODING_PCM_32BIT -> buffer.int.toFloat() / 2147483648f
                    else -> 0f
                }
            }
            mono /= channelCount

            sampleBuffer[sampleCount++] = mono
            if (sampleCount >= FFT_SIZE) {
                processFFT()
                sampleCount = 0
            }
        }
    }

    private fun processFFT() {
        // Apply Hanning window and copy to FFT input arrays
        for (i in 0 until FFT_SIZE) {
            fftReal[i] = sampleBuffer[i] * window[i]
            fftImag[i] = 0f
        }

        // In-place radix-2 Cooley-Tukey FFT
        fft(fftReal, fftImag, FFT_SIZE)

        // Compute average energy per frequency band
        bandEnergies.fill(0f)
        bandCounts.fill(0)
        for (i in 1 until FFT_SIZE / 2) {
            val energy = fftReal[i] * fftReal[i] + fftImag[i] * fftImag[i]
            val band = binToBand[i]
            bandEnergies[band] += energy
            bandCounts[band]++
        }

        // Normalize to 0..1 using dB scaling
        // FFT of windowed float PCM: normalize by (N/2)² to get energy relative to full scale
        val fftNorm = 4f / (FFT_SIZE.toFloat() * FFT_SIZE.toFloat())

        synchronized(lock) {
            for (i in 0 until BAND_COUNT) {
                val avg = if (bandCounts[i] > 0) bandEnergies[i] / bandCounts[i] else 0f
                val normEnergy = avg * fftNorm
                val dB = 10f * log10(normEnergy.coerceAtLeast(1e-10f))
                // Map -50dB..0dB to 0..1 (50dB dynamic range)
                magnitudes[i] = ((dB + 50f) / 50f).coerceIn(0f, 1f)
            }
        }
    }

    /** Thread-safe read of current band magnitudes into [out]. */
    fun getMagnitudes(out: FloatArray) {
        synchronized(lock) {
            System.arraycopy(magnitudes, 0, out, 0, minOf(magnitudes.size, out.size))
        }
    }

    /** Zero all magnitudes. Bars will smoothly decay via VisualizerView's lerp. */
    fun clear() {
        synchronized(lock) {
            magnitudes.fill(0f)
        }
        sampleCount = 0
    }

    fun release() {
        active = false
        clear()
        if (instance === this) instance = null
    }

    /** In-place radix-2 Cooley-Tukey FFT. */
    private fun fft(real: FloatArray, imag: FloatArray, n: Int) {
        // Bit-reversal permutation
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                var t = real[i]; real[i] = real[j]; real[j] = t
                t = imag[i]; imag[i] = imag[j]; imag[j] = t
            }
            var k = n / 2
            while (k <= j) { j -= k; k /= 2 }
            j += k
        }
        // Butterfly stages
        var step = 1
        while (step < n) {
            val halfStep = step
            step *= 2
            val wR = cos(PI / halfStep).toFloat()
            val wI = (-sin(PI / halfStep)).toFloat()
            var wr = 1f
            var wi = 0f
            for (m in 0 until halfStep) {
                var i = m
                while (i < n) {
                    val jj = i + halfStep
                    val tr = wr * real[jj] - wi * imag[jj]
                    val ti = wr * imag[jj] + wi * real[jj]
                    real[jj] = real[i] - tr
                    imag[jj] = imag[i] - ti
                    real[i] += tr
                    imag[i] += ti
                    i += step
                }
                val newWr = wr * wR - wi * wI
                wi = wr * wI + wi * wR
                wr = newWr
            }
        }
    }
}
