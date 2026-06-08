package com.example.poc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SharedCommonTest {

    @Test
    fun `fresh install starts at create master password`() {
        val controller = VaultAppController { "alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu" }

        val state = controller.bootstrap(savedConfig = null, biometricAvailable = true)

        assertEquals(VaultRoute.CreateMasterPassword, state.route)
        assertTrue(state.biometricAvailable)
    }

    @Test
    fun `setup flow persists recovery phrase and ends on main screen`() {
        val phrase = "alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu"
        val controller = VaultAppController { phrase }

        controller.bootstrap(savedConfig = null, biometricAvailable = true)
        val passwordResult = controller.createMasterPassword("supersecret", "supersecret")
        assertEquals(VaultRoute.EnableBiometric, passwordResult.uiState.route)
        assertTrue(passwordResult.uiState.biometricRequired)

        val biometricResult = controller.saveBiometricPreference(enabled = true)
        assertEquals(VaultRoute.RecoveryPhrase, biometricResult.uiState.route)
        assertEquals(phrase, biometricResult.uiState.recoveryPhrase)
        assertNotNull(biometricResult.persistedConfig)
        assertTrue(biometricResult.persistedConfig.biometricEnabled)
        assertFalse(biometricResult.persistedConfig.recoveryPhraseAcknowledged)

        val finishResult = controller.finishRecoveryPhraseStep()
        assertEquals(VaultRoute.Main, finishResult.uiState.route)
        assertTrue(finishResult.persistedConfig?.recoveryPhraseAcknowledged == true)
    }

    @Test
    fun `master password unlock works after bootstrap`() {
        val config = VaultConfig(
            masterPassword = "supersecret",
            biometricEnabled = false,
            recoveryPhrase = "alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu",
            recoveryPhraseAcknowledged = true,
        )
        val controller = VaultAppController()

        val state = controller.bootstrap(savedConfig = config, biometricAvailable = false)
        assertEquals(VaultRoute.Login, state.route)

        val unlockResult = controller.unlockWithPassword("supersecret")
        assertEquals(VaultRoute.Main, unlockResult.uiState.route)
    }

    @Test
    fun `forgot password verifies recovery phrase and updates master password`() {
        val config = VaultConfig(
            masterPassword = "oldpassword",
            biometricEnabled = true,
            recoveryPhrase = "alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu",
            recoveryPhraseAcknowledged = true,
        )
        val controller = VaultAppController()

        controller.bootstrap(savedConfig = config, biometricAvailable = true)
        assertEquals(VaultRoute.ForgotPassword, controller.openForgotPassword().route)

        val recoveryState = controller.verifyRecoveryPhrase(config.recoveryPhrase)
        assertEquals(VaultRoute.ResetPassword, recoveryState.route)

        val resetResult = controller.saveResetPassword("newpassword", "newpassword")
        assertEquals(VaultRoute.Main, resetResult.uiState.route)
        assertEquals("newpassword", resetResult.persistedConfig?.masterPassword)
    }

    @Test
    fun `first install cannot skip biometric when biometrics are available`() {
        val controller = VaultAppController()

        controller.bootstrap(savedConfig = null, biometricAvailable = true)
        controller.createMasterPassword("supersecret", "supersecret")

        val result = controller.saveBiometricPreference(enabled = false)

        assertEquals(VaultRoute.EnableBiometric, result.uiState.route)
        assertTrue(result.uiState.biometricRequired)
        assertTrue(result.uiState.message?.isError == true)
    }

    @Test
    fun `invalid password shows error`() {
        val controller = VaultAppController()

        controller.bootstrap(savedConfig = null, biometricAvailable = false)
        val result = controller.createMasterPassword("short", "short")

        assertEquals(VaultRoute.CreateMasterPassword, result.uiState.route)
        assertTrue(result.uiState.message?.isError == true)
    }
}