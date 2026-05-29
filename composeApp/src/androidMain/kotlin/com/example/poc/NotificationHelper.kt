package com.example.poc

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Central helper for firing PassKey notifications.
 * Used by AutofillService and AccessibilityService
 * so all paths produce consistent notifications.
 */
object NotificationHelper {

    private const val TAG = "PassKeyNotifHelper"

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
     * "PassKey can fill this page" notification — optional non-blocking suggestion UI.
     * High-priority so it appears as a heads-up banner over the current app/browser.
     * The "Fill" action directly fills the credentials via the accessibility service.
     */
    fun showFillAvailable(context: Context, siteName: String, entryId: String) {
        Log.i(TAG, "showFillAvailable siteName=$siteName entryId=$entryId")
        PassKeyTrace.i("NotifHelper", "showFillAvailable siteName=$siteName entryId=$entryId")

        val fillIntent = Intent(context, A11yFillReceiver::class.java).apply {
            action = A11yFillReceiver.ACTION_FILL
            putExtra(A11yFillReceiver.EXTRA_ENTRY_ID, entryId)
        }
        val fillPi = PendingIntent.getBroadcast(
            context, PassKeyApplication.NOTIF_FILL, fillIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("fill_entry_id", entryId)
        }
        val openPi = PendingIntent.getActivity(
            context, PassKeyApplication.NOTIF_FILL + 1, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notif = NotificationCompat.Builder(context, PassKeyApplication.CHANNEL_FILL)
            .setSmallIcon(R.drawable.passkey_logo)
            .setContentTitle("PassKey: saved password available")
            .setContentText("Tap 'Fill' to autofill $siteName credentials")
            .setAutoCancel(true)
            .setContentIntent(openPi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "Fill", fillPi)
            .build()

        mgr(context).notify(PassKeyApplication.NOTIF_FILL, notif)
    }

    /**
     * "Save password to PassKey?" prompt — fired by the accessibility service after a
     * successful login is detected (page navigated away from a login form).
     * The notification has a "Save" action button that fires [A11ySaveReceiver].
     */
    fun showSavePrompt(context: Context, siteName: String, username: String, password: String, origin: String) {
        val saveIntent = Intent(context, A11ySaveReceiver::class.java).apply {
            action = A11ySaveReceiver.ACTION_SAVE
            putExtra(A11ySaveReceiver.EXTRA_SITE,     siteName)
            putExtra(A11ySaveReceiver.EXTRA_USERNAME,  username)
            putExtra(A11ySaveReceiver.EXTRA_PASSWORD,  password)
            putExtra(A11ySaveReceiver.EXTRA_ORIGIN,    origin)
        }
        val savePi = PendingIntent.getBroadcast(
            context, PassKeyApplication.NOTIF_SAVE_PROMPT, saveIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(context, PassKeyApplication.CHANNEL_SAVE_PROMPT)
            .setSmallIcon(R.drawable.passkey_logo)
            .setContentTitle("Save password to PassKey?")
            .setContentText("$siteName · $username")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setColor(android.graphics.Color.parseColor("#111827"))
            .addAction(0, "Save", savePi)
            .build()

        mgr(context).notify(PassKeyApplication.NOTIF_SAVE_PROMPT, notif)
    }

    private fun mgr(ctx: Context) = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
}

