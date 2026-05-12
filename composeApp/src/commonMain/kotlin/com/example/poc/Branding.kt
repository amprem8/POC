package com.example.poc

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
import poc.composeapp.generated.resources.passkey_logo

const val AppName = "PassKey"
const val AppTagline = "Secure Password Manager"

@Composable
fun PassKeyLogo(
    size: Dp,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(Res.drawable.passkey_logo),
        contentDescription = AppName,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius)),
        contentScale = ContentScale.Crop,
    )
}

