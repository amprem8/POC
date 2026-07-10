package com.example.poc.vault

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH

@OptIn(ExperimentalForeignApi::class)
actual fun sha256AndBase64Url(input: String): String {
    val inputBytes = input.encodeToByteArray()
    val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)
    inputBytes.usePinned { pinnedInput ->
        digest.usePinned { pinnedDigest ->
            CC_SHA256(pinnedInput.addressOf(0), inputBytes.size.convert(), pinnedDigest.addressOf(0))
        }
    }
    return digest.toUrlSafeBase64()
}
