package com.nanamy.launcher

import android.content.Context
import android.util.Log
import android.widget.Toast

/**
 * Safe Toast helper that catches SystemUI asset path exceptions
 * caused when Android Studio reinstalls/deploys the app while Toasts are rendered.
 */
object SafeToast {

    private const val TAG = "SafeToast"

    fun show(context: Context?, text: String, duration: Int = Toast.LENGTH_SHORT) {
        if (context == null) return
        try {
            Toast.makeText(context.applicationContext, text, duration).show()
        } catch (e: Exception) {
            Log.w(TAG, "SystemUI Toast render suppressed: ${e.message}")
        }
    }
}
