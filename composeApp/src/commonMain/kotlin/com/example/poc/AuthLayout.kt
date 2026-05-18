package com.example.poc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AuthScreenLayout(
    title: String,
    subtitle: String,
    message: PassKeyMessage? = null,
    content: @Composable () -> Unit,
) {
    val inputController = rememberPassKeyInputController()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFF8FAFF), Color(0xFFEEF2FF), Color(0xFFF8FAFC)),
                ),
            )
            .dismissKeyboardOnTapOutside(inputController)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 20.dp),
        ) {
            Column(
                modifier = Modifier
                    .border(width = 1.dp, color = Color(0xFFE5E7EB), shape = RoundedCornerShape(32.dp))
                    .padding(horizontal = 28.dp, vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PassKeyLogo(size = 72.dp, cornerRadius = 20.dp)
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = AppName.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2563EB),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF111827),
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6B7280),
                )
                if (message != null) {
                    Spacer(modifier = Modifier.height(18.dp))
                    InlineMessage(message)
                }
                Spacer(modifier = Modifier.height(24.dp))
                content()
            }
        }
    }
}

@Composable
fun InlineMessage(message: PassKeyMessage) {
    val background = if (message.isError) Color(0xFFFEF2F2) else Color(0xFFEFF6FF)
    val foreground = if (message.isError) Color(0xFFB91C1C) else Color(0xFF1D4ED8)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = message.text,
            color = foreground,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}


