package com.surendramaran.yolov8tflite

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Transparent overlay on top of the camera preview that draws [BoundingBox] results.
 *
 * Coordinates are **normalized** (0–1); they are scaled by this view’s width and height in [draw].
 * Known class names get friendly labels and color hints for “compliant” vs “violation” styles.
 */
class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var results = listOf<BoundingBox>()
    private var boxPaint = Paint()
    private var textBackgroundPaint = Paint()
    private var textPaint = Paint()

    private var bounds = Rect()

    init {
        initPaints()
    }

    /** Resets paints and clears the canvas state for a fresh drawing pass. */
    fun clear() {
        textPaint.reset()
        textBackgroundPaint.reset()
        boxPaint.reset()
        invalidate()
        initPaints()
    }

    private fun initPaints() {
        textBackgroundPaint.style = Paint.Style.FILL
        textBackgroundPaint.textSize = TEXT_SIZE
        textBackgroundPaint.isAntiAlias = true

        textPaint.color = Color.WHITE
        textPaint.style = Paint.Style.FILL
        textPaint.textSize = TEXT_SIZE
        textPaint.isAntiAlias = true
        textPaint.isFakeBoldText = true

        boxPaint.strokeWidth = BOX_STROKE_WIDTH
        boxPaint.style = Paint.Style.STROKE
        boxPaint.isAntiAlias = true
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        results.forEach {
            val left = it.x1 * width
            val top = it.y1 * height
            val right = it.x2 * width
            val bottom = it.y2 * height

            val isDangerous = it.clsName == "no-helmet" || it.clsName == "no-vest"
            val boxColor = if (isDangerous) Color.parseColor("#FF3B3B") else Color.BLACK
            boxPaint.color = boxColor

            // Draw rounded bounding box
            val boxRect = RectF(left, top, right, bottom)
            canvas.drawRoundRect(boxRect, CORNER_RADIUS, CORNER_RADIUS, boxPaint)

            // Format label text
            val labelText = when (it.clsName) {
                "helmet" -> "HELMET DETECTED"
                "vest" -> "VEST DETECTED"
                "no-helmet" -> "NO HELMET"
                "no-vest" -> "NO VEST"
                "person" -> "PERSON"
                else -> it.clsName.uppercase()
            }

            textBackgroundPaint.color = boxColor
            textPaint.getTextBounds(labelText, 0, labelText.length, bounds)
            val textWidth = bounds.width()
            val textHeight = bounds.height()

            val bgLeft = left
            val bgRight = left + textWidth + LABEL_PADDING_H * 2
            val bgBottom = top
            val bgTop = top - textHeight - LABEL_PADDING_V * 2

            val labelRect = RectF(bgLeft, bgTop, bgRight, bgBottom)
            canvas.drawRoundRect(labelRect, LABEL_CORNER_RADIUS, LABEL_CORNER_RADIUS, textBackgroundPaint)
            canvas.drawText(labelText, bgLeft + LABEL_PADDING_H, bgBottom - LABEL_PADDING_V, textPaint)
        }
    }

    /** Replaces the current detections and requests a redraw. */
    fun setResults(boundingBoxes: List<BoundingBox>) {
        results = boundingBoxes
        invalidate()
    }

    companion object {
        private const val BOX_STROKE_WIDTH = 6f
        private const val CORNER_RADIUS = 16f
        private const val TEXT_SIZE = 40f
        private const val LABEL_PADDING_H = 12f
        private const val LABEL_PADDING_V = 8f
        private const val LABEL_CORNER_RADIUS = 6f
    }
}
