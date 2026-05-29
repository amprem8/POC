package com.example.poc

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SavedPasswordsScreen(
    entries: List<PasswordEntry>,
    onDelete: (String) -> Unit,
    headerContent: @Composable (() -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        headerContent?.invoke()

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "No saved passwords yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF6B7280),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Sign in with Autofill enabled and Android will offer the only save prompt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9CA3AF),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 0.dp,
                    vertical = 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(entries, key = { it.id }) { entry ->
                    PasswordEntryCard(
                        entry = entry,
                        onDelete = { onDelete(entry.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PasswordEntryCard(entry: PasswordEntry, onDelete: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(300)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFD1D5DB)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row — tap here to expand/collapse only
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Site initial avatar
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF4F6272), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = entry.siteName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.siteName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1F2937),
                    )
                    Text(
                        text = entry.username,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF6B7280),
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = Color(0xFF6B7280),
                )
            }

            // Expanded details
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(300)),
                exit = shrinkVertically(animationSpec = tween(300)),
            ) {
                Column(modifier = Modifier.padding(top = 14.dp)) {
                    EntryDetailRow(label = "Username", value = entry.username)
                    Spacer(Modifier.height(8.dp))
                    EntryDetailRow(label = "Password", value = entry.password, isPassword = true)
                    Spacer(Modifier.height(8.dp))
                    EntryDetailRow(label = "Login URL", value = entry.loginUrl)
                    Spacer(Modifier.height(8.dp))
                    EntryDetailRow(
                        label = "Date Modified",
                        value = formatEpochMillis(entry.dateModified),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = onDelete,
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Delete", color = Color(0xFFEF4444))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryDetailRow(label: String, value: String, isPassword: Boolean = false) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF9CA3AF),
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isPassword && !passwordVisible) "•".repeat(value.length.coerceAtLeast(1)) else value,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF1F2937),
                modifier = Modifier.weight(1f),
            )
            if (isPassword) {
                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                    Text(
                        if (passwordVisible) "Hide" else "Show",
                        fontSize = 12.sp,
                        color = Color(0xFF4F6272),
                    )
                }
            }
        }
    }
}