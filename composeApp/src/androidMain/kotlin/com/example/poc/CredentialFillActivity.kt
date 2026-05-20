package com.example.poc

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity
import org.json.JSONArray

/**
 * Called by the Credential Manager system when the user selects a saved password entry.
 * Shows biometric prompt then returns the credential.
 */
@RequiresApi(Build.VERSION_CODES.O)
class CredentialFillActivity : FragmentActivity() {

    companion object {
        const val EXTRA_ENTRY_ID = "entry_id"
        private const val TAG = "PassKeyCredFill"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val entryId = intent.getStringExtra(EXTRA_ENTRY_ID)
        val entry = entryId?.let { loadEntryById(it) }
        Log.i(TAG, "CredentialFillActivity launched entryId=$entryId found=${entry != null}")

        if (entry == null) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val canBiometric = BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

        if (!canBiometric) {
            returnCredential(entry)
            return
        }

        BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    returnCredential(entry)
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
                override fun onAuthenticationFailed() = Unit
            },
        ).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Verify to fill password")
                .setSubtitle("Authenticate to autofill — ${entry.siteName}")
                .setNegativeButtonText("Cancel")
                .build(),
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun returnCredential(entry: PasswordEntry) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val responseIntent = Intent()
            PendingIntentHandler.setGetCredentialResponse(
                responseIntent,
                androidx.credentials.GetCredentialResponse(
                    PasswordCredential(entry.username, entry.password)
                )
            )
            setResult(Activity.RESULT_OK, responseIntent)
            Log.i(TAG, "Returned credential for ${entry.siteName} / ${entry.username}")
        } else {
            setResult(Activity.RESULT_CANCELED)
        }
        finish()
    }

    private fun loadEntryById(id: String): PasswordEntry? {
        val prefs = getSharedPreferences("passkey_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("password_entries", null) ?: return null
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.getJSONObject(i)
                if (obj.getString("id") == id) PasswordEntry(
                    id = obj.getString("id"),
                    siteName = obj.getString("siteName"),
                    username = obj.getString("username"),
                    password = obj.getString("password"),
                    loginUrl = obj.getString("loginUrl"),
                    dateModified = obj.getLong("dateModified"),
                ) else null
            }.firstOrNull()
        } catch (e: Exception) { null }
    }
}

