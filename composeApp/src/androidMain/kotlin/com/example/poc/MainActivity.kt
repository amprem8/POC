package com.example.poc

import com.example.poc.vault.*

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.tooling.preview.Preview
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VaultTrace.i(
            "MainActivity",
            "onCreate savedState=${savedInstanceState != null} extras=${intent?.extras?.keySet()?.joinToString()}"
        )

        setContentView(
            ComposeView(this).apply {
                setContent {
                    val platformServices = remember { AndroidPlatformServices(this@MainActivity) }
                    App(platformServices = platformServices)
                }
            },
        )
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}