package com.patchouli.deepseekwidget.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.patchouli.deepseekwidget.R
import com.patchouli.deepseekwidget.util.Format
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

/**
 * Barre della spesa giornaliera. La modalita' compatta e' usata nella
 * card "Speso oggi" della Dashboard: nessuna label, nessun asse.
 */
class DailySpendChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var data: List<Pair<LocalDate, BigDecimal>> = emptyList()
    private var compact = false
    private var missingDays: Set<LocalDate> = emptySet()

    private val density = resources.displayMetrics.density
    private val scaled = min(resources.displayMetrics.scaledDensity, density * 1.3f)
    private val monoTypeface = ResourcesCompat.getFont(context, R.font.plex_mono_regular)
    private val sansTypeface = ResourcesCompat.getFont(context, R.font.plex_sans_regular)

    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ds_color_accent)
        style = Paint.Style.FILL
    }
    private val pastPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ds_color_bar_past)
        style = Paint.Style.FILL
    }
    private val stubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ds_color_border)
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ds_color_chart_grid)
        strokeWidth = 1f * density
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ds_color_text_tertiary)
        textSize = 9f * scaled
        typeface = monoTypeface
        textAlign = Paint.Align.CENTER
    }
    private val maxPaint = Paint(textPaint).apply {
        textSize = 10f * scaled
        color = ContextCompat.getColor(context, R.color.ds_color_text_secondary)
        textAlign = Paint.Align.LEFT
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ds_color_text_secondary)
        textSize = 13f * scaled
        typeface = sansTypeface
        textAlign = Paint.Align.CENTER
    }

    fun setData(items: List<Pair<LocalDate, BigDecimal>>) {
        data = items
        invalidate()
    }

    fun setCompact(value: Boolean) {
        compact = value
        invalidate()
    }

    /** Giorni senza bucket: nessuna barra (distinti dagli zeri espliciti). */
    fun setMissingDays(days: Set<LocalDate>) {
        missingDays = days
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) {
            canvas.drawText(
                context.getString(R.string.history_chart_no_spend),
                width / 2f,
                height / 2f,
                emptyPaint
            )
            return
        }
        if (width <= 0 || height <= 0) return

        if (compact) {
            drawCompact(canvas)
        } else {
            drawFull(canvas)
        }
    }

    private fun drawCompact(canvas: Canvas) {
        val gap = 3f * density
        val count = data.size
        val barWidth = ((width - gap * (count - 1)) / count).coerceAtLeast(1f)
        val maxValue = data.maxOf { it.second }.toFloat().coerceAtLeast(0f)
        val scale = if (maxValue > 0f) maxValue else 1f
        val top = 2f * density
        val bottom = height - 1f * density
        val today = LocalDate.now()

        data.forEachIndexed { index, (day, value) ->
            if (day in missingDays) return@forEachIndexed
            val x = index * (barWidth + gap)
            val h = if (value.signum() <= 0) 2f * density
            else ((value.toFloat() / scale) * (bottom - top)).coerceAtLeast(2f * density)
            val paint = when {
                value.signum() <= 0 -> stubPaint
                day == today -> accentPaint
                else -> pastPaint
            }
            canvas.drawRoundRect(
                RectF(x, bottom - h, x + barWidth, bottom),
                min(2f * density, barWidth / 3f),
                min(2f * density, barWidth / 3f),
                paint
            )
        }
    }

    private fun drawFull(canvas: Canvas) {
        val left = 2f * density
        val right = width - 2f * density
        val top = if (data.size <= 7) 20f * density else 4f * density
        val bottom = height - 18f * density
        if (right <= left || bottom <= top) return

        val maxValue = data.maxOf { it.second }.toFloat().coerceAtLeast(0f)
        val scale = if (maxValue > 0f) maxValue else 1f

        canvas.drawLine(left, bottom, right, bottom, gridPaint)

        val gap = if (data.size <= 7) 8f * density else 3f * density
        val available = (right - left) - gap * (data.size - 1)
        var barWidth = (available / data.size).coerceAtLeast(2f * density)
        if (data.size <= 7) barWidth = min(barWidth, 38f * density)
        val groupWidth = barWidth * data.size + gap * (data.size - 1)
        val start = left + max(0f, ((right - left) - groupWidth) / 2f)
        val today = LocalDate.now()
        var maxIndex = 0

        data.forEachIndexed { index, (day, value) ->
            if (value >= data[maxIndex].second) maxIndex = index
            if (day in missingDays) return@forEachIndexed
            val x = start + index * (barWidth + gap)
            val h = if (value.signum() <= 0) 2f * density
            else (value.toFloat() / scale * (bottom - top)).coerceAtLeast(2f * density)
            val paint = when {
                value.signum() <= 0 -> stubPaint
                day == today -> accentPaint
                else -> pastPaint
            }
            canvas.drawRoundRect(
                RectF(x, bottom - h, x + barWidth, bottom),
                min(3f * density, barWidth / 2f),
                min(3f * density, barWidth / 2f),
                paint
            )
        }

        if (data.size <= 7 && maxValue > 0f) {
            val x = start + maxIndex * (barWidth + gap)
            val label = Format.money(data[maxIndex].second)
            maxPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(
                label,
                x.coerceIn(left, right - maxPaint.measureText(label)),
                top - 5f * density,
                maxPaint
            )
        }

        val labelIndices = when {
            data.size <= 7 -> data.indices.toList()
            else -> listOf(0, data.lastIndex / 2, data.lastIndex).distinct()
        }
        labelIndices.forEach { index ->
            val day = data[index].first
            val x = start + index * (barWidth + gap) + barWidth / 2f
            val label = if (data.size <= 7) Format.dayOfMonth(day) else Format.dayLabel(day)
            textPaint.textAlign = Paint.Align.LEFT
            val widthLabel = textPaint.measureText(label)
            val labelX = x.coerceIn(left + widthLabel / 2f, right - widthLabel / 2f)
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(label, labelX, height - 5f * density, textPaint)
        }
    }
}
