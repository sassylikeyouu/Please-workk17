package com.example.ui.servercreation.components
 
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.ui.components.SafeResourceImage
import androidx.compose.ui.layout.ContentScale
import com.example.R

@Composable
fun BasicsFloatingIslandArtwork(
    modifier: Modifier = Modifier
) {
    SafeResourceImage(
        resId = R.drawable.review_world_icon,
        contentDescription = "Floating server island artwork",
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

@Composable
fun EngineArtwork(
    engineId: String,
    modifier: Modifier = Modifier
) {
    SafeResourceImage(
        resId = engineDrawable(engineId),
        contentDescription = "Server engine artwork",
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

fun engineDrawable(engineId: String): Int {
    return when (engineId) {
        "bedrock_power_nukkit_x" -> R.drawable.engine_powernukkitx
        "bedrock_power_nukkit" -> R.drawable.engine_powernukkit
        "bedrock_cloudburst_nukkit" -> R.drawable.engine_cloudburst_nukkit
        "nukkit-mot" -> R.drawable.engine_nukkit_mot
        "bedrock_nukkit" -> R.drawable.engine_nukkit
        else -> R.drawable.engine_nukkit // Fallback
    }
}

@Composable
fun VersionArtwork(
    modifier: Modifier = Modifier
) {
    SafeResourceImage(
        resId = R.drawable.review_version_icon,
        contentDescription = "Latest stable version artwork",
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

@Composable
fun WorldArtwork(
    worldType: String,
    modifier: Modifier = Modifier
) {
    val resId = when (worldType.lowercase()) {
        "survival" -> R.drawable.world_survival
        "creative" -> R.drawable.world_creative
        "adventure" -> R.drawable.world_adventure
        "flat" -> R.drawable.world_flat
        else -> R.drawable.world_survival
    }
    SafeResourceImage(
        resId = resId,
        contentDescription = "$worldType world artwork",
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

@Composable
fun PerformanceArtwork(
    profile: String,
    modifier: Modifier = Modifier
) {
    val resId = when (profile.lowercase()) {
        "low", "low_resource", "low resource" -> R.drawable.performance_balanced
        "balanced" -> R.drawable.performance_balanced
        "performance", "high" -> R.drawable.performance_high
        else -> R.drawable.performance_balanced
    }
    SafeResourceImage(
        resId = resId,
        contentDescription = "$profile performance profile artwork",
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

@Composable
fun TunnelArtwork(
    modifier: Modifier = Modifier
) {
    SafeResourceImage(
        resId = R.drawable.network_tunnel,
        contentDescription = "Secure network tunnel artwork",
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

@Composable
fun ReviewSuccessArtwork(
    modifier: Modifier = Modifier
) {
    SafeResourceImage(
        resId = R.drawable.review_success,
        contentDescription = "Server configuration ready",
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}
