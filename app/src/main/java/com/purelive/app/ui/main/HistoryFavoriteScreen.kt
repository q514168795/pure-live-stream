package com.purelive.app.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.purelive.app.data.db.FavoriteEntity
import com.purelive.app.data.db.WatchHistoryEntity
import com.purelive.app.data.model.AppLanguage
import com.purelive.app.data.model.LiveChannel
import com.purelive.app.data.model.Strings
import com.purelive.app.data.model.VodItem
import com.purelive.app.ui.player.formatDurationMs
import com.purelive.app.ui.theme.LocalShadcnColors
import com.purelive.app.ui.theme.ShadcnBadge
import com.purelive.app.ui.theme.ShadcnButton
import com.purelive.app.ui.theme.ShadcnScrollableTabs

@Composable
fun HistoryFavoriteScreen(
    historyList: List<WatchHistoryEntity>,
    favorites: List<FavoriteEntity>,
    currentLang: AppLanguage = AppLanguage.ZH,
    onResumeHistory: (WatchHistoryEntity) -> Unit,
    onDeleteHistory: (String) -> Unit,
    onClearAllHistory: () -> Unit,
    onOpenFavoriteVod: (VodItem) -> Unit,
    onOpenFavoriteLive: (LiveChannel) -> Unit,
    onDeleteFavorite: (String) -> Unit
) {
    val colors = LocalShadcnColors.current
    var selectedSubTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvasBg)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ShadcnScrollableTabs(
                items = listOf(
                    Strings.get("history_tab", currentLang, historyList.size),
                    Strings.get("favorites_tab", currentLang, favorites.size)
                ),
                selectedIndex = selectedSubTab,
                onSelect = { selectedSubTab = it },
                modifier = Modifier.weight(1f)
            )

            if (selectedSubTab == 0 && historyList.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                ShadcnButton(
                    text = Strings.get("clear_history", currentLang),
                    primary = false,
                    icon = Icons.Default.DeleteOutline,
                    onClick = onClearAllHistory
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (selectedSubTab == 0) {
            if (historyList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = Strings.get("no_history", currentLang),
                        color = colors.textMuted,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(historyList, key = { it.vodId }) { item ->
                        HistoryCard(
                            item = item,
                            currentLang = currentLang,
                            onClick = { onResumeHistory(item) },
                            onDelete = { onDeleteHistory(item.vodId) }
                        )
                    }
                }
            }
        } else {
            if (favorites.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = Strings.get("no_favorites", currentLang),
                        color = colors.textMuted,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(favorites, key = { it.itemId }) { fav ->
                        FavoriteCard(
                            fav = fav,
                            currentLang = currentLang,
                            onClick = {
                                if (fav.itemType == "LIVE") {
                                    val lines = fav.backupLinesJson.split("#")
                                        .filter { it.startsWith("http") }
                                        .ifEmpty { listOf(fav.remarksOrStreamUrl) }
                                    onOpenFavoriteLive(
                                        LiveChannel(
                                            id = fav.rawId,
                                            name = fav.title,
                                            group = fav.groupOrCategory,
                                            streamUrl = lines.first(),
                                            lines = lines
                                        )
                                    )
                                } else {
                                    onOpenFavoriteVod(
                                        VodItem(
                                            id = fav.rawId,
                                            title = fav.title,
                                            coverUrl = fav.coverUrl,
                                            category = fav.groupOrCategory,
                                            year = "",
                                            remarks = fav.remarksOrStreamUrl
                                        )
                                    )
                                }
                            },
                            onDelete = { onDeleteFavorite(fav.itemId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(
    item: WatchHistoryEntity,
    currentLang: AppLanguage,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalShadcnColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = colors.cardBg,
        border = BorderStroke(1.dp, colors.border),
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .height(86.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.inputBg)
            ) {
                if (item.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = item.coverUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (item.category.isNotBlank()) {
                        ShadcnBadge(text = item.category)
                    }
                    ShadcnBadge(text = item.episodeTitle)
                }
                Spacer(modifier = Modifier.height(6.dp))
                val progressText = if (item.positionMs > 0L) {
                    "${formatDurationMs(item.positionMs)}" +
                        if (item.durationMs > 0L) " / ${formatDurationMs(item.durationMs)}" else ""
                } else {
                    Strings.get("click_to_play", currentLang)
                }
                Text(
                    text = progressText,
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
            }

            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = "删除记录",
                tint = colors.textMuted,
                modifier = Modifier
                    .size(20.dp)
                    .clickable { onDelete() }
            )
        }
    }
}

@Composable
private fun FavoriteCard(
    fav: FavoriteEntity,
    currentLang: AppLanguage,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalShadcnColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = colors.cardBg,
        border = BorderStroke(1.dp, colors.border),
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (fav.itemType == "LIVE") {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.inputBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LiveTv,
                        contentDescription = null,
                        tint = colors.textPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .width(56.dp)
                        .height(74.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.inputBg)
                ) {
                    if (fav.coverUrl.isNotBlank()) {
                        AsyncImage(
                            model = fav.coverUrl,
                            contentDescription = fav.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (fav.itemType == "LIVE") {
                        ShadcnBadge(
                            text = "LIVE",
                            bgColor = colors.liveBadgeBg,
                            borderColor = colors.liveBadgeBorder,
                            textColor = colors.liveBadgeText
                        )
                    } else {
                        ShadcnBadge(text = fav.groupOrCategory.ifBlank { Strings.get("tab_vod", currentLang) })
                    }
                    Text(
                        text = fav.title,
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (fav.itemType == "LIVE") {
                        fav.groupOrCategory
                    } else {
                        fav.remarksOrStreamUrl.ifBlank { Strings.get("click_to_play", currentLang) }
                    },
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = "取消收藏",
                tint = colors.textMuted,
                modifier = Modifier
                    .size(20.dp)
                    .clickable { onDelete() }
            )
        }
    }
}
