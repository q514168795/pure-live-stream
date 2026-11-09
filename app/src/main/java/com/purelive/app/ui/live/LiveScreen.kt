package com.purelive.app.ui.live

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.purelive.app.data.db.FavoriteEntity
import com.purelive.app.data.model.AppLanguage
import com.purelive.app.data.model.LiveChannel
import com.purelive.app.data.model.Strings
import com.purelive.app.player.ExoPlayerManager
import com.purelive.app.ui.main.LiveUiState
import com.purelive.app.ui.player.TouchPlayerView
import com.purelive.app.ui.theme.LocalShadcnColors
import com.purelive.app.ui.theme.ShadcnBadge
import com.purelive.app.ui.theme.ShadcnButton
import com.purelive.app.ui.theme.ShadcnScrollableTabs

@Composable
fun LiveScreen(
    state: LiveUiState,
    favorites: List<FavoriteEntity>,
    currentLang: AppLanguage = AppLanguage.ZH,
    onSelectGroup: (String) -> Unit,
    onSelectChannel: (LiveChannel) -> Unit,
    onStartPlayback: () -> Unit,
    onSwitchLine: (Int) -> Unit,
    onAutoNextLine: () -> Unit,
    onToggleFavorite: (LiveChannel) -> Unit,
    onRetry: () -> Unit,
    onFullscreenChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val colors = LocalShadcnColors.current
    var isFullscreen by remember { mutableStateOf(false) }

    val playerManager = remember {
        ExoPlayerManager(
            context = context,
            onPlaybackError = {
                onAutoNextLine()
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            playerManager.release()
        }
    }

    LaunchedEffect(state.resolvedStreamUrl) {
        if (state.resolvedStreamUrl.isNotBlank() && state.isPlaybackStarted) {
            playerManager.play(state.resolvedStreamUrl)
        }
    }

    val currentChannel = state.currentChannel
    val isCurrentFav = currentChannel != null && favorites.any { it.itemId == "live_${currentChannel.name}" }

    if (isFullscreen) {
        TouchPlayerView(
            playerManager = playerManager,
            title = currentChannel?.name ?: Strings.get("tab_live", currentLang),
            subtitle = currentChannel?.group.orEmpty(),
            isLive = true,
            isLoadingStream = state.isResolvingStream,
            isFullscreen = true,
            isPlaybackStarted = state.isPlaybackStarted,
            onStartPlay = onStartPlayback,
            currentLang = currentLang,
            onToggleFullscreen = { full ->
                isFullscreen = full
                onFullscreenChange(full)
            },
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvasBg)
    ) {
        // 1. Live Player Viewport (16:9, Non-Autoplay Preview Supported)
        TouchPlayerView(
            playerManager = playerManager,
            title = currentChannel?.name ?: Strings.get("tab_live", currentLang),
            subtitle = currentChannel?.let { "${it.group} · " + Strings.get("line", currentLang, state.currentLineIndex + 1) }.orEmpty(),
            isLive = true,
            isLoadingStream = state.isResolvingStream,
            isFullscreen = false,
            isPlaybackStarted = state.isPlaybackStarted,
            onStartPlay = onStartPlayback,
            currentLang = currentLang,
            onToggleFullscreen = { full ->
                isFullscreen = full
                onFullscreenChange(full)
            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        )

        // 2. Active Channel Info Bar + Multi-Line Switcher
        if (currentChannel != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.cardBg)
                    .border(width = 1.dp, color = colors.border)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    ShadcnBadge(
                        text = "LIVE",
                        bgColor = colors.liveBadgeBg,
                        borderColor = colors.liveBadgeBorder,
                        textColor = colors.liveBadgeText
                    )
                    Text(
                        text = currentChannel.name,
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (currentChannel.lines.size > 1) {
                        currentChannel.lines.forEachIndexed { idx, _ ->
                            val selected = idx == state.currentLineIndex
                            Surface(
                                onClick = { onSwitchLine(idx) },
                                shape = RoundedCornerShape(6.dp),
                                color = if (selected) colors.primaryButtonBg else colors.inputBg,
                                border = if (selected) null else BorderStroke(1.dp, colors.border),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Text(
                                        text = Strings.get("line", currentLang, idx + 1),
                                        color = if (selected) colors.primaryButtonText else colors.textSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        onClick = { onToggleFavorite(currentChannel) },
                        shape = RoundedCornerShape(6.dp),
                        color = colors.inputBg,
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isCurrentFav) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = Strings.get("favorite", currentLang),
                                tint = if (isCurrentFav) colors.textPrimary else colors.textMuted,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. Group Tabs (央视 / 卫视 / 港澳 / 台湾 / 体育 ...)
        if (state.groups.isNotEmpty()) {
            val selectedIdx = state.groups.indexOf(state.selectedGroup).coerceAtLeast(0)
            ShadcnScrollableTabs(
                items = state.groups,
                selectedIndex = selectedIdx,
                onSelect = { idx ->
                    state.groups.getOrNull(idx)?.let(onSelectGroup)
                },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }

        // 4. Channel Grid
        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = colors.textPrimary, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = Strings.get("loading_live", currentLang), color = colors.textSecondary, fontSize = 13.sp)
                    }
                }
            }
            state.error != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(text = state.error, color = colors.textSecondary, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        ShadcnButton(
                            text = Strings.get("reload", currentLang),
                            icon = Icons.Default.Refresh,
                            onClick = onRetry
                        )
                    }
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.filteredChannels, key = { it.id }) { channel ->
                        val isSelected = currentChannel?.id == channel.id
                        val isFav = favorites.any { it.itemId == "live_${channel.name}" }

                        Surface(
                            onClick = { onSelectChannel(channel) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) colors.inputBg else colors.cardBg,
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) colors.textPrimary else colors.border
                            ),
                            shadowElevation = 0.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = channel.name,
                                        color = colors.textPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = if (isSelected && state.isPlaybackStarted) {
                                            "${Strings.get("current_playing", currentLang, channel.name)} · ${channel.lines.size}"
                                        } else {
                                            "${channel.group} · ${channel.lines.size}"
                                        },
                                        color = if (isSelected && state.isPlaybackStarted) colors.liveBadgeText else colors.textMuted,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }

                                Icon(
                                    imageVector = if (isFav) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = Strings.get("favorite", currentLang),
                                    tint = if (isFav) colors.textPrimary else colors.textMuted,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable { onToggleFavorite(channel) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
