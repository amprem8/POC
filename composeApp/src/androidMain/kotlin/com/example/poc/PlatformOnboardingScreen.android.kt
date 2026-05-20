package com.example.poc

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformOnboardingScreen(onFinish: () -> Unit) {
    // Android: uses AndroidOnboardingScreen which deep-links to real settings
    AndroidOnboardingScreen(onFinish = onFinish)
}

