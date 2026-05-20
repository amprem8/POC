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
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class AndroidPlatformServices(
    private val activity: FragmentActivity,
) : PlatformServices {

    private val preferences by lazy {
        activity.getSharedPreferences(PasswordRepository.PREFS_NAME, Context.MODE_PRIVATE)
    }

    override val serverBaseUrl: String = "http://10.0.2.2:$SERVER_PORT"

    override val biometricAvailable: Boolean
        get() = BiometricManager.from(activity)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    // ── PassKey config ────────────────────────────────────────────────────

    override fun loadPassKeyConfig(): PassKeyConfig? =
        decodePassKeyConfig(preferences.getString("passkey_config", null))

    override fun savePassKeyConfig(config: PassKeyConfig) {
        preferences.edit().putString("passkey_config", encodePassKeyConfig(config)).apply()
    }

    // ── Biometric ─────────────────────────────────────────────────────────

    override suspend fun promptBiometric(promptTitle: String, promptSubtitle: String): Boolean {
        if (!biometricAvailable) return false
        return suspendCoroutine { continuation ->
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
                        continuation.resume(true)
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) =
                        continuation.resume(false)
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

    // ── Clipboard / file ──────────────────────────────────────────────────

    override fun copyToClipboard(label: String, value: String) {
        val mgr = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        mgr.setPrimaryClip(ClipData.newPlainText(label, value))
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
                resolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(content) }
                values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return "Saved to Downloads/$fileName"
            }
        }
        val dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: activity.filesDir
        val file = File(dir, fileName).also { it.writeText(content) }
        return "Saved to ${file.absolutePath}"
    }

    // ── Password entries — delegate to PasswordRepository ─────────────────

    /** Reactive StateFlow — collect in Compose; emits on every save/delete from any path. */
    override val entriesFlow: StateFlow<List<PasswordEntry>>
        get() = PasswordRepository.entries

    override fun loadPasswordEntries(): List<PasswordEntry> = PasswordRepository.snapshot()

    override fun savePasswordEntry(entry: PasswordEntry) = PasswordRepository.save(entry)

    override fun deletePasswordEntry(id: String) = PasswordRepository.delete(id)
}
