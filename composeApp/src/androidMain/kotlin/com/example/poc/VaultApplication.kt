package com.example.poc

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class VaultApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        VaultTrace.i("App", "Application.onCreate pid=${android.os.Process.myPid()} SDK=${android.os.Build.VERSION.SDK_INT}")
        // Initialise singleton repository so StateFlow is live for all components
        PasswordRepository.init(this)
        VaultTrace.i("App", "Repository initialized entries=${PasswordRepository.snapshot().size}")
        createNotificationChannels()

        // Diagnostic: log which APIs are available on this device
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            VaultTrace.i("App", "Android 14+ detected — Credential Manager API available. Chrome will use CredentialProviderService for save/fill.")
        } else {
            VaultTrace.i("App", "Android <14 — using Autofill Framework only for save/fill.")
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_SAVED) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_SAVED,
                        "Vault — Password Saved",
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply { description = "Shown when a password is saved to Vault" }
                )
            }
            // Clean up legacy channels that are no longer used
            mgr.deleteNotificationChannel("vault_fill")
            mgr.deleteNotificationChannel("vault_save_prompt")
        }
    }

    companion object {
        const val CHANNEL_SAVED = "vault_saved"
        const val NOTIF_SAVED   = 2001
    }
}

