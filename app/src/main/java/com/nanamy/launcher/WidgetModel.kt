package com.nanamy.launcher

/**
 * Modelo para las páginas de widgets verticales.
 */
data class WidgetModel(
    val id: String,
    val title: String,
    val backgroundColor: Int,
    val type: WidgetType = WidgetType.GENERIC
)

enum class WidgetType {
    GENERIC, MUSIC, CALENDAR, NOTES, MESSAGES
}
