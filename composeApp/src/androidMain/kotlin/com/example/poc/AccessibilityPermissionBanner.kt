package com.example.poc

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Optional accessibility banner. Accessibility is NOT required for credential saving.
 * It is only used for overlay UI and manual fill-assist from the vault screen.
 * All credential saving goes through [VaultAutofillService.onSaveRequest].
 */
@Composable
fun AccessibilityPermissionBanner() {
    // Intentionally empty — accessibility is no longer part of the credential workflow.
    // The Autofill Framework handles all credential saving and filling.
    // This composable is kept as a no-op to avoid breaking any remaining references.
}

// Also apply lifecycle-aware refresh to the autofill banner
@Composable
fun AutofillPermissionBannerWithLifecycle() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var autofillEnabled by remember { mutableStateOf(isAutofillServiceEnabled(context)) }
    var credBannerDismissed by remember {
        mutableStateOf(
            context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
                .getBoolean("cred_banner_dismissed", false)
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                autofillEnabled = isAutofillServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val showCredBanner = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !credBannerDismissed

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Autofill status row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (autofillEnabled) Color(0xFFD1FAE5) else Color(0xFFFEE2E2),
                    RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (autofillEnabled) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = if (autofillEnabled) Color(0xFF065F46) else Color(0xFF991B1B),
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (autofillEnabled)
                    "✅ Autofill active — Vault saves & fills passwords via Android Autofill Framework"
                else
                    "⚠️ Tap to set Vault as the autofill provider (required for save & fill)",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = if (autofillEnabled) Color(0xFF064E3B) else Color(0xFF7F1D1D),
                modifier = Modifier.weight(1f),
            )
            if (!autofillEnabled) {
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { openAutofillSettings(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(34.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                ) {
                    Text("Fix Now", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (showCredBanner) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Check, null, tint = Color(0xFF1D4ED8), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Enable Credential Provider Fill (Android 14+)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E3A5F),
                            modifier = Modifier.weight(1f),
                        )
                        androidx.compose.material3.TextButton(
                            onClick = {
                                credBannerDismissed = true
                                context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
                                    .edit().putBoolean("cred_banner_dismissed", true).apply()
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        ) { Text("✕", color = Color(0xFF6B7280), fontSize = 14.sp) }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Let Vault appear as a password provider in Android's fill surfaces. Saving happens only through the system Autofill Framework prompt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF1E40AF),
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { openCredentialProviderSettings(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Open Passwords & Accounts Settings", color = Color.White,
                            fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ── Permission check helpers ───────────────────────────────────────────────

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val am = context.getSystemService(AccessibilityManager::class.java) ?: return false
    return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any {
            it.resolveInfo.serviceInfo.packageName == context.packageName &&
                it.resolveInfo.serviceInfo.name.contains("VaultAccessibilityService")
        }
}

fun isOverlayPermissionGranted(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true

fun openAccessibilityServiceDirectly(context: Context) {
    val componentName = "${context.packageName}/.VaultAccessibilityService"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(":settings:show_fragment_args",
                    android.os.Bundle().also { it.putString("service", componentName) })
            }
            context.startActivity(intent)
            return
        } catch (_: Exception) {}
    }
    try {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

fun openAccessibilitySettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

fun openOverlaySettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

fun isAutofillServiceEnabled(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val current = Settings.Secure.getString(context.contentResolver, "autofill_service") ?: ""
        // Different Android versions format the autofill_service string differently
        // Some use "pkg/pkg.ServiceName", others use "pkg/.ServiceName", etc.
        current.contains(context.packageName) && current.contains("VaultAutofillService")
    } else false
}

fun openAutofillSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

fun openCredentialProviderSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        try {
            context.startActivity(Intent("android.settings.CREDENTIAL_PROVIDER").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: Exception) {}
    }
    context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
