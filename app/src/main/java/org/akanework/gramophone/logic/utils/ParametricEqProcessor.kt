package org.akanework.gramophone.logic.utils

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.Log
import androidx.media3.exoplayer.audio.ToFloatPcmAudioProcessor
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.pow

/**
 * Media3 BaseAudioProcessor that chains 31 biquad filters for parametric EQ.
 * Follows the ReplayGainAudioProcessor pattern:
 * - onConfigure() returns ENCODING_PCM_FLOAT output
 * - Uses ToFloatPcmAudioProcessor for non-float input conversion
 * - Thread-safe config handoff via AtomicReference
 * - Clears filter state on flush to prevent artifacts on seek/track change
 */
class ParametricEqProcessor : BaseAudioProcessor() {

    companion object {
        private const val TAG = "ParametricEQ"

        /** Singleton for UI access. Set by GramophoneRenderFactory, cleared on release. */
        @Volatile
        var instance: ParametricEqProcessor? = null
            private set

        fun setInstance(p: ParametricEqProcessor?) {
            instance = p
        }
    }

    private val toFloatPcmAudioProcessor = ToFloatPcmAudioProcessor()

    // Thread-safe config handoff: UI thread sets, audio thread reads
    private val pendingConfig = AtomicReference<EqConfig?>(null)

    // Active state (only accessed on audio thread)
    private var filters: Array<BiquadFilter> = emptyArray()
    private var bandConfigs: List<BandConfig> = emptyList()
    private var eqEnabled = false
    private var preampLinear = 1f
    private var currentSampleRate = 0
    private var currentChannelCount = 0

    // Pending fields committed in onFlush (same pattern as ReplayGainAudioProcessor)
    private var pendingActive = false

    /**
     * Called from UI thread to update the EQ configuration.
     * The audio thread picks this up at the start of the next queueInput() call.
     */
    fun updateConfig(config: EqConfig) {
        pendingConfig.set(config)
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // Always output float — keeps format consistent regardless of EQ enabled state
        pendingActive = true
        toFloatPcmAudioProcessor.configure(inputAudioFormat)
        return AudioProcessor.AudioFormat(
            inputAudioFormat.sampleRate,
            inputAudioFormat.channelCount,
            C.ENCODING_PCM_FLOAT
        )
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        // Check for pending config from UI thread
        checkPendingConfig()

        val frameCount = inputBuffer.remaining() / inputAudioFormat.bytesPerFrame
        val outputBuffer = replaceOutputBuffer(frameCount * outputAudioFormat.bytesPerFrame)

        if (inputBuffer.hasRemaining()) {
            // Convert to float if needed
            val floatInput: ByteBuffer
            if (toFloatPcmAudioProcessor.isActive) {
                toFloatPcmAudioProcessor.queueInput(inputBuffer)
                floatInput = toFloatPcmAudioProcessor.output
            } else {
                floatInput = inputBuffer
            }

            val channelCount = outputAudioFormat.channelCount

            if (eqEnabled && filters.isNotEmpty()) {
                // Process through biquad cascade
                for (frame in 0 until frameCount) {
                    for (ch in 0 until channelCount) {
                        var sample = floatInput.getFloat()
                        sample *= preampLinear
                        for (i in filters.indices) {
                            if (bandConfigs[i].enabled) {
                                sample = filters[i].processSample(ch, sample)
                            }
                        }
                        outputBuffer.putFloat(sample)
                    }
                }
            } else {
                // Pass-through: just copy float samples unchanged
                outputBuffer.put(floatInput)
            }

            floatInput.position(floatInput.limit())
        }

        outputBuffer.flip()
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        toFloatPcmAudioProcessor.flush(streamMetadata)
        // Clear all filter state to prevent artifacts on seek/track change
        for (filter in filters) {
            filter.clearState()
        }
        // Apply any pending config
        checkPendingConfig()
    }

    override fun onReset() {
        toFloatPcmAudioProcessor.reset()
        filters = emptyArray()
        bandConfigs = emptyList()
        eqEnabled = false
        preampLinear = 1f
        currentSampleRate = 0
        currentChannelCount = 0
    }

    /**
     * Check for a pending config from the UI thread and apply it.
     * Called at the start of queueInput() on the audio thread.
     */
    private fun checkPendingConfig() {
        pendingConfig.getAndSet(null)?.let { config ->
            applyConfig(config)
        }
    }

    /**
     * Apply a new EQ configuration. Only called on the audio thread.
     */
    private fun applyConfig(config: EqConfig) {
        eqEnabled = config.enabled
        preampLinear = 10f.pow(config.preampDb / 20f)
        bandConfigs = config.bands

        val sampleRate = if (outputAudioFormat != AudioProcessor.AudioFormat.NOT_SET)
            outputAudioFormat.sampleRate else 44100
        val channelCount = if (outputAudioFormat != AudioProcessor.AudioFormat.NOT_SET)
            outputAudioFormat.channelCount else 2

        // Rebuild filters if band count changed or sample rate/channels changed
        if (filters.size != config.bands.size ||
            currentSampleRate != sampleRate ||
            currentChannelCount != channelCount
        ) {
            filters = Array(config.bands.size) { BiquadFilter(maxChannels = channelCount.coerceAtLeast(2)) }
            currentSampleRate = sampleRate
            currentChannelCount = channelCount
        }

        // Configure each filter with its band parameters
        for (i in config.bands.indices) {
            val band = config.bands[i]
            filters[i].configure(
                type = band.filterType.toBiquadType(),
                sampleRate = sampleRate,
                frequencyHz = band.frequencyHz,
                gainDb = band.gainDb,
                q = band.q
            )
        }

        Log.d(TAG, "Config applied: enabled=$eqEnabled, preamp=${config.preampDb}dB, " +
                "bands=${config.bands.size}, sampleRate=$sampleRate")
    }
}
