package com.example.poc.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun SsoSetupScreen(
    message: VaultMessage?,
    onSignInWithSso: () -> Unit,
) {
    AuthScreenLayout(
        title = "Sign in with Comcast SSO",
        subtitle = "Connect your Comcast account to complete setup. This enables secure sync across all your devices.",
        message = message,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF8FAFC), RoundedCornerShape(20.dp))
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = Color(0xFF2563EB),
                )
                Text(
                    text = "Your credentials will be securely stored and synced via Xvault backend using your Comcast SSO identity.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF334155),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = Color(0xFF0F172A),
                )
                Text(
                    text = "An in-app browser will open to authenticate with Comcast SSO. Your token is stored permanently on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B),
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onSignInWithSso,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF111827),
                contentColor = Color.White,
            ),
        ) {
            Icon(Icons.Default.Language, contentDescription = null)
            Spacer(modifier = Modifier.width(10.dp))
            Text("Sign in with Comcast SSO", fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "This is a one-time setup. You won't need to sign in again unless you reinstall the app.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF6B7280),
        )
    }
}

@Preview
@Composable
private fun SsoSetupScreenPreview() {
    VaultTheme {
        SsoSetupScreen(
            message = null,
            onSignInWithSso = {},
        )
    }
}

