package com.nanamy.launcher

import android.graphics.drawable.Drawable

/**
 * Representa un ítem en el grid de accesos rápidos.
 */
data class AppModel(
    val label: String,
    val packageName: String,
    val icon: Drawable?,
    val type: AppItemType = AppItemType.USER_APP
)

enum class AppItemType {
    SYSTEM_SETTINGS,
    NANAMY_SETTINGS,
    NANAMY_OS,
    USER_APP,
    ADD_BUTTON
}
