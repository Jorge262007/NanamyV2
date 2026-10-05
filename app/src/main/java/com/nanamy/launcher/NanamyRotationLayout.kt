package com.nanamy.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * Optimized container for 1:1 screens.
 * Uses static touch profiles for each rotation index (-3 to 3).
 */
class NanamyRotationLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val touchMatrix = Matrix()
    private val invertMatrix = Matrix()

    override fun dispatchDraw(canvas: Canvas) {
        val angle = RotationState.currentRotation
        if (angle == 0f) {
            super.dispatchDraw(canvas)
            return
        }

        canvas.save()
        val cx = width / 2f
        val cy = height / 2f
        canvas.rotate(angle, cx, cy)
        super.dispatchDraw(canvas)
        canvas.restore()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val index = RotationState.rotationIndex
        if (index == 0) return super.dispatchTouchEvent(ev)

        val cx = width / 2f
        val cy = height / 2f

        // Touch Profile: Mapping indices to touch transformation angles
        // Visual angle = index * 90. Touch angle = -(index * 90)
        val touchAngle = when (index) {
            -1, 3 -> 90f   // Left 90 / Right 270 (Visual -90, Touch +90)
            -2, 2 -> 180f  // Upside Down (Visual 180, Touch 180)
            -3, 1 -> -90f  // Left 270 / Right 90 (Visual 90, Touch -90)
            else -> 0f
        }

        // 1. Transform touch to match visual rotation
        touchMatrix.setRotate(touchAngle, cx, cy)
        ev.transform(touchMatrix)

        // 2. Dispatch to fragments/widgets
        val handled = super.dispatchTouchEvent(ev)

        // 3. Restore event coordinates for parent consistency
        invertMatrix.setRotate(-touchAngle, cx, cy)
        ev.transform(invertMatrix)

        return handled
    }
}
