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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import kotlinx.coroutines.delay

@Composable
fun SavedPasswordsScreen(
    entries: List<PasswordEntry>,
    onDelete: (String) -> Unit,
    onUpdateNotes: (String, String) -> Unit = { _, _ -> },
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(entries, key = { it.id }) { entry ->
                    PasswordEntryCard(
                        entry = entry,
                        onDelete = { onDelete(entry.id) },
                        onUpdateNotes = { notes -> onUpdateNotes(entry.id, notes) },
                    )
                }
            }
        }
    }
}

// ── Accent color palette for cards ─────────────────────────────────────────────
private val cardAccentColors = listOf(
    Color(0xFF6366F1), // indigo
    Color(0xFF8B5CF6), // violet
    Color(0xFFEC4899), // pink
    Color(0xFF14B8A6), // teal
    Color(0xFFF59E0B), // amber
    Color(0xFF3B82F6), // blue
    Color(0xFF10B981), // emerald
    Color(0xFFEF4444), // red
    Color(0xFF06B6D4), // cyan
    Color(0xFFF97316), // orange
)

private fun accentForEntry(entry: PasswordEntry): Color {
    val hash = entry.loginUrl.hashCode().let { if (it < 0) -it else it }
    return cardAccentColors[hash % cardAccentColors.size]
}

@Composable
private fun PasswordEntryCard(
    entry: PasswordEntry,
    onDelete: () -> Unit,
    onUpdateNotes: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val accent = accentForEntry(entry)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(300)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column {
            // ── Accent strip at the top ──────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(accent),
            )

            Column(modifier = Modifier.padding(16.dp)) {
                // Header row — tap to expand/collapse
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // ── Favicon / fallback letter ────────────────────────────
                    FaviconAvatar(entry = entry, accent = accent)

                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.siteName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF1F5F9),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = entry.username,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8),
                        )
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = Color(0xFF94A3B8),
                    )
                }

                // Expanded details
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(animationSpec = tween(300)),
                    exit = shrinkVertically(animationSpec = tween(300)),
                ) {
                    Column(modifier = Modifier.padding(top = 14.dp)) {
                        EntryDetailRow(label = "Username", value = entry.username, accent = accent)
                        Spacer(Modifier.height(10.dp))
                        EntryDetailRow(label = "Password", value = entry.password, isPassword = true, accent = accent)
                        Spacer(Modifier.height(10.dp))
                        EntryDetailRow(label = "Login URL", value = entry.loginUrl, accent = accent)
                        Spacer(Modifier.height(10.dp))
                        EntryDetailRow(
                            label = "Date Modified",
                            value = formatEpochMillis(entry.dateModified),
                            accent = accent,
                        )
                        Spacer(Modifier.height(12.dp))

                        // ── Notes field — auto-save, 60 char limit ──────────
                        NotesField(
                            initialValue = entry.notes,
                            onNotesChanged = onUpdateNotes,
                            accent = accent,
                        )

                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = onDelete) {
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
}

@Composable
private fun FaviconAvatar(entry: PasswordEntry, accent: Color) {
    val faviconUrl = entry.faviconUrl
    var showFallback by rememberSaveable { mutableStateOf(faviconUrl.isBlank()) }

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (showFallback) accent.copy(alpha = 0.15f) else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        if (!showFallback && faviconUrl.isNotBlank()) {
            AsyncImage(
                model = faviconUrl,
                contentDescription = "${entry.siteName} logo",
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit,
                onState = { state ->
                    if (state is AsyncImagePainter.State.Error) {
                        showFallback = true
                    }
                },
            )
        }

        if (showFallback) {
            Text(
                text = entry.siteName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                color = accent,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
        }
    }
}

@Composable
private fun NotesField(
    initialValue: String,
    onNotesChanged: (String) -> Unit,
    accent: Color,
) {
    var notes by rememberSaveable { mutableStateOf(initialValue) }
    var editing by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // When entering edit mode, request focus
    LaunchedEffect(editing) {
        if (editing) {
            // Small delay to let the composable settle before requesting focus
            delay(100)
            focusRequester.requestFocus()
        }
    }

    Column {
        Text(
            text = "Notes",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF94A3B8),
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(4.dp))

        if (editing) {
            // ── Editable mode ────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2A2A3C))
                    .padding(12.dp),
            ) {
                if (notes.isEmpty()) {
                    Text(
                        text = "Add a note (max 60 chars)…",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B),
                    )
                }
                BasicTextField(
                    value = notes,
                    onValueChange = { newValue ->
                        if (newValue.length <= 60) notes = newValue
                    },
                    textStyle = TextStyle(
                        color = Color(0xFFE2E8F0),
                        fontSize = 14.sp,
                    ),
                    cursorBrush = SolidColor(accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            onNotesChanged(notes)
                            editing = false
                        }
                    ),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${notes.length}/60",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF64748B),
                modifier = Modifier.align(Alignment.End),
            )
        } else {
            // ── Static display mode — tap to edit ────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2A2A3C))
                    .clickable { editing = true }
                    .padding(12.dp),
            ) {
                Text(
                    text = notes.ifEmpty { "Tap to add a note…" },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (notes.isEmpty()) Color(0xFF64748B) else Color(0xFFE2E8F0),
                )
            }
        }
    }
}

@Composable
private fun EntryDetailRow(
    label: String,
    value: String,
    isPassword: Boolean = false,
    accent: Color = Color(0xFF6366F1),
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF94A3B8),
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isPassword && !passwordVisible) "•".repeat(value.length.coerceAtLeast(1)) else value,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFE2E8F0),
                modifier = Modifier.weight(1f),
            )
            if (isPassword) {
                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                    Text(
                        if (passwordVisible) "Hide" else "Show",
                        fontSize = 12.sp,
                        color = accent,
                    )
                }
            }
        }
    }
}
