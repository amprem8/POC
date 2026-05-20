package com.example.poc

import android.app.Activity
import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.fragment.app.FragmentActivity

/**
 * Fallback save UI when Chrome / Google Password Manager hides the password from
 * Autofill and Accessibility. The user re-enters the password once and PassKey
 * stores it in the vault immediately.
 */
@RequiresApi(Build.VERSION_CODES.O)
class ManualSaveActivity : FragmentActivity() {

    companion object {
        private const val TAG = "PassKeyManualSave"
        const val EXTRA_USERNAME = "username"
        const val EXTRA_DOMAIN = "domain"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val username = intent.getStringExtra(EXTRA_USERNAME).orEmpty()
        val domain = intent.getStringExtra(EXTRA_DOMAIN).orEmpty()
        val siteName = domainToSiteName(domain)

        Log.i(TAG, "ManualSaveActivity launched for $domain / $username")

        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), 0)
        }

        val subtitle = TextView(this).apply {
            text = "$siteName · $username\nChrome hid the password. Enter it once to save in PassKey."
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#374151"))
        }

        val passwordInput = EditText(this).apply {
            hint = "Password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(android.graphics.Color.parseColor("#111827"))
            setHintTextColor(android.graphics.Color.parseColor("#9CA3AF"))
            setPadding(dp(12), dp(14), dp(12), dp(14))
        }

        container.addView(subtitle)
        container.addView(passwordInput)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Save to PassKey")
            .setView(container)
            .setCancelable(true)
            .setNegativeButton("Cancel") { _, _ ->
                Log.i(TAG, "Manual save canceled")
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
            .setPositiveButton("Save", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val password = passwordInput.text?.toString()?.trim().orEmpty()
                if (password.isBlank()) {
                    passwordInput.error = "Enter the password"
                    return@setOnClickListener
                }

                val entry = PasswordEntry(
                    id = System.currentTimeMillis().toString(),
                    siteName = siteName,
                    username = username,
                    password = password,
                    loginUrl = domain,
                    dateModified = System.currentTimeMillis(),
                )
                PasswordRepository.saveRaw(this, entry)
                NotificationHelper.showSaved(this, siteName, username)
                Toast.makeText(this, "Saved to PassKey", Toast.LENGTH_SHORT).show()
                Log.i(TAG, "Manual save complete for $domain / $username")
                setResult(Activity.RESULT_OK)
                dialog.dismiss()
                finish()
            }
        }

        dialog.setOnDismissListener {
            if (!isFinishing) finish()
        }
        dialog.show()
    }

    private fun domainToSiteName(domain: String): String =
        domain.removePrefix("https://").removePrefix("http://").removePrefix("www.")
            .split(".").firstOrNull()?.replaceFirstChar { it.uppercase() } ?: domain

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}

