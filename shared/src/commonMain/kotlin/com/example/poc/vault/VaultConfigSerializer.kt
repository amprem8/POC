package com.example.poc.vault

/**
 * Simple pipe-delimited serialization for VaultConfig.
 * Format: biometricEnabled|ssoAuthenticated|ssoEmail|onboardingSeen|ssoTokenTimestamp
 *
 * NOTE: SSO tokens are NO LONGER persisted. Only session metadata is stored.
 */
fun encodeVaultConfig(config: VaultConfig): String {
    return listOf(
        config.biometricEnabled.toString(),
        config.ssoAuthenticated.toString(),
        config.ssoEmail.orEmpty(),
        config.onboardingSeen.toString(),
        config.ssoTokenTimestamp.toString(),
    ).joinToString("|")
}

fun decodeVaultConfig(encoded: String?): VaultConfig? {
    if (encoded.isNullOrBlank()) return null
    return try {
        val parts = encoded.split("|")
        // Require at least 4 parts for the new format
        if (parts.size < 4) return null
        // Format check: first part should be "true" or "false" (biometricEnabled)
        val firstPartIsBoolean = parts[0] == "true" || parts[0] == "false"
        if (!firstPartIsBoolean) {
            // Old format detected – force fresh setup
            return null
        }

        // Handle both old format (6 parts with ssoToken) and new format (5 parts without)
        if (parts.size >= 6) {
            // Old format: biometricEnabled|ssoAuthenticated|ssoToken|ssoEmail|onboardingSeen|ssoTokenTimestamp
            // Migration: skip the ssoToken field (parts[2])
            VaultConfig(
                biometricEnabled = parts[0].toBoolean(),
                ssoAuthenticated = parts[1].toBoolean(),
                ssoEmail = parts[3].ifEmpty { null },
                onboardingSeen = parts[4].toBoolean(),
                ssoTokenTimestamp = parts.getOrNull(5)?.toLongOrNull() ?: 0L,
            )
        } else {
            // New format: biometricEnabled|ssoAuthenticated|ssoEmail|onboardingSeen|ssoTokenTimestamp
            VaultConfig(
                biometricEnabled = parts[0].toBoolean(),
                ssoAuthenticated = parts[1].toBoolean(),
                ssoEmail = parts[2].ifEmpty { null },
                onboardingSeen = parts[3].toBoolean(),
                ssoTokenTimestamp = parts.getOrNull(4)?.toLongOrNull() ?: 0L,
            )
        }
    } catch (e: Exception) {
        null
    }
}
