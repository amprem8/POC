package com.example.poc

import android.os.Build
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.provider.BeginCreateCredentialRequest
import androidx.credentials.provider.BeginCreateCredentialResponse
import androidx.credentials.provider.BeginCreatePasswordCredentialRequest
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.BeginGetPasswordOption
import androidx.credentials.provider.CreateEntry
import androidx.credentials.provider.CredentialProviderService
import androidx.credentials.provider.PasswordCredentialEntry
import android.app.PendingIntent
import android.content.Intent
import java.time.Instant

/**
 * Android Credential Provider Service.
 *
 * This registers PassKey as a selectable credential provider in Android's
 * Credential Manager system (Android 14+). Users can select this provider
 * when saving/filling passwords in Chrome, Firefox or any other app.
 *
 * To activate: Settings → Passwords & accounts → Additional providers → PassKey
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class PassKeyCredentialProviderService : CredentialProviderService() {

    companion object {
        private const val TAG = "PassKeyCredProv"
    }


    override fun onBeginGetCredentialRequest(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>,
    ) {
        try {
            PasswordRepository.init(this)
            val entries = PasswordRepository.snapshot()
            Log.i(
                TAG,
                "onBeginGetCredentialRequest options=${request.beginGetCredentialOptions.size} entries=${entries.size}"
            )
            val credentialEntries = mutableListOf<PasswordCredentialEntry>()

            for (option in request.beginGetCredentialOptions) {
                if (option is BeginGetPasswordOption) {
                    entries.forEach { entry ->
                        val fillIntent = Intent(this, CredentialFillActivity::class.java).apply {
                            putExtra(CredentialFillActivity.EXTRA_ENTRY_ID, entry.id)
                        }
                        val pendingIntent = PendingIntent.getActivity(
                            this,
                            entry.id.hashCode(),
                            fillIntent,
                            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            credentialEntries.add(
                                PasswordCredentialEntry.Builder(
                                    context = this,
                                    username = entry.username,
                                    pendingIntent = pendingIntent,
                                    beginGetPasswordOption = option,
                                )
                                    .setDisplayName(entry.siteName)
                                    .setLastUsedTime(Instant.ofEpochMilli(entry.dateModified))
                                    .build()
                            )
                        }
                    }
                }
            }

            Log.i(TAG, "Returning ${credentialEntries.size} credential entries")

            callback.onResult(
                BeginGetCredentialResponse.Builder()
                    .setCredentialEntries(credentialEntries)
                    .build()
            )
        } catch (e: Exception) {
            Log.e(TAG, "onBeginGetCredentialRequest failed", e)
            callback.onError(GetCredentialUnknownException(e.message))
        }
    }

    // ── Create credential (save) ──────────────────────────────────────────

    override fun onBeginCreateCredentialRequest(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>,
    ) {
        try {
            Log.i(TAG, "onBeginCreateCredentialRequest request=${request::class.java.simpleName}")
            if (request is BeginCreatePasswordCredentialRequest) {
                val saveIntent = Intent(this, CredentialSaveActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    saveIntent,
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )

                val createEntry = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    CreateEntry.Builder(
                        accountName = "PassKey Vault",
                        pendingIntent = pendingIntent,
                    ).build()
                } else null

                val builder = BeginCreateCredentialResponse.Builder()
                createEntry?.let { builder.addCreateEntry(it) }
                Log.i(TAG, "Returning create entry for password save sheet")
                callback.onResult(builder.build())
            } else {
                Log.w(TAG, "Unsupported create request type: ${request::class.java.name}")
                callback.onResult(BeginCreateCredentialResponse.Builder().build())
            }
        } catch (e: Exception) {
            Log.e(TAG, "onBeginCreateCredentialRequest failed", e)
            callback.onError(CreateCredentialUnknownException(e.message))
        }
    }

    // ── Clear credentials ──────────────────────────────────────────────────

    override fun onClearCredentialStateRequest(
        request: androidx.credentials.provider.ProviderClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void?, ClearCredentialException>,
    ) {
        Log.i(TAG, "onClearCredentialStateRequest")
        callback.onResult(null)
    }
}

