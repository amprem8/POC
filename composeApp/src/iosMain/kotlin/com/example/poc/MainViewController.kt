package com.example.poc

import com.example.poc.vault.*

import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController { App(platformServices = IOSPlatformServices()) }
