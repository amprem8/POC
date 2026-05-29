package com.example.poc

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver that handles the "Fill" action on the fill-available notification.
 * Delegates actual field-filling to [PassKeyAccessibilityService].
 */
class A11yFillReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PassKeyA11yFill"
        const val ACTION_FILL    = "com.example.poc.A11Y_FILL"
        const val EXTRA_ENTRY_ID = "entry_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FILL) return

        val entryId = intent.getStringExtra(EXTRA_ENTRY_ID).orEmpty()
        Log.i(TAG, "A11yFillReceiver.onReceive entryId=$entryId")
        PassKeyTrace.i("A11yFill", "onReceive entryId=$entryId")

        if (entryId.isBlank()) {
            Log.w(TAG, "No entry id — ignoring fill request")
            return
        }

        PasswordRepository.init(context)
        val entry = PasswordRepository.getById(entryId)
        if (entry == null) {
            Log.e(TAG, "Entry not found for id=$entryId")
            PassKeyTrace.e("A11yFill", "entry not found for id=$entryId")
            return
        }

        Log.i(TAG, "Filling credentials for ${entry.siteName} / ${entry.username}")
        PassKeyTrace.i("A11yFill", "filling site=${entry.siteName} user=${entry.username}")

        val filled = PassKeyAccessibilityService.fillCredentials(entry)
        if (!filled) {
            Log.w(TAG, "Fill returned false — accessibility service may not be running or no login form visible")
            PassKeyTrace.w("A11yFill", "fill=false site=${entry.siteName}")
        }

        val nmgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nmgr.cancel(PassKeyApplication.NOTIF_FILL)
    }
}