package com.example.poc

import com.example.poc.vault.*

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AndroidPlatformServices(
    private val activity: FragmentActivity,
) : PlatformServices {

    private val preferences by lazy {
        activity.getSharedPreferences(PasswordRepository.PREFS_NAME, Context.MODE_PRIVATE)
    }

    override val serverBaseUrl: String = "http://10.0.2.2:$SERVER_PORT"

    /**
     * Biometric availability check.
     * On emulators (detected via Build properties), biometric is always reported as
     * unavailable so the flow skips to SSO-only authentication.
     */
    override val biometricAvailable: Boolean
        get() {
            if (isEmulator()) return false
            return BiometricManager.from(activity)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS
        }

    // ── Emulator detection ───────────────────────────────────────────────

    private fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
            || Build.FINGERPRINT.startsWith("unknown")
            || Build.MODEL.contains("google_sdk")
            || Build.MODEL.contains("Emulator")
            || Build.MODEL.contains("Android SDK built for x86")
            || Build.MANUFACTURER.contains("Genymotion")
            || Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
            || Build.PRODUCT.contains("sdk")
            || Build.PRODUCT.contains("emulator")
            || Build.HARDWARE.contains("goldfish")
            || Build.HARDWARE.contains("ranchu"))
    }

    // ── Vault config ────────────────────────────────────────────────────

    override fun loadVaultConfig(): VaultConfig? =
        decodeVaultConfig(preferences.getString("vault_config", null))

    override fun saveVaultConfig(config: VaultConfig) {
        preferences.edit { putString("vault_config", encodeVaultConfig(config)) }
        // When SSO email is available, set xVault user context
        config.ssoEmail?.let { email ->
            PasswordRepository.setUserFromEmail(email)
        }
    }

    // ── Biometric ─────────────────────────────────────────────────────────

    override suspend fun promptBiometric(promptTitle: String, promptSubtitle: String): Boolean {
        // On emulator: always succeed (SSO is the real gate, biometric is skipped)
        if (isEmulator()) return true
        if (!biometricAvailable) return false
        return suspendCancellableCoroutine { continuation ->
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

    // ── Clipboard ───────────────────────────────────────────────────────

    override fun copyToClipboard(label: String, value: String) {
        val mgr = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        mgr.setPrimaryClip(ClipData.newPlainText(label, value))
    }

    override fun showToast(message: String) {
        android.widget.Toast.makeText(activity, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    // ── SSO Authentication ──────────────────────────────────────────────

    private val ssoAuthHandler by lazy { SsoAuthHandler(activity) }

    override suspend fun startSsoAuth(): SsoAuthResult {
        return ssoAuthHandler.authenticate()
    }

    // ── Password entries — delegate to PasswordRepository ─────────────────

    /** Reactive StateFlow — collect in Compose; emits on every save/delete from any path. */
    override val entriesFlow: StateFlow<List<PasswordEntry>>
        get() = PasswordRepository.entries

    override fun loadPasswordEntries(): List<PasswordEntry> {
        val snapshot = PasswordRepository.snapshot()
        VaultTrace.d("AndroidPlatform", "loadPasswordEntries size=${snapshot.size}")
        return snapshot
    }

    override fun deletePasswordEntry(id: String) {
        VaultTrace.i("AndroidPlatform", "deletePasswordEntry id=$id")
        PasswordRepository.delete(id)
    }

    override fun updateNotes(id: String, notes: String) {
        PasswordRepository.updateNotes(id, notes)
    }

    override fun enableOverlayMonitoringAfterLogin() {
        VaultTrace.i("AndroidPlatform", "enableOverlayMonitoringAfterLogin no-op; saving is owned by AutofillService")
    }

    // ── xVault sync ─────────────────────────────────────────────────────

    /**
     * Sync credentials from xVault after login.
     * Called from the UI layer after SSO authentication completes.
     */
    override suspend fun syncFromXVault(): Boolean {
        return PasswordRepository.syncFromXVault()
    }
}
