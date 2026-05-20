package com.example.poc

import androidx.compose.runtime.Composable

/** Platform-specific onboarding screen. Android fires deep-link intents; iOS shows info only. */
@Composable
expect fun PlatformOnboardingScreen(onFinish: () -> Unit)

