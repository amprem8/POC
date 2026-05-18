package com.example.poc

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun EnableBiometricScreen(
    message: PassKeyMessage?,
    onEnableBiometric: () -> Unit,
) {
    LaunchedEffect(Unit) {
        onEnableBiometric()
    }

    AuthScreenLayout(
        title = "Verify fingerprint unlock",
        subtitle = "Fingerprint verification is required on first-time setup when available. After setup, you can log in with either fingerprint or master password.",
        message = message,
        content = {
            Icon(
                imageVector = Icons.Default.Fingerprint,
                contentDescription = null,
                tint = Color(0xFF2563EB),
                modifier = Modifier.height(54.dp),
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Complete the biometric prompt from your device to finish securing this install.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF6B7280),
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onEnableBiometric,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2563EB),
                    contentColor = Color.White,
                ),
            ) {
                Text("Try fingerprint again", fontWeight = FontWeight.Medium)
            }
        },
    )
}

