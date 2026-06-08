package com.example.poc

import com.example.poc.vault.*

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Android implementation of the onboarding step action handler.
 *
 * Maps each step index to the correct Android Settings deep-link:
 *  0 → Autofill provider picker (ACTION_REQUEST_SET_AUTOFILL_SERVICE)
 *  1 → Credential Provider settings (android.settings.CREDENTIAL_PROVIDER / Android 14+)
 *  2 → Accessibility settings (direct deep-link to VaultAccessibilityService on Android 13+)
 */
fun handleOnboardingStepAction(context: Context, stepIndex: Int) {
    when (stepIndex) {
        // Step 0 — One-tap autofill (requires API 26+)
        0 -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                            data = Uri.parse("package:${context.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                    return
                } catch (_: Exception) {}
            }
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        // Step 1 — Credential Provider (Android 14+)
        1 -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                try {
                    context.startActivity(
                        Intent("android.settings.CREDENTIAL_PROVIDER").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    return
                } catch (_: Exception) {}
            }
            // Fallback: autofill settings
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                            data = Uri.parse("package:${context.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                    return
                } catch (_: Exception) {}
            }
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        // Step 2 — Accessibility service (direct deep-link on Android 13+)
        2 -> openAccessibilityServiceDirectly(context)
    }
}

/**
 * Composable that wraps onboarding with live permission status checks on each Resume.
 * Passes the set of already-completed steps so the UI can show green badges.
 */
@Composable
fun AndroidOnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var completedSteps by remember { mutableStateOf(detectCompletedSteps(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                completedSteps = detectCompletedSteps(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    OnboardingScreen(
        completedSteps = completedSteps,
        onStepAction = { stepIndex -> handleOnboardingStepAction(context, stepIndex) },
        onFinish = onFinish,
    )
}

/** Detects which onboarding steps are already completed by checking live permission state. */
fun detectCompletedSteps(context: Context): Set<Int> {
    val done = mutableSetOf<Int>()
    if (isAutofillServiceEnabled(context)) done.add(0)
    // Step 1 (Credential Provider) — no runtime check available; mark if autofill done
    if (isAutofillServiceEnabled(context)) done.add(1)
    if (isAccessibilityServiceEnabled(context)) done.add(2)
    return done
}



