package com.example.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.MineHostBlue
import com.example.ui.theme.MineHostBackgroundBottom

/**
 * A safe wrapper for loading local raster resources (JPG, PNG, WebP) using Coil.
 * painterResource() can crash if a resource is malformed or resolution fails.
 */
@Composable
fun SafeResourceImage(
    @DrawableRes resId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    fallback: @Composable () -> Unit = { DefaultFallback() }
) {
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(resId)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        loading = {
            Box(modifier = Modifier.fillMaxSize().background(Color.Transparent))
        },
        error = {
            fallback()
        }
    )
}

@Composable
private fun DefaultFallback() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MineHostBackgroundBottom),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.ImageNotSupported,
            contentDescription = "Image load failed",
            tint = MineHostBlue.copy(alpha = 0.5f)
        )
    }
}
