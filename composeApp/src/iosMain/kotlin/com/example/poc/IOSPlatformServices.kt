package com.example.poc

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
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

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IOSPlatformServices : PlatformServices {
    private val defaults = NSUserDefaults.standardUserDefaults

    override val serverBaseUrl: String = "http://127.0.0.1:$SERVER_PORT"
    override val biometricAvailable: Boolean
        get() = LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, null)

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
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData {
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}





