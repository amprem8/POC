package com.example.poc.vault

/**
 * Simple pipe-delimited serialization for VaultConfig.
 * Format: biometricEnabled|ssoAuthenticated|ssoToken|ssoEmail|onboardingSeen|ssoTokenTimestamp
 */
fun encodeVaultConfig(config: VaultConfig): String {
    return listOf(
        config.biometricEnabled.toString(),
        config.ssoAuthenticated.toString(),
        config.ssoToken.orEmpty(),
        config.ssoEmail.orEmpty(),
        config.onboardingSeen.toString(),
        config.ssoTokenTimestamp.toString(),
    ).joinToString("|")
}

fun decodeVaultConfig(encoded: String?): VaultConfig? {
    if (encoded.isNullOrBlank()) return null
    return try {
        val parts = encoded.split("|")
        // Require at least 5 parts for backward compatibility
        if (parts.size < 5) return null
        // New format check: first part should be "true" or "false" (biometricEnabled)
        val firstPartIsBoolean = parts[0] == "true" || parts[0] == "false"
        if (!firstPartIsBoolean) {
            // Old format detected – force fresh setup
            return null
        }
        VaultConfig(
            biometricEnabled = parts[0].toBoolean(),
            ssoAuthenticated = parts[1].toBoolean(),
            ssoToken = parts[2].ifEmpty { null },
            ssoEmail = parts[3].ifEmpty { null },
            onboardingSeen = parts[4].toBoolean(),
            ssoTokenTimestamp = parts.getOrNull(5)?.toLongOrNull() ?: 0L,
        )
    } catch (e: Exception) {
        null
    }
}
