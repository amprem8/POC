package com.example.poc.vault

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
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

// ── Peacock gradient for card top border ────────────────────────────────────────
private val peacockGradient = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFFFFB800), // Yellow
        Color(0xFFFF7A00), // Orange
        Color(0xFFFF3A3A), // Red
        Color(0xFF7C4DFF), // Purple
        Color(0xFF2196F3), // Blue
        Color(0xFF00C853), // Green
    ),
)

// Default accent for favicon fallback letters
private val defaultAccent = Color(0xFF2196F3)

@Composable
private fun PasswordEntryCard(
    entry: PasswordEntry,
    onDelete: () -> Unit,
    onUpdateNotes: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val accent = defaultAccent

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(300)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            // ── Peacock gradient strip at the top ──────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(peacockGradient),
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
                            color = Color(0xFF1A1A2E),
                        )
                        Spacer(Modifier.height(2.dp))
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

/**
 * 3-tier favicon loading:
 *  Tier 1 → DuckDuckGo Icons API (highest coverage, real site favicons)
 *  Tier 2 → Google Favicon API (secondary fallback)
 *  Tier 3 → First-letter fallback
 */
@Composable
private fun FaviconAvatar(entry: PasswordEntry, accent: Color) {
    // 0 = trying DuckDuckGo, 1 = trying Google, 2 = letter fallback
    var tier by rememberSaveable { mutableStateOf(if (entry.faviconUrl.isBlank()) 2 else 0) }

    val currentUrl = when (tier) {
        0 -> entry.faviconUrl
        1 -> entry.fallbackFaviconUrl
        else -> ""
    }

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (tier >= 2) accent.copy(alpha = 0.15f) else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        if (tier < 2 && currentUrl.isNotBlank()) {
            AsyncImage(
                model = currentUrl,
                contentDescription = "${entry.siteName} logo",
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit,
                onState = { state ->
                    if (state is AsyncImagePainter.State.Error) {
                        tier = if (tier == 0 && entry.fallbackFaviconUrl.isNotBlank()) 1 else 2
                    }
                },
            )
        }

        if (tier >= 2) {
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
            color = Color(0xFF6B7280),
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(4.dp))

        if (editing) {
            // ── Editable mode ────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF3F4F6))
                     .padding(12.dp),
             ) {
                 if (notes.isEmpty()) {
                     Text(
                         text = "Add a note (max 60 chars)…",
                         style = MaterialTheme.typography.bodySmall,
                         color = Color(0xFF9CA3AF),
                     )
                 }
                 BasicTextField(
                     value = notes,
                     onValueChange = { newValue ->
                         if (newValue.length <= 60) notes = newValue
                     },
                     textStyle = TextStyle(
                         color = Color(0xFF1F2937),
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
                color = Color(0xFF9CA3AF),
                modifier = Modifier.align(Alignment.End),
            )
        } else {
            // ── Static display mode — tap to edit ────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF3F4F6))
                     .clickable { editing = true }
                     .padding(12.dp),
             ) {
                 Text(
                     text = notes.ifEmpty { "Tap to add a note…" },
                     style = MaterialTheme.typography.bodySmall,
                     color = if (notes.isEmpty()) Color(0xFF9CA3AF) else Color(0xFF1F2937),
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
            color = Color(0xFF6B7280),
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
                        color = accent,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun SavedPasswordsScreenPreview() {
    val sampleEntries = listOf(
        PasswordEntry("1", "Google", "user@gmail.com", "pass", "google.com", System.currentTimeMillis()),
        PasswordEntry("2", "GitHub", "dev@github.com", "pass", "github.com", System.currentTimeMillis()),
    )
    VaultTheme {
        SavedPasswordsScreen(
            entries = sampleEntries,
            onDelete = {},
        )
    }
}

@Preview
@Composable
private fun SavedPasswordsScreenEmptyPreview() {
    VaultTheme {
        SavedPasswordsScreen(
            entries = emptyList(),
            onDelete = {},
        )
    }
}

