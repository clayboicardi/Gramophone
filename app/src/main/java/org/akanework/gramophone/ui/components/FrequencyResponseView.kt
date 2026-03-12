package org.akanework.gramophone.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
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
 * Grid lines at 0, ±5, ±10, ±15 dB and at labeled frequencies.
 * Band dots indicate each band's position; selected band is highlighted.
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
    private var bandFreqs: List<Float> = emptyList()
    private var bandGains: List<Float> = emptyList()
    private var preampDb: Float = 0f
    private var selectedBandIndex: Int = -1

    private val accentColor: Int
    private val onSurfaceColor: Int

    private val curvePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val boostFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val cutFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val gridLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 22f
    }

    private val freqLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20f
    }

    private val zeroLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val selectedDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    init {
        accentColor = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, 0xFF6200EE.toInt())
        onSurfaceColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFF000000.toInt())

        curvePaint.color = accentColor
        boostFillPaint.color = (accentColor and 0x00FFFFFF) or 0x30000000 // 18% alpha
        cutFillPaint.color = (accentColor and 0x00FFFFFF) or 0x18000000 // ~10% alpha
        gridPaint.color = (onSurfaceColor and 0x00FFFFFF) or 0x20000000 // ~12% alpha
        gridLabelPaint.color = (onSurfaceColor and 0x00FFFFFF) or 0x60000000 // ~37% alpha
        freqLabelPaint.color = (onSurfaceColor and 0x00FFFFFF) or 0x60000000
        zeroLinePaint.color = (onSurfaceColor and 0x00FFFFFF) or 0x50000000 // ~31% alpha
        dotPaint.color = (onSurfaceColor and 0x00FFFFFF) or 0x50000000 // ~31% alpha
        selectedDotPaint.color = accentColor
    }

    /**
     * Update the view with a new EQ configuration.
     * Creates internal BiquadFilter instances configured for display purposes.
     */
    fun updateConfig(config: EqConfig, sampleRate: Int = 48000) {
        preampDb = config.preampDb
        bandEnabled = config.bands.map { it.enabled }
        bandFreqs = config.bands.map { it.frequencyHz }
        bandGains = config.bands.map { it.gainDb }
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

    /**
     * Set the selected band index for highlighting on the curve.
     */
    fun setSelectedBand(index: Int) {
        selectedBandIndex = index
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        drawGrid(canvas, w, h)
        drawCurve(canvas, w, h)
        drawBandDots(canvas, w, h)
    }

    private fun drawGrid(canvas: Canvas, w: Float, h: Float) {
        // Horizontal dB grid lines
        for (db in listOf(-15, -10, -5, 0, 5, 10, 15)) {
            val y = dbToY(db.toDouble(), h)
            val paint = if (db == 0) zeroLinePaint else gridPaint
            canvas.drawLine(0f, y, w, y, paint)
            // dB labels on the left
            val label = if (db > 0) "+$db" else "$db"
            canvas.drawText(label, 4f, y - 4f, gridLabelPaint)
        }

        // Vertical frequency grid lines + bottom labels
        val freqPoints = listOf(
            20.0 to "20",
            100.0 to "100",
            1000.0 to "1k",
            10000.0 to "10k",
            20000.0 to "20k"
        )
        for ((freq, label) in freqPoints) {
            val x = freqToX(freq, w)
            canvas.drawLine(x, 0f, x, h, gridPaint)
            // Small tick mark at bottom
            canvas.drawLine(x, h - 8f, x, h, zeroLinePaint)
            // Label centered below tick
            val textWidth = freqLabelPaint.measureText(label)
            val labelX = (x - textWidth / 2f).coerceAtLeast(0f).coerceAtMost(w - textWidth)
            canvas.drawText(label, labelX, h - 10f, freqLabelPaint)
        }
    }

    private fun drawCurve(canvas: Canvas, w: Float, h: Float) {
        if (filters.isEmpty()) return

        val curvePath = Path()
        val boostPath = Path()
        val cutPath = Path()
        val zeroY = dbToY(0.0, h)
        val preampLinear = 10.0.pow(preampDb.toDouble() / 20.0)
        val widthInt = w.toInt()

        // Collect all curve points for fill splitting
        val curveYs = FloatArray(widthInt + 1)

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
            curveYs[px] = y

            if (px == 0) {
                curvePath.moveTo(0f, y)
            } else {
                curvePath.lineTo(px.toFloat(), y)
            }
        }

        // Build separate fill paths for boost (above 0dB = below zeroY) and cut
        buildFillPath(boostPath, curveYs, zeroY, w, isBoost = true)
        buildFillPath(cutPath, curveYs, zeroY, w, isBoost = false)

        canvas.drawPath(boostPath, boostFillPaint)
        canvas.drawPath(cutPath, cutFillPaint)
        canvas.drawPath(curvePath, curvePaint)
    }

    /**
     * Build a fill path between the curve and the 0dB line, only for the
     * boost region (curve above 0dB = y < zeroY) or cut region (curve below 0dB = y > zeroY).
     */
    private fun buildFillPath(path: Path, curveYs: FloatArray, zeroY: Float, w: Float, isBoost: Boolean) {
        var inRegion = false
        for (px in curveYs.indices) {
            val y = curveYs[px]
            val inThisRegion = if (isBoost) y < zeroY else y > zeroY

            if (inThisRegion) {
                if (!inRegion) {
                    // Start new region
                    path.moveTo(px.toFloat(), zeroY)
                    path.lineTo(px.toFloat(), y)
                    inRegion = true
                } else {
                    path.lineTo(px.toFloat(), y)
                }
            } else if (inRegion) {
                // Close region
                path.lineTo(px.toFloat(), zeroY)
                path.close()
                inRegion = false
            }
        }
        if (inRegion) {
            path.lineTo(w, zeroY)
            path.close()
        }
    }

    private fun drawBandDots(canvas: Canvas, w: Float, h: Float) {
        if (bandFreqs.isEmpty()) return

        for (i in bandFreqs.indices) {
            if (!bandEnabled.getOrElse(i) { true }) continue

            val freq = bandFreqs[i].toDouble()
            val gain = bandGains.getOrElse(i) { 0f }.toDouble()

            // Compute the actual combined magnitude at this band's frequency
            // (including preamp and all other bands' contributions)
            val preampLinear = 10.0.pow(preampDb.toDouble() / 20.0)
            var magnitude = preampLinear
            for (j in filters.indices) {
                if (bandEnabled.getOrElse(j) { true }) {
                    magnitude *= filters[j].magnitudeAt(freq)
                }
            }
            val db = 20.0 * log10(magnitude.coerceAtLeast(0.0001))

            val x = freqToX(freq, w)
            val y = dbToY(db, h)

            val isSelected = i == selectedBandIndex
            val radius = if (isSelected) 12f else 6f
            val paint = if (isSelected) selectedDotPaint else dotPaint
            canvas.drawCircle(x, y, radius, paint)
        }
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
