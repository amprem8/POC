package com.example.poc

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.delay

private enum class RootScreen {
    Splash,
    Login,
    Main,
}

@Composable
@Preview
fun App() {
    PassKeyTheme {
        var showSplash by rememberSaveable { mutableStateOf(true) }
        var isAuthenticated by rememberSaveable { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            delay(1100)
            showSplash = false
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            val rootScreen = when {
                showSplash -> RootScreen.Splash
                isAuthenticated -> RootScreen.Main
                else -> RootScreen.Login
            }

            AnimatedContent(targetState = rootScreen, label = "passkey-root") { screen ->
                when (screen) {
                    RootScreen.Splash -> OpeningSplashScreen()
                    RootScreen.Login -> LoginScreen(onLogin = { isAuthenticated = true })
                    RootScreen.Main -> BlankAuthenticatedScreen()
                }
            }
        }
    }
}


