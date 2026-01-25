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

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.wpewebkit.tools.minibrowser.Tab
import kotlin.math.roundToInt

/**
 * A chunky, themed tab card with zany shapes and colors.
 * Uses thick borders, asymmetric corners, and dynamic theming.
 */
@Composable
fun ChunkyTabCard(
    tab: Tab,
    theme: TabCardTheme,
    isSelected: Boolean,
    isDragging: Boolean,
    elevation: Dp,
    dragOffsetY: Float = 0f,
    onClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    // animate theme color transitions
    val backgroundColor by animateColorAsState(
        targetValue = if (isDragging) theme.primary.copy(alpha = 0.3f)
                      else if (isSelected) theme.background
                      else theme.background.copy(alpha = 0.7f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "bg-color"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isDragging) theme.accent
                      else if (isSelected) theme.primary
                      else theme.borderColor.copy(alpha = 0.5f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "border-color"
    )

    val borderWidth by animateDpAsState(
        targetValue = if (isDragging) 4.dp else if (isSelected) 3.dp else 2.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "border-width"
    )

    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.04f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "scale"
    )

    // chunky asymmetric shape - different corner radii for "zany" look
    val chunkyShape = remember(tab.id) {
        val seed = tab.id.hashCode()
        RoundedCornerShape(
            topStart = (12 + (seed and 0xF)).dp,
            topEnd = (8 + ((seed shr 4) and 0xF)).dp,
            bottomEnd = (16 + ((seed shr 8) and 0xF)).dp,
            bottomStart = (6 + ((seed shr 12) and 0xF)).dp
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(0, dragOffsetY.roundToInt()) }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(elevation, chunkyShape)
            .clip(chunkyShape)
            .background(backgroundColor)
            .border(borderWidth, borderColor, chunkyShape)
            .clickable(enabled = !isDragging, onClick = onClick)
    ) {
        // decorative chunky shapes in background
        ChunkyDecorations(
            theme = theme,
            seed = tab.id.hashCode(),
            modifier = Modifier.matchParentSize()
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // chunky favicon box
                ChunkyFaviconBox(
                    theme = theme,
                    isLoading = tab.isLoading,
                    modifier = Modifier.size(44.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // title and URL
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tab.title.ifEmpty { "New Tab" },
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = theme.onBackground
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tab.url.ifEmpty { "about:blank" },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = theme.onBackground.copy(alpha = 0.7f)
                    )
                }

                // media indicators
                MediaIndicators(tab = tab, theme = theme)

                // chunky close button
                ChunkyCloseButton(
                    theme = theme,
                    enabled = !isDragging,
                    onClick = onClose
                )
            }

            // chunky progress bar
            if (tab.isLoading) {
                ChunkyProgressBar(
                    progress = tab.progress,
                    theme = theme,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Decorative chunky shapes drawn in background
 */
@Composable
private fun ChunkyDecorations(
    theme: TabCardTheme,
    seed: Int,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // draw chunky diagonal stripe
        val stripeWidth = 40.dp.toPx()
        val stripeOffset = ((seed and 0xFF) / 255f) * w

        drawLine(
            color = theme.accent.copy(alpha = 0.1f),
            start = Offset(stripeOffset, 0f),
            end = Offset(stripeOffset + stripeWidth, h),
            strokeWidth = stripeWidth
        )

        // draw chunky corner accent
        val cornerSize = 20.dp.toPx()
        if ((seed and 1) == 0) {
            drawRoundRect(
                color = theme.primary.copy(alpha = 0.15f),
                topLeft = Offset(w - cornerSize - 8.dp.toPx(), 8.dp.toPx()),
                size = Size(cornerSize, cornerSize),
                cornerRadius = CornerRadius(4.dp.toPx())
            )
        }

        // draw chunky border accent dots
        val dotRadius = 3.dp.toPx()
        val dotColor = theme.secondary.copy(alpha = 0.3f)
        for (i in 0..2) {
            val x = 16.dp.toPx() + i * 12.dp.toPx()
            drawCircle(dotColor, dotRadius, Offset(x, h - 8.dp.toPx()))
        }
    }
}

/**
 * Chunky favicon box with thick border
 */
@Composable
private fun ChunkyFaviconBox(
    theme: TabCardTheme,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        theme.primary.copy(alpha = 0.2f),
                        theme.secondary.copy(alpha = 0.1f)
                    )
                )
            )
            .border(2.dp, theme.borderColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Language,
            contentDescription = null,
            tint = theme.primary,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    if (isLoading) {
                        rotationZ = (System.currentTimeMillis() % 3600) / 10f
                    }
                }
        )
    }
}

/**
 * Media state indicators (audio, camera, mic)
 */
@Composable
private fun MediaIndicators(
    tab: Tab,
    theme: TabCardTheme,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (tab.isPlayingAudio) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = "Playing audio",
                tint = theme.accent,
                modifier = Modifier.size(16.dp)
            )
        }
        if (tab.isCameraActive) {
            Icon(
                imageVector = Icons.Default.Videocam,
                contentDescription = "Camera active",
                tint = Color.Red.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp)
            )
        }
        if (tab.isMicrophoneActive) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Microphone active",
                tint = Color.Red.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Chunky close button with thick border
 */
@Composable
private fun ChunkyCloseButton(
    theme: TabCardTheme,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(32.dp)
            .border(2.dp, theme.borderColor.copy(alpha = 0.4f), CircleShape)
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close Tab",
            tint = theme.onBackground.copy(alpha = 0.8f),
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * Chunky progress bar with thick appearance
 */
@Composable
private fun ChunkyProgressBar(
    progress: Float,
    theme: TabCardTheme,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(6.dp)
            .padding(horizontal = 4.dp)
    ) {
        // track
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(3.dp))
                .background(theme.secondary.copy(alpha = 0.2f))
        )
        // progress
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(theme.primary, theme.accent)
                    )
                )
        )
    }
}

/**
 * Compact chunky tab for small screens / horizontal tab strip
 */
@Composable
fun CompactChunkyTab(
    tab: Tab,
    theme: TabCardTheme,
    isSelected: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) theme.background else theme.background.copy(alpha = 0.5f),
        label = "compact-bg"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) theme.primary else Color.Transparent,
        label = "compact-border"
    )

    Box(
        modifier = modifier
            .width(120.dp)
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = null,
                tint = theme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = tab.title.ifEmpty { "Tab" },
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = theme.onBackground,
                modifier = Modifier.weight(1f)
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = theme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(14.dp)
                        .clickable(onClick = onClose)
                )
            }
        }
    }
}
