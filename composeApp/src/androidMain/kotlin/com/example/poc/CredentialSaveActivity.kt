package com.example.poc

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity

/**
 * Called by the Credential Manager system when the user chooses PassKey
 * as the provider for a newly created password credential.
 */
@RequiresApi(Build.VERSION_CODES.O)
class CredentialSaveActivity : FragmentActivity() {

    companion object {
        private const val TAG = "PassKeyCredSave"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "CredentialSaveActivity launched")
        PassKeyTrace.i("CredSave", "onCreate sdk=${Build.VERSION.SDK_INT} extras=${intent?.extras?.keySet()?.joinToString()}")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val createRequest = PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)
            val passwordRequest = createRequest?.callingRequest as? CreatePasswordRequest
            Log.i(
                TAG,
                "createRequest=${createRequest != null} passwordRequest=${passwordRequest != null} " +
                    "callingPkg=${createRequest?.callingAppInfo?.packageName}"
            )
            PassKeyTrace.i(
                "CredSave",
                "provider createRequest=${createRequest != null} passwordRequest=${passwordRequest != null} callingPkg=${createRequest?.callingAppInfo?.packageName}"
            )

            if (passwordRequest != null) {
                val callingOrigin = createRequest.callingAppInfo.packageName
                val siteName = domainToSiteName(callingOrigin)
                Log.i(
                    TAG,
                    "Saving credential site=$siteName user=${passwordRequest.id} pwdLen=${passwordRequest.password.length}"
                )
                PassKeyTrace.i(
                    "CredSave",
                    "saving credential site=$siteName origin=$callingOrigin user=${passwordRequest.id} pwdLen=${passwordRequest.password.length}"
                )

                val entry = PasswordEntry(
                    id = System.currentTimeMillis().toString(),
                    siteName = siteName,
                    username = passwordRequest.id,
                    password = passwordRequest.password,
                    loginUrl = callingOrigin,
                    dateModified = System.currentTimeMillis(),
                )

                // Save through repository — updates StateFlow instantly
                PasswordRepository.saveRaw(this, entry)
                PassKeyTrace.i("CredSave", "repository save complete id=${entry.id} repoSize=${PasswordRepository.snapshot().size}")
                NotificationHelper.showSaved(this, siteName, passwordRequest.id)

                val responseIntent = Intent()
                PendingIntentHandler.setCreateCredentialResponse(
                    responseIntent,
                    androidx.credentials.CreatePasswordResponse()
                )
                setResult(Activity.RESULT_OK, responseIntent)
                Log.i(TAG, "Credential save completed successfully")
                PassKeyTrace.i("CredSave", "completed successfully")
                finish()
                return
            }
        }

        Log.w(TAG, "Credential save canceled — no password request available")
        PassKeyTrace.w("CredSave", "canceled no password request available")
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    private fun domainToSiteName(domain: String): String =
        domain.removePrefix("https://").removePrefix("http://").removePrefix("www.")
            .split(".").firstOrNull()?.replaceFirstChar { it.uppercase() } ?: domain
}
