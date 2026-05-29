package com.example.poc

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun BlankAuthenticatedScreen(
    platformServices: PlatformServices = PreviewPlatformServices(),
) {
    val sidebarPrimaryTextColor = Color(0xFF1F2937)
    val sidebarSecondaryTextColor = Color(0xFF475569)
    val drawerBackground = Color(0xFFCDD3DB)
    val navItemColor = Color(0xFFB6BEC9)
    var sidebarExpanded by rememberSaveable { mutableStateOf(false) }

    // ── Reactive entries: collect StateFlow if available, fallback to lifecycle refresh ──
    val entries by platformServices.entriesFlow.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // ── Main content — equal 16dp padding on all sides ────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            // Top bar row: hamburger (hidden when drawer open) + title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Circular hamburger — only visible when drawer is closed
                if (!sidebarExpanded) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .shadow(6.dp, CircleShape)
                            .clip(CircleShape)
                            .background(Color(0xFF374151))
                            .clickable { sidebarExpanded = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        HamburgerIcon(tint = Color.White)
                    }
                } else {
                    // Placeholder same size so title doesn't jump
                    Spacer(modifier = Modifier.size(44.dp))
                }

                Text(
                    text = "Saved Passwords",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1F2937),
                )
            }

            // Passwords list — fills remaining space with equal side padding
            SavedPasswordsScreen(
                entries = entries,
                onDelete = { id ->
                    platformServices.deletePasswordEntry(id)
                    // StateFlow auto-updates the UI — no manual reload needed
                },
                headerContent = { PlatformPasswordHeader() },
            )
        }

        // ── Scrim — tap outside drawer to close ───────────────────────
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
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { sidebarExpanded = false },
            )
        }

        // ── Slide-in Drawer ────────────────────────────────────────────
        AnimatedVisibility(
            visible = sidebarExpanded,
            enter = slideInHorizontally(tween(260)) { -it },
            exit = slideOutHorizontally(tween(210)) { -it },
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(270.dp),
                shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                color = drawerBackground,
                shadowElevation = 14.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 28.dp),
                ) {
                    // Header row: circular hamburger (closes drawer) + "Menu" label
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .shadow(4.dp, CircleShape)
                                .clip(CircleShape)
                                .background(Color(0xFF374151))
                                .clickable { sidebarExpanded = false },
                            contentAlignment = Alignment.Center,
                        ) {
                            HamburgerIcon(tint = Color.White)
                        }
                        Text(
                            text = "Menu",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = sidebarPrimaryTextColor,
                        )
                    }

                    Spacer(Modifier.height(28.dp))

                    // App branding
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PassKeyLogo(size = 46.dp, cornerRadius = 14.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = AppName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = sidebarPrimaryTextColor,
                            )
                            Text(
                                text = AppTagline,
                                style = MaterialTheme.typography.labelSmall,
                                color = sidebarSecondaryTextColor,
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Divider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFFB4BCC8)),
                    )

                    Spacer(Modifier.height(16.dp))

                    // Nav item — Passwords
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { sidebarExpanded = false },
                        shape = RoundedCornerShape(16.dp),
                        color = navItemColor,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Passwords",
                                tint = sidebarPrimaryTextColor,
                                modifier = Modifier.size(22.dp),
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Passwords",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = sidebarPrimaryTextColor,
                                )
                                Text(
                                    text = "Saved credentials",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = sidebarSecondaryTextColor,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HamburgerIcon(tint: Color) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.size(20.dp),
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
