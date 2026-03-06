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

package org.akanework.gramophone.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import org.akanework.gramophone.logic.utils.VisualizerProcessor

class VisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Mode { STRIP, AMBIENT }

    companion object {
        private const val BAND_COUNT = VisualizerProcessor.BAND_COUNT
        private const val SMOOTHING = 0.35f
        private const val BAR_GAP_RATIO = 0.3f
    }

    var mode = Mode.STRIP

    /** When true, onDraw pulls from VisualizerProcessor and re-schedules animation. */
    var isActive = false
        set(value) {
            field = value
            if (value) postInvalidateOnAnimation()
        }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Lerped display values — smoothly tracks target magnitudes from processor
    private val currentMagnitudes = FloatArray(BAND_COUNT)
    // Snapshot buffer — filled from processor each frame
    private val targetMagnitudes = FloatArray(BAND_COUNT)

    fun setBarColor(color: Int) {
        barPaint.color = color
        invalidate()
    }

    fun clear() {
        targetMagnitudes.fill(0f)
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Pull latest magnitudes from the processor (thread-safe copy)
        if (isActive) {
            VisualizerProcessor.instance?.getMagnitudes(targetMagnitudes)
        }

        // Lerp current toward target for smooth animation
        var anyActive = false
        for (i in 0 until BAND_COUNT) {
            currentMagnitudes[i] += (targetMagnitudes[i] - currentMagnitudes[i]) * SMOOTHING
            // Snap to zero when close enough to avoid infinite redraws
            if (currentMagnitudes[i] < 0.005f) currentMagnitudes[i] = 0f
            if (currentMagnitudes[i] > 0f || targetMagnitudes[i] > 0f) anyActive = true
        }

        // Calculate bar dimensions
        val barWidth = w / (BAND_COUNT + (BAND_COUNT - 1) * BAR_GAP_RATIO)
        val gapWidth = barWidth * BAR_GAP_RATIO

        for (i in 0 until BAND_COUNT) {
            val mag = currentMagnitudes[i]
            if (mag <= 0f) continue

            val left = i * (barWidth + gapWidth)
            val right = left + barWidth

            when (mode) {
                Mode.STRIP -> {
                    // Bars grow upward from bottom
                    val barHeight = mag * h
                    canvas.drawRect(left, h - barHeight, right, h, barPaint)
                }
                Mode.AMBIENT -> {
                    // Bars grow from center outward
                    val halfBarHeight = mag * h * 0.5f
                    val centerY = h * 0.5f
                    canvas.drawRect(left, centerY - halfBarHeight, right, centerY + halfBarHeight, barPaint)
                }
            }
        }

        // Continue animating if bars are still moving or we're actively capturing
        if (anyActive || isActive) {
            postInvalidateOnAnimation()
        }
    }
}
