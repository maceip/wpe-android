/**
 * Copyright (C) 2025 Igalia S.L. <info@igalia.com>
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

package org.wpewebkit.tools.minibrowser

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable
import org.wpewebkit.tools.minibrowser.navigation.BottomSheetSceneStrategy
import org.wpewebkit.tools.minibrowser.navigation.BrowserNavigator
import org.wpewebkit.tools.minibrowser.navigation.CompositeSceneStrategy
import org.wpewebkit.tools.minibrowser.navigation.rememberBrowserNavigationState
import org.wpewebkit.tools.minibrowser.ui.theme.MiniBrowserTheme
import org.wpewebkit.wpeview.WPEChromeClient
import org.wpewebkit.wpeview.WPEView
import org.wpewebkit.wpeview.WPEViewClient

@Serializable
private data object TabsList : NavKey

@Serializable
private data class TabRoute(val id: String) : NavKey

@Serializable
private data class TabSheet(val id: String) : NavKey

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun MiniBrowserApp(viewModel: BrowserViewModel) {
    MiniBrowserTheme {
        val context = LocalContext.current
        val focusManager = LocalFocusManager.current
        val browserState by viewModel.browserState.collectAsState()
        val tabs = browserState.tabs
        val tabRoutes = remember(tabs) { tabs.map { TabRoute(it.id) }.toSet() }
        val topLevelRoutes = remember(tabRoutes) { tabRoutes + TabsList }
        val navigationState = rememberBrowserNavigationState(
            startRoute = TabsList,
            topLevelRoutes = topLevelRoutes
        )
        val navigator = remember(navigationState) { BrowserNavigator(navigationState) }
        var pendingTabId by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(tabs.size) {
            if (tabs.isEmpty()) {
                val newTab = Tab.newTab(context, INITIAL_URL)
                viewModel.addTab(newTab)
                pendingTabId = newTab.id
            }
        }

        LaunchedEffect(pendingTabId, tabs) {
            val pending = pendingTabId
            if (pending != null && tabs.any { it.id == pending }) {
                navigator.navigate(TabRoute(pending))
                pendingTabId = null
            }
        }

        LaunchedEffect(navigationState.topLevelRoute) {
            val selected = navigationState.topLevelRoute
            if (selected is TabRoute) {
                viewModel.selectTab(selected.id)
            }
        }

        val windowAdaptiveInfo = currentWindowAdaptiveInfo()
        val directive = remember(windowAdaptiveInfo) {
            calculatePaneScaffoldDirective(windowAdaptiveInfo)
                .copy(horizontalPartitionSpacerSize = 0.dp)
        }
        val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>(directive = directive)
        val bottomSheetStrategy = remember { BottomSheetSceneStrategy<NavKey>() }
        val sceneStrategy = remember(bottomSheetStrategy, listDetailStrategy) {
            CompositeSceneStrategy(listOf(bottomSheetStrategy, listDetailStrategy))
        }

        val selectedTab = browserState.selectedTabId?.let { id ->
            tabs.firstOrNull { it.id == id }
        }

        val entryProvider = entryProvider {
            entry<TabsList>(
                metadata = ListDetailSceneStrategy.listPane(
                    detailPlaceholder = {
                        EmptyState(message = "Select or create a tab to start browsing.")
                    }
                )
            ) {
                TabListPane(
                    tabs = tabs,
                    selectedTabId = browserState.selectedTabId,
                    onTabSelected = { id ->
                        viewModel.selectTab(id)
                        navigator.navigate(TabRoute(id))
                    },
                    onNewTab = {
                        val newTab = Tab.newTab(context, INITIAL_URL)
                        viewModel.addTab(newTab)
                        pendingTabId = newTab.id
                    }
                )
            }
            entry<TabRoute>(
                metadata = ListDetailSceneStrategy.detailPane()
            ) { route ->
                val tab = tabs.firstOrNull { it.id == route.id }
                if (tab == null) {
                    EmptyState(message = "No tab available.")
                } else {
                    BrowserDetailPane(
                        tab = tab,
                        focusManager = focusManager,
                        onUpdateUrl = { url ->
                            viewModel.updateTabUrl(tab.id, url)
                        },
                        onUpdateLoading = { isLoading ->
                            viewModel.updateTabLoading(tab.id, isLoading)
                        },
                        onOpenSheet = {
                            navigator.navigate(TabSheet(tab.id))
                        },
                        onRequestBack = {
                            if (tab.webview.canGoBack()) {
                                tab.webview.goBack()
                            } else {
                                navigator.goBack()
                            }
                        }
                    )
                }
            }
            entry<TabSheet>(
                metadata = BottomSheetSceneStrategy.bottomSheet()
            ) { sheet ->
                val tab = tabs.firstOrNull { it.id == sheet.id }
                TabActionsSheet(
                    title = tab?.url ?: "Tab actions",
                    onReload = {
                        tab?.webview?.reload()
                        navigator.goBack()
                    },
                    onDismiss = {
                        navigator.goBack()
                    }
                )
            }
        }

        NavDisplay(
            entries = navigationState.toDecoratedEntries(entryProvider),
            onBack = {
                if (selectedTab?.webview?.canGoBack() == true) {
                    selectedTab.webview.goBack()
                } else {
                    navigator.goBack()
                }
            },
            sceneStrategy = sceneStrategy,
            transitionSpec = {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(320)
                ) togetherWith slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(320)
                )
            },
            popTransitionSpec = {
                slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(280)
                ) togetherWith slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(280)
                )
            },
            predictivePopTransitionSpec = {
                slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(280)
                ) togetherWith slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(280)
                )
            }
        )
    }
}

@Composable
private fun TabListPane(
    tabs: List<Tab>,
    selectedTabId: String?,
    onTabSelected: (String) -> Unit,
    onNewTab: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Tabs",
            style = MaterialTheme.typography.headlineSmall
        )
        HorizontalDivider()
        if (tabs.isEmpty()) {
            EmptyState(message = "No tabs yet.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f, fill = true),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(tabs, key = { it.id }) { tab ->
                    val isSelected = tab.id == selectedTabId
                    ListItem(
                        headlineContent = { Text(text = tab.url.ifBlank { "New tab" }) },
                        supportingContent = {
                            val status = if (tab.isLoading) "Loading" else "Ready"
                            Text(text = "$status • ${tab.id.take(8)}")
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                            .clickable { onTabSelected(tab.id) }
                    )
                }
            }
        }
        FloatingActionButton(
            onClick = onNewTab,
            modifier = Modifier.align(Alignment.End)
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "New tab")
        }
    }
}

@Composable
private fun BrowserDetailPane(
    tab: Tab,
    focusManager: FocusManager,
    onUpdateUrl: (String) -> Unit,
    onUpdateLoading: (Boolean) -> Unit,
    onOpenSheet: () -> Unit,
    onRequestBack: () -> Unit
) {
    var addressText by remember(tab.id) { mutableStateOf(tab.url) }
    var progressValue by remember(tab.id) { mutableStateOf(0) }
    var canGoBack by remember(tab.id) { mutableStateOf(false) }
    var canGoForward by remember(tab.id) { mutableStateOf(false) }

    LaunchedEffect(tab.url) {
        if (addressText != tab.url) {
            addressText = tab.url
        }
    }

    DisposableEffect(tab.id) {
        val chromeClient = object : WPEChromeClient() {
            override fun onProgressChanged(view: WPEView, progress: Int) {
                super.onProgressChanged(view, progress)
                progressValue = progress
            }

            override fun onUriChanged(view: WPEView, uri: String) {
                super.onUriChanged(view, uri)
                onUpdateUrl(uri)
            }
        }

        val viewClient = object : WPEViewClient() {
            override fun onPageStarted(view: WPEView, url: String) {
                super.onPageStarted(view, url)
                onUpdateUrl(url)
                onUpdateLoading(true)
                canGoBack = view.canGoBack()
                canGoForward = view.canGoForward()
            }

            override fun onPageFinished(view: WPEView, url: String) {
                super.onPageFinished(view, url)
                onUpdateLoading(false)
                canGoBack = view.canGoBack()
                canGoForward = view.canGoForward()
            }
        }

        tab.webview.wpeChromeClient = chromeClient
        tab.webview.wpeViewClient = viewClient

        onDispose {
            tab.webview.wpeChromeClient = null
            tab.webview.wpeViewClient = null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onRequestBack,
                enabled = canGoBack
            ) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
            }
            IconButton(
                onClick = { tab.webview.goForward() },
                enabled = canGoForward
            ) {
                Icon(imageVector = Icons.Default.ArrowForward, contentDescription = "Forward")
            }
            IconButton(
                onClick = { tab.webview.reload() }
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reload")
            }
            IconButton(onClick = onOpenSheet) {
                Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Tab options")
            }
            Spacer(modifier = Modifier.size(8.dp))
            OutlinedTextField(
                value = addressText,
                onValueChange = { addressText = it },
                singleLine = true,
                modifier = Modifier.weight(1f),
                placeholder = { Text(text = "Search or type URL") },
                keyboardOptions = androidx.compose.ui.text.input.KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = androidx.compose.ui.text.input.KeyboardActions(
                    onDone = {
                        focusManager.clearFocus(force = true)
                        onUpdateLoading(true)
                        onCommit(addressText, tab)
                    }
                )
            )
        }
        if (progressValue in 1..99) {
            LinearProgressIndicator(
                progress = progressValue / 100f,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = {
                    tab.webview.also { view ->
                        (view.parent as? ViewGroup)?.removeView(view)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            AnimatedVisibility(
                visible = tab.isLoading,
                enter = fadeIn(animationSpec = tween(240)),
                exit = fadeOut(animationSpec = tween(240))
            ) {
                LoadingOverlay()
            }
        }
    }
}

private fun onCommit(text: String, tab: Tab) {
    val url: String = if ((text.contains(".") || text.contains(":")) && !text.contains(" ")) {
        normalizeAddress(text)
    } else {
        SEARCH_URI_BASE + text
    }
    tab.webview.loadUrl(url)
}

@Composable
private fun EmptyState(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun TabActionsSheet(
    title: String,
    onReload: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Tab actions", style = MaterialTheme.typography.titleMedium)
        Text(text = title, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onReload) {
                Text(text = "Reload")
            }
            Button(onClick = onDismiss) {
                Text(text = "Close")
            }
        }
    }
}

@Composable
private fun LoadingOverlay() {
    val shimmerTransition = rememberInfiniteTransition(label = "tab-loading")
    val shimmer by shimmerTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400),
            repeatMode = RepeatMode.Restart
        ),
        label = "tab-loading-shimmer"
    )
    val overlayBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f + (0.2f * shimmer)),
            MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayBrush),
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Opening new tab...",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
