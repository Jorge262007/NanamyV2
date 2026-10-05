package com.nanamy.launcher.eyes

import kotlin.random.Random

/**
 * Maneja el parpadeo automático de los ojos.
 */
class BlinkAssistant {

    var blinkAmount: Float = 0f
        private set

    private var state: BlinkState = BlinkState.OPEN
    private var stateElapsedMs: Long = 0L
    private var nextBlinkInMs: Long = randomBlinkInterval()

    private enum class BlinkState { OPEN, CLOSING, CLOSED, OPENING }

    private fun randomBlinkInterval(): Long = Random.nextLong(2500L, 6000L)

    private companion object {
        const val CLOSING_DURATION_MS = 90L
        const val CLOSED_HOLD_MS = 60L
        const val OPENING_DURATION_MS = 110L
    }

    fun doBlink() {
        if (state == BlinkState.OPEN) {
            state = BlinkState.CLOSING
            stateElapsedMs = 0L
        }
    }

    fun update(deltaMs: Long, randomBlink: Boolean = true) {
        when (state) {
            BlinkState.OPEN -> {
                blinkAmount = 0f
                if (randomBlink) {
                    stateElapsedMs += deltaMs
                    if (stateElapsedMs >= nextBlinkInMs) {
                        stateElapsedMs = 0L
                        nextBlinkInMs = randomBlinkInterval()
                        state = BlinkState.CLOSING
                    }
                }
            }
            BlinkState.CLOSING -> {
                stateElapsedMs += deltaMs
                blinkAmount = (stateElapsedMs.toFloat() / CLOSING_DURATION_MS).coerceIn(0f, 1f)
                if (stateElapsedMs >= CLOSING_DURATION_MS) {
                    stateElapsedMs = 0L
                    state = BlinkState.CLOSED
                }
            }
            BlinkState.CLOSED -> {
                blinkAmount = 1f
                stateElapsedMs += deltaMs
                if (stateElapsedMs >= CLOSED_HOLD_MS) {
                    stateElapsedMs = 0L
                    state = BlinkState.OPENING
                }
            }
            BlinkState.OPENING -> {
                stateElapsedMs += deltaMs
                blinkAmount = 1f - (stateElapsedMs.toFloat() / OPENING_DURATION_MS).coerceIn(0f, 1f)
                if (stateElapsedMs >= OPENING_DURATION_MS) {
                    stateElapsedMs = 0L
                    state = BlinkState.OPEN
                }
            }
        }
    }
}
