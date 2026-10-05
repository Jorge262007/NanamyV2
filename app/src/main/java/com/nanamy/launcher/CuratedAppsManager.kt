package com.nanamy.launcher

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Gestiona la lista de aplicaciones elegidas por el usuario.
 */
class CuratedAppsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "curated_apps_prefs"
        private const val KEY_APPS_LIST = "curated_apps_list"
    }

    fun getCuratedPackages(): List<String> {
        val savedString = prefs.getString(KEY_APPS_LIST, "") ?: ""
        if (savedString.isEmpty()) return emptyList()
        return savedString.split(",").filter { it.isNotEmpty() }
    }

    fun addApp(packageName: String) {
        val current = getCuratedPackages().toMutableList()
        if (!current.contains(packageName)) {
            current.add(packageName)
            saveList(current)
        }
    }

    fun removeApp(packageName: String) {
        val current = getCuratedPackages().toMutableList()
        if (current.remove(packageName)) {
            saveList(current)
        }
    }

    private fun saveList(list: List<String>) {
        prefs.edit {
            putString(KEY_APPS_LIST, list.joinToString(","))
        }
    }
}
