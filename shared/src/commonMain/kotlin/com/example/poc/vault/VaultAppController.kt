package com.example.poc.vault

enum class VaultRoute {
    Splash,
    EnableBiometric,
    SsoSetup,        // First-time SSO configuration (Azure AD OIDC + PKCE)
    Login,
    Onboarding,      // First-time permission setup (shown once after first login)
    Main,
}

data class VaultMessage(
    val text: String,
    val isError: Boolean = false,
)

data class VaultConfig(
    val biometricEnabled: Boolean,
    val ssoAuthenticated: Boolean = false,
    // SSO tokens are NO LONGER persisted — kept in-memory only during the session.
    // Only the email (for xVault user-id) and timestamp (for session validity) are stored.
    val ssoEmail: String? = null,
    val onboardingSeen: Boolean = false,
    val ssoTokenTimestamp: Long = 0L, // epoch millis when SSO session was established
) {
    companion object {
        /** SSO session validity duration: 6 hours in milliseconds. */
        const val SSO_SESSION_DURATION_MS = 6 * 60 * 60 * 1000L
    }

    /** Returns true if the SSO token is still valid (obtained less than 6 hours ago). */
    fun isSsoSessionValid(nowMillis: Long): Boolean {
        if (!ssoAuthenticated || ssoTokenTimestamp == 0L) return false
        val elapsed = nowMillis - ssoTokenTimestamp
        return elapsed in 0..SSO_SESSION_DURATION_MS
    }
}

data class VaultUiState(
    val route: VaultRoute = VaultRoute.Splash,
    val biometricAvailable: Boolean = false,
    val biometricEnabled: Boolean = false,
    val biometricRequired: Boolean = false,
    val message: VaultMessage? = null,
)

data class VaultActionResult(
    val uiState: VaultUiState,
    val persistedConfig: VaultConfig? = null,
)

class VaultAppController {
    private var currentConfig: VaultConfig? = null
    private var biometricAvailable = false

    fun bootstrap(savedConfig: VaultConfig?, biometricAvailable: Boolean, nowMillis: Long): VaultUiState {
        currentConfig = savedConfig
        this.biometricAvailable = biometricAvailable

        return when {
            // Fresh install – start with biometric setup
            savedConfig == null -> VaultUiState(
                route = VaultRoute.EnableBiometric,
                biometricAvailable = biometricAvailable,
                biometricRequired = true,
            )
            // Existing user — ALWAYS require SSO login on every app open.
            // The user must authenticate via Comcast SSO each time.
            // biometric + onboarding are already done, so go straight to Login.
            else -> loginState()
        }
    }

    /**
     * Called after successful biometric verification during first-time setup.
     * Proceeds to SSO setup.
     */
    fun saveBiometricPreference(enabled: Boolean): VaultActionResult {
        val config = VaultConfig(
            biometricEnabled = enabled,
            ssoAuthenticated = false,
        )
        currentConfig = config

        return VaultActionResult(
            uiState = VaultUiState(
                route = VaultRoute.SsoSetup,
                biometricAvailable = biometricAvailable,
                biometricEnabled = enabled,
                message = VaultMessage("Fingerprint configured successfully. Now sign in with Comcast SSO."),
            ),
            persistedConfig = config,
        )
    }

    /**
     * Called after user completes SSO sign-in via Azure AD OIDC + PKCE.
     * Stores the real SSO token and email, proceeds to onboarding or main.
     */
    fun completeSsoSetup(email: String, nowMillis: Long): VaultActionResult {
        val updatedConfig = (currentConfig ?: VaultConfig(biometricEnabled = true)).copy(
            ssoAuthenticated = true,
            ssoEmail = email,
            ssoTokenTimestamp = nowMillis,
        )
        currentConfig = updatedConfig

        val targetRoute = if (!updatedConfig.onboardingSeen) VaultRoute.Onboarding else VaultRoute.Main
        return VaultActionResult(
            uiState = VaultUiState(
                route = targetRoute,
                biometricAvailable = biometricAvailable,
                biometricEnabled = updatedConfig.biometricEnabled,
            ),
            persistedConfig = updatedConfig,
        )
    }

    /**
     * Unlock via biometric on login screen.
     * Only allowed if the SSO session is still valid (within 6 hours).
     */
    fun unlockWithBiometric(nowMillis: Long): VaultActionResult {
        val config = currentConfig
        return if (config?.biometricEnabled == true) {
            if (!config.isSsoSessionValid(nowMillis)) {
                // SSO session expired – force re-authentication via SSO
                VaultActionResult(
                    uiState = loginState(
                        message = VaultMessage(
                            "Your session has expired. Please sign in with Comcast SSO to continue.",
                            isError = true,
                        ),
                    ),
                )
            } else {
                val target = if (!config.onboardingSeen) VaultRoute.Onboarding else VaultRoute.Main
                VaultActionResult(
                    uiState = VaultUiState(
                        route = target,
                        biometricAvailable = biometricAvailable,
                        biometricEnabled = true,
                    ),
                )
            }
        } else {
            VaultActionResult(
                uiState = loginState(message = VaultMessage("Biometric unlock is not enabled on this device.", isError = true)),
            )
        }
    }

    /**
     * Unlock via SSO on login screen — validates that SSO was previously configured.
     */
    fun unlockWithSso(): VaultActionResult {
        val config = currentConfig
        return if (config?.ssoAuthenticated == true) {
            val target = if (!config.onboardingSeen) VaultRoute.Onboarding else VaultRoute.Main
            VaultActionResult(
                uiState = VaultUiState(
                    route = target,
                    biometricAvailable = biometricAvailable,
                    biometricEnabled = config.biometricEnabled,
                    message = VaultMessage("Signed in with Comcast SSO."),
                ),
            )
        } else {
            VaultActionResult(
                uiState = loginState(message = VaultMessage("SSO is not configured. Please reinstall and set up.", isError = true)),
            )
        }
    }

    private fun loginState(message: VaultMessage? = null): VaultUiState {
        val config = currentConfig
        return VaultUiState(
            route = VaultRoute.Login,
            biometricAvailable = biometricAvailable,
            biometricEnabled = config?.biometricEnabled == true,
            message = message,
        )
    }
}
