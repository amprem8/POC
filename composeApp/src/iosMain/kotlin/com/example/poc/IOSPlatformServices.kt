package com.example.poc

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import platform.UIKit.UIPasteboard
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val PassKeyConfigKey = "passkey_config"
private const val PasswordEntriesKey = "password_entries"

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IOSPlatformServices : PlatformServices {
    private val defaults = NSUserDefaults.standardUserDefaults
    private val _entries = MutableStateFlow<List<PasswordEntry>>(emptyList())

    override val serverBaseUrl: String = "http://127.0.0.1:$SERVER_PORT"
    override val biometricAvailable: Boolean
        get() = LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, null)
    override val entriesFlow: StateFlow<List<PasswordEntry>> = _entries.asStateFlow()

    init { _entries.value = loadPasswordEntries() }

    override fun loadPassKeyConfig(): PassKeyConfig? {
        return decodePassKeyConfig(defaults.stringForKey(PassKeyConfigKey))
    }

    override fun savePassKeyConfig(config: PassKeyConfig) {
        defaults.setObject(encodePassKeyConfig(config), forKey = PassKeyConfigKey)
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

    override fun saveRecoveryTextFile(fileName: String, content: String): String {
        val downloadsPath = NSHomeDirectory() + "/Downloads"
        NSFileManager.defaultManager.createDirectoryAtPath(downloadsPath, true, null, null)
        val path = "$downloadsPath/$fileName"
        NSFileManager.defaultManager.createFileAtPath(path, content.encodeToByteArray().toNSData(), null)
        return "Saved recovery file to $path"
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
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun savePasswordEntry(entry: PasswordEntry) {
        val current = loadPasswordEntries().toMutableList()
        current.removeAll { it.id == entry.id }
        current.add(entry)
        defaults.setObject(encodeEntries(current), forKey = PasswordEntriesKey)
        _entries.value = current
    }

    override fun deletePasswordEntry(id: String) {
        val current = loadPasswordEntries().filter { it.id != id }
        defaults.setObject(encodeEntries(current), forKey = PasswordEntriesKey)
        _entries.value = current
    }

    private fun encodeEntries(entries: List<PasswordEntry>): String =
        entries.joinToString("||ENTRY||") { e ->
            listOf("id=${e.id}", "siteName=${e.siteName}", "username=${e.username}",
                   "password=${e.password}", "loginUrl=${e.loginUrl}", "dateModified=${e.dateModified}")
                .joinToString("||FIELD||")
        }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData {
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}





