package com.nanamy.launcher.eyes

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View

class EyeFaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val leftLook = LookAssistant()
    private val blink = BlinkAssistant()

    private var currentState: NanamyState = NanamyState.IDLE
    private var currentConfig: EyeConfig = EyePresets.forState(NanamyState.IDLE)
    private var targetConfig: EyeConfig = currentConfig.copyOf()
    private var stateTransitionProgress = 1f

    var lookRangeX: Float = 220f
    var lookRangeY: Float = 220f
    var eyeSeparation: Float = 180f

    private val settingsRepository by lazy {
        (context.applicationContext as com.nanamy.launcher.NanamyApplication).settingsRepository
    }

    private val leftEyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val rightEyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var isAnimating = false
    private var lastFrameTimeNs: Long = 0L

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isAnimating) return
            val deltaMs = if (lastFrameTimeNs == 0L) {
                16L
            } else {
                ((frameTimeNanos - lastFrameTimeNs) / 1_000_000L).coerceIn(1L, 100L)
            }
            lastFrameTimeNs = frameTimeNanos
            
            val isSpeaking = currentState == NanamyState.SPEAKING
            if (isSpeaking) {
                leftLook.lookAtCenter()
            }
            
            leftLook.update(deltaMs, randomLook = !isSpeaking)
            blink.update(deltaMs, randomBlink = true)
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun startAnimating() {
        if (isAnimating) return
        isAnimating = true
        lastFrameTimeNs = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun stopAnimating() {
        isAnimating = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) startAnimating() else stopAnimating()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (visibility == VISIBLE) startAnimating()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimating()
    }

    fun setState(state: NanamyState) {
        if (state == currentState) return
        currentState = state
        targetConfig = EyePresets.forState(state)
        stateTransitionProgress = 0f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (stateTransitionProgress < 1f) {
            stateTransitionProgress = (stateTransitionProgress + 0.08f).coerceAtMost(1f)
            currentConfig = lerpConfig(currentConfig, targetConfig, 0.15f)
        } else {
            currentConfig = targetConfig
        }

        val centerX = width / 2f
        val centerY = height / 2f
        val blinkScale = 1f - blink.blinkAmount

        val lookX = leftLook.currentX
        val lookY = leftLook.currentY
        val boost = leftLook.curiousHeightBoost * 0.35f

        val dir = leftLook.currentDirection
        val leftGrows = dir == LookAssistant.Direction.NW || dir == LookAssistant.Direction.SW || dir == LookAssistant.Direction.W
        val rightGrows = dir == LookAssistant.Direction.NE || dir == LookAssistant.Direction.SE || dir == LookAssistant.Direction.E
        val isDiagonal = dir == LookAssistant.Direction.NE || dir == LookAssistant.Direction.NW ||
                dir == LookAssistant.Direction.SE || dir == LookAssistant.Direction.SW
        val isUpperDiagonal = dir == LookAssistant.Direction.NE || dir == LookAssistant.Direction.NW

        val baseHeight = currentConfig.height * blinkScale.coerceAtLeast(0.05f)
        val baseOffsetY = lookY * lookRangeY
        val grownHeight = baseHeight * (1f + boost)
        val heightDelta = grownHeight - baseHeight

        val leftConfig = currentConfig.copyOf().apply {
            offsetX = -eyeSeparation / 2f + lookX * lookRangeX
            height = baseHeight * if (leftGrows) (1f + boost) else 1f
            offsetY = if (!leftGrows && boost > 0.001f && isDiagonal) {
                if (isUpperDiagonal) baseOffsetY + heightDelta / 2f else baseOffsetY - heightDelta / 2f
            } else {
                baseOffsetY
            }
        }

        val rightConfig = currentConfig.copyOf().apply {
            offsetX = eyeSeparation / 2f + lookX * lookRangeX
            height = baseHeight * if (rightGrows) (1f + boost) else 1f
            offsetY = if (!rightGrows && boost > 0.001f && isDiagonal) {
                if (isUpperDiagonal) baseOffsetY + heightDelta / 2f else baseOffsetY - heightDelta / 2f
            } else {
                baseOffsetY
            }
        }

        val sharedColor = settingsRepository.eyesColor
        leftEyePaint.color = sharedColor
        rightEyePaint.color = sharedColor

        EyeDrawer.draw(canvas, centerX, centerY, leftConfig, leftEyePaint)
        EyeDrawer.draw(canvas, centerX, centerY, rightConfig, rightEyePaint)
    }

    private fun lerpConfig(from: EyeConfig, to: EyeConfig, t: Float): EyeConfig = EyeConfig(
        width = from.width + (to.width - from.width) * t,
        height = from.height + (to.height - from.height) * t,
        slopeTop = from.slopeTop + (to.slopeTop - from.slopeTop) * t,
        slopeBottom = from.slopeBottom + (to.slopeBottom - from.slopeBottom) * t,
        radiusTop = from.radiusTop + (to.radiusTop - from.radiusTop) * t,
        radiusBottom = from.radiusBottom + (to.radiusBottom - from.radiusBottom) * t
    )
}