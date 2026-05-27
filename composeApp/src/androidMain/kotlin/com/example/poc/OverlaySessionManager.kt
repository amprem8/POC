package com.example.poc

import android.content.Context
import androidx.core.content.edit

object OverlaySessionManager {

    private const val PREF = "passkey_prefs"
    private const val KEY = "overlay_enabled_after_login"

    fun enable(context: Context) {
        PassKeyTrace.i("OverlaySession", "enable() package=${context.packageName}")
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit {
                putBoolean(KEY, true)
            }
    }

    fun disable(context: Context) {
        PassKeyTrace.i("OverlaySession", "disable() package=${context.packageName}")
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit {
                putBoolean(KEY, false)
            }
    }

    fun isEnabled(context: Context): Boolean {
        val enabled = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean(KEY, false)
        PassKeyTrace.d("OverlaySession", "isEnabled() -> $enabled")
        return enabled
    }
}