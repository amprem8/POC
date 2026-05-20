package com.example.poc

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 4-step guided onboarding screen.
 * Each step asks one permission in user-friendly language.
 * "Enable" button fires the corresponding deep-link settings intent (Android-side).
 * "Skip" marks that step done without opening settings.
 *
 * [onStepAction] is called with the step index when the primary button is tapped.
 * [onFinish] is called after the last step is completed or skipped.
 */
@Composable
fun OnboardingScreen(
    steps: List<OnboardingStep> = ONBOARDING_STEPS,
    completedSteps: Set<Int> = emptySet(),
    onStepAction: (stepIndex: Int) -> Unit,
    onFinish: () -> Unit,
) {
    var currentIndex by rememberSaveable { mutableStateOf(0) }

    val progress by animateFloatAsState(
        targetValue = (currentIndex + 1).toFloat() / steps.size.toFloat(),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "onboarding-progress",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            // ── Header: progress bar + step counter ───────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    color = Color(0xFF374151),
                    trackColor = Color(0xFFE5E7EB),
                    strokeCap = StrokeCap.Round,
                )
                Text(
                    text = "${currentIndex + 1} / ${steps.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF6B7280),
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.height(40.dp))

            // ── Step content with slide animation ─────────────────────
            val step = steps[currentIndex]
            AnimatedContent(
                targetState = currentIndex,
                transitionSpec = {
                    (slideInHorizontally(tween(320)) { it / 2 } + fadeIn(tween(280))) togetherWith
                        (slideOutHorizontally(tween(240)) { -it / 2 } + fadeOut(tween(200)))
                },
                label = "onboarding-step",
            ) { idx ->
                val s = steps[idx]
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Large emoji icon in a circle
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .background(Color(0xFF374151), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(s.icon, fontSize = 40.sp)
                    }

                    Spacer(Modifier.height(32.dp))

                    Text(
                        text = s.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827),
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = s.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF6B7280),
                        lineHeight = 24.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )

                    // ── Status badges ──────────────────────────────────
                    Spacer(Modifier.height(20.dp))

                    // Green badge when permission is granted
                    AnimatedVisibility(visible = idx in completedSteps) {
                        Row(
                            modifier = Modifier
                                .background(Color(0xFFD1FAE5), RoundedCornerShape(20.dp))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("✅", fontSize = 14.sp)
                            Text(
                                "Permission granted — tap Continue",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF065F46),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    // Waiting hint when permission not yet granted
                    AnimatedVisibility(visible = idx !in completedSteps) {
                        Row(
                            modifier = Modifier
                                .background(Color(0xFFFEF3C7), RoundedCornerShape(20.dp))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("👆", fontSize = 14.sp)
                            Text(
                                "Tap the button below to open Settings",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF92400E),
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // ── Action buttons ─────────────────────────────────────────
            val isLast = currentIndex == steps.size - 1
            val isDone = currentIndex in completedSteps

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = {
                        if (isDone) {
                            // Permission already granted — advance or finish
                            if (isLast) onFinish() else currentIndex++
                        } else {
                            // Not yet granted — open Settings and STAY on this step.
                            // ON_RESUME will re-check; user taps again once green badge shows.
                            onStepAction(currentIndex)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDone) Color(0xFF065F46) else Color(0xFF374151),
                    ),
                ) {
                    Text(
                        text = when {
                            isDone && isLast -> "Finish Setup"
                            isDone           -> "Continue →"
                            isLast           -> step.buttonLabel
                            else             -> step.buttonLabel
                        },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = Color.White,
                    )
                }

                TextButton(
                    onClick = {
                        if (isLast) onFinish() else currentIndex++
                    },
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                ) {
                    Text(
                        text = step.skipLabel,
                        color = Color(0xFF9CA3AF),
                        fontSize = 14.sp,
                    )
                }
            }

            // ── Dot indicators ─────────────────────────────────────────
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                steps.forEachIndexed { i, _ ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (i == currentIndex) 10.dp else 7.dp)
                            .background(
                                color = when {
                                    i == currentIndex -> Color(0xFF374151)
                                    i in completedSteps -> Color(0xFF065F46)
                                    else -> Color(0xFFD1D5DB)
                                },
                                shape = CircleShape,
                            ),
                    )
                }
            }
        }
    }
}

