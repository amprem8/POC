package com.example.poc.vault

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun passKeyTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color(0xFF111827),
    unfocusedTextColor = Color(0xFF111827),
    disabledTextColor = Color(0xFF6B7280),
    focusedContainerColor = Color(0xFFF8FAFC),
    unfocusedContainerColor = Color(0xFFF8FAFC),
    disabledContainerColor = Color(0xFFF3F4F6),
    focusedPlaceholderColor = Color(0xFF94A3B8),
    unfocusedPlaceholderColor = Color(0xFF94A3B8),
    focusedBorderColor = Color(0xFF2563EB),
    unfocusedBorderColor = Color(0xFFD1D5DB),
    cursorColor = Color(0xFF2563EB),
    focusedTrailingIconColor = Color(0xFF475569),
    unfocusedTrailingIconColor = Color(0xFF64748B),
)

