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
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.BeginGetPasswordOption
import androidx.credentials.provider.CreateEntry
import androidx.credentials.provider.CredentialProviderService
import androidx.credentials.provider.PasswordCredentialEntry
import android.app.PendingIntent
import android.content.Intent
import java.time.Instant

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class VaultCredentialProviderService : CredentialProviderService() {

    companion object {
        private const val TAG = "VaultCredProv"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "✅ CredentialProviderService.onCreate — service created SDK=${Build.VERSION.SDK_INT}")
        VaultTrace.i("CredProvider", "onCreate — service created SDK=${Build.VERSION.SDK_INT}")
        PasswordRepository.init(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy — credential provider service destroyed")
        VaultTrace.d("CredProvider", "onDestroy")
    }

    override fun onBeginGetCredentialRequest(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>,
    ) {
        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.i(TAG, " onBeginGetCredentialRequest  options=${request.beginGetCredentialOptions.size}")
        VaultTrace.i("CredProvider", "onBeginGetCredentialRequest options=${request.beginGetCredentialOptions.size}")
        try {
            PasswordRepository.init(this)
            val entries = PasswordRepository.snapshot()
            Log.i(TAG, "  stored entries=${entries.size}  ids=${entries.joinToString { it.id }}")
            VaultTrace.i("CredProvider", "beginGet entries=${entries.size} ids=${entries.joinToString { it.id }}")

            val credentialEntries = mutableListOf<PasswordCredentialEntry>()

            // Extract the calling origin (web domain or package name) for filtering
            @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
            val callingOrigin = runCatching { request.callingAppInfo?.origin }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { normalizeCredentialOrigin(it) }
                ?: request.callingAppInfo?.packageName
                    ?.let { normalizeCredentialOrigin(it) }
                ?: ""
            Log.d(TAG, "  callingOrigin=$callingOrigin")
            VaultTrace.d("CredProvider", "beginGet callingOrigin=$callingOrigin")

            for ((optIdx, option) in request.beginGetCredentialOptions.withIndex()) {
                Log.d(TAG, "  option[$optIdx] class=${option::class.java.simpleName}")
                if (option is BeginGetPasswordOption) {
                    Log.d(TAG, "    → BeginGetPasswordOption, building entries")
                    // Filter entries by origin when a web origin is known; show all when blank
                    val matchingEntries = if (callingOrigin.isNotBlank())
                        entries.filter { originsMatch(it.loginUrl, callingOrigin) }
                    else entries
                    Log.i(TAG, "    origin='$callingOrigin' → ${matchingEntries.size}/${entries.size} matching entries")
                    VaultTrace.i("CredProvider", "beginGet origin='$callingOrigin' matches=${matchingEntries.size} total=${entries.size}")
                    matchingEntries.forEach { entry ->
                        Log.d(TAG, "      building PasswordCredentialEntry for ${entry.username} @ ${entry.siteName}")
                        val fillIntent = Intent(this, CredentialFillActivity::class.java).apply {
                            putExtra(CredentialFillActivity.EXTRA_ENTRY_ID, entry.id)
                        }
                        val pendingIntent = PendingIntent.getActivity(
                            this, entry.id.hashCode(), fillIntent,
                            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                        )
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
                } else {
                    Log.d(TAG, "    → Unsupported option type, skipping")
                }
            }

            Log.i(TAG, "✅ onBeginGetCredentialRequest returning ${credentialEntries.size} entries")
            VaultTrace.i("CredProvider", "beginGet returning credentialEntries=${credentialEntries.size}")
            callback.onResult(
                BeginGetCredentialResponse.Builder()
                    .setCredentialEntries(credentialEntries)
                    .build()
            )
        } catch (e: Exception) {
            Log.e(TAG, "onBeginGetCredentialRequest EXCEPTION", e)
            VaultTrace.e("CredProvider", "beginGet EXCEPTION", e)
            callback.onError(GetCredentialUnknownException(e.message))
        }
    }

    // ── Create credential (save) ──────────────────────────────────────────

    override fun onBeginCreateCredentialRequest(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>,
    ) {
        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.i(TAG, " onBeginCreateCredentialRequest  type='${request.type}'")
        VaultTrace.i("CredProvider", "onBeginCreateCredentialRequest type='${request.type}'")
        try {
            if (!request.type.contains("password", ignoreCase = true)) {
                Log.w(TAG, "⚠️ beginCreate ignored — type='${request.type}' (not a password request)")
                VaultTrace.w("CredProvider", "beginCreate ignored type='${request.type}' — not a password type")
                callback.onResult(BeginCreateCredentialResponse.Builder().build())
                return
            }

            Log.i(TAG, "  type='${request.type}' — offering Vault save entry")
            VaultTrace.i("CredProvider", "beginCreate OFFERING save entry for type='${request.type}'")

            val saveIntent = Intent(this, CredentialSaveActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this, 0, saveIntent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val createEntry = CreateEntry.Builder(
                accountName   = getString(R.string.app_name),
                pendingIntent = pendingIntent,
            ).build()

            Log.i(TAG, "✅ onBeginCreateCredentialRequest → returning CreateEntry '${getString(R.string.app_name)}'")
            VaultTrace.i("CredProvider", "beginCreate returning CreateEntry account='${getString(R.string.app_name)}'")
            callback.onResult(
                BeginCreateCredentialResponse.Builder()
                    .addCreateEntry(createEntry)
                    .build()
            )
        } catch (e: Exception) {
            Log.e(TAG, "onBeginCreateCredentialRequest EXCEPTION", e)
            VaultTrace.e("CredProvider", "beginCreate EXCEPTION", e)
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
        VaultTrace.i("CredProvider", "clearCredentialState requested")
        callback.onResult(null)
    }
}