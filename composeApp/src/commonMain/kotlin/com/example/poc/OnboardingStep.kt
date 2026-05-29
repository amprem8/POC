package com.example.poc

/**
 * Onboarding step data — all wording is user-friendly (no technical jargon).
 */
data class OnboardingStep(
    val index: Int,           // 0-based
    val icon: String,         // emoji icon
    val title: String,
    val description: String,
    val buttonLabel: String,
    val skipLabel: String = "Skip for now",
)

val ONBOARDING_STEPS = listOf(
    OnboardingStep(
        index = 0,
        icon = "🔑",
        title = "One-tap password filling",
        description = "Set PassKey as your Autofill provider so Android can fill and save passwords with one system prompt.",
        buttonLabel = "Enable One-Tap Fill",
        skipLabel = "Skip",
    ),
    OnboardingStep(
        index = 1,
        icon = "🌐",
        title = "Android 14+ provider integration",
        description = "On Android 14 and newer, you can also expose PassKey as a Credential Manager fill provider alongside the standard Autofill flow.",
        buttonLabel = "Enable Provider Fill",
        skipLabel = "Skip",
    ),
    OnboardingStep(
        index = 2,
        icon = "🛡️",
        title = "Works in all browsers",
        description = "Optional accessibility monitoring can detect browser login forms and surface lightweight fill suggestions without creating its own save flow.",
        buttonLabel = "Enable Browser Detection",
        skipLabel = "Skip",
    ),
)

