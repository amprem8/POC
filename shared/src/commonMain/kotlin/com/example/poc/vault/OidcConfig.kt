package com.example.poc.vault

/**
 * OpenID Connect configuration for Comcast SSO (Azure AD).
 * Application Type: Mobile Native App (Authorization Code with PKCE)
 */
object OidcConfig {
    const val CLIENT_ID = "8d3e07dd-4d1a-478e-964e-d4cc6089856a"
    const val REDIRECT_URI = "http://localhost:8769/auth/callback"

    /**
     * Use "organizations" instead of a specific tenant ID.
     * This allows any Azure AD organizational account to authenticate,
     * and Azure AD will route to the correct tenant based on the user's email.
     * The app registration in Comcast's tenant will handle validation.
     */
    const val TENANT = "organizations"

    /** Azure AD OIDC endpoints */
    const val AUTHORITY = "https://login.microsoftonline.com/$TENANT"
    const val AUTHORIZATION_ENDPOINT = "$AUTHORITY/oauth2/v2.0/authorize"
    const val TOKEN_ENDPOINT = "$AUTHORITY/oauth2/v2.0/token"

    /** Scopes: openid for ID token, profile for name, email for email claim */
    const val SCOPES = "openid profile email offline_access"
}

/**
 * Result from an SSO authentication attempt.
 */
data class SsoAuthResult(
    val success: Boolean,
    val idToken: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val email: String? = null,
    val error: String? = null,
)

