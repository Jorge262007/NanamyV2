package com.nanamy.launcher.eyes

/**
 * Estados del "cerebro" (LLM) que la cara de Nanamy puede reflejar.
 */
enum class NanamyState {
    IDLE,       // esperando, sin actividad
    LISTENING,  // escuchando al usuario
    THINKING,   // esperando respuesta del LLM
    SPEAKING    // reproduciendo TTS
}

/**
 * Cada estado tiene una configuración base para AMBOS ojos.
 */
object EyePresets {

    fun forState(state: NanamyState): EyeConfig = when (state) {
        NanamyState.IDLE -> EyeConfig(
            width = 140f, height = 200f,
            slopeTop = 0f, slopeBottom = 0f,
            radiusTop = 24f, radiusBottom = 24f
        )
        NanamyState.LISTENING -> EyeConfig(
            width = 145f, height = 205f,
            slopeTop = 0f, slopeBottom = 0f,
            radiusTop = 26f, radiusBottom = 26f
        )
        NanamyState.THINKING -> EyeConfig(
            width = 140f, height = 100f,
            slopeTop = 0.15f, slopeBottom = -0.1f,
            radiusTop = 18f, radiusBottom = 18f
        )
        NanamyState.SPEAKING -> EyeConfig(
            width = 140f, height = 200f,
            slopeTop = 0f, slopeBottom = 0f,
            radiusTop = 24f, radiusBottom = 24f
        )
    }
}
