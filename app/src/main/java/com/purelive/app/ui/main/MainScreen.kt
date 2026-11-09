package com.purelive.app.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.purelive.app.data.model.AppLanguage
import com.purelive.app.data.model.Strings
import com.purelive.app.ui.live.LiveScreen
import com.purelive.app.ui.theme.LocalShadcnColors
import com.purelive.app.ui.theme.ShadcnButton
import com.purelive.app.ui.vod.VodPlayerDetailOverlay
import com.purelive.app.ui.vod.VodScreen

private data class NavTabItem(
    val key: String,
    val icon: ImageVector
)

@Composable
fun MainScreen(viewModel: PureLiveViewModel) {
    val context = LocalContext.current
    val liveState by viewModel.liveState.collectAsState()
    val vodState by viewModel.vodState.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val historyList by viewModel.watchHistory.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()
    val isForceUpdateVisible by viewModel.isForceUpdateVisible.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val isDownloadingUpdate by viewModel.isDownloadingUpdate.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()

    // Tab 0: 影视点播 (VOD), Tab 1: 电视直播 (Live), Tab 2: 历史/收藏 (History)
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var isFullscreenPlayer by rememberSaveable { mutableStateOf(false) }
    var isBugFeedbackOpen by rememberSaveable { mutableStateOf(false) }

    val colors = LocalShadcnColors.current

    val tabs = listOf(
        NavTabItem("tab_vod", Icons.Default.MovieFilter),
        NavTabItem("tab_live", Icons.Default.LiveTv),
        NavTabItem("tab_history", Icons.Default.Bookmarks)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvasBg)
    ) {
        if (playerState.isVisible && playerState.vodItem != null) {
            // VOD Detail & Fullscreen Player Overlay
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (!isFullscreenPlayer) Modifier.statusBarsPadding().navigationBarsPadding() else Modifier)
            ) {
                VodPlayerDetailOverlay(
                    playerState = playerState,
                    favorites = favorites,
                    currentLang = appLanguage,
                    onSelectSourceAndEpisode = viewModel::selectVodSourceAndEpisode,
                    onStartPlayback = viewModel::startVodPlayback,
                    onNextEpisode = viewModel::playNextVodEpisode,
                    onSaveProgress = viewModel::saveWatchProgress,
                    onToggleFavorite = viewModel::toggleVodFavorite,
                    onClose = {
                        isFullscreenPlayer = false
                        viewModel.closeVodPlayer()
                    },
                    onFullscreenChange = { isFullscreenPlayer = it }
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (!isFullscreenPlayer) Modifier.statusBarsPadding().navigationBarsPadding() else Modifier)
            ) {
                // Top Header Bar (Clean without cluttered badges)
                if (!isFullscreenPlayer) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(colors.canvasBg)
                            .drawBehind {
                                drawLine(
                                    color = colors.border,
                                    start = Offset(0f, size.height),
                                    end = Offset(size.width, size.height),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = Strings.get("app_name", appLanguage),
                            color = colors.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 1. Language Toggle Button (ZH ⇄ ES)
                            Surface(
                                onClick = viewModel::toggleLanguage,
                                shape = RoundedCornerShape(6.dp),
                                color = colors.inputBg,
                                border = BorderStroke(1.dp, colors.border),
                                shadowElevation = 0.dp,
                                modifier = Modifier.height(30.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Translate,
                                        contentDescription = "切换语言",
                                        tint = colors.textPrimary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = if (appLanguage == AppLanguage.ZH) "中 / ES" else "ES / 中",
                                        color = colors.textPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // 2. Theme Toggle Button (Light ⇄ Dark)
                            Surface(
                                onClick = viewModel::toggleDarkMode,
                                shape = RoundedCornerShape(6.dp),
                                color = colors.inputBg,
                                border = BorderStroke(1.dp, colors.border),
                                shadowElevation = 0.dp,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                        contentDescription = if (isDarkMode) "切换日间浅色模式" else "切换夜间暗黑模式",
                                        tint = colors.textPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            // 3. Bug Feedback Button (GitHub Issues)
                            Surface(
                                onClick = { isBugFeedbackOpen = true },
                                shape = RoundedCornerShape(6.dp),
                                color = colors.inputBg,
                                border = BorderStroke(1.dp, colors.border),
                                shadowElevation = 0.dp,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.BugReport,
                                        contentDescription = Strings.get("bug_feedback", appLanguage),
                                        tint = colors.textPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Main Content Area (Tab 0: VOD, Tab 1: Live, Tab 2: History)
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> VodScreen(
                            state = vodState,
                            currentLang = appLanguage,
                            onSelectCategory = viewModel::selectVodCategory,
                            onUpdateFilter = viewModel::updateVodFilter,
                            onSearchQueryChange = viewModel::updateSearchQuery,
                            onSubmitSearch = viewModel::submitSearch,
                            onClearSearch = viewModel::clearSearch,
                            onLoadNextPage = viewModel::loadNextVodPage,
                            onOpenVodDetail = viewModel::openVodDetail,
                            onStartPlayback = viewModel::startVodPlayback,
                            onRetry = viewModel::loadVodCategoriesAndInitialList
                        )
                        1 -> LiveScreen(
                            state = liveState,
                            favorites = favorites,
                            currentLang = appLanguage,
                            onSelectGroup = viewModel::selectLiveGroup,
                            onSelectChannel = { ch -> viewModel.selectLiveChannel(ch, 0, autoStart = false) },
                            onStartPlayback = viewModel::startLivePlayback,
                            onSwitchLine = viewModel::switchLiveLine,
                            onAutoNextLine = viewModel::autoSwitchNextLiveLineOnError,
                            onToggleFavorite = viewModel::toggleLiveFavorite,
                            onRetry = viewModel::loadLiveChannels,
                            onFullscreenChange = { isFullscreenPlayer = it }
                        )
                        2 -> HistoryFavoriteScreen(
                            historyList = historyList,
                            favorites = favorites,
                            currentLang = appLanguage,
                            onResumeHistory = viewModel::openVodFromHistory,
                            onDeleteHistory = viewModel::deleteHistoryItem,
                            onClearAllHistory = viewModel::clearAllHistory,
                            onOpenFavoriteVod = viewModel::openVodDetail,
                            onOpenFavoriteLive = { channel ->
                                selectedTab = 1
                                viewModel.selectLiveGroup(channel.group)
                                viewModel.selectLiveChannel(channel, 0, autoStart = true)
                            },
                            onDeleteFavorite = viewModel::deleteFavoriteItem
                        )
                    }
                }

                // Bottom Navigation Bar (Tab 0: VOD, Tab 1: Live, Tab 2: History)
                if (!isFullscreenPlayer) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .background(colors.canvasBg)
                            .drawBehind {
                                drawLine(
                                    color = colors.border,
                                    start = Offset(0f, 0f),
                                    end = Offset(size.width, 0f),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tabs.forEachIndexed { index, item ->
                            val selected = index == selectedTab
                            val label = Strings.get(item.key, appLanguage)
                            Surface(
                                onClick = { selectedTab = index },
                                shape = RoundedCornerShape(6.dp),
                                color = if (selected) colors.primaryButtonBg else colors.cardBg,
                                border = if (selected) null else BorderStroke(1.dp, colors.border),
                                shadowElevation = 0.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = label,
                                        tint = if (selected) colors.primaryButtonText else colors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = label,
                                        color = if (selected) colors.primaryButtonText else colors.textSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==================== MANDATORY FORCE UPDATE DIALOG ====================
        if (isForceUpdateVisible && updateInfo != null) {
            val info = updateInfo!!
            Dialog(
                onDismissRequest = { /* Force update: cannot dismiss */ },
                properties = DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false
                )
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.cardBg,
                    border = BorderStroke(1.dp, colors.border),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = colors.textPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "${Strings.get("force_update_title", appLanguage)} (v${info.latestVersionName})",
                            color = colors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (appLanguage == AppLanguage.ES) info.changelogEs.ifBlank { Strings.get("force_update_desc", appLanguage) }
                            else info.changelogZh.ifBlank { Strings.get("force_update_desc", appLanguage) },
                            color = colors.textSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        ShadcnButton(
                            text = if (isDownloadingUpdate) {
                                "${Strings.get("downloading_update", appLanguage)} ($downloadProgress%)"
                            } else {
                                Strings.get("force_update_button", appLanguage)
                            },
                            primary = true,
                            enabled = !isDownloadingUpdate,
                            onClick = {
                                viewModel.performForceUpdate(context, info)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // ==================== BUG FEEDBACK DIALOG (AUTO GITHUB ISSUES) ====================
        if (isBugFeedbackOpen) {
            BugFeedbackDialog(
                isOpen = isBugFeedbackOpen,
                appLanguage = appLanguage,
                onDismiss = { isBugFeedbackOpen = false },
                onSubmit = { content, callback ->
                    viewModel.submitBugFeedback(content, context, callback)
                }
            )
        }
    }
}

@Composable
fun BugFeedbackDialog(
    isOpen: Boolean,
    appLanguage: AppLanguage,
    onDismiss: () -> Unit,
    onSubmit: (String, (Boolean) -> Unit) -> Unit
) {
    if (!isOpen) return
    val colors = LocalShadcnColors.current
    val context = LocalContext.current
    var feedbackText by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    val deviceInfo = remember {
        "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE}"
    }

    Dialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !isSubmitting, dismissOnClickOutside = !isSubmitting)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = colors.cardBg,
            border = BorderStroke(1.dp, colors.border),
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = null,
                            tint = colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = Strings.get("bug_feedback_title", appLanguage),
                            color = colors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (!isSubmitting) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = colors.textSecondary,
                            modifier = Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onDismiss() }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = Strings.get("bug_feedback_desc", appLanguage),
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Input box
                BasicTextField(
                    value = feedbackText,
                    onValueChange = { feedbackText = it },
                    textStyle = TextStyle(color = colors.textPrimary, fontSize = 13.sp),
                    cursorBrush = SolidColor(colors.textPrimary),
                    enabled = !isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .background(colors.inputBg, RoundedCornerShape(6.dp))
                        .border(1.dp, colors.border, RoundedCornerShape(6.dp))
                        .padding(10.dp),
                    decorationBox = { innerTextField ->
                        if (feedbackText.isEmpty()) {
                            Text(
                                text = Strings.get("bug_feedback_hint", appLanguage),
                                color = colors.textMuted,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                        innerTextField()
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Diagnostic preview chip
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = colors.inputBg,
                    border = BorderStroke(1.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = Strings.get("bug_feedback_diag_label", appLanguage),
                            color = colors.textMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Pure Stream v1.0.0 · $deviceInfo",
                            color = colors.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShadcnButton(
                        text = Strings.get("bug_feedback_cancel", appLanguage),
                        onClick = onDismiss,
                        primary = false,
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                    )

                    ShadcnButton(
                        text = if (isSubmitting) Strings.get("bug_feedback_submitting", appLanguage)
                               else Strings.get("bug_feedback_submit", appLanguage),
                        onClick = {
                            if (feedbackText.isBlank()) {
                                Toast.makeText(
                                    context,
                                    Strings.get("bug_feedback_empty", appLanguage),
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@ShadcnButton
                            }
                            isSubmitting = true
                            onSubmit(feedbackText) { success ->
                                isSubmitting = false
                                if (success) {
                                    Toast.makeText(
                                        context,
                                        Strings.get("bug_feedback_success", appLanguage),
                                        Toast.LENGTH_LONG
                                    ).show()
                                    onDismiss()
                                } else {
                                    // Copy to clipboard fallback
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Feedback", "Pure Stream v1.0.0\n$deviceInfo\n\n$feedbackText")
                                    cm.setPrimaryClip(clip)
                                    Toast.makeText(
                                        context,
                                        Strings.get("bug_feedback_error", appLanguage),
                                        Toast.LENGTH_LONG
                                    ).show()
                                    onDismiss()
                                }
                            }
                        },
                        primary = true,
                        modifier = Modifier
                            .weight(1.3f)
                            .height(36.dp)
                    )
                }
            }
        }
    }
}
