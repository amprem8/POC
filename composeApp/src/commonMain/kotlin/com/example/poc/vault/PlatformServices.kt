package com.example.poc.vault

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface PlatformServices {
    val serverBaseUrl: String
    val biometricAvailable: Boolean

    /** Reactive stream of all saved password entries — UI collects this for instant updates. */
    val entriesFlow: StateFlow<List<PasswordEntry>>

    fun loadVaultConfig(): VaultConfig?
    fun saveVaultConfig(config: VaultConfig)
    suspend fun promptBiometric(promptTitle: String, promptSubtitle: String): Boolean
    fun copyToClipboard(label: String, value: String)
    fun showToast(message: String) {}

    /**
     * Launches the system browser for Comcast SSO (Azure AD OIDC + PKCE).
     * Returns [SsoAuthResult] with tokens and email on success, or error on failure.
     */
    suspend fun startSsoAuth(): SsoAuthResult

    fun loadPasswordEntries(): List<PasswordEntry>
    fun deletePasswordEntry(id: String)
    fun updateNotes(id: String, notes: String)
    fun enableOverlayMonitoringAfterLogin() = Unit
}

class PreviewPlatformServices : PlatformServices {
    private var config: VaultConfig? = null
    private val _entries = MutableStateFlow<List<PasswordEntry>>(emptyList())

    override val serverBaseUrl: String = "http://localhost:$SERVER_PORT"
    override val biometricAvailable: Boolean = true
    override val entriesFlow: StateFlow<List<PasswordEntry>> = _entries.asStateFlow()

    override fun loadVaultConfig(): VaultConfig? = config
    override fun saveVaultConfig(config: VaultConfig) { this.config = config }
    override suspend fun promptBiometric(promptTitle: String, promptSubtitle: String): Boolean = true
    override fun copyToClipboard(label: String, value: String) = Unit
    override suspend fun startSsoAuth(): SsoAuthResult = SsoAuthResult(
        success = true,
        idToken = "preview-id-token",
        accessToken = "preview-access-token",
        email = "preview@comcast.com",
    )
    override fun loadPasswordEntries(): List<PasswordEntry> = _entries.value

    override fun deletePasswordEntry(id: String) {
        _entries.value = _entries.value.filter { it.id != id }
    }

    override fun updateNotes(id: String, notes: String) {
        _entries.value = _entries.value.map {
            if (it.id == id) it.copy(notes = notes) else it
        }
    }
}
