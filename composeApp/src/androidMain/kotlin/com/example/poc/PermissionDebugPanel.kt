package com.example.poc

import com.example.poc.vault.*

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Debug panel shown inside the vault header.
 * Displays live status of the fill-related integrations Vault uses.
 * Tapping any row with a red ✗ opens the relevant settings page directly.
 * Collapsed by default — tap the header to expand.
 */
@Composable
fun PermissionDebugPanel() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var expanded by remember { mutableStateOf(false) }

    // Live-refreshed on every ON_RESUME
    var flags by remember { mutableStateOf(readAllFlags(context)) }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) flags = readAllFlags(context)
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val allGood = flags.values.all { it }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (allGood) Color(0xFF064E3B) else Color(0xFF1F2937))
            .clickable { expanded = !expanded }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        // ── Header row ────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VaultLogo(size = 20.dp, cornerRadius = 6.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (allGood) "Vault — All permissions active" else "Vault — Some permissions missing",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (expanded) "▲" else "▼",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
            )
        }

        // ── Expanded flag rows ────────────────────────────────────────
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                Divider(color = Color.White.copy(alpha = 0.15f), thickness = 0.5.dp)
                Spacer(Modifier.height(8.dp))

                flags.entries.toList().forEach { (label, granted) ->
                    FlagRow(
                        label = label,
                        granted = granted,
                        onFix = { handleFlagFix(context, label) },
                    )
                    Spacer(Modifier.height(4.dp))
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    "Tap any ✗ row to fix it directly",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f),
                )

                // Chrome configuration help
                Spacer(Modifier.height(6.dp))
                Text(
                    "📱 Chrome setup: Chrome → ⋮ → Settings → Passwords → " +
                        "turn OFF 'Offer to save passwords' → " +
                        "turn OFF 'Auto Sign-in' → " +
                        "then Chrome uses Vault as the system autofill provider",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF93C5FD),
                    lineHeight = 16.sp,
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                    flags["Credential Provider (Android 14+ fill)"] == false) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "📍 Credential Provider: Settings → Passwords, credentials & autofill → Additional providers → toggle Vault ON → then tap Done ✓ above",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFCD34D),
                        lineHeight = 16.sp,
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "ℹ️ On Android 14+ Chrome uses Credential Manager (not Autofill). Both Autofill Service AND Credential Provider must be enabled.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFA5B4FC),
                        lineHeight = 16.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun FlagRow(label: String, granted: Boolean, onFix: () -> Unit) {
    val context = LocalContext.current
    val isCredentialProvider = label.contains("Credential")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (granted) Color.White.copy(alpha = 0.08f)
                else Color(0xFFEF4444).copy(alpha = 0.18f)
            )
            .clickable(enabled = !granted) { onFix() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    if (granted) Color(0xFF34D399) else Color(0xFFEF4444),
                    CircleShape,
                )
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (granted) Color.White else Color(0xFFFCA5A5),
            fontWeight = if (granted) FontWeight.Normal else FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!granted) {
            Spacer(Modifier.width(6.dp))
            // For Credential Provider: two buttons — Open Settings + Mark Done
            if (isCredentialProvider) {
                Button(
                    onClick = { onFix() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF374151)),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.height(26.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text("Open", fontSize = 10.sp, color = Color.White)
                }
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick = {
                        // Fallback marker for devices where secure settings cannot be read.
                        context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
                            .edit().putBoolean("credential_provider_enabled", true).apply()
                        onFix()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF065F46)),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.height(26.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text("Done ✓", fontSize = 10.sp, color = Color.White)
                }
            } else {
                Text(
                    "Fix →",
                    fontSize = 10.sp,
                    color = Color(0xFFFCA5A5),
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            Text("✓", fontSize = 11.sp, color = Color(0xFF34D399))
        }
    }
}

// ── Data ──────────────────────────────────────────────────────────────────

/** Returns ordered map of flag label → granted status. */
fun readAllFlags(context: Context): LinkedHashMap<String, Boolean> {
    val map = LinkedHashMap<String, Boolean>()
    map["Autofill Service (save & fill passwords)"] = isAutofillServiceEnabled(context)
    map["Notifications (save confirmation)"] = isNotificationsEnabled(context)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        map["Credential Provider (Android 14+ fill)"] = isCredentialProviderEnabled(context)
    }
    return map
}

fun isCredentialProviderEnabled(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
    return try {
        // Check all known secure settings keys for credential providers
        val keysToCheck = listOf(
            "credential_service_primary",
            "credential_service",
            "credential_provider",
            "autofill_service_search_uri",
        )
        val ourServiceName = "VaultCredentialProviderService"
        val ourPackage = context.packageName
        keysToCheck.any { key ->
            val value = Settings.Secure.getString(context.contentResolver, key) ?: ""
            value.contains(ourPackage) && value.contains(ourServiceName)
        }
    } catch (_: Exception) {
        // Fallback to user-acknowledged flag
        context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
            .getBoolean("credential_provider_enabled", false)
    }
}

fun isNotificationsEnabled(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val mgr = context.getSystemService(android.app.NotificationManager::class.java)
        mgr?.areNotificationsEnabled() == true
    } else {
        androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}

// ── Fix handlers ─────────────────────────────────────────────────────────

fun handleFlagFix(context: Context, label: String) {
    when {
        label.contains("Autofill")      -> openAutofillSettings(context)
        label.contains("Notifications") -> openNotificationSettings(context)
        label.contains("Credential")    -> openCredentialProviderSettingsBestEffort(context)
    }
}

/**
 * Opens the Credential Provider settings using multiple fallback intents.
 * Samsung One UI path differs from stock Android.
 * We try several known intents in order until one works.
 */
fun openCredentialProviderSettingsBestEffort(context: Context) {
    val intentsToTry = listOf(
        // Stock Android 14+ (Pixel, most phones)
        "android.settings.CREDENTIAL_PROVIDER",
        // Samsung One UI 6+ path
        "com.samsung.android.settings.autofill.AUTOFILL_SETTINGS",
        // Generic passwords & accounts
        "com.android.settings.PASSWORDS_AND_ACCOUNTS",
    )

    for (action in intentsToTry) {
        try {
            context.startActivity(
                android.content.Intent(action)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        } catch (_: Exception) {}
    }

    // Final fallback — general settings
    try {
        context.startActivity(
            android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {}
}

fun openNotificationSettings(context: Context) {
    val intent = android.content.Intent().apply {
        action = android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
        putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        context.startActivity(
            android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}





