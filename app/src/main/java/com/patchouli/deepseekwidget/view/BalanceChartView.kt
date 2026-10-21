package com.patchouli.deepseekwidget.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.patchouli.deepseekwidget.R
import com.patchouli.deepseekwidget.util.Format
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Grafico a linea del saldo: ultimo valore per giorno, assi "nice",
 * griglia orizzontale e gestione esplicita di serie vuota o con un punto.
 */
class BalanceChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var points: List<Pair<Long, BigDecimal>> = emptyList()
    private var reveal = 1f
    private var animator: ValueAnimator? = null

    private val density = resources.displayMetrics.density
    private val scaled = min(resources.displayMetrics.scaledDensity, density * 1.3f)
    private val zone = ZoneId.systemDefault()

    private val monoTypeface = ResourcesCompat.getFont(context, R.font.plex_mono_regular)
    private val sansTypeface = ResourcesCompat.getFont(context, R.font.plex_sans_regular)

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ds_color_text_tertiary)
        textSize = 10f * scaled
        typeface = monoTypeface
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ds_color_text_secondary)
        textSize = 13f * scaled
        typeface = sansTypeface
        textAlign = Paint.Align.CENTER
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ds_color_chart_grid)
        strokeWidth = 1f * density
        style = Paint.Style.STROKE
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ds_color_accent)
        strokeWidth = 2f * density
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ds_color_accent)
        style = Paint.Style.FILL
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ds_color_surface)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ds_color_chart_grid)
        strokeWidth = 1f * density
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(4f * density, 4f * density), 0f)
    }

    fun setData(data: List<Pair<Long, BigDecimal>>) {
        val sorted = data.sortedBy { it.first }
        if (sorted == points) {
            invalidate()
            return
        }
        points = sorted
        animator?.cancel()
        reveal = 1f
        if (points.size >= 2 && isAttachedToWindow) {
            startReveal()
        }
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (points.size >= 2 && reveal < 1f) startReveal()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    private fun startReveal() {
        val durationScale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        if (durationScale == 0f) {
            reveal = 1f
            invalidate()
            return
        }
        reveal = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320L
            addUpdateListener {
                reveal = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.isEmpty()) {
            canvas.drawText(
                context.getString(R.string.history_chart_no_data),
                width / 2f,
                height / 2f,
                emptyPaint
            )
            return
        }

        val top = 12f * density
        val bottom = height - 22f * density
        val right = width - 6f * density
        if (bottom <= top || right <= 0f) return

        val values = points.map { it.second.toDouble() }
        var minValue = values.min()
        var maxValue = values.max()
        if (maxValue - minValue < 0.01) {
            val pad = max(0.5, abs(maxValue) * 0.05)
            minValue -= pad
            maxValue += pad
        }
        val rawStep = (maxValue - minValue) / 3.0
        if (rawStep <= 0.0) return
        val step = niceStep(rawStep)
        val tickMin = floor(minValue / step) * step
        val tickMax = ceil(maxValue / step) * step
        val ticks = buildList {
            var tick = tickMin
            var guard = 0
            while (tick <= tickMax + step * 0.01 && guard < 8) {
                add(tick)
                tick += step
                guard++
            }
        }
        if (ticks.size < 2) return

        labelPaint.textAlign = Paint.Align.RIGHT
        val widest = ticks.maxOf { labelPaint.measureText(Format.axisMoney(it)) }
        val gutter = (widest + 8f * density).coerceIn(40f * density, 64f * density)
        val left = gutter
        if (right - left < 40f * density) return

        fun yFor(value: Double): Float {
            val fraction = ((value - tickMin) / (tickMax - tickMin)).toFloat()
            return bottom - fraction * (bottom - top)
        }

        val labelOffset = (labelPaint.ascent() + labelPaint.descent()) / 2f
        ticks.forEach { tick ->
            val y = yFor(tick)
            canvas.drawLine(left, y, right, y, gridPaint)
            canvas.drawText(
                Format.axisMoney(tick),
                left - 6f * density,
                y - labelOffset,
                labelPaint
            )
        }

        val centerIndex = points.lastIndex / 2
        val xAt = { index: Int ->
            if (points.size == 1) right - 4f * density
            else left + index.toFloat() / points.lastIndex.toFloat() * (right - left)
        }

        if (points.size == 1) {
            val value = points.first().second.toDouble()
            val y = yFor(value)
            canvas.drawLine(left, y, right, y, guidePaint)
            drawLastPoint(canvas, xAt(0), y)
            drawXLabel(canvas, points.first().first, xAt(0), left, right)
            return
        }

        val linePath = Path()
        val areaPath = Path()
        points.forEachIndexed { index, (_, value) ->
            val x = xAt(index)
            val y = yFor(value.toDouble())
            if (index == 0) {
                linePath.moveTo(x, y)
                areaPath.moveTo(x, bottom)
                areaPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                areaPath.lineTo(x, y)
            }
        }
        areaPath.lineTo(xAt(points.lastIndex), bottom)
        areaPath.close()

        fillPaint.shader = LinearGradient(
            0f,
            top,
            0f,
            bottom,
            ContextCompat.getColor(context, R.color.ds_color_chart_fill),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )

        canvas.save()
        canvas.clipRect(left, top - 2f * density, left + (right - left) * reveal, bottom + 2f * density)
        canvas.drawPath(areaPath, fillPaint)
        canvas.drawPath(linePath, linePaint)
        canvas.restore()

        if (reveal >= 1f) {
            drawLastPoint(
                canvas,
                xAt(points.lastIndex),
                yFor(points.last().second.toDouble())
            )
        }

        labelPaint.textAlign = Paint.Align.LEFT
        val firstLabel = dateLabel(points.first().first)
        val lastLabel = dateLabel(points.last().first)
        val firstWidth = labelPaint.measureText(firstLabel)
        val lastWidth = labelPaint.measureText(lastLabel)
        if (points.size > 2 && right - left > 220f * density) {
            val midX = (left + right) / 2f
            val midLabel = dateLabel(points[centerIndex].first)
            val midWidth = labelPaint.measureText(midLabel)
            if (midX - midWidth / 2f > left + firstWidth + 4f * density &&
                midX + midWidth / 2f < right - lastWidth - 4f * density
            ) {
                labelPaint.textAlign = Paint.Align.CENTER
                canvas.drawText(midLabel, midX, height - 6f * density, labelPaint)
            }
        }
        labelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(firstLabel, left, height - 6f * density, labelPaint)
        labelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(lastLabel, right, height - 6f * density, labelPaint)
    }

    private fun drawLastPoint(canvas: Canvas, x: Float, y: Float) {
        canvas.drawCircle(x, y, 4.25f * density, ringPaint)
        canvas.drawCircle(x, y, 3.5f * density, dotPaint)
    }

    private fun drawXLabel(canvas: Canvas, ts: Long, x: Float, left: Float, right: Float) {
        val label = dateLabel(ts)
        labelPaint.textAlign = Paint.Align.CENTER
        val half = labelPaint.measureText(label) / 2f
        val clamped = x.coerceIn(left + half, right - half)
        canvas.drawText(label, clamped, height - 6f * density, labelPaint)
    }

    private fun dateLabel(ts: Long): String {
        val day: LocalDate = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
        return Format.dayLabel(day)
    }

    private fun niceStep(raw: Double): Double {
        if (raw <= 0.0) return 1.0
        val exponent = floor(kotlin.math.log10(raw))
        val fraction = raw / Math.pow(10.0, exponent)
        val nice = when {
            fraction <= 1.0 -> 1.0
            fraction <= 2.0 -> 2.0
            fraction <= 5.0 -> 5.0
            else -> 10.0
        }
        return nice * Math.pow(10.0, exponent)
    }
}
