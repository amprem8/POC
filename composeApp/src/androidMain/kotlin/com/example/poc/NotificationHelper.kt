package com.example.poc

import com.example.poc.vault.*

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Central helper for firing Vault notifications.
 *
 * Only used for "password saved" confirmation notifications.
 * All credential saving flows through [VaultAutofillService.onSaveRequest]
 * or [CredentialSaveActivity] — no accessibility-based notifications.
 */
object NotificationHelper {

    private const val TAG = "VaultNotifHelper"

    /** "Password saved" confirmation notification — tapping opens Vault vault. */
    fun showSaved(context: Context, siteName: String, username: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pi = PendingIntent.getActivity(
            context, VaultApplication.NOTIF_SAVED, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(context, VaultApplication.CHANNEL_SAVED)
            .setSmallIcon(R.drawable.vault_logo)
            .setContentTitle("Password saved to Vault")
            .setContentText("$siteName · $username")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(Color.parseColor("#111827"))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        mgr(context).notify(VaultApplication.NOTIF_SAVED, notif)
    }

    private fun mgr(ctx: Context) = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
}
