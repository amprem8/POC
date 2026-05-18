package com.example.poc

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp

@Composable
fun BlankAuthenticatedScreen() {
    val sidebarContainerColor = Color(0xFFCDD3DB)
    val sidebarBorderColor = Color(0xFFB4BCC8)
    val sidebarCardColor = Color(0xFFB6BEC9)
    val sidebarPrimaryTextColor = Color(0xFF1F2937)
    val sidebarSecondaryTextColor = Color(0xFF475569)
    var sidebarExpanded by rememberSaveable { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val sidebarWidth by animateDpAsState(
        targetValue = if (sidebarExpanded) 220.dp else 92.dp,
        animationSpec = tween(durationMillis = 220),
        label = "passkey-sidebar-width",
    )
    val showExpandedLabels = sidebarWidth >= 176.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(sidebarWidth)
                    .border(
                        width = 1.dp,
                        color = sidebarBorderColor,
                        shape = RoundedCornerShape(30.dp),
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                    ) { sidebarExpanded = !sidebarExpanded },
                shape = RoundedCornerShape(30.dp),
                color = sidebarContainerColor,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (showExpandedLabels) Arrangement.Start else Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PassKeyLogo(
                            size = 52.dp,
                            cornerRadius = 16.dp,
                        )
                        if (showExpandedLabels) {
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = AppName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = sidebarPrimaryTextColor,
                                )
                                Text(
                                    text = "Tap to collapse",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = sidebarSecondaryTextColor,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = sidebarCardColor,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            horizontalArrangement = if (showExpandedLabels) Arrangement.Start else Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "PassKey",
                                tint = sidebarPrimaryTextColor,
                            )
                            if (showExpandedLabels) {
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "PassKey",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = sidebarPrimaryTextColor,
                                    )
                                    Text(
                                        text = "Vault workspace",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = sidebarSecondaryTextColor,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    if (showExpandedLabels) {
                        Text(
                            text = AppTagline,
                            style = MaterialTheme.typography.labelMedium,
                            color = sidebarSecondaryTextColor,
                        )
                    } else {
                        Text(
                            text = ">",
                            style = MaterialTheme.typography.titleMedium,
                            color = sidebarSecondaryTextColor,
                        )
                    }
                }
            }
            }
        }
    }
