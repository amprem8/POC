package com.example.poc

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
@Preview
fun App(platformServices: PlatformServices = PreviewPlatformServices()) {
    VaultTheme {
        var showSplash by rememberSaveable { mutableStateOf(true) }
        val controller = remember { VaultAppController() }
        var uiState by remember { mutableStateOf(VaultUiState()) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            delay(1100)
            showSplash = false
            uiState = controller.bootstrap(
                savedConfig = platformServices.loadVaultConfig(),
                biometricAvailable = platformServices.biometricAvailable,
            )
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            AnimatedContent(targetState = if (showSplash) VaultRoute.Splash else uiState.route, label = "vault-root") { route ->
                when (route) {
                    VaultRoute.Splash -> OpeningSplashScreen()
                    VaultRoute.CreateMasterPassword -> CreateMasterPasswordScreen(
                        message = uiState.message,
                        onContinue = { password, confirmPassword ->
                            applyActionResult(
                                result = controller.createMasterPassword(password, confirmPassword),
                                platformServices = platformServices,
                            ) { uiState = it }
                        },
                    )

                    VaultRoute.EnableBiometric -> EnableBiometricScreen(
                        message = uiState.message,
                        onEnableBiometric = {
                            scope.launch {
                                val success = platformServices.promptBiometric(
                                    promptTitle = "Enable Touch ID",
                                    promptSubtitle = "Use biometric unlock for faster access to Vault",
                                )
                                if (success) {
                                    applyActionResult(
                                        result = controller.saveBiometricPreference(enabled = true),
                                        platformServices = platformServices,
                                    ) { uiState = it }
                                } else {
                                    uiState = uiState.copy(
                                        route = VaultRoute.EnableBiometric,
                                        biometricRequired = true,
                                        message = VaultMessage("Fingerprint verification was cancelled. Complete it to finish first-time setup.", isError = true),
                                    )
                                }
                            }
                        },
                    )

                    VaultRoute.RecoveryPhrase -> RecoveryPhraseScreen(
                        recoveryPhrase = uiState.recoveryPhrase,
                        message = uiState.message,
                        onCopy = {
                            platformServices.copyToClipboard("$AppName Recovery", uiState.recoveryPhrase)
                            uiState = uiState.copy(message = VaultMessage("Recovery phrase copied."))
                        },
                        onDownload = {
                            val location = platformServices.saveRecoveryTextFile(
                                fileName = "$AppName Recovery.txt",
                                content = controller.recoveryFileContent(AppName),
                            )
                            uiState = uiState.copy(message = VaultMessage(location))
                        },
                        onContinue = {
                            applyActionResult(
                                result = controller.finishRecoveryPhraseStep(),
                                platformServices = platformServices,
                            ) { uiState = it }
                        },
                    )

                    VaultRoute.Login -> LoginScreen(
                        biometricEnabled = uiState.biometricEnabled,
                        message = uiState.message,
                        onBiometricLogin = {
                            scope.launch {
                                runBiometricUnlock(
                                    platformServices = platformServices,
                                    controller = controller,
                                    onUiState = { uiState = it },
                                )
                            }
                        },
                        onPasswordLogin = { password ->

                            val result = controller.unlockWithPassword(password)

                            if (result.uiState.route == VaultRoute.Main) {
                                platformServices.enableOverlayMonitoringAfterLogin()
                            }

                            applyActionResult(
                                result = result,
                                platformServices = platformServices,
                            ) {
                                uiState = it
                            }
                        },
                        onForgotPassword = { uiState = controller.openForgotPassword() },
                    )

                    VaultRoute.ForgotPassword -> ForgotPasswordScreen(
                        message = uiState.message,
                        onVerify = { phrase -> uiState = controller.verifyRecoveryPhrase(phrase) },
                        onBackToLogin = { uiState = controller.cancelForgotPassword() },
                    )

                    VaultRoute.ResetPassword -> CreateMasterPasswordScreen(
                        message = uiState.message,
                        title = "Reset master password",
                        subtitle = "Your recovery phrase was verified. Set a new master password for this install.",
                        helperText = "Use the same screen to save the new password you will use on future logins.",
                        primaryButtonText = "Save new password",
                        footerText = "After saving, you can unlock with either Touch ID or the new master password.",
                        onContinue = { password, confirmPassword ->
                            applyActionResult(
                                result = controller.saveResetPassword(password, confirmPassword),
                                platformServices = platformServices,
                            ) { uiState = it }
                        },
                    )

                    VaultRoute.Main -> BlankAuthenticatedScreen(platformServices = platformServices)

                    VaultRoute.Onboarding -> PlatformOnboardingScreen(
                        onFinish = {
                            // Mark onboarding seen, persist, then go to Main vault
                            val cfg = platformServices.loadVaultConfig()
                            if (cfg != null) {
                                platformServices.saveVaultConfig(cfg.copy(onboardingSeen = true))
                            }
                            uiState = uiState.copy(route = VaultRoute.Main)
                        }
                    )
                }
            }
        }
    }
}

private fun applyActionResult(
    result: VaultActionResult,
    platformServices: PlatformServices,
    onUiState: (VaultUiState) -> Unit,
) {
    result.persistedConfig?.let(platformServices::saveVaultConfig)
    onUiState(result.uiState)
}

private suspend fun runBiometricUnlock(
    platformServices: PlatformServices,
    controller: VaultAppController,
    onUiState: (VaultUiState) -> Unit,
) {
    val success = platformServices.promptBiometric(
        promptTitle = "Unlock with Touch ID",
        promptSubtitle = "Authenticate to open Vault",
    )

    if (success) {

        val result = controller.unlockWithBiometric()

        if (result.uiState.route == VaultRoute.Main) {
            platformServices.enableOverlayMonitoringAfterLogin()
        }

        applyActionResult(
            result = result,
            platformServices = platformServices,
            onUiState = onUiState,
        )
    } else {
        onUiState(
            VaultUiState(
                route = VaultRoute.Login,
                biometricAvailable = platformServices.biometricAvailable,
                biometricEnabled = platformServices.loadVaultConfig()?.biometricEnabled == true,
                message = VaultMessage("Biometric authentication was cancelled.", isError = true),
            ),
        )
    }
}


