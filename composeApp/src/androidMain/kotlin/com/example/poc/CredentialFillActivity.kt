package com.example.poc

import android.app.Activity
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
        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.i(TAG, "🚀 CredentialFillActivity.onCreate")
        PassKeyTrace.i("CredFill", "onCreate launched")

        PasswordRepository.init(this)
        val entryId = intent.getStringExtra(EXTRA_ENTRY_ID)
        val entry   = entryId?.let(PasswordRepository::getById)

        Log.i(TAG, "  entryId=$entryId  found=${entry != null}  site=${entry?.siteName}  user=${entry?.username}")
        PassKeyTrace.i("CredFill", "entryId=$entryId found=${entry != null} site=${entry?.siteName}")

        if (entry == null) {
            Log.w(TAG, "❌ Entry not found for id=$entryId — aborting")
            PassKeyTrace.w("CredFill", "entry not found for id=$entryId")
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val canBiometric = BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

        Log.i(TAG, "  biometricAvailable=$canBiometric")
        PassKeyTrace.i("CredFill", "biometricAvailable=$canBiometric site=${entry.siteName}")

        if (!canBiometric) {
            Log.i(TAG, "  No biometric — returning credential directly")
            PassKeyTrace.i("CredFill", "no biometric — filling directly")
            returnCredential(entry)
            return
        }

        Log.i(TAG, "  Showing biometric prompt for ${entry.siteName}")
        PassKeyTrace.i("CredFill", "showing biometric prompt for ${entry.siteName}")

        BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    Log.i(TAG, "✅ Biometric succeeded — returning credential for ${entry.siteName}")
                    PassKeyTrace.i("CredFill", "biometric SUCCESS → returning credential for ${entry.siteName}")
                    returnCredential(entry)
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    Log.w(TAG, "⚠️ Biometric error code=$errorCode msg=$errString")
                    PassKeyTrace.w("CredFill", "biometric error code=$errorCode msg=$errString")
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
                override fun onAuthenticationFailed() {
                    Log.w(TAG, "⚠️ Biometric failed (wrong finger/face)")
                    PassKeyTrace.w("CredFill", "biometric auth failed (wrong biometric)")
                }
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
        Log.i(TAG, "  returnCredential for ${entry.siteName} / ${entry.username} SDK=${Build.VERSION.SDK_INT}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val responseIntent = Intent()
            PendingIntentHandler.setGetCredentialResponse(
                responseIntent,
                androidx.credentials.GetCredentialResponse(PasswordCredential(entry.username, entry.password))
            )
            setResult(Activity.RESULT_OK, responseIntent)
            Log.i(TAG, "✅ Credential returned via PendingIntentHandler for ${entry.siteName} / ${entry.username}")
            PassKeyTrace.i("CredFill", "FILL SUCCESS site=${entry.siteName} user=${entry.username}")
        } else {
            Log.w(TAG, "⚠️ SDK < 34 — cannot return credential via Credential Manager")
            PassKeyTrace.w("CredFill", "SDK=${Build.VERSION.SDK_INT} < 34 — Credential Manager not available")
            setResult(Activity.RESULT_CANCELED)
        }
        finish()
    }
}