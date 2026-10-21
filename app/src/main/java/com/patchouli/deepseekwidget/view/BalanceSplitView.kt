package com.patchouli.deepseekwidget.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.patchouli.deepseekwidget.R
import java.math.BigDecimal

/**
 * Barra di ripartizione tra credito omaggio e ricariche.
 * Il segmento non nullo ha sempre almeno 3 dp per restare visibile.
 */
class BalanceSplitView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var granted: BigDecimal = BigDecimal.ZERO
    private var topped: BigDecimal = BigDecimal.ZERO
    private val density = resources.displayMetrics.density
    private val radius = 3f * density
    private val gap = 2f * density
    private val minSegment = 3f * density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ds_color_border)
        style = Paint.Style.FILL
    }
    private val grantedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ds_color_accent)
        style = Paint.Style.FILL
    }
    private val toppedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ds_color_data_muted)
        style = Paint.Style.FILL
    }

    fun setValues(granted: BigDecimal, topped: BigDecimal) {
        this.granted = granted.max(BigDecimal.ZERO)
        this.topped = topped.max(BigDecimal.ZERO)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, radius, radius, trackPaint)

        val total = granted.add(topped)
        if (total.signum() <= 0) return

        val available = width.toFloat()
        val both = granted.signum() > 0 && topped.signum() > 0
        val usable = if (both) (available - gap).coerceAtLeast(0f) else available

        var grantedWidth = usable * granted.toFloat() / total.toFloat()
        var toppedWidth = usable - grantedWidth
        if (both) {
            if (grantedWidth < minSegment) {
                grantedWidth = minSegment
                toppedWidth = usable - grantedWidth
            }
            if (toppedWidth < minSegment) {
                toppedWidth = minSegment
                grantedWidth = usable - toppedWidth
            }
        }

        if (grantedWidth > 0f) {
            val left = 0f
            val right = left + grantedWidth
            canvas.drawRoundRect(
                RectF(left, 0f, right, height.toFloat()),
                radius,
                radius,
                grantedPaint
            )
        }
        if (toppedWidth > 0f) {
            val left = if (both) grantedWidth + gap else 0f
            val right = left + toppedWidth
            canvas.drawRoundRect(
                RectF(left, 0f, right, height.toFloat()),
                radius,
                radius,
                toppedPaint
            )
        }
    }
}
