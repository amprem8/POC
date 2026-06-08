package com.example.poc

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class SidebarSection(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val gradient: List<Color>,
    val badge: String? = null,
)

private val sidebarSections = listOf(
    SidebarSection(
        id = "all",
        title = "Vault",
        subtitle = "Every saved web password",
        icon = Icons.Default.Apps,
        gradient = listOf(Color(0xFF0EA5E9), Color(0xFF2563EB)),
    ),
    SidebarSection(
        id = "vault-feature",
        title = "Vault",
        subtitle = "Next-gen sign-ins",
        icon = Icons.Default.VpnKey,
        gradient = listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)),
        badge = "SOON",
    ),
    SidebarSection(
        id = "send",
        title = "Send",
        subtitle = "Secure sharing hub",
        icon = Icons.AutoMirrored.Filled.Send,
        gradient = listOf(Color(0xFFF97316), Color(0xFFEA580C)),
        badge = "SOON",
    ),
    SidebarSection(
        id = "generator",
        title = "Generator",
        subtitle = "Strong password ideas",
        icon = Icons.Default.AutoAwesome,
        gradient = listOf(Color(0xFFEC4899), Color(0xFFDB2777)),
        badge = "SOON",
    ),
    SidebarSection(
        id = "wifi",
        title = "WiFi",
        subtitle = "Network credentials vault",
        icon = Icons.Default.Wifi,
        gradient = listOf(Color(0xFF14B8A6), Color(0xFF0F766E)),
        badge = "SOON",
    ),
    SidebarSection(
        id = "security",
        title = "Security",
        subtitle = "Watchtower and alerts",
        icon = Icons.Default.Security,
        gradient = listOf(Color(0xFFF59E0B), Color(0xFFD97706)),
        badge = "SOON",
    ),
    SidebarSection(
        id = "deleted",
        title = "Deleted",
        subtitle = "Recently removed items",
        icon = Icons.Default.DeleteOutline,
        gradient = listOf(Color(0xFFEF4444), Color(0xFFDC2626)),
        badge = "SOON",
    ),
)

@Composable
fun BlankAuthenticatedScreen(
    platformServices: PlatformServices = PreviewPlatformServices(),
) {
    val sidebarPrimaryTextColor = Color(0xFF1F2937)
    val sidebarSecondaryTextColor = Color(0xFF475569)
    val drawerBackground = Color(0xFFF8FAFF)
    val screenBackground = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFF8FAFC),
            Color(0xFFF1F5F9),
            Color(0xFFEFF6FF),
        ),
    )
    val peacockBorderBrush = Brush.sweepGradient(
        colors = listOf(
            Color(0xFFFFB800),
            Color(0xFFFF7A00),
            Color(0xFFFF3A3A),
            Color(0xFF7C4DFF),
            Color(0xFF2196F3),
            Color(0xFF00C853),
            Color(0xFFFFB800),
        ),
    )
    var sidebarExpanded by rememberSaveable { mutableStateOf(false) }
    val selectedSectionState = rememberSaveable { mutableStateOf("all") }

    val entries by platformServices.entriesFlow.collectAsState()
    val selectedSection = sidebarSections.firstOrNull { it.id == selectedSectionState.value } ?: sidebarSections.first()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!sidebarExpanded) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .shadow(6.dp, CircleShape)
                            .clip(CircleShape)
                            .background(Color.Black)
                            .border(2.dp, peacockBorderBrush, CircleShape)
                            .clickable { sidebarExpanded = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        HamburgerIcon(tint = Color.White)
                    }
                } else {
                    Spacer(modifier = Modifier.size(44.dp))
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (selectedSection.id == "all") "Vault" else selectedSection.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = sidebarPrimaryTextColor,
                    )
                    if (selectedSection.id != "all") {
                        Text(
                            text = "This section is styled and ready for future features",
                            style = MaterialTheme.typography.bodySmall,
                            color = sidebarSecondaryTextColor,
                        )
                    }
                }
            }

            if (selectedSection.id == "all") {
                SavedPasswordsScreen(
                    entries = entries,
                    onDelete = { id ->
                        platformServices.deletePasswordEntry(id)
                    },
                    onUpdateNotes = { id, notes ->
                        platformServices.updateNotes(id, notes)
                    },
                    headerContent = { PlatformPasswordHeader() },
                )
            } else {
                SectionPlaceholderCard(section = selectedSection)
            }
        }

        AnimatedVisibility(
            visible = sidebarExpanded,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.38f))
                    .clickable(
                        interactionSource = MutableInteractionSource(),
                        indication = null,
                    ) { sidebarExpanded = false },
            )
        }

        AnimatedVisibility(
            visible = sidebarExpanded,
            enter = slideInHorizontally(tween(260)) { -it },
            exit = slideOutHorizontally(tween(210)) { -it },
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(312.dp)
                    .statusBarsPadding(),
                shape = RoundedCornerShape(topEnd = 34.dp, bottomEnd = 34.dp),
                color = drawerBackground,
                shadowElevation = 22.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 22.dp),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        color = Color.Transparent,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(28.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF111827),
                                            Color(0xFF1D4ED8),
                                            Color(0xFF7C3AED),
                                        ),
                                    ),
                                )
                                .padding(18.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .shadow(4.dp, CircleShape)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.28f))
                                            .border(2.dp, peacockBorderBrush, CircleShape)
                                            .clickable { sidebarExpanded = false },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        HamburgerIcon(tint = Color.White)
                                    }
                                    Text(
                                        text = "Workspace",
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    Text(
                        text = "Collections",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = sidebarSecondaryTextColor,
                    )

                    Spacer(Modifier.height(12.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color.White.copy(alpha = 0.74f))
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        sidebarSections.forEach { section ->
                            SidebarSectionCard(
                                section = section,
                                selected = section.id == selectedSection.id,
                                onClick = {
                                    selectedSectionState.value = section.id
                                    sidebarExpanded = false
                                },
                            )
                        }
                    }

                }
            }
        }
    }
}

@Composable
private fun SidebarSectionCard(
    section: SidebarSection,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val cardBrush = Brush.linearGradient(section.gradient)
    val textColor = if (selected) Color.White else Color(0xFF0F172A)
    val secondaryTextColor = if (selected) Color.White.copy(alpha = 0.76f) else Color(0xFF475569)
    val badgeBackground = if (selected) Color.White.copy(alpha = 0.18f) else section.gradient.first().copy(alpha = 0.12f)
    val iconBackground = if (selected) Color.White.copy(alpha = 0.18f) else section.gradient.first().copy(alpha = 0.14f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = MutableInteractionSource(),
                indication = null,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(24.dp),
        color = if (selected) Color.Transparent else Color.White,
        shadowElevation = if (selected) 12.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (selected) {
                        cardBrush
                    } else {
                        Brush.linearGradient(listOf(Color.White, Color(0xFFF8FAFC)))
                    },
                )
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(iconBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = section.icon,
                    contentDescription = section.title,
                    tint = if (selected) Color.White else section.gradient.first(),
                    modifier = Modifier.size(24.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                )
                Text(
                    text = section.subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = secondaryTextColor,
                )
            }

            section.badge?.let { badge ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(badgeBackground)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else section.gradient.first(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionPlaceholderCard(section: SidebarSection) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = Color.Transparent,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(section.gradient))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = section.icon,
                        contentDescription = section.title,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = "This section is already added to the new sidebar design. Right now, only ALL is wired to real saved website passwords.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.82f),
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = Color.White.copy(alpha = 0.92f),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Coming soon",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                )
                Text(
                    text = "Use ALL to browse the credentials already captured from browsers and apps. Vault, Send, Generator, WiFi, Security, and Deleted are currently design-ready placeholders.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF475569),
                )
            }
        }
    }
}

@Composable
private fun HamburgerIcon(tint: Color) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(20.dp)
            .padding(vertical = 3.dp),
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(tint, RoundedCornerShape(2.dp)),
            )
        }
    }
}
