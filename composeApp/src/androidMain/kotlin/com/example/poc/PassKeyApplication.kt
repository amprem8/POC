package com.example.poc

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class PassKeyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PassKeyTrace.i("App", "Application.onCreate pid=${android.os.Process.myPid()}")
        // Initialise singleton repository so StateFlow is live for all components
        PasswordRepository.init(this)
        PassKeyTrace.i("App", "Repository initialized entries=${PasswordRepository.snapshot().size}")
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_SAVED) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_SAVED,
                        "PassKey — Password Saved",
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply { description = "Shown when a password is saved to PassKey" }
                )
            }
            if (mgr.getNotificationChannel(CHANNEL_FILL) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_FILL,
                        "PassKey — Autofill Available",
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply { description = "Shown when PassKey can fill a login form" }
                )
            }
            if (mgr.getNotificationChannel(CHANNEL_SAVE_PROMPT) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_SAVE_PROMPT,
                        "PassKey — Save Password?",
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply { description = "Prompts you to save a new password after login" }
                )
            }
        }
    }

    companion object {
        const val CHANNEL_SAVED        = "passkey_saved"
        const val CHANNEL_FILL         = "passkey_fill"
        const val CHANNEL_SAVE_PROMPT  = "passkey_save_prompt"
        const val NOTIF_SAVED          = 2001
        const val NOTIF_FILL           = 2002
        const val NOTIF_SAVE_PROMPT    = 2003
    }
}

