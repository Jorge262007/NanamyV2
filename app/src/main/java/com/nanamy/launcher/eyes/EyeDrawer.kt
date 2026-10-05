package com.nanamy.launcher.eyes

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

/**
 * Dibuja un ojo sobre un Canvas de Android a partir de un EyeConfig.
 */
object EyeDrawer {

    /**
     * Dibuja un ojo centrado en (centerX, centerY) según los parámetros de config.
     */
    fun draw(canvas: Canvas, centerX: Float, centerY: Float, config: EyeConfig, paint: Paint) {
        val cx = centerX + config.offsetX
        val cy = centerY + config.offsetY

        val deltaYTop = config.height * config.slopeTop / 2f
        val deltaYBottom = config.height * config.slopeBottom / 2f

        val halfW = config.width / 2f
        val halfH = config.height / 2f

        var radiusTop = config.radiusTop
        var radiusBottom = config.radiusBottom
        val totalHeight = config.height + deltaYTop - deltaYBottom
        if (radiusTop > 0 && radiusBottom > 0 && totalHeight - 1 < radiusBottom + radiusTop) {
            val sum = radiusBottom + radiusTop
            val corrected = (totalHeight - 1) / sum
            radiusTop *= corrected
            radiusBottom *= corrected
        }
        radiusTop = max(0f, min(radiusTop, halfH))
        radiusBottom = max(0f, min(radiusBottom, halfH))
        
        val topLeftY = cy - halfH - deltaYTop
        val topRightY = cy - halfH + deltaYTop
        val bottomLeftY = cy + halfH - deltaYBottom
        val bottomRightY = cy + halfH + deltaYBottom

        val left = cx - halfW
        val right = cx + halfW

        val path = Path().apply {
            moveTo(left + radiusTop, topLeftY)
            lineTo(right - radiusTop, topRightY)
            quadTo(right, topRightY, right, topRightY + radiusTop)
            lineTo(right, bottomRightY - radiusBottom)
            quadTo(right, bottomRightY, right - radiusBottom, bottomRightY)
            lineTo(left + radiusBottom, bottomLeftY)
            quadTo(left, bottomLeftY, left, bottomLeftY - radiusBottom)
            lineTo(left, topLeftY + radiusTop)
            quadTo(left, topLeftY, left + radiusTop, topLeftY)
            close()
        }

        canvas.drawPath(path, paint)
    }

    fun drawSimpleOval(canvas: Canvas, centerX: Float, centerY: Float, config: EyeConfig, paint: Paint) {
        val cx = centerX + config.offsetX
        val cy = centerY + config.offsetY
        val rect = RectF(
            cx - config.width / 2f,
            cy - config.height / 2f,
            cx + config.width / 2f,
            cy + config.height / 2f
        )
        canvas.drawOval(rect, paint)
    }
}
