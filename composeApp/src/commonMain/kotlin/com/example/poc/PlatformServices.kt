package com.example.poc

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
    fun saveRecoveryTextFile(fileName: String, content: String): String
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
    override fun saveRecoveryTextFile(fileName: String, content: String): String = "Preview saved: $fileName"
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
