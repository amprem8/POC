package com.example.poc

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformOnboardingScreen(onFinish: () -> Unit) {
    // iOS: show the generic onboarding screen (no settings deep-links on iOS)
    OnboardingScreen(
        onStepAction = { /* no-op on iOS */ },
        onFinish = onFinish,
    )
}

