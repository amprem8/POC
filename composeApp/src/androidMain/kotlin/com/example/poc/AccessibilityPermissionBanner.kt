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

@Composable
fun AccessibilityPermissionBanner() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var a11yEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                a11yEnabled = isAccessibilityServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (a11yEnabled) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFD1FAE5), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Check, null, tint = Color(0xFF065F46), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "✅ Browser form detection is active — PassKey can surface fill suggestions while Android handles saving",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF064E3B),
                fontWeight = FontWeight.Medium,
            )
        }
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "⚡ Enable Browser Login Detection",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF78350F),
            )
            Text(
                "Turn on PassKey accessibility monitoring if you want non-blocking login-form detection in browsers. Android Autofill remains the only save flow.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF92400E),
                lineHeight = 18.sp,
            )

            PermissionStepRow(
                step = "1",
                title = "Accessibility Service",
                description = "Detects login forms and can show lightweight fill suggestions",
                done = a11yEnabled,
                buttonText = "Enable",
                onClick = { openAccessibilityServiceDirectly(context) },
            )
        }
    }
}

// Also apply lifecycle-aware refresh to the autofill banner
@Composable
fun AutofillPermissionBannerWithLifecycle() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var autofillEnabled by remember { mutableStateOf(isAutofillServiceEnabled(context)) }
    var credBannerDismissed by remember {
        mutableStateOf(
            context.getSharedPreferences("passkey_prefs", Context.MODE_PRIVATE)
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
                    "✅ Autofill active — PassKey fills passwords in all browsers"
                else
                    "⚠️ Tap to set PassKey as the autofill provider",
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
                                context.getSharedPreferences("passkey_prefs", Context.MODE_PRIVATE)
                                    .edit().putBoolean("cred_banner_dismissed", true).apply()
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        ) { Text("✕", color = Color(0xFF6B7280), fontSize = 14.sp) }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Let PassKey appear as a password provider in Android's fill surfaces. Saving still happens only through the system Autofill prompt.",
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

@Composable
private fun PermissionStepRow(
    step: String,
    title: String,
    description: String,
    done: Boolean,
    buttonText: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (done) Color(0xFFD1FAE5) else Color(0xFFFEE2E2),
                RoundedCornerShape(10.dp),
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(if (done) Color(0xFF065F46) else Color(0xFF374151), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
            } else {
                Text(step, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                color = if (done) Color(0xFF064E3B) else Color(0xFF1F2937),
            )
            Text(
                description, style = MaterialTheme.typography.labelSmall,
                color = if (done) Color(0xFF047857) else Color(0xFF6B7280),
            )
        }
        if (!done) {
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF374151)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(34.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp),
            ) {
                Text(buttonText, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
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
                it.resolveInfo.serviceInfo.name.contains("PassKeyAccessibilityService")
        }
}

fun isOverlayPermissionGranted(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true

/**
 * Opens the exact accessibility settings page for THIS app's service.
 * On Android 13+ we can deep-link directly to the service toggle page.
 * On older versions we fall back to the main Accessibility settings list.
 */
fun openAccessibilityServiceDirectly(context: Context) {
    // Try to deep-link directly to PassKeyAccessibilityService's own settings page
    // URI format: package:com.example.poc/.PassKeyAccessibilityService
    val componentName = "${context.packageName}/.PassKeyAccessibilityService"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Android 13+ supports direct deep-link to a specific accessibility service
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
    // Fallback: open the accessibility settings list (works on all Android versions)
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
        // Goes directly to PassKey's "Appear on top" toggle page — works on all brands
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

fun isAutofillServiceEnabled(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val current = Settings.Secure.getString(context.contentResolver, "autofill_service")
        current == "${context.packageName}/${context.packageName}.PassKeyAutofillService"
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
