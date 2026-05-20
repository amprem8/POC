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
        description = "PassKey can automatically fill your saved passwords whenever you sign in to any app or website — just like magic.",
        buttonLabel = "Enable One-Tap Fill",
        skipLabel = "Skip",
    ),
    OnboardingStep(
        index = 1,
        icon = "🌐",
        title = "Save passwords from Chrome",
        description = "When you sign in through Chrome, PassKey will offer to save your password — just like Google, but all in your vault.",
        buttonLabel = "Enable Chrome Saving",
        skipLabel = "Skip",
    ),
    OnboardingStep(
        index = 2,
        icon = "🛡️",
        title = "Works in all browsers",
        description = "PassKey monitors all browsers — Firefox, Samsung Internet, Edge, Brave and more — to save your passwords even when Chrome isn't used.",
        buttonLabel = "Enable All-Browser Support",
        skipLabel = "Skip",
    ),
    OnboardingStep(
        index = 3,
        icon = "💬",
        title = "Save prompts over browser",
        description = "When you log in, PassKey can show a quick save banner right over your browser. No switching apps needed.",
        buttonLabel = "Allow Save Prompts",
        skipLabel = "Skip",
    ),
)

