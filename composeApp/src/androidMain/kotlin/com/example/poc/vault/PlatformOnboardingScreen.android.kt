package com.example.poc.vault

import androidx.compose.runtime.Composable
import com.example.poc.AndroidOnboardingScreen

@Composable
actual fun PlatformOnboardingScreen(onFinish: () -> Unit) {
    // Android: uses AndroidOnboardingScreen which deep-links to real settings
    AndroidOnboardingScreen(onFinish = onFinish)
}

