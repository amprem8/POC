package com.example.poc.vault

import java.security.MessageDigest

actual fun sha256AndBase64Url(input: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(input.encodeToByteArray())
    return bytes.toUrlSafeBase64()
}
