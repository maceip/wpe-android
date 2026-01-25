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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import org.wpewebkit.tools.minibrowser.Tab
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabListPane(
    tabs: List<Tab>,
    selectedTabId: String?,
    onTabClick: (Tab) -> Unit,
    onTabClose: (Tab) -> Unit,
    onNewTab: () -> Unit,
    onMoveTab: (Int, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val hazeState = remember { HazeState() }

    // drag state
    var draggingItemIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    // check glass effect availability
    // note: in low power mode, glass effects are disabled to save battery
    val glassEnabled = try {
        LocalGlassEffectController.current.isEnabled
    } catch (e: IllegalStateException) {
        true // default to enabled if controller not provided
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            // frosted glass top bar - content renders "under" it
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (glassEnabled) {
                            Modifier.hazeChild(
                                state = hazeState,
                                style = HazeStyle(
                                    tint = HazeTint(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)),
                                    blurRadius = 24.dp
                                )
                            )
                        } else {
                            Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                        }
                    )
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = "Tabs (${tabs.size})",
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewTab,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Tab"
                )
            }
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        // main content area - source for glass blur
        Box(
            modifier = Modifier
                .fillMaxSize()
                .haze(hazeState)
        ) {
            if (tabs.isEmpty()) {
                EmptyTabsMessage(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(
                        items = tabs,
                        key = { _, tab -> tab.id }
                    ) { index, tab ->
                        val isDragging = draggingItemIndex == index

                        // generate theme from URL - creates deterministic "zany" colors
                        val theme = remember(tab.url) {
                            if (tab.url.isNotEmpty()) generateThemeFromUrl(tab.url)
                            else TabCardTheme.Default
                        }

                        // animate elevation
                        val elevation by animateDpAsState(
                            targetValue = when {
                                isDragging -> 12.dp
                                tab.id == selectedTabId -> 6.dp
                                else -> 2.dp
                            },
                            animationSpec = spring(),
                            label = "elevation"
                        )

                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + scaleIn(initialScale = 0.92f),
                            exit = fadeOut() + scaleOut(targetScale = 0.92f)
                        ) {
                            ChunkyTabCard(
                                tab = tab,
                                theme = theme,
                                isSelected = tab.id == selectedTabId,
                                isDragging = isDragging,
                                elevation = elevation,
                                dragOffsetY = if (isDragging) dragOffsetY else 0f,
                                onClick = { onTabClick(tab) },
                                onClose = { onTabClose(tab) },
                                modifier = Modifier
                                    .zIndex(if (isDragging) 1f else 0f)
                                    .pointerInput(tabs.size) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggingItemIndex = index
                                                dragOffsetY = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffsetY += dragAmount.y

                                                val itemHeight = 100.dp.toPx()
                                                val targetIndex = (index + (dragOffsetY / itemHeight).roundToInt())
                                                    .coerceIn(0, tabs.lastIndex)

                                                if (targetIndex != draggingItemIndex && targetIndex != index) {
                                                    onMoveTab(draggingItemIndex, targetIndex)
                                                    draggingItemIndex = targetIndex
                                                    dragOffsetY -= (targetIndex - index) * itemHeight
                                                }
                                            },
                                            onDragEnd = {
                                                draggingItemIndex = -1
                                                dragOffsetY = 0f
                                            },
                                            onDragCancel = {
                                                draggingItemIndex = -1
                                                dragOffsetY = 0f
                                            }
                                        )
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyTabsMessage(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // chunky icon container
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "No open tabs",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap + to open a new tab",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}
