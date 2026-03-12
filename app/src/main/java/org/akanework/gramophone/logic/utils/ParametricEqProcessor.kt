package org.akanework.gramophone.logic.utils

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.Log
import androidx.media3.exoplayer.audio.ToFloatPcmAudioProcessor
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

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
    private var currentConfig: EqConfig? = null

    // Crossfade state for smooth preset transitions
    private var oldFilters: Array<BiquadFilter> = emptyArray()
    private var oldBandConfigs: List<BandConfig> = emptyList()
    private var oldPreampLinear = 1f
    private var crossfadeFramesRemaining = 0
    private var crossfadeTotalFrames = 0
    private var isCrossfading = false

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
        val newSampleRate = inputAudioFormat.sampleRate
        val newChannelCount = inputAudioFormat.channelCount
        val sampleRateChanged = newSampleRate != currentSampleRate
        val channelCountChanged = newChannelCount != currentChannelCount

        toFloatPcmAudioProcessor.configure(inputAudioFormat)

        // Recalculate coefficients if sample rate or channel count changed
        if ((sampleRateChanged || channelCountChanged) && currentConfig != null) {
            currentSampleRate = newSampleRate
            currentChannelCount = newChannelCount
            // Force rebuild filters for new format
            filters = emptyArray()
            applyConfig(currentConfig!!)
        }

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
                if (isCrossfading && crossfadeFramesRemaining > 0) {
                    // Crossfade: process through both old and new filter chains
                    for (frame in 0 until frameCount) {
                        for (ch in 0 until channelCount) {
                            val inputSample = floatInput.getFloat()

                            // Old chain
                            var oldSample = inputSample * oldPreampLinear
                            for (i in oldFilters.indices) {
                                if (i < oldBandConfigs.size && oldBandConfigs[i].enabled) {
                                    oldSample = oldFilters[i].processSample(ch, oldSample)
                                }
                            }

                            // New chain
                            var newSample = inputSample * preampLinear
                            for (i in filters.indices) {
                                if (bandConfigs[i].enabled) {
                                    newSample = filters[i].processSample(ch, newSample)
                                }
                            }

                            // Cos/sin crossfade for energy preservation
                            val t = 1.0f - (crossfadeFramesRemaining.toFloat() / crossfadeTotalFrames)
                            val fadeIn = sin(t * Math.PI.toFloat() / 2f)
                            val fadeOut = cos(t * Math.PI.toFloat() / 2f)
                            outputBuffer.putFloat(oldSample * fadeOut + newSample * fadeIn)

                            if (ch == channelCount - 1) crossfadeFramesRemaining--
                        }
                        if (crossfadeFramesRemaining <= 0) {
                            isCrossfading = false
                            for (f in oldFilters) f.clearState()
                            // Process remaining frames normally
                            break
                        }
                    }
                    // If crossfade ended mid-buffer, process remaining frames normally
                    while (floatInput.hasRemaining() && outputBuffer.hasRemaining()) {
                        val inputSample = floatInput.getFloat()
                        var sample = inputSample * preampLinear
                        for (i in filters.indices) {
                            if (bandConfigs[i].enabled) {
                                sample = filters[i].processSample(
                                    (outputBuffer.position() / 4) % channelCount, sample
                                )
                            }
                        }
                        outputBuffer.putFloat(sample)
                    }
                } else {
                    // Normal processing through biquad cascade
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
        oldFilters = emptyArray()
        bandConfigs = emptyList()
        oldBandConfigs = emptyList()
        eqEnabled = false
        preampLinear = 1f
        oldPreampLinear = 1f
        currentSampleRate = 0
        currentChannelCount = 0
        currentConfig = null
        isCrossfading = false
        crossfadeFramesRemaining = 0
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
     * Uses crossfade when many bands change at once (preset switch) to prevent clicks.
     */
    private fun applyConfig(config: EqConfig) {
        val oldConfig = currentConfig
        currentConfig = config
        eqEnabled = config.enabled

        val sampleRate = if (outputAudioFormat != AudioProcessor.AudioFormat.NOT_SET)
            outputAudioFormat.sampleRate else 44100
        val channelCount = if (outputAudioFormat != AudioProcessor.AudioFormat.NOT_SET)
            outputAudioFormat.channelCount else 2
        val maxCh = channelCount.coerceAtLeast(8)

        // Count how many bands changed (to decide if crossfade is needed)
        val changedBandCount = if (oldConfig != null) {
            config.bands.indices.count { i ->
                i < oldConfig.bands.size && config.bands[i].gainDb != oldConfig.bands[i].gainDb
            }
        } else 0

        val needsCrossfade = changedBandCount > 3 && oldConfig != null && filters.isNotEmpty()

        if (needsCrossfade) {
            // Save old filter state for crossfade
            oldPreampLinear = preampLinear
            oldBandConfigs = bandConfigs
            if (oldFilters.size != filters.size) {
                oldFilters = Array(filters.size) { BiquadFilter(maxChannels = maxCh) }
            }
            for (i in filters.indices) {
                oldFilters[i].copyFrom(filters[i])
            }
        }

        preampLinear = 10f.pow(config.preampDb / 20f)
        bandConfigs = config.bands

        // Rebuild filters if band count changed or sample rate/channels changed
        if (filters.size != config.bands.size ||
            currentSampleRate != sampleRate ||
            currentChannelCount != channelCount
        ) {
            filters = Array(config.bands.size) { BiquadFilter(maxChannels = maxCh) }
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

        if (needsCrossfade) {
            // Clear state on new filters (start fresh for crossfade)
            for (f in filters) f.clearState()
            // ~10ms crossfade at current sample rate
            crossfadeTotalFrames = (sampleRate * 0.01).toInt().coerceIn(128, 2048)
            crossfadeFramesRemaining = crossfadeTotalFrames
            isCrossfading = true
        } else {
            isCrossfading = false
        }

        Log.d(TAG, "Config applied: enabled=$eqEnabled, preamp=${config.preampDb}dB, " +
                "bands=${config.bands.size}, sampleRate=$sampleRate, crossfade=$needsCrossfade")
    }
}
