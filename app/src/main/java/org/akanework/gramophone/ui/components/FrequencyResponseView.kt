package org.akanework.gramophone.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.google.android.material.color.MaterialColors
import org.akanework.gramophone.logic.utils.BiquadFilter
import org.akanework.gramophone.logic.utils.EqConfig
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow

/**
 * Read-only custom View that draws the combined frequency response curve
 * for the parametric EQ. Log X axis (20Hz–20kHz), linear Y axis (-15 to +15 dB).
 * Grid lines at 0, ±5, ±10, ±15 dB and at 100, 1k, 10k Hz.
 */
class FrequencyResponseView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val MIN_FREQ = 20.0
        private const val MAX_FREQ = 20000.0
        private const val MIN_DB = -15.0
        private const val MAX_DB = 15.0
    }

    // Filters configured from the current EQ config
    private var filters: List<BiquadFilter> = emptyList()
    private var bandEnabled: List<Boolean> = emptyList()
    private var preampDb: Float = 0f

    private val curvePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val gridLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
    }

    private val zeroLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    init {
        val accentColor = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, 0xFF6200EE.toInt())
        curvePaint.color = accentColor
        fillPaint.color = (accentColor and 0x00FFFFFF) or 0x30000000 // 18% alpha
        val onSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFF000000.toInt())
        gridPaint.color = (onSurface and 0x00FFFFFF) or 0x20000000 // ~12% alpha
        gridLabelPaint.color = (onSurface and 0x00FFFFFF) or 0x60000000 // ~37% alpha
        zeroLinePaint.color = (onSurface and 0x00FFFFFF) or 0x40000000 // ~25% alpha
    }

    /**
     * Update the view with a new EQ configuration.
     * Creates internal BiquadFilter instances configured for display purposes.
     */
    fun updateConfig(config: EqConfig, sampleRate: Int = 48000) {
        preampDb = config.preampDb
        bandEnabled = config.bands.map { it.enabled }
        filters = config.bands.map { band ->
            BiquadFilter(maxChannels = 1).apply {
                configure(
                    type = band.filterType.toBiquadType(),
                    sampleRate = sampleRate,
                    frequencyHz = band.frequencyHz,
                    gainDb = band.gainDb,
                    q = band.q
                )
            }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        drawGrid(canvas, w, h)
        drawCurve(canvas, w, h)
    }

    private fun drawGrid(canvas: Canvas, w: Float, h: Float) {
        // Horizontal dB grid lines
        for (db in listOf(-15, -10, -5, 0, 5, 10, 15)) {
            val y = dbToY(db.toDouble(), h)
            val paint = if (db == 0) zeroLinePaint else gridPaint
            canvas.drawLine(0f, y, w, y, paint)
            // Label on the left
            val label = if (db > 0) "+$db" else "$db"
            canvas.drawText(label, 4f, y - 4f, gridLabelPaint)
        }

        // Vertical frequency grid lines
        for (freq in listOf(100.0, 1000.0, 10000.0)) {
            val x = freqToX(freq, w)
            canvas.drawLine(x, 0f, x, h, gridPaint)
            val label = if (freq >= 1000) "${(freq / 1000).toInt()}k" else "${freq.toInt()}"
            canvas.drawText(label, x + 4f, h - 4f, gridLabelPaint)
        }
    }

    private fun drawCurve(canvas: Canvas, w: Float, h: Float) {
        if (filters.isEmpty()) return

        val path = Path()
        val fillPath = Path()
        val zeroY = dbToY(0.0, h)
        val preampLinear = 10.0.pow(preampDb.toDouble() / 20.0)
        val widthInt = w.toInt()

        for (px in 0..widthInt) {
            val freq = xToFreq(px.toFloat(), w)
            var magnitude = preampLinear
            for (i in filters.indices) {
                if (bandEnabled.getOrElse(i) { true }) {
                    magnitude *= filters[i].magnitudeAt(freq)
                }
            }
            val db = 20.0 * log10(magnitude.coerceAtLeast(0.0001))
            val y = dbToY(db, h)

            if (px == 0) {
                path.moveTo(px.toFloat(), y)
                fillPath.moveTo(px.toFloat(), zeroY)
                fillPath.lineTo(px.toFloat(), y)
            } else {
                path.lineTo(px.toFloat(), y)
                fillPath.lineTo(px.toFloat(), y)
            }
        }

        fillPath.lineTo(w, zeroY)
        fillPath.close()

        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(path, curvePaint)
    }

    // Log-scale X axis: 20Hz → 20kHz
    private fun freqToX(freq: Double, w: Float): Float {
        return (ln(freq / MIN_FREQ) / ln(MAX_FREQ / MIN_FREQ) * w).toFloat()
    }

    private fun xToFreq(x: Float, w: Float): Double {
        return MIN_FREQ * (MAX_FREQ / MIN_FREQ).pow(x.toDouble() / w.toDouble())
    }

    // Linear Y axis: -15dB → +15dB (inverted: top = +15)
    private fun dbToY(db: Double, h: Float): Float {
        return ((MAX_DB - db) / (MAX_DB - MIN_DB) * h).toFloat()
    }
}
