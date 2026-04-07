package com.mapp.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Red reference **dot** (distance ray origin) and vertical **line** (alignment guide).
 * Use [selectionMode] to choose which handle to drag; both can be repositioned on screen.
 */
class ReferenceOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    enum class SelectionMode {
        /** Touches do not drag; dot still defines ray if [referenceListener] is wired. */
        NONE,

        /** Drag the red dot; highlights when active. */
        DOT,

        /** Drag the vertical line horizontally; highlights when active. */
        LINE,
    }

    var selectionMode: SelectionMode = SelectionMode.NONE
        set(value) {
            field = value
            invalidate()
        }

    /** Notified when the dot moves (pixels, view coordinates, top-left origin). */
    var referenceListener: ReferenceListener? = null

    interface ReferenceListener {
        fun onReferenceDotMoved(xPx: Float, yPx: Float)
    }

    private val density = resources.displayMetrics.density

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF0000.toInt()
        style = Paint.Style.FILL
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF0000.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
    }

    private val selectionRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF7043.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
    }

    private val dotRadius = 8f * density
    private val hitSlop = 28f * density

    /** Dot center in pixels. */
    var dotX: Float = 0f
        private set
    var dotY: Float = 0f
        private set

    /** Vertical line x position in pixels. */
    var lineX: Float = 0f
        private set

    private var draggingDot = false
    private var draggingLine = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        if (dotX == 0f && dotY == 0f) {
            dotX = w * 0.5f
            dotY = h * 0.5f
            lineX = w * 0.5f
            notifyDot()
        } else if (lineX == 0f) {
            lineX = w * 0.5f
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val lx = lineX.coerceIn(0f, width.toFloat())
        canvas.drawLine(lx, 0f, lx, height.toFloat(), linePaint)

        if (selectionMode == SelectionMode.LINE) {
            canvas.drawLine(lx, 0f, lx, height.toFloat(), selectionRingPaint)
        }
        if (selectionMode == SelectionMode.DOT) {
            canvas.drawCircle(dotX, dotY, dotRadius + 6f * density, selectionRingPaint)
        }

        canvas.drawCircle(dotX, dotY, dotRadius, dotPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (selectionMode == SelectionMode.NONE) {
            return false
        }

        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                when (selectionMode) {
                    SelectionMode.DOT -> {
                        if (hitDot(x, y)) {
                            draggingDot = true
                            parent?.requestDisallowInterceptTouchEvent(true)
                            moveDot(x, y)
                            return true
                        }
                    }
                    SelectionMode.LINE -> {
                        if (hitLine(x)) {
                            draggingLine = true
                            parent?.requestDisallowInterceptTouchEvent(true)
                            moveLine(x)
                            return true
                        }
                    }
                    SelectionMode.NONE -> {}
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggingDot) {
                    moveDot(x, y)
                    return true
                }
                if (draggingLine) {
                    moveLine(x)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                draggingDot = false
                draggingLine = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return false
    }

    private fun hitDot(x: Float, y: Float): Boolean {
        return hypot((x - dotX).toDouble(), (y - dotY).toDouble()) <= (dotRadius + hitSlop)
    }

    private fun hitLine(x: Float): Boolean {
        return abs(x - lineX) <= hitSlop
    }

    private fun moveDot(x: Float, y: Float) {
        dotX = x.coerceIn(0f, width.toFloat())
        dotY = y.coerceIn(0f, height.toFloat())
        invalidate()
        notifyDot()
    }

    private fun moveLine(x: Float) {
        lineX = x.coerceIn(0f, width.toFloat())
        invalidate()
    }

    private fun notifyDot() {
        referenceListener?.onReferenceDotMoved(dotX, dotY)
    }

    /** Center dot and line without firing listener (e.g. layout init). */
    fun resetToCenter() {
        if (width <= 0) return
        dotX = width * 0.5f
        dotY = height * 0.5f
        lineX = width * 0.5f
        invalidate()
        notifyDot()
    }
}
