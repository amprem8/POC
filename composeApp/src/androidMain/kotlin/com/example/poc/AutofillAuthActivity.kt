package com.example.poc

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.autofill.Dataset
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import org.json.JSONArray
import org.json.JSONObject

/**
 * Transparent trampoline activity that shows biometric prompt before
 * returning an autofill Dataset to the requesting app.
 *
 * Launched by [PassKeyAutofillService] when biometric confirmation is needed.
 */
@RequiresApi(Build.VERSION_CODES.O)
class AutofillAuthActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val entryId = intent.getStringExtra(EXTRA_ENTRY_ID)
        val usernameAutofillId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.getParcelableExtra(EXTRA_USERNAME_ID, android.view.autofill.AutofillId::class.java)
        } else null
        val passwordAutofillId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.getParcelableExtra(EXTRA_PASSWORD_ID, android.view.autofill.AutofillId::class.java)
        } else null

        val biometricManager = BiometricManager.from(this)
        val canAuth = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

        if (!canAuth) {
            // No biometric available — return directly
            fillAndFinish(entryId, usernameAutofillId, passwordAutofillId)
            return
        }

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    fillAndFinish(entryId, usernameAutofillId, passwordAutofillId)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }

                override fun onAuthenticationFailed() = Unit
            },
        )

        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Verify your identity")
                .setSubtitle("Authenticate to autofill your password")
                .setNegativeButtonText("Cancel")
                .build(),
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun fillAndFinish(
        entryId: String?,
        usernameId: android.view.autofill.AutofillId?,
        passwordId: android.view.autofill.AutofillId?,
    ) {
        val entry = entryId?.let { loadEntryById(it) }

        if (entry == null) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val datasetBuilder = Dataset.Builder()
        val label = "${entry.siteName} — ${entry.username}"

        usernameId?.let { id ->
            val view = RemoteViews(packageName, android.R.layout.simple_list_item_1)
            view.setTextViewText(android.R.id.text1, "🔑 $label")
            datasetBuilder.setValue(id, AutofillValue.forText(entry.username), view)
        }
        passwordId?.let { id ->
            val view = RemoteViews(packageName, android.R.layout.simple_list_item_1)
            view.setTextViewText(android.R.id.text1, "🔑 $label")
            datasetBuilder.setValue(id, AutofillValue.forText(entry.password), view)
        }

        val replyIntent = Intent().apply {
            putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, datasetBuilder.build())
        }
        setResult(Activity.RESULT_OK, replyIntent)
        finish()
    }

    private fun loadEntryById(id: String): PasswordEntry? {
        val prefs = getSharedPreferences("passkey_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("password_entries", null) ?: return null
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.getJSONObject(i)
                if (obj.getString("id") == id) {
                    PasswordEntry(
                        id = obj.getString("id"),
                        siteName = obj.getString("siteName"),
                        username = obj.getString("username"),
                        password = obj.getString("password"),
                        loginUrl = obj.getString("loginUrl"),
                        dateModified = obj.getLong("dateModified"),
                    )
                } else null
            }.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val EXTRA_ENTRY_ID = "entry_id"
        const val EXTRA_USERNAME_ID = "username_autofill_id"
        const val EXTRA_PASSWORD_ID = "password_autofill_id"
    }
}

