package com.example.poc.vault

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import org.jetbrains.compose.resources.painterResource
import poc.composeapp.generated.resources.Res
import poc.composeapp.generated.resources.vault_logo

const val AppName = "Vault"
const val AppTagline = "Secure Password Manager"

@Composable
fun VaultLogo(
    size: Dp,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(Res.drawable.vault_logo),
        contentDescription = AppName,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius)),
        contentScale = ContentScale.Crop,
    )
}

