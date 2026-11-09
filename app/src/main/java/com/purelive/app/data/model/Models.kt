package com.purelive.app.data.model

data class VodItem(
    val id: String,
    val title: String,
    val coverUrl: String,
    val category: String,
    val year: String,
    val remarks: String,
    val area: String = "",
    val lang: String = "",
    val actor: String = "",
    val director: String = "",
    val blurb: String = "",
    val score: String = "",
    val playSources: List<VodPlaySource> = emptyList()
)

data class VodPlaySource(
    val name: String,
    val episodes: List<Episode>
)

data class Episode(
    val title: String,
    val url: String
)

data class LiveChannel(
    val id: String,
    val name: String,
    val group: String,
    val streamUrl: String,
    val lines: List<String> = listOf(streamUrl)
)

data class VodCategory(
    val typeId: Int,
    val name: String,
    val filters: List<VodFilterGroup> = emptyList()
)

data class VodFilterGroup(
    val key: String,
    val name: String,
    val options: List<VodFilterOption>
)

data class VodFilterOption(
    val label: String,
    val value: String
)

data class PagedVodResult(
    val items: List<VodItem>,
    val page: Int,
    val pageCount: Int,
    val total: Int
)

data class UpdateInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val downloadUrl: String,
    val changelogZh: String,
    val changelogEs: String,
    val isMandatory: Boolean = true
)
