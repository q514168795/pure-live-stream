package com.purelive.app.ui.main

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.purelive.app.data.api.HuarenRepository
import com.purelive.app.data.db.AppDatabase
import com.purelive.app.data.db.FavoriteEntity
import com.purelive.app.data.db.WatchHistoryEntity
import com.purelive.app.data.model.AppLanguage
import com.purelive.app.data.model.LiveChannel
import com.purelive.app.data.model.Strings
import com.purelive.app.data.model.UpdateInfo
import com.purelive.app.data.model.VodCategory
import com.purelive.app.data.model.VodItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class LiveUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val groups: List<String> = emptyList(),
    val selectedGroup: String = "央视",
    val allChannels: List<LiveChannel> = emptyList(),
    val filteredChannels: List<LiveChannel> = emptyList(),
    val currentChannel: LiveChannel? = null,
    val currentLineIndex: Int = 0,
    val resolvedStreamUrl: String = "",
    val isResolvingStream: Boolean = false,
    val isPlaybackStarted: Boolean = false
)

data class VodUiState(
    val isLoadingCategories: Boolean = true,
    val isLoadingList: Boolean = true,
    val isAppendingPage: Boolean = false,
    val error: String? = null,
    val categories: List<VodCategory> = emptyList(),
    val selectedCategoryIndex: Int = 0,
    val selectedFilters: Map<String, String> = emptyMap(),
    val items: List<VodItem> = emptyList(),
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val searchQuery: String = "",
    val isSearchMode: Boolean = false
)

data class ActiveVodPlayerState(
    val isVisible: Boolean = false,
    val isLoadingDetail: Boolean = false,
    val isResolvingEpisode: Boolean = false,
    val vodItem: VodItem? = null,
    val selectedSourceIndex: Int = 0,
    val selectedEpisodeIndex: Int = 0,
    val resolvedEpisodeUrl: String = "",
    val startPositionMs: Long = 0L,
    val isPlaybackStarted: Boolean = false,
    val errorMessage: String? = null
)

class PureLiveViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HuarenRepository(application)
    private val database = AppDatabase.getInstance(application)
    private val historyDao = database.historyDao()
    private val favoriteDao = database.favoriteDao()

    private val uiPrefs = application.getSharedPreferences("pure_live_ui_prefs", Context.MODE_PRIVATE)

    // Dark Mode State
    private val _isDarkMode = MutableStateFlow(uiPrefs.getBoolean("is_dark_mode", false))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    fun toggleDarkMode() {
        val next = !_isDarkMode.value
        _isDarkMode.value = next
        uiPrefs.edit().putBoolean("is_dark_mode", next).apply()
    }

    // Language State (ZH / ES)
    private val _appLanguage = MutableStateFlow(
        if (uiPrefs.getString("app_lang", "zh") == "es") AppLanguage.ES else AppLanguage.ZH
    )
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    fun toggleLanguage() {
        val next = if (_appLanguage.value == AppLanguage.ZH) AppLanguage.ES else AppLanguage.ZH
        _appLanguage.value = next
        uiPrefs.edit().putString("app_lang", next.code).apply()
    }

    // Force Update State
    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    private val _isForceUpdateVisible = MutableStateFlow(false)
    val isForceUpdateVisible: StateFlow<Boolean> = _isForceUpdateVisible.asStateFlow()

    private val _isDownloadingUpdate = MutableStateFlow(false)
    val isDownloadingUpdate: StateFlow<Boolean> = _isDownloadingUpdate.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    private val _liveState = MutableStateFlow(LiveUiState())
    val liveState: StateFlow<LiveUiState> = _liveState.asStateFlow()

    private val _vodState = MutableStateFlow(VodUiState())
    val vodState: StateFlow<VodUiState> = _vodState.asStateFlow()

    private val _playerState = MutableStateFlow(ActiveVodPlayerState())
    val playerState: StateFlow<ActiveVodPlayerState> = _playerState.asStateFlow()

    val watchHistory: StateFlow<List<WatchHistoryEntity>> = historyDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<FavoriteEntity>> = favoriteDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var vodLoadJob: Job? = null
    private var liveResolveJob: Job? = null
    private var vodResolveJob: Job? = null

    init {
        viewModelScope.launch {
            repository.refreshBootstrapNodes()
        }
        loadLiveChannels()
        loadVodCategoriesAndInitialList()
        checkForUpdates(manual = false)
    }

    fun getAppVersionCode(): Int {
        return try {
            val pInfo = getApplication<Application>().packageManager.getPackageInfo(getApplication<Application>().packageName, 0)
            pInfo.versionCode
        } catch (_: Throwable) {
            1
        }
    }

    fun getAppVersionName(): String {
        return try {
            val pInfo = getApplication<Application>().packageManager.getPackageInfo(getApplication<Application>().packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (_: Throwable) {
            "1.0.0"
        }
    }

    // ==================== LIVE TV ====================\

    fun loadLiveChannels() {
        viewModelScope.launch {
            _liveState.update { it.copy(isLoading = true, error = null) }
            try {
                val channels = repository.fetchLiveChannels()
                if (channels.isEmpty()) {
                    _liveState.update {
                        it.copy(isLoading = false, error = "未能加载直播频道，请检查网络后重试")
                    }
                    return@launch
                }
                val groups = channels.map { it.group }.distinct()
                val initialGroup = _liveState.value.selectedGroup.takeIf { it in groups }
                    ?: groups.firstOrNull()
                    ?: "央视"
                val groupChannels = channels.filter { it.group == initialGroup }
                val firstChannel = _liveState.value.currentChannel ?: groupChannels.firstOrNull()

                _liveState.update {
                    it.copy(
                        isLoading = false,
                        error = null,
                        groups = groups,
                        selectedGroup = initialGroup,
                        allChannels = channels,
                        filteredChannels = groupChannels,
                        currentChannel = firstChannel,
                        currentLineIndex = 0,
                        isPlaybackStarted = false,
                        resolvedStreamUrl = ""
                    )
                }
            } catch (e: Throwable) {
                _liveState.update {
                    it.copy(isLoading = false, error = "直播加载异常: ${e.message ?: "未知错误"}")
                }
            }
        }
    }

    fun selectLiveGroup(group: String) {
        val all = _liveState.value.allChannels
        val filtered = all.filter { it.group == group }
        _liveState.update {
            it.copy(selectedGroup = group, filteredChannels = filtered)
        }
    }

    fun selectLiveChannel(channel: LiveChannel, lineIndex: Int = 0, autoStart: Boolean = false) {
        val safeLineIdx = lineIndex.coerceIn(0, (channel.lines.size - 1).coerceAtLeast(0))

        _liveState.update {
            it.copy(
                currentChannel = channel,
                currentLineIndex = safeLineIdx,
                isPlaybackStarted = autoStart
            )
        }

        if (autoStart) {
            startLivePlayback()
        }
    }

    fun startLivePlayback(forceRefreshToken: Boolean = false) {
        val channel = _liveState.value.currentChannel ?: return
        val safeLineIdx = _liveState.value.currentLineIndex.coerceIn(0, (channel.lines.size - 1).coerceAtLeast(0))
        val rawUrl = channel.lines.getOrElse(safeLineIdx) { channel.streamUrl }

        _liveState.update { it.copy(isResolvingStream = true, isPlaybackStarted = true) }

        liveResolveJob?.cancel()
        liveResolveJob = viewModelScope.launch {
            val resolved = repository.resolveLiveStreamUrl(rawUrl, forceRefreshToken = forceRefreshToken)
            _liveState.update {
                it.copy(
                    resolvedStreamUrl = resolved,
                    isResolvingStream = false
                )
            }
        }
    }

    fun switchLiveLine(lineIndex: Int) {
        val current = _liveState.value.currentChannel ?: return
        selectLiveChannel(current, lineIndex, autoStart = _liveState.value.isPlaybackStarted)
    }

    fun autoSwitchNextLiveLineOnError() {
        val state = _liveState.value
        val current = state.currentChannel ?: return
        if (current.lines.size > 1 && state.currentLineIndex + 1 < current.lines.size) {
            selectLiveChannel(current, state.currentLineIndex + 1, autoStart = true)
        } else {
            startLivePlayback(forceRefreshToken = true)
        }
    }

    // ==================== VOD BROWSE & SEARCH ====================\

    fun loadVodCategoriesAndInitialList() {
        viewModelScope.launch {
            _vodState.update { it.copy(isLoadingCategories = true, isLoadingList = true, error = null) }
            try {
                val categories = repository.fetchCategories()
                _vodState.update {
                    it.copy(
                        isLoadingCategories = false,
                        categories = categories,
                        selectedCategoryIndex = 0,
                        selectedFilters = emptyMap()
                    )
                }
                val firstTypeId = categories.firstOrNull()?.typeId ?: 1
                fetchVodPage(typeId = firstTypeId, page = 1, filters = emptyMap(), reset = true)
            } catch (e: Throwable) {
                _vodState.update {
                    it.copy(
                        isLoadingCategories = false,
                        isLoadingList = false,
                        error = "点播分类加载失败: ${e.message}"
                    )
                }
            }
        }
    }

    fun selectVodCategory(index: Int) {
        val categories = _vodState.value.categories
        val cat = categories.getOrNull(index) ?: return
        _vodState.update {
            it.copy(
                selectedCategoryIndex = index,
                selectedFilters = emptyMap(),
                isSearchMode = false,
                searchQuery = ""
            )
        }
        fetchVodPage(typeId = cat.typeId, page = 1, filters = emptyMap(), reset = true)
    }

    fun updateVodFilter(key: String, value: String) {
        val current = _vodState.value
        val cat = current.categories.getOrNull(current.selectedCategoryIndex) ?: return
        val nextFilters = current.selectedFilters.toMutableMap()
        if (value.isBlank()) {
            nextFilters.remove(key)
        } else {
            nextFilters[key] = value
        }
        _vodState.update { it.copy(selectedFilters = nextFilters, isSearchMode = false) }
        fetchVodPage(typeId = cat.typeId, page = 1, filters = nextFilters, reset = true)
    }

    fun updateSearchQuery(query: String) {
        _vodState.update { it.copy(searchQuery = query) }
    }

    fun submitSearch() {
        val q = _vodState.value.searchQuery.trim()
        if (q.isEmpty()) {
            clearSearch()
            return
        }
        vodLoadJob?.cancel()
        vodLoadJob = viewModelScope.launch {
            _vodState.update {
                it.copy(isSearchMode = true, isLoadingList = true, error = null, items = emptyList())
            }
            val result = repository.searchVod(q, page = 1)
            _vodState.update {
                it.copy(
                    isLoadingList = false,
                    items = result.items,
                    currentPage = result.page,
                    totalPages = result.pageCount,
                    error = if (result.items.isEmpty()) "未找到与「$q」相关的影视内容" else null
                )
            }
        }
    }

    fun clearSearch() {
        val state = _vodState.value
        val cat = state.categories.getOrNull(state.selectedCategoryIndex)
        _vodState.update { it.copy(searchQuery = "", isSearchMode = false) }
        if (cat != null) {
            fetchVodPage(cat.typeId, 1, state.selectedFilters, reset = true)
        }
    }

    fun loadNextVodPage() {
        val state = _vodState.value
        if (state.isLoadingList || state.isAppendingPage || state.currentPage >= state.totalPages) return
        val nextPage = state.currentPage + 1

        if (state.isSearchMode && state.searchQuery.isNotBlank()) {
            vodLoadJob = viewModelScope.launch {
                _vodState.update { it.copy(isAppendingPage = true) }
                val res = repository.searchVod(state.searchQuery, page = nextPage)
                _vodState.update {
                    it.copy(
                        isAppendingPage = false,
                        items = (it.items + res.items).distinctBy { v -> v.id },
                        currentPage = nextPage,
                        totalPages = res.pageCount
                    )
                }
            }
        } else {
            val cat = state.categories.getOrNull(state.selectedCategoryIndex) ?: return
            fetchVodPage(cat.typeId, nextPage, state.selectedFilters, reset = false)
        }
    }

    private fun fetchVodPage(
        typeId: Int,
        page: Int,
        filters: Map<String, String>,
        reset: Boolean
    ) {
        vodLoadJob?.cancel()
        vodLoadJob = viewModelScope.launch {
            if (reset) {
                _vodState.update { it.copy(isLoadingList = true, error = null, items = emptyList()) }
            } else {
                _vodState.update { it.copy(isAppendingPage = true) }
            }
            val result = repository.fetchVodList(typeId = typeId, page = page, filters = filters)
            _vodState.update { prev ->
                val merged = if (reset) result.items else (prev.items + result.items).distinctBy { it.id }
                prev.copy(
                    isLoadingList = false,
                    isAppendingPage = false,
                    items = merged,
                    currentPage = page,
                    totalPages = result.pageCount,
                    error = if (merged.isEmpty()) "暂无符合筛选条件的影视内容" else null
                )
            }
        }
    }

    // ==================== VOD DETAIL & PLAYER ====================\

    fun openVodDetail(item: VodItem) {
        _playerState.update {
            ActiveVodPlayerState(
                isVisible = true,
                isLoadingDetail = true,
                vodItem = item,
                isPlaybackStarted = false
            )
        }

        viewModelScope.launch {
            val savedHistory = historyDao.getByVodId(item.id)
            val fullDetail = if (item.playSources.isNotEmpty() && item.blurb.isNotEmpty()) {
                item
            } else {
                repository.fetchVodDetail(item.id) ?: item
            }

            if (fullDetail.playSources.isEmpty()) {
                _playerState.update {
                    it.copy(
                        isLoadingDetail = false,
                        vodItem = fullDetail,
                        errorMessage = "该影视暂无可用播放源"
                    )
                }
                return@launch
            }

            val srcIdx = (savedHistory?.sourceIndex ?: 0)
                .coerceIn(0, fullDetail.playSources.lastIndex)
            val epList = fullDetail.playSources[srcIdx].episodes
            val epIdx = (savedHistory?.episodeIndex ?: 0)
                .coerceIn(0, (epList.size - 1).coerceAtLeast(0))
            val resumePos = savedHistory?.positionMs ?: 0L

            _playerState.update {
                it.copy(
                    isLoadingDetail = false,
                    vodItem = fullDetail,
                    selectedSourceIndex = srcIdx,
                    selectedEpisodeIndex = epIdx,
                    startPositionMs = resumePos,
                    isPlaybackStarted = false,
                    errorMessage = null
                )
            }
            // Non-autoplay: wait for user to click [立即播放] or choose an episode!
        }
    }

    fun startVodPlayback() {
        val current = _playerState.value
        val vod = current.vodItem ?: return
        resolveAndPlayVodEpisode(
            vod = vod,
            sourceIndex = current.selectedSourceIndex,
            episodeIndex = current.selectedEpisodeIndex,
            startPositionMs = current.startPositionMs
        )
    }

    fun openVodFromHistory(history: WatchHistoryEntity) {
        val stub = VodItem(
            id = history.vodId,
            title = history.title,
            coverUrl = history.coverUrl,
            category = history.category,
            year = "",
            remarks = history.remarks
        )
        openVodDetail(stub)
    }

    fun selectVodSourceAndEpisode(sourceIndex: Int, episodeIndex: Int) {
        val current = _playerState.value
        val vod = current.vodItem ?: return
        val source = vod.playSources.getOrNull(sourceIndex) ?: return
        if (episodeIndex !in source.episodes.indices) return

        resolveAndPlayVodEpisode(vod, sourceIndex, episodeIndex, 0L)
    }

    fun playNextVodEpisode() {
        val current = _playerState.value
        val vod = current.vodItem ?: return
        val source = vod.playSources.getOrNull(current.selectedSourceIndex) ?: return
        val nextEp = current.selectedEpisodeIndex + 1
        if (nextEp in source.episodes.indices) {
            selectVodSourceAndEpisode(current.selectedSourceIndex, nextEp)
        }
    }

    private fun resolveAndPlayVodEpisode(
        vod: VodItem,
        sourceIndex: Int,
        episodeIndex: Int,
        startPositionMs: Long
    ) {
        val source = vod.playSources.getOrNull(sourceIndex) ?: return
        val episode = source.episodes.getOrNull(episodeIndex) ?: return

        _playerState.update {
            it.copy(
                selectedSourceIndex = sourceIndex,
                selectedEpisodeIndex = episodeIndex,
                isResolvingEpisode = true,
                isPlaybackStarted = true,
                startPositionMs = startPositionMs,
                errorMessage = null
            )
        }

        vodResolveJob?.cancel()
        vodResolveJob = viewModelScope.launch {
            val resolvedUrl = repository.resolveVodPlayUrl(episode.url)
            if (resolvedUrl.isBlank()) {
                _playerState.update {
                    it.copy(
                        isResolvingEpisode = false,
                        errorMessage = "播放源解析失败，请尝试切换其他线路"
                    )
                }
            } else {
                _playerState.update {
                    it.copy(
                        isResolvingEpisode = false,
                        resolvedEpisodeUrl = resolvedUrl
                    )
                }
                saveWatchProgress(positionMs = startPositionMs, durationMs = 0L)
            }
        }
    }

    fun saveWatchProgress(positionMs: Long, durationMs: Long) {
        val state = _playerState.value
        val vod = state.vodItem ?: return
        val source = vod.playSources.getOrNull(state.selectedSourceIndex) ?: return
        val ep = source.episodes.getOrNull(state.selectedEpisodeIndex) ?: return

        viewModelScope.launch {
            historyDao.upsert(
                WatchHistoryEntity(
                    vodId = vod.id,
                    title = vod.title,
                    coverUrl = vod.coverUrl,
                    category = vod.category,
                    remarks = vod.remarks,
                    sourceIndex = state.selectedSourceIndex,
                    episodeIndex = state.selectedEpisodeIndex,
                    episodeTitle = ep.title,
                    positionMs = positionMs.coerceAtLeast(0L),
                    durationMs = durationMs.coerceAtLeast(0L),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun closeVodPlayer() {
        vodResolveJob?.cancel()
        _playerState.update { ActiveVodPlayerState(isVisible = false, isPlaybackStarted = false) }
    }

    // ==================== AUTO UPDATE & FORCE UPDATE ====================

    fun checkForUpdates(manual: Boolean = false, context: Context? = null) {
        viewModelScope.launch {
            // Check if newer version is configured or available
            val curCode = getAppVersionCode()
            val remoteInfo = repository.checkRemoteUpdate()
            if (remoteInfo != null && remoteInfo.latestVersionCode > curCode) {
                _updateInfo.value = remoteInfo
                _isForceUpdateVisible.value = true
            } else if (manual && context != null) {
                Toast.makeText(
                    context,
                    Strings.get("already_latest", _appLanguage.value),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun performForceUpdate(context: Context, updateInfo: UpdateInfo) {
        if (_isDownloadingUpdate.value) return
        viewModelScope.launch {
            _isDownloadingUpdate.value = true
            _downloadProgress.value = 0
            val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
            val apkFile = File(downloadDir, "PureLiveStream_v${updateInfo.latestVersionName}.apk")

            Toast.makeText(
                context,
                Strings.get("downloading_update", _appLanguage.value),
                Toast.LENGTH_SHORT
            ).show()

            val success = repository.downloadApkFile(updateInfo.downloadUrl, apkFile) { progress ->
                _downloadProgress.value = progress
            }
            _isDownloadingUpdate.value = false

            if (success && apkFile.exists()) {
                installApk(context, apkFile)
            } else {
                Toast.makeText(
                    context,
                    Strings.get("download_failed", _appLanguage.value),
                    Toast.LENGTH_SHORT
                ).show()
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.downloadUrl)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (_: Throwable) {
                    Toast.makeText(context, "无法打开更新链接", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun installApk(context: Context, apkFile: File) {
        try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
        } catch (_: Throwable) {
            Toast.makeText(context, "无法启动安装器，请在文件管理器中点击安装", Toast.LENGTH_LONG).show()
        }
    }

    // ==================== FAVORITES & HISTORY ====================\

    fun toggleVodFavorite(vod: VodItem) {
        val itemId = "vod_${vod.id}"
        viewModelScope.launch {
            if (favoriteDao.isFavorite(itemId)) {
                favoriteDao.deleteById(itemId)
            } else {
                favoriteDao.insert(
                    FavoriteEntity(
                        itemId = itemId,
                        itemType = "VOD",
                        rawId = vod.id,
                        title = vod.title,
                        coverUrl = vod.coverUrl,
                        groupOrCategory = vod.category,
                        remarksOrStreamUrl = vod.remarks,
                        addedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun toggleLiveFavorite(channel: LiveChannel) {
        val itemId = "live_${channel.name}"
        viewModelScope.launch {
            if (favoriteDao.isFavorite(itemId)) {
                favoriteDao.deleteById(itemId)
            } else {
                favoriteDao.insert(
                    FavoriteEntity(
                        itemId = itemId,
                        itemType = "LIVE",
                        rawId = channel.id,
                        title = channel.name,
                        coverUrl = "",
                        groupOrCategory = channel.group,
                        remarksOrStreamUrl = channel.streamUrl,
                        backupLinesJson = channel.lines.joinToString("#"),
                        addedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun deleteHistoryItem(vodId: String) {
        viewModelScope.launch {
            historyDao.deleteById(vodId)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            historyDao.clearAll()
        }
    }

    fun deleteFavoriteItem(itemId: String) {
        viewModelScope.launch {
            favoriteDao.deleteById(itemId)
        }
    }

    fun submitBugFeedback(
        content: String,
        context: Context,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val success = repository.submitFeedbackToGithub(content, context)
            onResult(success)
        }
    }
}
