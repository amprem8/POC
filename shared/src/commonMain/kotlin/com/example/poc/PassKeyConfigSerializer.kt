package com.example.poc

/**
 * Simple pipe-delimited serialization for PassKeyConfig.
 * Format: masterPassword|biometricEnabled|recoveryPhrase|recoveryPhraseAcknowledged
 */
fun encodePassKeyConfig(config: PassKeyConfig): String {
    return listOf(
        config.masterPassword,
        config.biometricEnabled.toString(),
        config.recoveryPhrase,
        config.recoveryPhraseAcknowledged.toString(),
    ).joinToString("|")
}

fun decodePassKeyConfig(encoded: String?): PassKeyConfig? {
    if (encoded.isNullOrBlank()) return null
    return try {
        val parts = encoded.split("|")
        if (parts.size < 4) return null
        PassKeyConfig(
            masterPassword = parts[0],
            biometricEnabled = parts[1].toBoolean(),
            recoveryPhrase = parts[2],
            recoveryPhraseAcknowledged = parts[3].toBoolean(),
        )
    } catch (e: Exception) {
        null
    }
}

