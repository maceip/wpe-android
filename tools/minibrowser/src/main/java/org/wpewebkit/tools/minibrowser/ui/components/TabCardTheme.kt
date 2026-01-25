/**
 * Copyright (C) 2026
 *   Author: maceip
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

package org.wpewebkit.tools.minibrowser.ui.components

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Extracted color theme for a tab card, derived from page content.
 * Used to create "zany" chunky themed tab cards.
 */
data class TabCardTheme(
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val background: Color,
    val onBackground: Color,
    val borderColor: Color,
    val isLight: Boolean
) {
    companion object {
        /** Default theme before colors are extracted */
        val Default = TabCardTheme(
            primary = Color(0xFF6750A4),
            secondary = Color(0xFF625B71),
            accent = Color(0xFFD0BCFF),
            background = Color(0xFFF3EDF7),
            onBackground = Color(0xFF1D1B20),
            borderColor = Color(0xFFCAC4D0),
            isLight = true
        )

        /** Generate a random zany theme for variety */
        fun randomZany(seed: Int): TabCardTheme {
            val hue = (seed * 137) % 360
            val primary = Color.hsl(hue.toFloat(), 0.7f, 0.5f)
            val secondary = Color.hsl((hue + 30) % 360f, 0.6f, 0.4f)
            val accent = Color.hsl((hue + 180) % 360f, 0.8f, 0.6f)
            val isLight = (seed % 2) == 0
            return TabCardTheme(
                primary = primary,
                secondary = secondary,
                accent = accent,
                background = if (isLight) Color.hsl(hue.toFloat(), 0.2f, 0.95f)
                             else Color.hsl(hue.toFloat(), 0.3f, 0.15f),
                onBackground = if (isLight) Color.hsl(hue.toFloat(), 0.3f, 0.2f)
                               else Color.hsl(hue.toFloat(), 0.2f, 0.9f),
                borderColor = Color.hsl((hue + 60) % 360f, 0.5f, 0.6f),
                isLight = isLight
            )
        }
    }
}

/**
 * Extract a TabCardTheme from a bitmap (page screenshot or favicon)
 */
suspend fun extractThemeFromBitmap(bitmap: Bitmap): TabCardTheme = withContext(Dispatchers.Default) {
    try {
        val palette = Palette.from(bitmap).generate()

        val vibrant = palette.vibrantSwatch
        val muted = palette.mutedSwatch
        val dominant = palette.dominantSwatch
        val lightVibrant = palette.lightVibrantSwatch
        val darkVibrant = palette.darkVibrantSwatch

        val primary = (vibrant ?: dominant)?.let { Color(it.rgb) } ?: TabCardTheme.Default.primary
        val secondary = (muted ?: lightVibrant)?.let { Color(it.rgb) } ?: TabCardTheme.Default.secondary
        val accent = (darkVibrant ?: vibrant)?.let { Color(it.rgb) } ?: TabCardTheme.Default.accent

        // determine if overall theme is light or dark
        val dominantLuminance = dominant?.let {
            val r = (it.rgb shr 16 and 0xFF) / 255f
            val g = (it.rgb shr 8 and 0xFF) / 255f
            val b = (it.rgb and 0xFF) / 255f
            0.299f * r + 0.587f * g + 0.114f * b
        } ?: 0.5f

        val isLight = dominantLuminance > 0.5f

        val background = if (isLight) {
            (lightVibrant ?: muted)?.let { Color(it.rgb).copy(alpha = 0.15f) }
                ?: Color.White.copy(alpha = 0.9f)
        } else {
            (darkVibrant ?: muted)?.let { Color(it.rgb).copy(alpha = 0.2f) }
                ?: Color.Black.copy(alpha = 0.8f)
        }

        val onBackground = if (isLight) {
            (darkVibrant ?: dominant)?.let { Color(it.rgb) } ?: Color.Black
        } else {
            (lightVibrant ?: dominant)?.let { Color(it.rgb) } ?: Color.White
        }

        // create a contrasting border color
        val borderColor = accent.copy(alpha = 0.6f)

        TabCardTheme(
            primary = primary,
            secondary = secondary,
            accent = accent,
            background = background,
            onBackground = onBackground,
            borderColor = borderColor,
            isLight = isLight
        )
    } catch (e: Exception) {
        TabCardTheme.Default
    }
}

/**
 * Remember and lazily load a TabCardTheme from a bitmap source.
 * Returns default theme initially, updates when extraction completes.
 *
 * @param tabId unique identifier for caching
 * @param bitmapProvider suspend function to get the bitmap (favicon, screenshot)
 */
@Composable
fun rememberTabCardTheme(
    tabId: String,
    bitmapProvider: (suspend () -> Bitmap?)? = null
): TabCardTheme {
    var theme by remember(tabId) { mutableStateOf(TabCardTheme.Default) }

    LaunchedEffect(tabId, bitmapProvider) {
        if (bitmapProvider != null) {
            try {
                val bitmap = bitmapProvider()
                if (bitmap != null) {
                    theme = extractThemeFromBitmap(bitmap)
                }
            } catch (e: Exception) {
                // keep default theme on error
            }
        }
    }

    return theme
}

/**
 * Generate a deterministic but varied theme based on URL hash.
 * Used as initial theme before page loads.
 */
fun generateThemeFromUrl(url: String): TabCardTheme {
    if (url.isEmpty()) return TabCardTheme.Default
    return TabCardTheme.randomZany(abs(url.hashCode()))
}
