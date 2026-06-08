package com.example.poc.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.poc.AutofillPermissionBannerWithLifecycle
import com.example.poc.PermissionDebugPanel

@Composable
actual fun PlatformPasswordHeader() {
    Column(
        modifier = Modifier.padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // ── Debug panel: live permission flags (tap to expand / fix) ──
        PermissionDebugPanel()
        // ── Autofill + Credential Provider permission banners ────────
        AutofillPermissionBannerWithLifecycle()
    }
}
