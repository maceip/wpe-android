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

import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.getSystemService
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

/**
 * Interface for controlling glass/blur effects based on device capabilities
 * and power state. Implementations should disable effects in low power mode
 * to preserve battery.
 */
interface GlassEffectController {
    /** Whether glass effects are currently enabled */
    val isEnabled: Boolean

    /** Whether the device supports native blur (Android 12+) */
    val supportsNativeBlur: Boolean

    /** Whether device is in low power/battery saver mode */
    val isLowPowerMode: Boolean
}

/**
 * Default implementation that checks device capabilities and power state
 */
class DefaultGlassEffectController(
    private val powerManager: PowerManager?
) : GlassEffectController {

    override val supportsNativeBlur: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    override val isLowPowerMode: Boolean
        get() = powerManager?.isPowerSaveMode == true

    // disable glass effects in low power mode to save battery
    override val isEnabled: Boolean
        get() = !isLowPowerMode
}

/** Composition local for glass effect controller */
val LocalGlassEffectController = compositionLocalOf<GlassEffectController> {
    error("No GlassEffectController provided")
}

/**
 * Provides glass effect controller to the composition tree
 */
@Composable
fun ProvideGlassEffectController(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val powerManager = remember { context.getSystemService<PowerManager>() }
    val controller = remember(powerManager) {
        DefaultGlassEffectController(powerManager)
    }

    CompositionLocalProvider(
        LocalGlassEffectController provides controller,
        content = content
    )
}

/**
 * Remember a HazeState for use with glass effects
 */
@Composable
fun rememberGlassState(): HazeState {
    return remember { HazeState() }
}

/**
 * Modifier to mark content that should be blurred behind glass surfaces.
 * Apply this to the main content that glass overlays will blur.
 */
fun Modifier.glassSource(state: HazeState): Modifier {
    return this.haze(state)
}

/**
 * Modifier to create a frosted glass surface effect.
 * Content behind this surface will appear blurred.
 *
 * Falls back to semi-transparent background when:
 * - Device is in low power mode
 * - Glass effects are disabled
 *
 * @param state The shared HazeState from the content source
 * @param tintColor The tint color for the glass effect
 * @param blurRadius The blur radius in dp (default 20)
 * @param fallbackColor The color to use when glass is disabled
 */
@Composable
fun Modifier.frostedGlass(
    state: HazeState,
    tintColor: Color = Color.White.copy(alpha = 0.7f),
    blurRadius: Float = 20f,
    fallbackColor: Color = Color.White.copy(alpha = 0.85f)
): Modifier {
    val controller = LocalGlassEffectController.current

    return if (controller.isEnabled) {
        this.hazeChild(
            state = state,
            style = HazeStyle(
                tint = HazeTint(tintColor),
                blurRadius = blurRadius.dp,
            )
        )
    } else {
        // fallback for low power mode - simple semi-transparent background
        this.background(fallbackColor)
    }
}

/**
 * Default glass style for navigation bars - light frosted appearance
 */
object GlassStyles {
    val navBarLight = HazeStyle(
        tint = HazeTint(Color.White.copy(alpha = 0.75f)),
        blurRadius = 24.dp,
    )

    val navBarDark = HazeStyle(
        tint = HazeTint(Color.Black.copy(alpha = 0.6f)),
        blurRadius = 24.dp,
    )

    val tabCard = HazeStyle(
        tint = HazeTint(Color.White.copy(alpha = 0.6f)),
        blurRadius = 16.dp,
    )
}

private val Float.dp: androidx.compose.ui.unit.Dp
    get() = androidx.compose.ui.unit.Dp(this)
