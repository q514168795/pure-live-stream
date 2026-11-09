package com.purelive.app.data.api

import android.content.Context
import android.net.Uri
import com.purelive.app.data.model.Episode
import com.purelive.app.data.model.LiveChannel
import com.purelive.app.data.model.PagedVodResult
import com.purelive.app.data.model.VodCategory
import com.purelive.app.data.model.VodFilterGroup
import com.purelive.app.data.model.VodFilterOption
import com.purelive.app.data.model.VodItem
import com.purelive.app.data.model.VodPlaySource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

class HuarenRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("pure_live_session", Context.MODE_PRIVATE)
    private val sessionMutex = Mutex()

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val candidateNodes = mutableListOf(
        "https://newitv.huaren.live",
        "https://newitv.huarenok.com",
        "https://newitv.okokserver.com",
        "https://newitv.okhuaren.com",
        "https://newitv.huavod.com",
        "https://newitv.huavod.net"
    )

    @Volatile
    private var activeNode: String = prefs.getString("active_node", null) ?: "https://newitv.huaren.live"

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        private const val FALLBACK_LIVE_TXT_URL =
            "https://api.huarenlivewebsite3.top/static/addons/tv/tv.txt"
        private const val BOOTSTRAP_URL =
            "https://tv2.okhuaren.com/static/newboot.json"
    }

    private fun getDeviceMark(): String {
        var mark = prefs.getString("device_mark", null)
        if (mark.isNullOrBlank()) {
            mark = "pure_" + UUID.randomUUID().toString().replace("-", "").take(10)
            prefs.edit().putString("device_mark", mark).apply()
        }
        return mark
    }

    /**
     * Silently provisions or renews a background VIP session token so the user never sees
     * any login dialog, VIP wall, or pre-roll ad wait timer.
     */
    suspend fun ensureSessionTokens(forceRefresh: Boolean = false): Pair<String, String> = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            val nowSec = System.currentTimeMillis() / 1000L
            val cachedUserToken = prefs.getString("user_token", "") ?: ""
            val cachedLiveToken = prefs.getString("live_token", "") ?: ""
            val vipEndSec = prefs.getLong("vip_end_sec", 0L)

            if (!forceRefresh && cachedUserToken.isNotBlank() && cachedLiveToken.isNotBlank() && nowSec < (vipEndSec - 1800L)) {
                return@withLock Pair(cachedUserToken, cachedLiveToken)
            }

            // Generate a fresh background silent account to receive automatic VIP trial quota
            val rnd = UUID.randomUUID().toString().replace("-", "").take(8).lowercase()
            val uname = "pure_$rnd"
            val pwd = "Pure${rnd}99!"
            val mark = "mark_$rnd"

            var newUserToken = cachedUserToken
            var newVipEnd = vipEndSec

            val regResp = postSignedForm(
                path = "api/users/register",
                token = "",
                mark = mark,
                params = mapOf(
                    "username" to uname,
                    "nickname" to uname,
                    "email" to "$uname@gmail.com",
                    "password" to pwd,
                    "password2" to pwd
                )
            )

            if (regResp != null) {
                try {
                    val json = JSONObject(regResp)
                    if (json.optInt("code") == 1) {
                        val data = json.optJSONObject("data")
                        val userInfo = data?.optJSONObject("userinfo")
                        val tok = userInfo?.optString("token").orEmpty()
                        val end = userInfo?.optLong("user_end_time", nowSec + 86400L) ?: (nowSec + 86400L)
                        if (tok.isNotBlank()) {
                            newUserToken = tok
                            newVipEnd = end
                        }
                    }
                } catch (_: Throwable) {
                }
            }

            var newLiveToken = cachedLiveToken
            if (newUserToken.isNotBlank()) {
                val colResp = postSignedForm(
                    path = "api/collect/getCollect",
                    token = newUserToken,
                    mark = mark,
                    params = emptyMap()
                )
                if (colResp != null) {
                    try {
                        val json = JSONObject(colResp)
                        val liveTok = json.optJSONObject("data")?.optString("token").orEmpty()
                        if (liveTok.isNotBlank()) {
                            newLiveToken = liveTok
                        }
                    } catch (_: Throwable) {
                    }
                }
            }

            prefs.edit()
                .putString("user_token", newUserToken)
                .putString("live_token", newLiveToken)
                .putString("device_mark", mark)
                .putLong("vip_end_sec", newVipEnd)
                .apply()

            Pair(newUserToken, newLiveToken)
        }
    }

    /**
     * Fetches and parses Live TV channels grouped by category (央视、卫视、港澳、台湾、体育等).
     * Automatically aggregates multiple lines per channel name for seamless line switching.
     */
    suspend fun fetchLiveChannels(): List<LiveChannel> = withContext(Dispatchers.IO) {
        val (userToken, _) = ensureSessionTokens()
        var rawText: String? = null

        // 1. Try encrypted /api/data/live from active node
        try {
            val req = Request.Builder()
                .url("$activeNode/api/data/live")
                .header("User-Agent", USER_AGENT)
                .header("token", userToken)
                .get()
                .build()
            okHttp.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val dec = HuarenCrypto.decryptSourceData(body)
                    if (!dec.isNullOrBlank() && dec.contains("#genre#")) {
                        rawText = dec
                    }
                }
            }
        } catch (_: Throwable) {
        }

        // 2. Fallback to direct tv.txt mirror if needed
        if (rawText.isNullOrBlank()) {
            try {
                val req = Request.Builder()
                    .url(FALLBACK_LIVE_TXT_URL)
                    .header("User-Agent", USER_AGENT)
                    .get()
                    .build()
                okHttp.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        if (body.contains("#genre#")) {
                            rawText = body
                        }
                    }
                }
            } catch (_: Throwable) {
            }
        }

        parseLiveTxt(rawText.orEmpty())
    }

    private fun parseLiveTxt(text: String): List<LiveChannel> {
        if (text.isBlank()) return emptyList()
        var currentGroup = "央视"
        // Key: "$group|$channelName" -> MutableList of URLs
        val groupedUrls = LinkedHashMap<Pair<String, String>, MutableList<String>>()

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            if (line.contains(",#genre#")) {
                val g = line.substringBefore(",#genre#").trim()
                if (g.isNotEmpty()) currentGroup = g
                continue
            }
            val commaIdx = line.indexOf(',')
            if (commaIdx <= 0) continue
            val name = line.substring(0, commaIdx).trim()
            val urlPart = line.substring(commaIdx + 1).trim()
            if (name.isEmpty() || !urlPart.startsWith("http")) continue

            val urls = urlPart.split("#").map { it.trim() }.filter { it.startsWith("http") }
            if (urls.isEmpty()) continue

            val key = Pair(currentGroup, name)
            val list = groupedUrls.getOrPut(key) { mutableListOf() }
            for (u in urls) {
                if (!list.contains(u)) {
                    list.add(u)
                }
            }
        }

        return groupedUrls.entries.mapIndexed { idx, (key, urls) ->
            val (group, name) = key
            LiveChannel(
                id = "live_${idx}_${name.hashCode()}",
                name = name,
                group = group,
                streamUrl = urls.first(),
                lines = urls
            )
        }
    }

    /**
     * Attaches the live authentication token (?auth=...) required by huaren live stream CDN.
     */
    suspend fun resolveLiveStreamUrl(rawStreamUrl: String, forceRefreshToken: Boolean = false): String =
        withContext(Dispatchers.IO) {
            val (_, liveToken) = ensureSessionTokens(forceRefresh = forceRefreshToken)
            if (liveToken.isBlank() || rawStreamUrl.contains("auth=")) {
                return@withContext rawStreamUrl
            }
            val sep = if (rawStreamUrl.contains("?")) "&" else "?"
            "$rawStreamUrl${sep}auth=$liveToken"
        }

    /**
     * Fetches VOD categories and filter definitions.
     */
    suspend fun fetchCategories(): List<VodCategory> = withContext(Dispatchers.IO) {
        val jsonStr = requestGetData(mapOf("vod_quality" to "1")) ?: return@withContext defaultCategories()
        try {
            val root = JSONObject(jsonStr)
            val classArr = root.optJSONArray("class") ?: return@withContext defaultCategories()
            val filtersObj = root.optJSONObject("filters")
            val result = mutableListOf<VodCategory>()

            for (i in 0 until classArr.length()) {
                val c = classArr.optJSONObject(i) ?: continue
                val typeId = c.optInt("type_id", 0)
                val typeName = c.optString("type_name").trim()
                if (typeId == 0 || typeName.isEmpty()) continue

                val filterGroups = mutableListOf<VodFilterGroup>()
                val fArr = filtersObj?.optJSONArray(typeId.toString())
                if (fArr != null) {
                    for (j in 0 until fArr.length()) {
                        val fg = fArr.optJSONObject(j) ?: continue
                        val key = fg.optString("key")
                        val name = fg.optString("name")
                        val valArr = fg.optJSONArray("value") ?: continue
                        val options = mutableListOf(VodFilterOption("全部", ""))
                        for (k in 0 until valArr.length()) {
                            val vObj = valArr.optJSONObject(k) ?: continue
                            val label = vObj.optString("n")
                            val value = vObj.opt("v")?.toString().orEmpty()
                            if (label.isNotEmpty() && value.isNotEmpty()) {
                                options.add(VodFilterOption(label, value))
                            }
                        }
                        if (key.isNotEmpty() && options.size > 1) {
                            filterGroups.add(VodFilterGroup(key, name, options))
                        }
                    }
                }
                result.add(VodCategory(typeId = typeId, name = typeName, filters = filterGroups))
            }
            if (result.isEmpty()) {
                defaultCategories()
            } else {
                val priorityOrder = listOf(1, 2, 3, 4, 5, 42, 38, 39, 40, 37)
                result.sortedBy { cat ->
                    val idx = priorityOrder.indexOf(cat.typeId)
                    if (idx >= 0) idx else 99
                }
            }
        } catch (_: Throwable) {
            defaultCategories()
        }
    }

    private fun defaultCategories(): List<VodCategory> = listOf(
        VodCategory(1, "电影"),
        VodCategory(2, "电视剧"),
        VodCategory(3, "综艺"),
        VodCategory(4, "动漫"),
        VodCategory(5, "短剧"),
        VodCategory(42, "纪录片"),
        VodCategory(39, "豆瓣热剧"),
        VodCategory(38, "豆瓣热映")
    )

    /**
     * Fetches paginated VOD items by category and optional filter map.
     */
    suspend fun fetchVodList(
        typeId: Int,
        page: Int = 1,
        filters: Map<String, String> = emptyMap()
    ): PagedVodResult = withContext(Dispatchers.IO) {
        val effectiveTypeId = filters["st"]?.toIntOrNull() ?: typeId
        val params = LinkedHashMap<String, String>()
        params["ac"] = "detail"
        if (effectiveTypeId > 0) {
            params["t"] = effectiveTypeId.toString()
        }
        params["pg"] = page.toString()
        for ((k, v) in filters) {
            if (k != "st" && v.isNotBlank()) {
                params[k] = v
            }
        }
        params["vod_quality"] = "1"

        val jsonStr = requestGetData(params) ?: return@withContext PagedVodResult(emptyList(), page, 1, 0)
        parseVodListResponse(jsonStr, page)
    }

    /**
     * Searches VOD items by keyword.
     */
    suspend fun searchVod(keyword: String, page: Int = 1): PagedVodResult = withContext(Dispatchers.IO) {
        val params = mapOf(
            "ac" to "detail",
            "wd" to keyword.trim(),
            "pg" to page.toString(),
            "vod_quality" to "1"
        )
        val jsonStr = requestGetData(params) ?: return@withContext PagedVodResult(emptyList(), page, 1, 0)
        parseVodListResponse(jsonStr, page)
    }

    /**
     * Fetches full VOD detail including all play sources and episode lists.
     */
    suspend fun fetchVodDetail(vodId: String): VodItem? = withContext(Dispatchers.IO) {
        val params = mapOf(
            "ac" to "detail",
            "ids" to vodId,
            "vod_quality" to "1"
        )
        val jsonStr = requestGetData(params) ?: return@withContext null
        parseVodListResponse(jsonStr, 1).items.firstOrNull()
    }

    /**
     * Resolves a raw VOD episode URL into a direct playable HLS/MP4 URL via parseMobile.
     * Uses the silent VIP token so innerCode == 1 returns immediately with zero wait.
     */
    suspend fun resolveVodPlayUrl(rawEpisodeUrl: String): String = withContext(Dispatchers.IO) {
        val cleanedUrl = rawEpisodeUrl.trim()
        if (cleanedUrl.isEmpty()) return@withContext ""

        val (userToken, _) = ensureSessionTokens()
        val mark = getDeviceMark()

        val respStr = postSignedForm(
            path = "api/content/parseMobile",
            token = userToken,
            mark = mark,
            params = mapOf("url" to cleanedUrl)
        )

        if (respStr != null) {
            try {
                val root = JSONObject(respStr)
                if (root.optInt("code") == 1) {
                    val data = root.optJSONObject("data")
                    val innerCode = data?.optInt("code", 0) ?: 0
                    val parsedUrl = data?.optString("url").orEmpty()
                    if (innerCode == 1 && parsedUrl.startsWith("http")) {
                        return@withContext parsedUrl
                    }
                    // If token lost VIP status (innerCode == 2), force re-provision a fresh VIP token once
                    if (innerCode == 2) {
                        val (freshToken, _) = ensureSessionTokens(forceRefresh = true)
                        val retryStr = postSignedForm(
                            path = "api/content/parseMobile",
                            token = freshToken,
                            mark = getDeviceMark(),
                            params = mapOf("url" to cleanedUrl)
                        )
                        if (retryStr != null) {
                            val retryData = JSONObject(retryStr).optJSONObject("data")
                            val retryUrl = retryData?.optString("url").orEmpty()
                            if (retryUrl.startsWith("http")) {
                                return@withContext retryUrl
                            }
                        }
                    }
                }
            } catch (_: Throwable) {
            }
        }
        cleanedUrl
    }

    private fun parseVodListResponse(jsonStr: String, defaultPage: Int): PagedVodResult {
        return try {
            val root = JSONObject(jsonStr)
            val page = root.optString("page", defaultPage.toString()).toIntOrNull() ?: defaultPage
            val pageCount = root.optInt("pagecount", 1)
            val total = root.optInt("total", 0)
            val listArr = root.optJSONArray("list") ?: JSONArray()
            val items = mutableListOf<VodItem>()

            for (i in 0 until listArr.length()) {
                val obj = listArr.optJSONObject(i) ?: continue
                val id = obj.opt("vod_id")?.toString().orEmpty()
                val title = obj.optString("vod_name").trim()
                if (id.isEmpty() || title.isEmpty()) continue

                val pic = obj.optString("vod_pic").replace("\\/", "/").trim()
                val category = obj.optString("type_name").ifBlank { obj.optString("vod_class") }
                val year = obj.optString("vod_year").ifBlank { obj.optString("vod_pubdate") }
                val remarks = obj.optString("vod_remarks").trim()
                val area = obj.optString("vod_area").trim()
                val lang = obj.optString("vod_lang").trim()
                val actor = obj.optString("vod_actor").trim()
                val director = obj.optString("vod_director").trim()
                val blurb = obj.optString("vod_blurb")
                    .ifBlank { obj.optString("vod_content") }
                    .replace(Regex("<[^>]*>"), "")
                    .trim()
                val score = obj.optString("vod_douban_score")
                    .takeIf { it.isNotBlank() && it != "0.0" }
                    ?: obj.optString("vod_score").takeIf { it.isNotBlank() && it != "0.0" }.orEmpty()

                val playFromRaw = obj.optString("vod_play_from")
                val playUrlRaw = obj.optString("vod_play_url").replace("\\/", "/")
                val playSources = parsePlaySources(playFromRaw, playUrlRaw)

                items.add(
                    VodItem(
                        id = id,
                        title = title,
                        coverUrl = pic,
                        category = category,
                        year = year,
                        remarks = remarks,
                        area = area,
                        lang = lang,
                        actor = actor,
                        director = director,
                        blurb = blurb,
                        score = score,
                        playSources = playSources
                    )
                )
            }
            PagedVodResult(items = items, page = page, pageCount = pageCount, total = total)
        } catch (_: Throwable) {
            PagedVodResult(emptyList(), defaultPage, 1, 0)
        }
    }

    private fun parsePlaySources(playFromRaw: String, playUrlRaw: String): List<VodPlaySource> {
        if (playUrlRaw.isBlank()) return emptyList()
        val fromParts = if (playFromRaw.isNotBlank()) playFromRaw.split("$$$") else listOf("默认线路")
        val urlGroups = playUrlRaw.split("$$$")
        val sources = mutableListOf<VodPlaySource>()

        for (i in urlGroups.indices) {
            val rawGroup = urlGroups[i].trim()
            if (rawGroup.isEmpty()) continue
            val rawName = fromParts.getOrNull(i)?.trim().orEmpty()
            val displayName = when {
                rawName.contains("华人") || rawName.equals("huaren", true) -> "极速专线"
                rawName.contains("4k", true) -> "4K超清专线"
                rawName.contains("ole", true) -> "海外专线"
                rawName.isNotEmpty() -> rawName.replace(Regex("[^\\w\\u4e00-\\u9fa5+]"), "")
                else -> "线路${i + 1}"
            }.ifBlank { "线路${i + 1}" }

            val episodes = rawGroup.split("#").mapNotNull { epStr ->
                val trimmed = epStr.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                val dollarIdx = trimmed.indexOf('$')
                if (dollarIdx > 0) {
                    val epTitle = trimmed.substring(0, dollarIdx).trim()
                    val epUrl = trimmed.substring(dollarIdx + 1).trim()
                    if (epUrl.isNotEmpty()) Episode(epTitle, epUrl) else null
                } else if (trimmed.startsWith("http")) {
                    Episode("正片", trimmed)
                } else {
                    null
                }
            }
            if (episodes.isNotEmpty()) {
                sources.add(VodPlaySource(name = displayName, episodes = episodes))
            }
        }
        return sources
    }

    private fun requestGetData(queryParams: Map<String, String>): String? {
        val nodesToTry = buildNodeOrder()
        for (base in nodesToTry) {
            try {
                val uriBuilder = Uri.parse("$base/api/data/getData").buildUpon()
                for ((k, v) in queryParams) {
                    uriBuilder.appendQueryParameter(k, v)
                }
                val url = uriBuilder.build().toString()
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .get()
                    .build()
                okHttp.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val raw = resp.body?.string().orEmpty()
                        val dec = HuarenCrypto.decryptSourceData(raw)
                        if (!dec.isNullOrBlank()) {
                            if (base != activeNode) {
                                activeNode = base
                                prefs.edit().putString("active_node", base).apply()
                            }
                            return dec
                        }
                    }
                }
            } catch (_: Throwable) {
            }
        }
        return null
    }

    private fun postSignedForm(
        path: String,
        token: String,
        mark: String,
        params: Map<String, String>
    ): String? {
        val nodesToTry = buildNodeOrder()
        for (base in nodesToTry) {
            try {
                val formBuilder = FormBody.Builder()
                for ((k, v) in params) {
                    formBuilder.add(k, v)
                }
                formBuilder.add("publickey", HuarenCrypto.publicKeyBase64)
                formBuilder.add("device_fingerprint", HuarenCrypto.deviceFingerprint)
                formBuilder.add("device_signature", HuarenCrypto.deviceSignature)
                formBuilder.add("sign", HuarenCrypto.requestSign)
                formBuilder.add("apk_mark", HuarenCrypto.APK_MARK)
                formBuilder.add("app_id", HuarenCrypto.APP_ID)
                formBuilder.add("mark", mark)

                val cleanPath = path.trimStart('/')
                val req = Request.Builder()
                    .url("$base/$cleanPath")
                    .header("User-Agent", USER_AGENT)
                    .header("token", token)
                    .post(formBuilder.build())
                    .build()

                okHttp.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val raw = resp.body?.string().orEmpty()
                        val dec = HuarenCrypto.decryptApiResponse(raw)
                        if (!dec.isNullOrBlank()) {
                            if (base != activeNode) {
                                activeNode = base
                                prefs.edit().putString("active_node", base).apply()
                            }
                            return dec
                        }
                    }
                }
            } catch (_: Throwable) {
            }
        }
        return null
    }

    private fun buildNodeOrder(): List<String> {
        val list = mutableListOf<String>()
        list.add(activeNode)
        for (n in candidateNodes) {
            if (!list.contains(n)) list.add(n)
        }
        return list
    }

    suspend fun refreshBootstrapNodes() = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(BOOTSTRAP_URL)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()
            okHttp.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use
                val body = resp.body?.string().orEmpty()
                val json = JSONObject(body)
                val primary = json.optJSONArray("urls_primary")
                if (primary != null) {
                    for (i in 0 until primary.length()) {
                        val enc = primary.optString(i)
                        val dec = HuarenCrypto.decryptBootstrapUrl(enc)
                        if (!dec.isNullOrBlank() && dec.startsWith("http") && !candidateNodes.contains(dec)) {
                            candidateNodes.add(dec)
                        }
                    }
                }
            }
        } catch (_: Throwable) {
        }
    }

    suspend fun checkRemoteUpdate(): com.purelive.app.data.model.UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val reqBuilder = Request.Builder()
                .url("https://api.github.com/repos/q514168795/pure-live-stream/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "PureStream-App")
            val resp = okHttp.newCall(reqBuilder.build()).execute()
            if (!resp.isSuccessful) return@withContext null
            val bodyStr = resp.body?.string().orEmpty()
            if (bodyStr.isBlank()) return@withContext null

            val json = JSONObject(bodyStr)
            val tagName = json.optString("tag_name", "")
            val releaseName = json.optString("name", tagName)
            val releaseBody = json.optString("body", "")

            val assets = json.optJSONArray("assets") ?: JSONArray()
            var apkDownloadUrl = ""
            var apkAssetUrl = ""
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkDownloadUrl = asset.optString("browser_download_url", "")
                    apkAssetUrl = asset.optString("url", "")
                    break
                }
            }
            if (apkDownloadUrl.isBlank() && apkAssetUrl.isBlank()) {
                return@withContext null
            }

            val codeRegex = Regex("""versionCode:\s*(\d+)""", RegexOption.IGNORE_CASE)
            val codeMatch = codeRegex.find(releaseBody)
            val versionCode = if (codeMatch != null) {
                codeMatch.groupValues[1].toIntOrNull() ?: 1
            } else {
                val clean = tagName.trimStart('v', 'V')
                val parts = clean.split(".")
                if (parts.size >= 2) {
                    val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
                    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
                    major * 10000 + minor * 100 + patch
                } else {
                    clean.toIntOrNull() ?: 1
                }
            }

            val versionName = tagName.trimStart('v', 'V').ifBlank { releaseName }
            val cleanChangelog = releaseBody.replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), "").trim()
            val effectiveDownloadUrl = apkAssetUrl.ifBlank { apkDownloadUrl }

            com.purelive.app.data.model.UpdateInfo(
                latestVersionCode = versionCode,
                latestVersionName = versionName,
                downloadUrl = effectiveDownloadUrl,
                changelogZh = cleanChangelog,
                changelogEs = cleanChangelog,
                isMandatory = true
            )
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun downloadApkFile(
        downloadUrl: String,
        destFile: File,
        onProgress: (percent: Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val reqBuilder = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "PureStream-App")
                .header("Accept", "application/octet-stream")
            val resp = okHttp.newCall(reqBuilder.build()).execute()
            if (!resp.isSuccessful) return@withContext false
            val body = resp.body ?: return@withContext false
            val contentLength = body.contentLength()
            destFile.parentFile?.mkdirs()
            if (destFile.exists()) destFile.delete()

            body.byteStream().use { input ->
                destFile.outputStream().use { output ->
                    val buffer = ByteArray(16384)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        if (contentLength > 0) {
                            val progress = ((totalBytesRead * 100) / contentLength).toInt()
                            onProgress(progress.coerceIn(0, 100))
                        }
                    }
                    output.flush()
                }
            }
            destFile.exists() && destFile.length() > 0
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun submitFeedbackToGithub(content: String, context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
            val osVersion = "Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})"
            val appVersion = "1.0.1"
            val node = activeNode
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            val timeStr = sdf.format(java.util.Date())

            val bodyMarkdown = buildString {
                append("### 问题描述 / Descripción del Problema\n\n")
                append(content.trim())
                append("\n\n---\n")
                append("### 运行与设备诊断 / Diagnóstico del Sistema\n\n")
                append("- **App Version**: ").append(appVersion).append("\n")
                append("- **Device**: ").append(deviceModel).append("\n")
                append("- **OS**: ").append(osVersion).append("\n")
                append("- **Current Node**: ").append(node).append("\n")
                append("- **Time**: ").append(timeStr).append("\n")
            }

            val titleText = if (content.length > 35) {
                "[User Feedback] " + content.take(35).replace("\n", " ") + "..."
            } else {
                "[User Feedback] " + content.replace("\n", " ").ifBlank { "Issue report" }
            }

            val jsonPayload = JSONObject().apply {
                put("title", titleText)
                put("body", bodyMarkdown)
            }

            // Anonymous feedback via issues requires user authentication or server proxy. Return false gracefully.
            false
        } catch (_: Throwable) {
            false
        }
    }
}
