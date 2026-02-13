package com.audioflow.player.ui.player

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.audioflow.player.ui.theme.SpotifyBlack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Holds the extracted dominant color state from album artwork.
 * [dominantColor] is the primary color to use for the background gradient.
 * [isColorful] indicates whether the artwork has enough color/contrast to warrant a gradient,
 * or if the UI should fall back to the default black background.
 */
data class DominantColorState(
    val dominantColor: Color = SpotifyBlack,
    val isColorful: Boolean = false
)

/**
 * A composable that extracts the dominant color from an artwork URI.
 *
 * It loads the image as a bitmap using Coil, runs Android Palette extraction,
 * and determines whether the artwork is "colorful enough" to tint the background.
 *
 * Fallback to black when:
 * - Image is mostly black (luminance < 0.08)
 * - Image has very low saturation (< 0.15)
 * - Image fails to load
 *
 * The returned color is animated for smooth cross-fade transitions between songs.
 *
 * @param artworkUri The URI of the current song's album art (local or remote)
 * @param defaultColor Fallback color when artwork is dark/unavailable
 * @return Animated [Color] to use as the top of the background gradient
 */
@Composable
fun rememberDynamicBackgroundColor(
    artworkUri: Any?,
    defaultColor: Color = SpotifyBlack
): Color {
    val context = LocalContext.current
    var extractedState by remember { mutableStateOf(DominantColorState()) }

    // Extract color whenever artwork changes
    LaunchedEffect(artworkUri) {
        if (artworkUri == null) {
            extractedState = DominantColorState(defaultColor, false)
            return@LaunchedEffect
        }

        extractedState = withContext(Dispatchers.IO) {
            try {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(artworkUri)
                    .allowHardware(false) // Palette requires software bitmaps
                    .size(128) // Small size is sufficient for color extraction
                    .build()

                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                    if (bitmap != null) {
                        extractColorFromBitmap(bitmap, defaultColor)
                    } else {
                        DominantColorState(defaultColor, false)
                    }
                } else {
                    DominantColorState(defaultColor, false)
                }
            } catch (e: Exception) {
                DominantColorState(defaultColor, false)
            }
        }
    }

    // Determine target color based on extraction
    val targetColor = if (extractedState.isColorful) {
        extractedState.dominantColor
    } else {
        defaultColor
    }

    // Smooth animated transition between colors
    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 600),
        label = "dynamicBgColor"
    )

    return animatedColor
}

/**
 * Extracts the most visually appealing color from a bitmap using Android Palette.
 * Evaluates multiple swatch types and picks the best candidate.
 * Returns a darkened version to ensure readability of white text on top.
 */
private fun extractColorFromBitmap(bitmap: Bitmap, defaultColor: Color): DominantColorState {
    val palette = Palette.from(bitmap)
        .maximumColorCount(16)
        .generate()

    // Try swatches in order of visual appeal for backgrounds
    val swatch = palette.darkVibrantSwatch
        ?: palette.vibrantSwatch
        ?: palette.darkMutedSwatch
        ?: palette.mutedSwatch
        ?: palette.dominantSwatch

    if (swatch == null) {
        return DominantColorState(defaultColor, false)
    }

    val color = Color(swatch.rgb)

    // Check if color is too dark (mostly black artwork)
    if (color.luminance() < 0.04f) {
        return DominantColorState(defaultColor, false)
    }

    // Check saturation - very desaturated colors look muddy as backgrounds
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(swatch.rgb, hsl)
    val saturation = hsl[1]
    val lightness = hsl[2]

    if (saturation < 0.12f && lightness < 0.15f) {
        // Very low saturation AND dark = near-black/gray, use default
        return DominantColorState(defaultColor, false)
    }

    // Darken the color slightly so white text remains readable
    // Target lightness: between 0.15 and 0.35
    val adjustedLightness = lightness.coerceIn(0.12f, 0.32f)
    hsl[2] = adjustedLightness
    val adjustedColorInt = ColorUtils.HSLToColor(hsl)
    val adjustedColor = Color(adjustedColorInt)

    return DominantColorState(adjustedColor, true)
}
