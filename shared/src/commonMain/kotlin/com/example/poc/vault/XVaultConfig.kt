package com.example.poc.vault

/**
 * Configuration for Comcast xVault (HashiCorp Vault-compatible) backend.
 *
 * Credentials are stored per-user under KV2 engine:
 *   Path: kv2/data/users/{userId}/{entryId}
 *
 * Authentication: AppRole (role_id + secret_id → client_token)
 */
object XVaultConfig {
    const val BASE_URL = "https://prod.xvault.comcast.com"
    const val NAMESPACE = "csse-passwordmanager"

    // AppRole credentials for the password-manager service
    const val ROLE_ID = "f13edb68-2cd9-42fd-b541-93576b818774"
    const val SECRET_ID = "cfc3fbee-9d3f-4315-a37f-885f074bf07e"

    // API paths (relative to BASE_URL)
    const val AUTH_APPROLE_LOGIN = "/xvault/v1/auth/approle/login"
    const val KV2_DATA_PREFIX = "/xvault/v1/kv2/data/users"
    const val KV2_METADATA_PREFIX = "/xvault/v1/kv2/metadata/users"

    /**
     * Derive a vault user-id from the SSO email.
     * e.g. "pavudi605@apac.comcast.com" → "pavudi605"
     */
    fun userIdFromEmail(email: String): String =
        email.substringBefore("@").lowercase().trim()

    /**
     * Sanitize a credential key for use as a vault path segment.
     * e.g. "login.xfinity.com" → "login-xfinity-com"
     */
    fun sanitizePathSegment(value: String): String =
        value.lowercase()
            .replace(Regex("[^a-z0-9-]"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
}

