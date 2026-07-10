package com.example.poc.vault

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
            val bootstrapState = controller.bootstrap(
                savedConfig = platformServices.loadVaultConfig(),
                biometricAvailable = platformServices.biometricAvailable,
                nowMillis = currentTimeMillis(),
            )
            uiState = bootstrapState
            // If session is still valid and auto-resumed to Main, enable overlay monitoring
            if (bootstrapState.route == VaultRoute.Main) {
                platformServices.enableOverlayMonitoringAfterLogin()
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            AnimatedContent(targetState = if (showSplash) VaultRoute.Splash else uiState.route, label = "vault-root") { route ->
                when (route) {
                    VaultRoute.Splash -> OpeningSplashScreen()

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

                    VaultRoute.SsoSetup -> SsoSetupScreen(
                        message = uiState.message,
                        onSignInWithSso = {
                            scope.launch {
                                // Show loading state
                                uiState = uiState.copy(
                                    message = VaultMessage("Opening Comcast SSO..."),
                                )
                                val ssoResult = platformServices.startSsoAuth()
                                val email = ssoResult.email
                                if (ssoResult.success && email != null) {
                                    applyActionResult(
                                        result = controller.completeSsoSetup(
                                            token = ssoResult.idToken ?: "",
                                            email = email,
                                            nowMillis = currentTimeMillis(),
                                        ),
                                        platformServices = platformServices,
                                    ) { uiState = it }
                                    platformServices.showToast("Welcome, $email")
                                } else {
                                    uiState = uiState.copy(
                                        route = VaultRoute.SsoSetup,
                                        message = VaultMessage(
                                            ssoResult.error ?: "SSO authentication failed. Please try again.",
                                            isError = true,
                                        ),
                                    )
                                }
                            }
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
                        onSsoLogin = {
                            scope.launch {
                                uiState = uiState.copy(
                                    message = VaultMessage("Opening Comcast SSO..."),
                                )
                                val ssoResult = platformServices.startSsoAuth()
                                val email = ssoResult.email
                                if (ssoResult.success && email != null) {
                                    // Update stored token with the fresh one
                                    applyActionResult(
                                        result = controller.completeSsoSetup(
                                            token = ssoResult.idToken ?: "",
                                            email = email,
                                            nowMillis = currentTimeMillis(),
                                        ),
                                        platformServices = platformServices,
                                    ) { uiState = it }
                                    platformServices.enableOverlayMonitoringAfterLogin()
                                    platformServices.showToast("Welcome back, $email")
                                } else {
                                    uiState = uiState.copy(
                                        route = VaultRoute.Login,
                                        message = VaultMessage(
                                            ssoResult.error ?: "SSO authentication failed. Please try again.",
                                            isError = true,
                                        ),
                                    )
                                }
                            }
                        },
                    )

                    VaultRoute.Main -> BlankAuthenticatedScreen(platformServices = platformServices)

                    VaultRoute.Onboarding -> PlatformOnboardingScreen(
                        onFinish = {
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
        val result = controller.unlockWithBiometric(nowMillis = currentTimeMillis())

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
