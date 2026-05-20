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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SavedPasswordsScreen(
    entries: List<PasswordEntry>,
    onSave: (PasswordEntry) -> Unit,
    onDelete: (String) -> Unit,
    headerContent: @Composable (() -> Unit)? = null,
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Platform header (autofill banners, etc.)
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
                            "Tap + to add your first entry",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF9CA3AF),
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(bottom = 80.dp), // space so FAB doesn't cover last card
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

        // FAB overlaid at bottom-end — no Scaffold needed
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = Color(0xFF374151),
            contentColor = Color.White,
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add entry")
        }
    }

    if (showAddDialog) {
        AddPasswordDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { entry ->
                onSave(entry)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun PasswordEntryCard(entry: PasswordEntry, onDelete: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(300))
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFD1D5DB)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
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
    var passwordVisible by remember { mutableStateOf(false) }
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

@Composable
private fun AddPasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (PasswordEntry) -> Unit,
) {
    var siteName by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var loginUrl by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var siteError by rememberSaveable { mutableStateOf(false) }
    var userError by rememberSaveable { mutableStateOf(false) }
    var passError by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Password", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = siteName,
                    onValueChange = { siteName = it; siteError = false },
                    label = { Text("Site Name (e.g. Google)") },
                    isError = siteError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; userError = false },
                    label = { Text("Username / Email") },
                    isError = userError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; passError = false },
                    label = { Text("Password") },
                    isError = passError,
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        TextButton(onClick = { passwordVisible = !passwordVisible }) {
                            Text(if (passwordVisible) "Hide" else "Show", fontSize = 12.sp)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = loginUrl,
                    onValueChange = { loginUrl = it },
                    label = { Text("Login URL") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                siteError = siteName.isBlank()
                userError = username.isBlank()
                passError = password.isBlank()
                if (!siteError && !userError && !passError) {
                    onConfirm(
                        PasswordEntry(
                            id = currentTimeMillis().toString(),
                            siteName = siteName.trim(),
                            username = username.trim(),
                            password = password,
                            loginUrl = loginUrl.trim(),
                            dateModified = currentTimeMillis(),
                        )
                    )
                }
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
