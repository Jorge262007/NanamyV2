package com.nanamy.launcher.eyes

/**
 * Configuración geométrica de un ojo en un momento dado.
 * Portado de EyeConfig.h (esp32-eyes, playfultechnology).
 *
 * Todos los valores son en "unidades de diseño" (dp lógicos), no píxeles crudos.
 */
data class EyeConfig(
    var width: Float = 40f,
    var height: Float = 40f,
    // Pendiente/inclinación del borde superior e inferior.
    // Positivo = el borde baja hacia el centro de la cara (ojos "tristes" o "enojados" según combinación)
    var slopeTop: Float = 0f,
    var slopeBottom: Float = 0f,
    // Radio de las esquinas superior/inferior (0 = esquina recta, valor alto = muy redondeado/ovalado)
    var radiusTop: Float = 8f,
    var radiusBottom: Float = 8f,
    // Desplazamiento del centro del ojo respecto a su posición base (para "mirar" a un lado, o expresiones)
    var offsetX: Float = 0f,
    var offsetY: Float = 0f
) {
    fun copyOf(): EyeConfig = this.copy()
}
