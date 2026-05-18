package com.example.poc

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val PassKeyPrefs = "passkey_prefs"
private const val PassKeyConfigKey = "passkey_config"

class AndroidPlatformServices(
    private val activity: FragmentActivity,
) : PlatformServices {
    private val preferences by lazy {
        activity.getSharedPreferences(PassKeyPrefs, Context.MODE_PRIVATE)
    }

    override val serverBaseUrl: String = "http://10.0.2.2:$SERVER_PORT"

    override val biometricAvailable: Boolean
        get() = BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    override fun loadPassKeyConfig(): PassKeyConfig? {
        return decodePassKeyConfig(preferences.getString(PassKeyConfigKey, null))
    }

    override fun savePassKeyConfig(config: PassKeyConfig) {
        preferences.edit()
            .putString(PassKeyConfigKey, encodePassKeyConfig(config))
            .apply()
    }

    override suspend fun promptBiometric(promptTitle: String, promptSubtitle: String): Boolean {
        if (!biometricAvailable) return false

        return suspendCoroutine { continuation ->
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        continuation.resume(true)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        continuation.resume(false)
                    }

                    override fun onAuthenticationFailed() = Unit
                },
            )

            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(promptTitle)
                    .setSubtitle(promptSubtitle)
                    .setNegativeButtonText("Cancel")
                    .build(),
            )
        }
    }

    override fun copyToClipboard(label: String, value: String) {
        val manager = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText(label, value))
    }

    override fun saveRecoveryTextFile(fileName: String, content: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = activity.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(content)
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return "Saved recovery file to Downloads/$fileName"
            }
        }

        val targetDir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: activity.filesDir
        val file = File(targetDir, fileName)
        file.writeText(content)
        return "Saved recovery file to ${file.absolutePath}"
    }
}

