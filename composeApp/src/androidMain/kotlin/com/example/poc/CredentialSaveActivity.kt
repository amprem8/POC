package com.example.poc

import com.example.poc.vault.*

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity

/**
 * Handles the Credential Manager "Save password" flow on Android 14+.
 *
 * Chrome and other Credential-Manager-aware browsers call this when the user
 * chooses "Save to Vault" from the system save sheet.  We extract username +
 * password from the [CreatePasswordRequest] and persist them through
 * [PasswordRepository.saveFromAutofill] — the same single save path used by
 * [VaultAutofillService.onSaveRequest].
 *
 * There is intentionally NO second confirmation dialog here: Android's system
 * save sheet already acts as the single user-facing confirmation step.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class CredentialSaveActivity : FragmentActivity() {

    companion object {
        private const val TAG = "VaultCredSave"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.i(TAG, " CredentialSaveActivity.onCreate  intent=$intent")
        VaultTrace.i("CredSave", "onCreate launched intent action=${intent?.action} extras=${intent?.extras?.keySet()}")

        PasswordRepository.init(this)

        val request = PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)
        if (request == null) {
            Log.w(TAG, "❌ No CreateCredentialRequest in intent — aborting")
            VaultTrace.w("CredSave", "onCreate aborted — PendingIntentHandler returned null request")
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        Log.i(TAG, "  request type=${request.callingRequest::class.java.simpleName}  pkg=${request.callingAppInfo.packageName}")
        VaultTrace.i("CredSave", "request type=${request.callingRequest::class.java.simpleName} callingPkg=${request.callingAppInfo.packageName}")

        val createRequest = request.callingRequest
        if (createRequest !is CreatePasswordRequest) {
            Log.w(TAG, "❌ Unsupported request type ${createRequest::class.java.simpleName} — expected CreatePasswordRequest")
            VaultTrace.w("CredSave", "unsupported request type ${createRequest::class.java.simpleName}")
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val username = createRequest.id.trim()
        val password = createRequest.password.trim()
        // Prefer the origin from the request (the actual web origin / RP ID reported by the browser).
        // Fall back to the calling package name only when no web origin is available.
        @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
        val rawOrigin = createRequest.origin
            ?.takeIf { it.isNotBlank() }
            ?: runCatching { request.callingAppInfo.origin }.getOrNull()
                ?.takeIf { it.isNotBlank() }
            ?: request.callingAppInfo.packageName
        val origin = normalizeCredentialOrigin(rawOrigin)

        Log.i(TAG, "  user='$username'  passLen=${password.length}  origin=$origin  rawOrigin=$rawOrigin")
        VaultTrace.i("CredSave", "credentials received user='$username' passLen=${password.length} origin=$origin rawOrigin=$rawOrigin")

        if (username.isBlank() || password.isBlank()) {
            Log.w(TAG, "❌ Blank username or password — skipping save. user='$username' passLen=${password.length}")
            VaultTrace.w("CredSave", "skipped — blank credentials user='$username' passLen=${password.length}")
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val entry = PasswordEntry(
            id           = System.currentTimeMillis().toString(),
            siteName     = originDisplayName(origin),
            username     = username,
            password     = password,
            loginUrl     = origin,
            dateModified = System.currentTimeMillis(),
        )

        Log.i(TAG, "✅ Saving: site=${entry.siteName}  user=${entry.username}  origin=${entry.loginUrl}")
        PasswordRepository.saveFromAutofill(entry)
        Log.i(TAG, "✅ PasswordRepository.save() — credential stored successfully")
        VaultTrace.i("CredSave", "SAVE SUCCESS site=${entry.siteName} user=${entry.username} origin=${entry.loginUrl}")

        Toast.makeText(this, "✅ Password saved for ${entry.siteName}", Toast.LENGTH_SHORT).show()

        val responseIntent = Intent()
        PendingIntentHandler.setCreateCredentialResponse(
            responseIntent,
            androidx.credentials.CreatePasswordResponse()
        )
        setResult(Activity.RESULT_OK, responseIntent)
        Log.i(TAG, "✅ CredentialSaveActivity finishing with RESULT_OK")
        finish()
    }
}