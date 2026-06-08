package com.example.poc.vault

/**
 * Simple pipe-delimited serialization for VaultConfig.
 * Format: masterPassword|biometricEnabled|recoveryPhrase|recoveryPhraseAcknowledged|onboardingSeen
 * Backward-compatible: older 4-part strings decode with onboardingSeen=false.
 */
fun encodeVaultConfig(config: VaultConfig): String {
    return listOf(
        config.masterPassword,
        config.biometricEnabled.toString(),
        config.recoveryPhrase,
        config.recoveryPhraseAcknowledged.toString(),
        config.onboardingSeen.toString(),
    ).joinToString("|")
}

fun decodeVaultConfig(encoded: String?): VaultConfig? {
    if (encoded.isNullOrBlank()) return null
    return try {
        val parts = encoded.split("|")
        if (parts.size < 4) return null
        VaultConfig(
            masterPassword = parts[0],
            biometricEnabled = parts[1].toBoolean(),
            recoveryPhrase = parts[2],
            recoveryPhraseAcknowledged = parts[3].toBoolean(),
            onboardingSeen = parts.getOrNull(4)?.toBoolean() ?: false,
        )
    } catch (e: Exception) {
        null
    }
}
