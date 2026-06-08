package com.example.poc.vault

import kotlin.random.Random

enum class VaultRoute {
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

data class VaultMessage(
    val text: String,
    val isError: Boolean = false,
)

data class VaultConfig(
    val masterPassword: String,
    val biometricEnabled: Boolean,
    val recoveryPhrase: String,
    val recoveryPhraseAcknowledged: Boolean,
    val onboardingSeen: Boolean = false,   // true after user completes the first-time setup flow
)

data class VaultUiState(
    val route: VaultRoute = VaultRoute.Splash,
    val biometricAvailable: Boolean = false,
    val biometricEnabled: Boolean = false,
    val biometricRequired: Boolean = false,
    val recoveryPhrase: String = "",
    val message: VaultMessage? = null,
)

data class VaultActionResult(
    val uiState: VaultUiState,
    val persistedConfig: VaultConfig? = null,
)

private data class PendingSetup(
    val masterPassword: String,
    val recoveryPhrase: String,
)

class VaultAppController(
    private val recoveryPhraseProvider: () -> String = ::generateRecoveryPhrase,
) {
    private var currentConfig: VaultConfig? = null
    private var pendingSetup: PendingSetup? = null
    private var biometricSetupRequired = false
    private var recoveryVerified = false
    private var biometricAvailable = false

    fun bootstrap(savedConfig: VaultConfig?, biometricAvailable: Boolean): VaultUiState {
        currentConfig = savedConfig
        pendingSetup = null
        biometricSetupRequired = false
        recoveryVerified = false
        this.biometricAvailable = biometricAvailable

        return when {
            savedConfig == null -> VaultUiState(
                route = VaultRoute.CreateMasterPassword,
                biometricAvailable = biometricAvailable,
            )

            !savedConfig.recoveryPhraseAcknowledged -> VaultUiState(
                route = VaultRoute.RecoveryPhrase,
                biometricAvailable = biometricAvailable,
                biometricEnabled = savedConfig.biometricEnabled,
                recoveryPhrase = savedConfig.recoveryPhrase,
                message = VaultMessage("Save your recovery phrase. This is the only screen where it will be shown in full."),
            )

            else -> loginState()
        }
    }

    fun createMasterPassword(password: String, confirmPassword: String): VaultActionResult {
        val normalizedPassword = password.trim()
        val normalizedConfirmPassword = confirmPassword.trim()

        val validationMessage = validatePassword(normalizedPassword, normalizedConfirmPassword)
        if (validationMessage != null) {
            return VaultActionResult(
                uiState = VaultUiState(
                    route = VaultRoute.CreateMasterPassword,
                    biometricAvailable = biometricAvailable,
                    message = VaultMessage(validationMessage, isError = true),
                ),
            )
        }

        pendingSetup = PendingSetup(
            masterPassword = normalizedPassword,
            recoveryPhrase = recoveryPhraseProvider(),
        )
        biometricSetupRequired = biometricAvailable

        return if (biometricAvailable) {
            VaultActionResult(
                uiState = VaultUiState(
                    route = VaultRoute.EnableBiometric,
                    biometricAvailable = true,
                    biometricRequired = true,
                ),
            )
        } else {
            persistPendingSetup(enableBiometric = false)
        }
    }

    fun saveBiometricPreference(enabled: Boolean): VaultActionResult {
        if (biometricSetupRequired && !enabled) {
            return VaultActionResult(
                uiState = VaultUiState(
                    route = VaultRoute.EnableBiometric,
                    biometricAvailable = biometricAvailable,
                    biometricRequired = true,
                    message = VaultMessage(
                        "Fingerprint verification is required to finish first-time setup on this device.",
                        isError = true,
                    ),
                ),
            )
        }

        return persistPendingSetup(enableBiometric = enabled)
    }

    fun finishRecoveryPhraseStep(): VaultActionResult {
        val updatedConfig = currentConfig?.copy(recoveryPhraseAcknowledged = true)
            ?: return VaultActionResult(
                uiState = VaultUiState(
                    route = VaultRoute.CreateMasterPassword,
                    biometricAvailable = biometricAvailable,
                ),
            )

        currentConfig = updatedConfig
        pendingSetup = null
        biometricSetupRequired = false

        // First-time user: route to onboarding after recovery phrase step
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

    fun unlockWithPassword(password: String): VaultActionResult {
        val config = currentConfig
        if (config == null) {
            return VaultActionResult(
                uiState = VaultUiState(
                    route = VaultRoute.CreateMasterPassword,
                    biometricAvailable = biometricAvailable,
                ),
            )
        }

        return if (password == config.masterPassword) {
            val target = if (!config.onboardingSeen) VaultRoute.Onboarding else VaultRoute.Main
            VaultActionResult(
                uiState = VaultUiState(
                    route = target,
                    biometricAvailable = biometricAvailable,
                    biometricEnabled = config.biometricEnabled,
                ),
            )
        } else {
            VaultActionResult(
                uiState = loginState(message = VaultMessage("Incorrect master password.", isError = true)),
            )
        }
    }

    fun unlockWithBiometric(): VaultActionResult {
        val config = currentConfig
        return if (config?.biometricEnabled == true) {
            val target = if (!config.onboardingSeen) VaultRoute.Onboarding else VaultRoute.Main
            VaultActionResult(
                uiState = VaultUiState(
                    route = target,
                    biometricAvailable = biometricAvailable,
                    biometricEnabled = true,
                ),
            )
        } else {
            VaultActionResult(
                uiState = loginState(message = VaultMessage("Biometric unlock is not enabled on this device.", isError = true)),
            )
        }
    }

    fun openForgotPassword(): VaultUiState {
        recoveryVerified = false
        return VaultUiState(
            route = VaultRoute.ForgotPassword,
            biometricAvailable = biometricAvailable,
            biometricEnabled = currentConfig?.biometricEnabled == true,
        )
    }

    fun cancelForgotPassword(): VaultUiState {
        recoveryVerified = false
        return loginState()
    }

    fun verifyRecoveryPhrase(input: String): VaultUiState {
        val config = currentConfig
        if (config == null) {
            return VaultUiState(
                route = VaultRoute.CreateMasterPassword,
                biometricAvailable = biometricAvailable,
            )
        }

        val normalizedInput = input.trim().replace(Regex("\\s+"), " ")
        recoveryVerified = normalizedInput == config.recoveryPhrase

        return if (recoveryVerified) {
            VaultUiState(
                route = VaultRoute.ResetPassword,
                biometricAvailable = biometricAvailable,
                biometricEnabled = config.biometricEnabled,
                message = VaultMessage("Recovery phrase verified. Create a new master password."),
            )
        } else {
            VaultUiState(
                route = VaultRoute.ForgotPassword,
                biometricAvailable = biometricAvailable,
                biometricEnabled = config.biometricEnabled,
                message = VaultMessage("Recovery phrase does not match the one saved for this install.", isError = true),
            )
        }
    }

    fun saveResetPassword(password: String, confirmPassword: String): VaultActionResult {
        if (!recoveryVerified) {
            return VaultActionResult(
                uiState = VaultUiState(
                    route = VaultRoute.ForgotPassword,
                    biometricAvailable = biometricAvailable,
                    biometricEnabled = currentConfig?.biometricEnabled == true,
                    message = VaultMessage("Verify the recovery phrase before setting a new master password.", isError = true),
                ),
            )
        }

        val normalizedPassword = password.trim()
        val normalizedConfirmPassword = confirmPassword.trim()
        val validationMessage = validatePassword(normalizedPassword, normalizedConfirmPassword)
        if (validationMessage != null) {
            return VaultActionResult(
                uiState = VaultUiState(
                    route = VaultRoute.ResetPassword,
                    biometricAvailable = biometricAvailable,
                    biometricEnabled = currentConfig?.biometricEnabled == true,
                    message = VaultMessage(validationMessage, isError = true),
                ),
            )
        }

        val updatedConfig = currentConfig?.copy(masterPassword = normalizedPassword)
            ?: return VaultActionResult(
                uiState = VaultUiState(
                    route = VaultRoute.CreateMasterPassword,
                    biometricAvailable = biometricAvailable,
                ),
            )

        currentConfig = updatedConfig
        recoveryVerified = false

        val target = if (!updatedConfig.onboardingSeen) VaultRoute.Onboarding else VaultRoute.Main
        return VaultActionResult(
            uiState = VaultUiState(
                route = target,
                biometricAvailable = biometricAvailable,
                biometricEnabled = updatedConfig.biometricEnabled,
                message = VaultMessage("Master password updated successfully."),
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

    private fun persistPendingSetup(enableBiometric: Boolean): VaultActionResult {
        val setup = pendingSetup
            ?: return VaultActionResult(
                uiState = VaultUiState(
                    route = VaultRoute.CreateMasterPassword,
                    biometricAvailable = biometricAvailable,
                    message = VaultMessage("Start by creating a master password.", isError = true),
                ),
            )

        val config = VaultConfig(
            masterPassword = setup.masterPassword,
            biometricEnabled = enableBiometric,
            recoveryPhrase = setup.recoveryPhrase,
            recoveryPhraseAcknowledged = false,
        )
        currentConfig = config
        biometricSetupRequired = false

        return VaultActionResult(
            uiState = VaultUiState(
                route = VaultRoute.RecoveryPhrase,
                biometricAvailable = biometricAvailable,
                biometricEnabled = enableBiometric,
                recoveryPhrase = config.recoveryPhrase,
                message = VaultMessage("Copy or download your recovery phrase before continuing."),
            ),
            persistedConfig = config,
        )
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

