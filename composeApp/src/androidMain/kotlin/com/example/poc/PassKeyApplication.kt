package com.example.poc

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class PassKeyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PassKeyTrace.i("App", "Application.onCreate pid=${android.os.Process.myPid()} SDK=${android.os.Build.VERSION.SDK_INT}")
        // Initialise singleton repository so StateFlow is live for all components
        PasswordRepository.init(this)
        PassKeyTrace.i("App", "Repository initialized entries=${PasswordRepository.snapshot().size}")
        createNotificationChannels()

        // Diagnostic: log which APIs are available on this device
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            PassKeyTrace.i("App", "Android 14+ detected — Credential Manager API available. Chrome will use CredentialProviderService for save/fill.")
        } else {
            PassKeyTrace.i("App", "Android <14 — using Autofill Framework only for save/fill.")
        }
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
            // Clean up legacy channels that are no longer used
            mgr.deleteNotificationChannel("passkey_fill")
            mgr.deleteNotificationChannel("passkey_save_prompt")
        }
    }

    companion object {
        const val CHANNEL_SAVED = "passkey_saved"
        const val NOTIF_SAVED   = 2001
    }
}

