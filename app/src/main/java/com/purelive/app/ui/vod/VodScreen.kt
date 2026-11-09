package com.purelive.app.ui.vod

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.purelive.app.data.db.FavoriteEntity
import com.purelive.app.data.model.AppLanguage
import com.purelive.app.data.model.Strings
import com.purelive.app.data.model.VodFilterGroup
import com.purelive.app.data.model.VodItem
import com.purelive.app.player.ExoPlayerManager
import com.purelive.app.ui.main.ActiveVodPlayerState
import com.purelive.app.ui.main.VodUiState
import com.purelive.app.ui.player.TouchPlayerView
import com.purelive.app.ui.theme.LocalShadcnColors
import com.purelive.app.ui.theme.ShadcnBadge
import com.purelive.app.ui.theme.ShadcnButton
import com.purelive.app.ui.theme.ShadcnScrollableTabs
import com.purelive.app.ui.theme.ShadcnSearchBar

@Composable
fun VodScreen(
    state: VodUiState,
    currentLang: AppLanguage = AppLanguage.ZH,
    onSelectCategory: (Int) -> Unit,
    onUpdateFilter: (String, String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    onClearSearch: () -> Unit,
    onLoadNextPage: () -> Unit,
    onOpenVodDetail: (VodItem) -> Unit,
    onStartPlayback: () -> Unit,
    onRetry: () -> Unit
) {
    val colors = LocalShadcnColors.current
    val currentCategory = state.categories.getOrNull(state.selectedCategoryIndex)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvasBg)
    ) {
        // 1. Search Bar (Full Width, No Expand Button)
        ShadcnSearchBar(
            value = state.searchQuery,
            onValueChange = onSearchQueryChange,
            onSearch = onSubmitSearch,
            onClear = onClearSearch,
            placeholder = Strings.get("search_placeholder", currentLang),
            buttonText = Strings.get("search_button", currentLang),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        )

        // 2. Category Tabs (电影 / 电视剧 / 综艺 / 动漫 / 短剧 / 纪录片 ...)
        if (!state.isSearchMode && state.categories.isNotEmpty()) {
            val localizedCategoryNames = state.categories.map { cat ->
                when (cat.typeId) {
                    1 -> Strings.get("cat_movie", currentLang)
                    2 -> Strings.get("cat_series", currentLang)
                    3 -> Strings.get("cat_variety", currentLang)
                    4 -> Strings.get("cat_anime", currentLang)
                    5 -> Strings.get("cat_short", currentLang)
                    42 -> Strings.get("cat_doc", currentLang)
                    38 -> Strings.get("cat_hot_movie", currentLang)
                    39 -> Strings.get("cat_hot_series", currentLang)
                    40 -> Strings.get("cat_hot_variety", currentLang)
                    37 -> Strings.get("cat_hot_anime", currentLang)
                    else -> cat.name
                }
            }
            ShadcnScrollableTabs(
                items = localizedCategoryNames,
                selectedIndex = state.selectedCategoryIndex,
                onSelect = onSelectCategory,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        } else if (state.isSearchMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = Strings.get("search_results", currentLang, state.searchQuery),
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = Strings.get("back_to_browse", currentLang),
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { onClearSearch() }
                )
            }
        }

        // 3. Multi-Condition Filter Rows (分类 / 地区 / 语言 / 年份 / 排序) - Directly Expanded
        if (!state.isSearchMode && !currentCategory?.filters.isNullOrEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .background(colors.cardBg, RoundedCornerShape(8.dp))
                    .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                    .padding(vertical = 6.dp, horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                currentCategory?.filters?.forEach { group ->
                    FilterGroupRow(
                        group = group,
                        selectedValue = state.selectedFilters[group.key].orEmpty(),
                        currentLang = currentLang,
                        onSelect = { v -> onUpdateFilter(group.key, v) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 4. Poster Grid
        when {
            state.isLoadingList -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = colors.textPrimary, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = Strings.get("loading_vod", currentLang), color = colors.textSecondary, fontSize = 13.sp)
                    }
                }
            }
            state.error != null && state.items.isEmpty() -> {
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
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(state.items, key = { _, item -> item.id }) { index, item ->
                        if (index >= state.items.size - 6) {
                            LaunchedEffect(index) {
                                onLoadNextPage()
                            }
                        }
                        VodPosterCard(
                            item = item,
                            onClick = { onOpenVodDetail(item) }
                        )
                    }

                    if (state.isAppendingPage) {
                        item(span = { GridItemSpan(3) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = colors.textSecondary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterGroupRow(
    group: VodFilterGroup,
    selectedValue: String,
    currentLang: AppLanguage,
    onSelect: (String) -> Unit
) {
    val colors = LocalShadcnColors.current
    val localizedGroupName = Strings.getFilterLabel(group.name, currentLang)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "$localizedGroupName:",
            color = colors.textMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(end = 4.dp)
        )
        group.options.forEach { opt ->
            val selected = opt.value == selectedValue
            val localizedOptionLabel = Strings.getFilterLabel(opt.label, currentLang)
            Surface(
                onClick = { onSelect(opt.value) },
                shape = RoundedCornerShape(6.dp),
                color = if (selected) colors.primaryButtonBg else colors.inputBg,
                border = if (selected) null else BorderStroke(1.dp, colors.border),
                shadowElevation = 0.dp,
                modifier = Modifier.height(28.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 10.dp)
                ) {
                    Text(
                        text = localizedOptionLabel,
                        color = if (selected) colors.primaryButtonText else colors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun VodPosterCard(
    item: VodItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalShadcnColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = colors.cardBg,
        border = BorderStroke(1.dp, colors.border),
        shadowElevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.68f) // Standard 2:3 vertical poster ratio
                    .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))
                    .background(colors.inputBg)
            ) {
                if (item.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = item.coverUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = item.title.take(4),
                            color = colors.textMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Top-Right Score Badge (Only when valid non-zero score exists)
                if (item.score.isNotBlank() && item.score != "0.0") {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = colors.scoreBadgeBg.copy(alpha = 0.95f),
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                    ) {
                        Text(
                            text = item.score,
                            color = colors.scoreBadgeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }

                // Bottom Remarks Overlay (Clean deduplicated episode status)
                val cleanRemarks = remember(item.remarks, item.score) {
                    val r = item.remarks.trim()
                    val s = item.score.trim()
                    if (r.isEmpty() || r == s || r == "${s}分" || (s.isNotEmpty() && r == "未出分")) {
                        ""
                    } else {
                        r
                    }
                }
                if (cleanRemarks.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (colors.isDark) colors.canvasBg.copy(alpha = 0.85f) else colors.cardBg.copy(alpha = 0.92f),
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp)
                    ) {
                        Text(
                            text = cleanRemarks,
                            color = colors.textPrimary,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)) {
                Text(
                    text = item.title,
                    color = colors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = listOf(item.category, item.year).filter { it.isNotBlank() }.joinToString(" · ")
                        .ifBlank { "影视点播" },
                    color = colors.textSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun VodPlayerDetailOverlay(
    playerState: ActiveVodPlayerState,
    favorites: List<FavoriteEntity>,
    currentLang: AppLanguage = AppLanguage.ZH,
    onSelectSourceAndEpisode: (Int, Int) -> Unit,
    onStartPlayback: () -> Unit,
    onNextEpisode: () -> Unit,
    onSaveProgress: (Long, Long) -> Unit,
    onToggleFavorite: (VodItem) -> Unit,
    onClose: () -> Unit,
    onFullscreenChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val colors = LocalShadcnColors.current
    var isFullscreen by remember { mutableStateOf(false) }
    var isEpisodeAscending by rememberSaveable { mutableStateOf(true) }

    val vod = playerState.vodItem ?: return
    val currentSource = vod.playSources.getOrNull(playerState.selectedSourceIndex)
    val currentEpisode = currentSource?.episodes?.getOrNull(playerState.selectedEpisodeIndex)
    val isFav = favorites.any { it.itemId == "vod_${vod.id}" }

    val playerManager = remember {
        ExoPlayerManager(
            context = context,
            onPlaybackEnded = {
                onNextEpisode()
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            val pos = playerManager.exoPlayer.currentPosition.coerceAtLeast(0L)
            val dur = playerManager.exoPlayer.duration.coerceAtLeast(0L)
            if (pos > 1000L) {
                onSaveProgress(pos, dur)
            }
            playerManager.release()
        }
    }

    LaunchedEffect(playerState.resolvedEpisodeUrl, playerState.isPlaybackStarted) {
        if (playerState.isPlaybackStarted && playerState.resolvedEpisodeUrl.isNotBlank()) {
            playerManager.play(
                url = playerState.resolvedEpisodeUrl,
                startPositionMs = playerState.startPositionMs
            )
        }
    }

    BackHandler {
        if (isFullscreen) {
            isFullscreen = false
            onFullscreenChange(false)
        } else {
            val pos = playerManager.exoPlayer.currentPosition.coerceAtLeast(0L)
            val dur = playerManager.exoPlayer.duration.coerceAtLeast(0L)
            if (pos > 1000L) {
                onSaveProgress(pos, dur)
            }
            onClose()
        }
    }

    if (isFullscreen) {
        TouchPlayerView(
            playerManager = playerManager,
            title = vod.title,
            subtitle = currentEpisode?.title ?: vod.remarks,
            isLive = false,
            isLoadingStream = playerState.isLoadingDetail || playerState.isResolvingEpisode,
            isFullscreen = true,
            isPlaybackStarted = playerState.isPlaybackStarted,
            coverUrl = vod.coverUrl,
            onStartPlay = onStartPlayback,
            currentLang = currentLang,
            onToggleFullscreen = { full ->
                isFullscreen = full
                onFullscreenChange(full)
            },
            onNextEpisode = onNextEpisode,
            onProgressUpdate = onSaveProgress,
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvasBg)
    ) {
        // 1. Video Player (16:9, Non-Autoplay Preview Supported)
        TouchPlayerView(
            playerManager = playerManager,
            title = vod.title,
            subtitle = currentEpisode?.let { Strings.get("current_playing", currentLang, it.title) } ?: vod.remarks,
            isLive = false,
            isLoadingStream = playerState.isLoadingDetail || playerState.isResolvingEpisode,
            isFullscreen = false,
            isPlaybackStarted = playerState.isPlaybackStarted,
            coverUrl = vod.coverUrl,
            onStartPlay = onStartPlayback,
            currentLang = currentLang,
            onToggleFullscreen = { full ->
                isFullscreen = full
                onFullscreenChange(full)
            },
            onBackClick = {
                val pos = playerManager.exoPlayer.currentPosition.coerceAtLeast(0L)
                val dur = playerManager.exoPlayer.duration.coerceAtLeast(0L)
                if (pos > 1000L) {
                    onSaveProgress(pos, dur)
                }
                onClose()
            },
            onNextEpisode = onNextEpisode,
            onProgressUpdate = onSaveProgress,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        )

        // 2. Metadata + Source Tabs + Episode Selection
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Title + Favorite Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = vod.title,
                        color = colors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (vod.category.isNotBlank()) {
                            ShadcnBadge(text = vod.category)
                        }
                        if (vod.year.isNotBlank()) {
                            ShadcnBadge(text = vod.year)
                        }
                        if (vod.area.isNotBlank()) {
                            ShadcnBadge(text = vod.area)
                        }
                        if (vod.remarks.isNotBlank()) {
                            ShadcnBadge(text = vod.remarks)
                        }
                        if (vod.score.isNotBlank()) {
                            ShadcnBadge(
                                text = "豆瓣 ${vod.score}",
                                bgColor = colors.scoreBadgeBg,
                                textColor = colors.scoreBadgeText
                            )
                        }
                    }
                }

                ShadcnButton(
                    text = if (isFav) Strings.get("favorited", currentLang) else Strings.get("favorite", currentLang),
                    icon = if (isFav) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    primary = isFav,
                    onClick = { onToggleFavorite(vod) }
                )
            }

            if (vod.actor.isNotBlank() || vod.director.isNotBlank()) {
                Text(
                    text = buildString {
                        if (vod.director.isNotBlank()) append("${Strings.get("director", currentLang)}：${vod.director}  ")
                        if (vod.actor.isNotBlank()) append("${Strings.get("actor", currentLang)}：${vod.actor}")
                    },
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (vod.blurb.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.cardBg,
                    border = BorderStroke(1.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = vod.blurb,
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (playerState.errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = colors.cardBg,
                    border = BorderStroke(1.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = playerState.errorMessage,
                        color = colors.textPrimary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Play Sources (线路切换)
            if (vod.playSources.isNotEmpty()) {
                Text(
                    text = Strings.get("play_sources", currentLang, vod.playSources.size),
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                ShadcnScrollableTabs(
                    items = vod.playSources.map { "${it.name} (${it.episodes.size})" },
                    selectedIndex = playerState.selectedSourceIndex,
                    onSelect = { srcIdx ->
                        val targetEpIdx = playerState.selectedEpisodeIndex
                            .coerceIn(0, (vod.playSources[srcIdx].episodes.size - 1).coerceAtLeast(0))
                        onSelectSourceAndEpisode(srcIdx, targetEpIdx)
                    }
                )
            }

            // Episode Grid (选集切换 + 排序功能)
            if (currentSource != null && currentSource.episodes.isNotEmpty()) {
                val totalEpCount = currentSource.episodes.size

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = Strings.get("episodes", currentLang),
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = Strings.get("episodes_count", currentLang, totalEpCount),
                            color = colors.textSecondary,
                            fontSize = 12.sp
                        )
                    }

                    // Episode Sorting Button (正序 1-N ⇄ 倒序 N-1)
                    Surface(
                        onClick = { isEpisodeAscending = !isEpisodeAscending },
                        shape = RoundedCornerShape(6.dp),
                        color = colors.inputBg,
                        border = BorderStroke(1.dp, colors.border),
                        shadowElevation = 0.dp,
                        modifier = Modifier.height(28.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapVert,
                                contentDescription = "集数排序",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (isEpisodeAscending) Strings.get("sort_asc", currentLang, totalEpCount) else Strings.get("sort_desc", currentLang, totalEpCount),
                                color = colors.textPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Render episode grid according to current sorting
                val displayedIndices = remember(totalEpCount, isEpisodeAscending) {
                    if (isEpisodeAscending) {
                        currentSource.episodes.indices.toList()
                    } else {
                        currentSource.episodes.indices.reversed().toList()
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 420.dp)
                ) {
                    items(displayedIndices, key = { epIdx -> epIdx }) { originalIdx ->
                        val ep = currentSource.episodes[originalIdx]
                        val selected = originalIdx == playerState.selectedEpisodeIndex
                        Surface(
                            onClick = {
                                onSelectSourceAndEpisode(playerState.selectedSourceIndex, originalIdx)
                            },
                            shape = RoundedCornerShape(6.dp),
                            color = if (selected) colors.primaryButtonBg else colors.cardBg,
                            border = if (selected) null else BorderStroke(1.dp, colors.border),
                            shadowElevation = 0.dp,
                            modifier = Modifier.height(38.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            ) {
                                Text(
                                    text = ep.title,
                                    color = if (selected) colors.primaryButtonText else colors.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            } else if (playerState.isLoadingDetail) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = colors.textPrimary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}
