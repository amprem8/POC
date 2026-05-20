package com.example.poc

import kotlin.random.Random

enum class PassKeyRoute {
    Splash,
    CreateMasterPassword,
    EnableBiometric,
    RecoveryPhrase,
    Login,
    ForgotPassword,
    ResetPassword,
    Onboarding,   // First-time permission setup (shown once after first login)
    Main,
}

data class PassKeyMessage(
    val text: String,
    val isError: Boolean = false,
)

data class PassKeyConfig(
    val masterPassword: String,
    val biometricEnabled: Boolean,
    val recoveryPhrase: String,
    val recoveryPhraseAcknowledged: Boolean,
    val onboardingSeen: Boolean = false,   // true after user completes the first-time setup flow
)

data class PassKeyUiState(
    val route: PassKeyRoute = PassKeyRoute.Splash,
    val biometricAvailable: Boolean = false,
    val biometricEnabled: Boolean = false,
    val biometricRequired: Boolean = false,
    val recoveryPhrase: String = "",
    val message: PassKeyMessage? = null,
)

data class PassKeyActionResult(
    val uiState: PassKeyUiState,
    val persistedConfig: PassKeyConfig? = null,
)

private data class PendingSetup(
    val masterPassword: String,
    val recoveryPhrase: String,
)

class VaultAppController(
    private val recoveryPhraseProvider: () -> String = ::generateRecoveryPhrase,
) {
    private var currentConfig: PassKeyConfig? = null
    private var pendingSetup: PendingSetup? = null
    private var biometricSetupRequired = false
    private var recoveryVerified = false
    private var biometricAvailable = false

    fun bootstrap(savedConfig: PassKeyConfig?, biometricAvailable: Boolean): PassKeyUiState {
        currentConfig = savedConfig
        pendingSetup = null
        biometricSetupRequired = false
        recoveryVerified = false
        this.biometricAvailable = biometricAvailable

        return when {
            savedConfig == null -> PassKeyUiState(
                route = PassKeyRoute.CreateMasterPassword,
                biometricAvailable = biometricAvailable,
            )

            !savedConfig.recoveryPhraseAcknowledged -> PassKeyUiState(
                route = PassKeyRoute.RecoveryPhrase,
                biometricAvailable = biometricAvailable,
                biometricEnabled = savedConfig.biometricEnabled,
                recoveryPhrase = savedConfig.recoveryPhrase,
                message = PassKeyMessage("Save your recovery phrase. This is the only screen where it will be shown in full."),
            )

            else -> loginState()
        }
    }

    fun createMasterPassword(password: String, confirmPassword: String): PassKeyActionResult {
        val normalizedPassword = password.trim()
        val normalizedConfirmPassword = confirmPassword.trim()

        val validationMessage = validatePassword(normalizedPassword, normalizedConfirmPassword)
        if (validationMessage != null) {
            return PassKeyActionResult(
                uiState = PassKeyUiState(
                    route = PassKeyRoute.CreateMasterPassword,
                    biometricAvailable = biometricAvailable,
                    message = PassKeyMessage(validationMessage, isError = true),
                ),
            )
        }

        pendingSetup = PendingSetup(
            masterPassword = normalizedPassword,
            recoveryPhrase = recoveryPhraseProvider(),
        )
        biometricSetupRequired = biometricAvailable

        return if (biometricAvailable) {
            PassKeyActionResult(
                uiState = PassKeyUiState(
                    route = PassKeyRoute.EnableBiometric,
                    biometricAvailable = true,
                    biometricRequired = true,
                ),
            )
        } else {
            persistPendingSetup(enableBiometric = false)
        }
    }

    fun saveBiometricPreference(enabled: Boolean): PassKeyActionResult {
        if (biometricSetupRequired && !enabled) {
            return PassKeyActionResult(
                uiState = PassKeyUiState(
                    route = PassKeyRoute.EnableBiometric,
                    biometricAvailable = biometricAvailable,
                    biometricRequired = true,
                    message = PassKeyMessage(
                        "Fingerprint verification is required to finish first-time setup on this device.",
                        isError = true,
                    ),
                ),
            )
        }

        return persistPendingSetup(enableBiometric = enabled)
    }

    fun finishRecoveryPhraseStep(): PassKeyActionResult {
        val updatedConfig = currentConfig?.copy(recoveryPhraseAcknowledged = true)
            ?: return PassKeyActionResult(
                uiState = PassKeyUiState(
                    route = PassKeyRoute.CreateMasterPassword,
                    biometricAvailable = biometricAvailable,
                ),
            )

        currentConfig = updatedConfig
        pendingSetup = null
        biometricSetupRequired = false

        // First-time user: route to onboarding after recovery phrase step
        val targetRoute = if (!updatedConfig.onboardingSeen) PassKeyRoute.Onboarding else PassKeyRoute.Main
        return PassKeyActionResult(
            uiState = PassKeyUiState(
                route = targetRoute,
                biometricAvailable = biometricAvailable,
                biometricEnabled = updatedConfig.biometricEnabled,
            ),
            persistedConfig = updatedConfig,
        )
    }

    fun unlockWithPassword(password: String): PassKeyActionResult {
        val config = currentConfig
        if (config == null) {
            return PassKeyActionResult(
                uiState = PassKeyUiState(
                    route = PassKeyRoute.CreateMasterPassword,
                    biometricAvailable = biometricAvailable,
                ),
            )
        }

        return if (password == config.masterPassword) {
            val target = if (!config.onboardingSeen) PassKeyRoute.Onboarding else PassKeyRoute.Main
            PassKeyActionResult(
                uiState = PassKeyUiState(
                    route = target,
                    biometricAvailable = biometricAvailable,
                    biometricEnabled = config.biometricEnabled,
                ),
            )
        } else {
            PassKeyActionResult(
                uiState = loginState(message = PassKeyMessage("Incorrect master password.", isError = true)),
            )
        }
    }

    fun unlockWithBiometric(): PassKeyActionResult {
        val config = currentConfig
        return if (config?.biometricEnabled == true) {
            val target = if (!config.onboardingSeen) PassKeyRoute.Onboarding else PassKeyRoute.Main
            PassKeyActionResult(
                uiState = PassKeyUiState(
                    route = target,
                    biometricAvailable = biometricAvailable,
                    biometricEnabled = true,
                ),
            )
        } else {
            PassKeyActionResult(
                uiState = loginState(message = PassKeyMessage("Biometric unlock is not enabled on this device.", isError = true)),
            )
        }
    }

    fun openForgotPassword(): PassKeyUiState {
        recoveryVerified = false
        return PassKeyUiState(
            route = PassKeyRoute.ForgotPassword,
            biometricAvailable = biometricAvailable,
            biometricEnabled = currentConfig?.biometricEnabled == true,
        )
    }

    fun cancelForgotPassword(): PassKeyUiState {
        recoveryVerified = false
        return loginState()
    }

    fun verifyRecoveryPhrase(input: String): PassKeyUiState {
        val config = currentConfig
        if (config == null) {
            return PassKeyUiState(
                route = PassKeyRoute.CreateMasterPassword,
                biometricAvailable = biometricAvailable,
            )
        }

        val normalizedInput = input.trim().replace(Regex("\\s+"), " ")
        recoveryVerified = normalizedInput == config.recoveryPhrase

        return if (recoveryVerified) {
            PassKeyUiState(
                route = PassKeyRoute.ResetPassword,
                biometricAvailable = biometricAvailable,
                biometricEnabled = config.biometricEnabled,
                message = PassKeyMessage("Recovery phrase verified. Create a new master password."),
            )
        } else {
            PassKeyUiState(
                route = PassKeyRoute.ForgotPassword,
                biometricAvailable = biometricAvailable,
                biometricEnabled = config.biometricEnabled,
                message = PassKeyMessage("Recovery phrase does not match the one saved for this install.", isError = true),
            )
        }
    }

    fun saveResetPassword(password: String, confirmPassword: String): PassKeyActionResult {
        if (!recoveryVerified) {
            return PassKeyActionResult(
                uiState = PassKeyUiState(
                    route = PassKeyRoute.ForgotPassword,
                    biometricAvailable = biometricAvailable,
                    biometricEnabled = currentConfig?.biometricEnabled == true,
                    message = PassKeyMessage("Verify the recovery phrase before setting a new master password.", isError = true),
                ),
            )
        }

        val normalizedPassword = password.trim()
        val normalizedConfirmPassword = confirmPassword.trim()
        val validationMessage = validatePassword(normalizedPassword, normalizedConfirmPassword)
        if (validationMessage != null) {
            return PassKeyActionResult(
                uiState = PassKeyUiState(
                    route = PassKeyRoute.ResetPassword,
                    biometricAvailable = biometricAvailable,
                    biometricEnabled = currentConfig?.biometricEnabled == true,
                    message = PassKeyMessage(validationMessage, isError = true),
                ),
            )
        }

        val updatedConfig = currentConfig?.copy(masterPassword = normalizedPassword)
            ?: return PassKeyActionResult(
                uiState = PassKeyUiState(
                    route = PassKeyRoute.CreateMasterPassword,
                    biometricAvailable = biometricAvailable,
                ),
            )

        currentConfig = updatedConfig
        recoveryVerified = false

        val target = if (!updatedConfig.onboardingSeen) PassKeyRoute.Onboarding else PassKeyRoute.Main
        return PassKeyActionResult(
            uiState = PassKeyUiState(
                route = target,
                biometricAvailable = biometricAvailable,
                biometricEnabled = updatedConfig.biometricEnabled,
                message = PassKeyMessage("Master password updated successfully."),
            ),
            persistedConfig = updatedConfig,
        )
    }

    fun recoveryFileContent(appName: String): String {
        val phrase = currentConfig?.recoveryPhrase ?: pendingSetup?.recoveryPhrase.orEmpty()
        return buildString {
            appendLine("$appName Recovery")
            appendLine()
            appendLine("Recovery phrase:")
            appendLine(phrase)
            appendLine()
            appendLine("Store this file in a safe place. Use this phrase only from the Forgot Password screen inside the app.")
        }.trimEnd()
    }

    private fun persistPendingSetup(enableBiometric: Boolean): PassKeyActionResult {
        val setup = pendingSetup
            ?: return PassKeyActionResult(
                uiState = PassKeyUiState(
                    route = PassKeyRoute.CreateMasterPassword,
                    biometricAvailable = biometricAvailable,
                    message = PassKeyMessage("Start by creating a master password.", isError = true),
                ),
            )

        val config = PassKeyConfig(
            masterPassword = setup.masterPassword,
            biometricEnabled = enableBiometric,
            recoveryPhrase = setup.recoveryPhrase,
            recoveryPhraseAcknowledged = false,
        )
        currentConfig = config
        biometricSetupRequired = false

        return PassKeyActionResult(
            uiState = PassKeyUiState(
                route = PassKeyRoute.RecoveryPhrase,
                biometricAvailable = biometricAvailable,
                biometricEnabled = enableBiometric,
                recoveryPhrase = config.recoveryPhrase,
                message = PassKeyMessage("Copy or download your recovery phrase before continuing."),
            ),
            persistedConfig = config,
        )
    }

    private fun loginState(message: PassKeyMessage? = null): PassKeyUiState {
        val config = currentConfig
        return PassKeyUiState(
            route = PassKeyRoute.Login,
            biometricAvailable = biometricAvailable,
            biometricEnabled = config?.biometricEnabled == true,
            message = message,
        )
    }

    private fun validatePassword(password: String, confirmPassword: String): String? {
        return when {
            password.isBlank() -> "Enter a master password to continue."
            password.length < 8 -> "Master password must be at least 8 characters long."
            confirmPassword.isBlank() -> "Confirm the master password to continue."
            password != confirmPassword -> "Master passwords do not match."
            else -> null
        }
    }
}

private val recoveryWords = listOf(
    "amber", "anchor", "aster", "banner", "birch", "canyon", "cinder", "clover",
    "coral", "ember", "falcon", "frost", "glimmer", "harbor", "hazel", "indigo",
    "juniper", "lagoon", "linen", "meadow", "nectar", "onyx", "orchid", "parade",
    "pebble", "quartz", "raven", "saffron", "spruce", "summit", "thunder", "timber",
    "topaz", "trident", "velvet", "willow",
)

fun generateRecoveryPhrase(wordCount: Int = 12, random: Random = Random.Default): String {
    return List(wordCount) {
        recoveryWords[random.nextInt(recoveryWords.size)]
    }.joinToString(" ")
}

