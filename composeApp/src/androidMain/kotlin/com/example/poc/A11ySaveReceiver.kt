package com.example.poc

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver that handles the "Save" action from [NotificationHelper.showSavePrompt].
 *
 * Fired by the accessibility-service fallback when a login form submission is
 * detected and the autofill [onSaveRequest] path was not available (e.g. because
 * the OS unbound the autofill service before the user submitted the form).
 */
class A11ySaveReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PassKeyA11ySave"

        const val ACTION_SAVE    = "com.example.poc.A11Y_SAVE"
        const val EXTRA_SITE     = "site"
        const val EXTRA_USERNAME = "username"
        const val EXTRA_PASSWORD = "password"
        const val EXTRA_ORIGIN   = "origin"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SAVE) return

        val site     = intent.getStringExtra(EXTRA_SITE).orEmpty()
        val username = intent.getStringExtra(EXTRA_USERNAME).orEmpty().trim()
        val password = intent.getStringExtra(EXTRA_PASSWORD).orEmpty().trim()
        val origin   = intent.getStringExtra(EXTRA_ORIGIN).orEmpty().trim()

        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.i(TAG, " A11ySaveReceiver.onReceive site=$site user=$username passLen=${password.length} origin=$origin")
        PassKeyTrace.i("A11ySave", "onReceive site=$site user='$username' passLen=${password.length} origin=$origin")

        if (username.isBlank() || password.isBlank() || origin.isBlank()) {
            Log.w(TAG, "❌ Skipping save — blank credentials or origin. user='$username' passLen=${password.length} origin='$origin'")
            PassKeyTrace.w("A11ySave", "skipped — blank credentials/origin")
            return
        }

        PasswordRepository.init(context)
        val entry = PasswordEntry(
            id           = System.currentTimeMillis().toString(),
            siteName     = site.ifBlank { originDisplayName(origin) },
            username     = username,
            password     = password,
            loginUrl     = origin,
            dateModified = System.currentTimeMillis(),
        )

        PasswordRepository.saveFromAutofill(entry)
        Log.i(TAG, "✅ Saved ${entry.siteName} / ${entry.username}")
        PassKeyTrace.i("A11ySave", "SAVE SUCCESS site=${entry.siteName} user=${entry.username} origin=${entry.loginUrl}")

        // Dismiss the save-prompt notification and show the "saved" confirmation
        val nmgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nmgr.cancel(PassKeyApplication.NOTIF_SAVE_PROMPT)
        NotificationHelper.showSaved(context, entry.siteName, entry.username)
    }
}
