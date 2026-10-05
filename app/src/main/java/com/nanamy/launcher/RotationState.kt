package com.nanamy.launcher

import android.util.Log
import kotlin.math.abs

/**
 * Robust cumulative rotation state.
 * Range: -3 to 3. (4 and -4 reset to 0)
 * Left (L2) subtracts, Right (R2) adds.
 */
object RotationState {
    var rotationIndex = 0

    // Descriptive aliases
    const val NORMAL = 0
    const val ROTATION_LEFT_90 = -1
    const val ROTATION_RIGHT_270 = 3
    const val UPSIDE_DOWN_180_LEFT = -2
    const val UPSIDE_DOWN_180_RIGHT = 2
    const val ROTATION_RIGHT_90 = 1
    const val ROTATION_LEFT_270 = -3

    val currentRotation: Float
        get() = rotationIndex * 90f

    /**
     * Updates index based on delta (+1 or -1) with reset logic at 4/-4
     */
    fun updateIndex(delta: Int) {
        val next = rotationIndex + delta
        rotationIndex = if (abs(next) >= 4) 0 else next
        Log.d("NanamyRotation", "Updated Rotation State: $rotationIndex (Delta: $delta, Angle: $currentRotation)")
    }
}
