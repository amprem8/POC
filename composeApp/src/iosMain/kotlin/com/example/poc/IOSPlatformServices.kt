package com.example.poc

import com.example.poc.vault.*

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSUserDefaults
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import platform.UIKit.UIPasteboard
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val VaultConfigKey = "vault_config"
private const val PasswordEntriesKey = "password_entries"

class IOSPlatformServices : PlatformServices {
    private val defaults = NSUserDefaults.standardUserDefaults
    private val _entries = MutableStateFlow<List<PasswordEntry>>(emptyList())

    override val serverBaseUrl: String = "http://127.0.0.1:$SERVER_PORT"
    override val biometricAvailable: Boolean
        get() = LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, null)
    override val entriesFlow: StateFlow<List<PasswordEntry>> = _entries.asStateFlow()

    init { _entries.value = loadPasswordEntries() }

    override fun loadVaultConfig(): VaultConfig? {
        return decodeVaultConfig(defaults.stringForKey(VaultConfigKey))
    }

    override fun saveVaultConfig(config: VaultConfig) {
        defaults.setObject(encodeVaultConfig(config), forKey = VaultConfigKey)
    }

    override suspend fun promptBiometric(promptTitle: String, promptSubtitle: String): Boolean {
        if (!biometricAvailable) return false

        return suspendCoroutine { continuation ->
            val context = LAContext()
            val reason = "$promptTitle\n$promptSubtitle"
            context.evaluatePolicy(
                policy = LAPolicyDeviceOwnerAuthenticationWithBiometrics,
                localizedReason = reason,
            ) { success, _ ->
                continuation.resume(success)
            }
        }
    }

    override fun copyToClipboard(label: String, value: String) {
        UIPasteboard.generalPasteboard.string = value
    }

    override fun showToast(message: String) {
        // iOS doesn't have native toast; log for now
        println("Toast: $message")
    }

    // ── SSO Authentication ──────────────────────────────────────────────

    override suspend fun startSsoAuth(): SsoAuthResult {
        // TODO: Implement using ASWebAuthenticationSession when iOS is prioritized
        // For now, return a descriptive error
        return SsoAuthResult(
            success = false,
            error = "SSO authentication is not yet implemented for iOS. Coming in Phase 3.",
        )
    }

    override fun loadPasswordEntries(): List<PasswordEntry> {
        val json = defaults.stringForKey(PasswordEntriesKey) ?: return emptyList()
        return try {
            json.split("||ENTRY||").filter { it.isNotBlank() }.map { entryStr ->
                val map = entryStr.split("||FIELD||").associate {
                    val idx = it.indexOf('=')
                    it.substring(0, idx) to it.substring(idx + 1)
                }
                PasswordEntry(
                    id = map["id"] ?: "",
                    siteName = map["siteName"] ?: "",
                    username = map["username"] ?: "",
                    password = map["password"] ?: "",
                    loginUrl = map["loginUrl"] ?: "",
                    dateModified = map["dateModified"]?.toLongOrNull() ?: 0L,
                    notes = map["notes"] ?: "",
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun deletePasswordEntry(id: String) {
        val current = loadPasswordEntries().filter { it.id != id }
        defaults.setObject(encodeEntries(current), forKey = PasswordEntriesKey)
        _entries.value = current
    }

    override fun updateNotes(id: String, notes: String) {
        val current = loadPasswordEntries().map {
            if (it.id == id) it.copy(notes = notes) else it
        }
        defaults.setObject(encodeEntries(current), forKey = PasswordEntriesKey)
        _entries.value = current
    }

    private fun encodeEntries(entries: List<PasswordEntry>): String =
        entries.joinToString("||ENTRY||") { e ->
            listOf("id=${e.id}", "siteName=${e.siteName}", "username=${e.username}",
                   "password=${e.password}", "loginUrl=${e.loginUrl}", "dateModified=${e.dateModified}",
                   "notes=${e.notes}")
                .joinToString("||FIELD||")
        }
}
