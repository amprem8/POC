package com.example.poc

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Central helper for firing PassKey notifications.
 * Used by AutofillService, CredentialSaveActivity, AccessibilityService
 * so all paths produce consistent notifications.
 */
object NotificationHelper {

    /** "Password saved" confirmation notification — tapping opens PassKey vault. */
    fun showSaved(context: Context, siteName: String, username: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pi = PendingIntent.getActivity(
            context, PassKeyApplication.NOTIF_SAVED, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(context, PassKeyApplication.CHANNEL_SAVED)
            .setSmallIcon(R.drawable.passkey_logo)
            .setContentTitle("Password saved to PassKey")
            .setContentText("$siteName · $username")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(Color.parseColor("#111827"))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        mgr(context).notify(PassKeyApplication.NOTIF_SAVED, notif)
    }

    /**
     * "PassKey can fill this page" notification — used as fallback when overlay is blocked.
     * High-priority so it appears as a heads-up banner over the browser.
     */
    fun showFillAvailable(context: Context, siteName: String, entryId: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("fill_entry_id", entryId)
        }
        val pi = PendingIntent.getActivity(
            context, PassKeyApplication.NOTIF_FILL, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(context, PassKeyApplication.CHANNEL_FILL)
            .setSmallIcon(R.drawable.passkey_logo)
            .setContentTitle("PassKey: saved password available")
            .setContentText("Tap to fill $siteName credentials")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        mgr(context).notify(PassKeyApplication.NOTIF_FILL, notif)
    }

    private fun mgr(ctx: Context) = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
}

