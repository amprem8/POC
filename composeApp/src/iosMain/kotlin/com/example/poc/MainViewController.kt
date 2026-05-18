package com.example.poc

import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController { App(platformServices = IOSPlatformServices()) }
