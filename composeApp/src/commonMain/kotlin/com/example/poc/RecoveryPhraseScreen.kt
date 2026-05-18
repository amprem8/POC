package com.example.poc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun RecoveryPhraseScreen(
    recoveryPhrase: String,
    message: PassKeyMessage?,
    onCopy: () -> Unit,
    onDownload: () -> Unit,
    onContinue: () -> Unit,
) {
    AuthScreenLayout(
        title = "Save your recovery phrase",
        subtitle = "You can use this phrase only from the Forgot Password screen if you ever need to reset the master password.",
        message = message,
    ) {
        SelectionContainer {
            Text(
                text = recoveryPhrase,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8FAFC), shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                    .padding(18.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF111827),
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Copy it or download a .txt file before you continue. The file is saved to your Downloads folder, so move it somewhere safe outside the app afterward.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF6B7280),
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onCopy,
                modifier = Modifier.weight(1f).height(52.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF111827),
                ),
            ) {
                Text(
                    text = "Copy",
                    maxLines = 1,
                    softWrap = false,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            OutlinedButton(
                onClick = onDownload,
                modifier = Modifier.weight(1f).height(52.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF111827),
                ),
            ) {
                Text(
                    text = "Download TXT",
                    maxLines = 1,
                    softWrap = false,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF111827),
                contentColor = Color.White,
            ),
        ) {
            Text("I saved it", fontWeight = FontWeight.Medium)
        }
    }
}


