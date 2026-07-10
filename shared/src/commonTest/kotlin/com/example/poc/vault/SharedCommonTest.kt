package com.example.poc.vault

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SharedCommonTest {

    // Use a fixed "now" for testing — 2026-07-10T00:00:00Z
    private val NOW = 1783814400000L
    // A timestamp within the 6-hour window
    private val RECENT = NOW - (3 * 60 * 60 * 1000L)   // 3 hours ago
    // A timestamp outside the 6-hour window
    private val EXPIRED = NOW - (7 * 60 * 60 * 1000L)  // 7 hours ago

    @Test
    fun `fresh install starts at enable biometric`() {
        val controller = VaultAppController()

        val state = controller.bootstrap(savedConfig = null, biometricAvailable = true, nowMillis = NOW)

        assertEquals(VaultRoute.EnableBiometric, state.route)
        assertTrue(state.biometricAvailable)
        assertTrue(state.biometricRequired)
    }

    @Test
    fun `setup flow goes biometric then sso then onboarding`() {
        val controller = VaultAppController()

        controller.bootstrap(savedConfig = null, biometricAvailable = true, nowMillis = NOW)

        val biometricResult = controller.saveBiometricPreference(enabled = true)
        assertEquals(VaultRoute.SsoSetup, biometricResult.uiState.route)
        assertNotNull(biometricResult.persistedConfig)
        assertTrue(biometricResult.persistedConfig.biometricEnabled)

        val ssoResult = controller.completeSsoSetup(token = "test-id-token", email = "user@comcast.net", nowMillis = NOW)
        assertEquals(VaultRoute.Onboarding, ssoResult.uiState.route)
        assertNotNull(ssoResult.persistedConfig)
        assertTrue(ssoResult.persistedConfig.ssoAuthenticated == true)
        assertEquals(NOW, ssoResult.persistedConfig.ssoTokenTimestamp)
    }

    @Test
    fun `existing user with valid session skips login`() {
        val config = VaultConfig(
            biometricEnabled = true,
            ssoAuthenticated = true,
            ssoToken = "dummy-token",
            ssoEmail = "user@comcast.net",
            onboardingSeen = true,
            ssoTokenTimestamp = RECENT,
        )
        val controller = VaultAppController()

        val state = controller.bootstrap(savedConfig = config, biometricAvailable = true, nowMillis = NOW)
        assertEquals(VaultRoute.Main, state.route)
    }

    @Test
    fun `existing user with expired session goes to login`() {
        val config = VaultConfig(
            biometricEnabled = true,
            ssoAuthenticated = true,
            ssoToken = "dummy-token",
            ssoEmail = "user@comcast.net",
            onboardingSeen = true,
            ssoTokenTimestamp = EXPIRED,
        )
        val controller = VaultAppController()

        val state = controller.bootstrap(savedConfig = config, biometricAvailable = true, nowMillis = NOW)
        assertEquals(VaultRoute.Login, state.route)
    }

    @Test
    fun `biometric unlock works with valid session`() {
        val config = VaultConfig(
            biometricEnabled = true,
            ssoAuthenticated = true,
            ssoToken = "dummy-token",
            ssoEmail = "user@comcast.net",
            onboardingSeen = true,
            ssoTokenTimestamp = RECENT,
        )
        val controller = VaultAppController()

        controller.bootstrap(savedConfig = config, biometricAvailable = true, nowMillis = NOW)

        val unlockResult = controller.unlockWithBiometric(nowMillis = NOW)
        assertEquals(VaultRoute.Main, unlockResult.uiState.route)
    }

    @Test
    fun `biometric unlock fails with expired session`() {
        val config = VaultConfig(
            biometricEnabled = true,
            ssoAuthenticated = true,
            ssoToken = "dummy-token",
            ssoEmail = "user@comcast.net",
            onboardingSeen = true,
            ssoTokenTimestamp = EXPIRED,
        )
        val controller = VaultAppController()

        controller.bootstrap(savedConfig = config, biometricAvailable = true, nowMillis = NOW)

        val unlockResult = controller.unlockWithBiometric(nowMillis = NOW)
        assertEquals(VaultRoute.Login, unlockResult.uiState.route)
        assertTrue(unlockResult.uiState.message?.isError == true)
        assertTrue(unlockResult.uiState.message?.text?.contains("expired") == true)
    }

    @Test
    fun `sso unlock works after bootstrap`() {
        val config = VaultConfig(
            biometricEnabled = true,
            ssoAuthenticated = true,
            ssoToken = "dummy-token",
            ssoEmail = "user@comcast.net",
            onboardingSeen = true,
            ssoTokenTimestamp = EXPIRED, // even expired, SSO login refreshes the token
        )
        val controller = VaultAppController()

        controller.bootstrap(savedConfig = config, biometricAvailable = true, nowMillis = NOW)

        val unlockResult = controller.unlockWithSso()
        assertEquals(VaultRoute.Main, unlockResult.uiState.route)
    }

    @Test
    fun `sso unlock fails when not configured`() {
        val config = VaultConfig(
            biometricEnabled = true,
            ssoAuthenticated = false,
            onboardingSeen = true,
        )
        val controller = VaultAppController()

        controller.bootstrap(savedConfig = config, biometricAvailable = true, nowMillis = NOW)

        val unlockResult = controller.unlockWithSso()
        assertEquals(VaultRoute.Login, unlockResult.uiState.route)
        assertTrue(unlockResult.uiState.message?.isError == true)
    }
}