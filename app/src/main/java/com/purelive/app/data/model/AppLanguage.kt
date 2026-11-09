package com.purelive.app.data.model

enum class AppLanguage(val code: String, val label: String) {
    ZH("zh", "中文"),
    ES("es", "Español")
}

object Strings {
    fun get(key: String, lang: AppLanguage, vararg args: Any): String {
        val template = when (lang) {
            AppLanguage.ZH -> zhMap[key] ?: key
            AppLanguage.ES -> esMap[key] ?: zhMap[key] ?: key
        }
        return if (args.isNotEmpty()) {
            try {
                String.format(template, *args)
            } catch (_: Throwable) {
                template
            }
        } else {
            template
        }
    }

    fun getFilterLabel(rawText: String, lang: AppLanguage): String {
        if (lang == AppLanguage.ZH) return rawText
        return filterEsMap[rawText] ?: esMap[rawText] ?: rawText
    }

    private val zhMap = mapOf(
        "app_name" to "纯粹影视",
        "tab_vod" to "影视点播",
        "tab_live" to "电视直播",
        "tab_history" to "历史/收藏",
        "search_placeholder" to "搜索电影、剧集、综艺、动漫...",
        "search_button" to "搜索",
        "search_results" to "搜索结果：「%s」",
        "back_to_browse" to "返回分类浏览",
        "filter" to "筛选",
        "all" to "全部",
        "play_now" to "立即播放",
        "start_live" to "开始直播",
        "click_to_play" to "点击开始播放",
        "pause" to "暂停",
        "resume" to "继续",
        "buffering" to "缓冲中...",
        "episodes" to "选集列表",
        "episodes_count" to "共%d集",
        "sort_asc" to "正序 1-%d",
        "sort_desc" to "倒序 %d-1",
        "current_playing" to "正在播放：%s",
        "play_sources" to "播放线路 (%d)",
        "line" to "线路%d",
        "speed" to "倍速",
        "subtitles" to "实时字幕",
        "subtitles_not_supported" to "当前设备未配备系统级实时字幕功能",
        "favorite" to "收藏",
        "favorited" to "已收藏",
        "director" to "导演",
        "actor" to "主演",
        "reload" to "重新加载",
        "loading_vod" to "正在加载影视资源...",
        "loading_live" to "正在同步直播频道列表...",
        "no_history" to "暂无观看历史，播放进度将自动保存在本地",
        "no_favorites" to "暂无收藏内容，支持收藏直播频道与点播剧集",
        "clear_history" to "清空历史",
        "history_tab" to "观看历史 (%d)",
        "favorites_tab" to "我的收藏 (%d)",
        "force_update_title" to "发现重要版本更新",
        "force_update_desc" to "当前版本协议已升级，为保障视频播放与线路稳定，请升级后继续使用。",
        "force_update_button" to "立即更新",
        "downloading_update" to "正在下载更新",
        "download_failed" to "下载失败，请重试",
        "check_update" to "检查更新",
        "already_latest" to "当前已是最新版本",
        "checking_update" to "正在检查更新...",
        "cat_movie" to "电影",
        "cat_series" to "电视剧",
        "cat_variety" to "综艺",
        "cat_anime" to "动漫",
        "cat_short" to "短剧",
        "cat_doc" to "纪录片",
        "cat_hot_movie" to "豆瓣热映",
        "cat_hot_series" to "豆瓣热剧",
        "cat_hot_variety" to "豆瓣热综",
        "cat_hot_anime" to "豆瓣热漫",
        "bug_feedback" to "问题反馈",
        "bug_feedback_title" to "提交问题与建议",
        "bug_feedback_desc" to "遇到播放失败、线路卡顿或内容缺失？请在下方填写描述，系统将自动汇总环境诊断并直接提交至 GitHub。",
        "bug_feedback_hint" to "请详细描述您遇到的问题（如哪部影片、第几集、出现什么提示）...",
        "bug_feedback_diag_label" to "自动随同提交的诊断信息：",
        "bug_feedback_submit" to "自动提交至 GitHub",
        "bug_feedback_submitting" to "正在提交至 GitHub...",
        "bug_feedback_cancel" to "取消",
        "bug_feedback_success" to "反馈已成功提交至 GitHub，感谢您的反馈！",
        "bug_feedback_error" to "网络连接受阻，已将反馈与诊断信息复制到剪贴板",
        "bug_feedback_empty" to "请先输入问题描述"
    )

    private val esMap = mapOf(
        "app_name" to "Pure Stream",
        "tab_vod" to "Películas",
        "tab_live" to "TV en Vivo",
        "tab_history" to "Mi Lista",
        "search_placeholder" to "Buscar películas, series, anime...",
        "search_button" to "Buscar",
        "search_results" to "Resultados de: «%s»",
        "back_to_browse" to "Volver a categorías",
        "filter" to "Filtros",
        "all" to "Todos",
        "play_now" to "Reproducir",
        "start_live" to "Ver en Vivo",
        "click_to_play" to "Haz clic para reproducir",
        "pause" to "Pausar",
        "resume" to "Continuar",
        "buffering" to "Cargando...",
        "episodes" to "Episodios",
        "episodes_count" to "%d episodios",
        "sort_asc" to "Ascendente 1-%d",
        "sort_desc" to "Descendente %d-1",
        "current_playing" to "Reproduciendo: %s",
        "play_sources" to "Servidores (%d)",
        "line" to "Línea %d",
        "speed" to "Velocidad",
        "subtitles" to "Subtítulos",
        "subtitles_not_supported" to "Este dispositivo no admite la función de subtítulos en vivo del sistema",
        "favorite" to "Favorito",
        "favorited" to "Guardado",
        "director" to "Director",
        "actor" to "Reparto",
        "reload" to "Recargar",
        "loading_vod" to "Cargando contenidos multimedia...",
        "loading_live" to "Sincronizando canales en vivo...",
        "no_history" to "Sin historial. El progreso se guardará automáticamente",
        "no_favorites" to "Sin favoritos guardados",
        "clear_history" to "Borrar historial",
        "history_tab" to "Historial (%d)",
        "favorites_tab" to "Favoritos (%d)",
        "force_update_title" to "Actualización obligatoria requerida",
        "force_update_desc" to "Esta versión ha sido actualizada. Actualice ahora para garantizar la reproducción y estabilidad del servicio.",
        "force_update_button" to "Actualizar ahora",
        "downloading_update" to "Descargando actualización",
        "download_failed" to "Error al descargar, intente de nuevo",
        "check_update" to "Buscar actualizaciones",
        "already_latest" to "Ya tienes la versión más reciente",
        "checking_update" to "Comprobando actualizaciones...",
        "cat_movie" to "Películas",
        "cat_series" to "Series",
        "cat_variety" to "Variedades",
        "cat_anime" to "Anime",
        "cat_short" to "Mini Series",
        "cat_doc" to "Documentales",
        "cat_hot_movie" to "Cine Popular",
        "cat_hot_series" to "Series Populares",
        "cat_hot_variety" to "Shows Populares",
        "cat_hot_anime" to "Anime Popular",
        "bug_feedback" to "Reportar error",
        "bug_feedback_title" to "Reportar un problema",
        "bug_feedback_desc" to "¿Problemas de reproducción, cortes o errores? Descríbalo aquí y se enviará automáticamente a GitHub con los diagnósticos.",
        "bug_feedback_hint" to "Describa detalladamente el problema (título de la película, episodio, error observado)...",
        "bug_feedback_diag_label" to "Diagnóstico incluido automáticamente:",
        "bug_feedback_submit" to "Enviar a GitHub",
        "bug_feedback_submitting" to "Enviando a GitHub...",
        "bug_feedback_cancel" to "Cancelar",
        "bug_feedback_success" to "¡Reporte enviado exitosamente a GitHub! Gracias por su ayuda.",
        "bug_feedback_error" to "Fallo de conexión. Se copió el reporte y diagnóstico al portapapeles.",
        "bug_feedback_empty" to "Por favor escriba una descripción del problema"
    )

    private val filterEsMap = mapOf(
        // Groups
        "分类" to "Categoría",
        "地区" to "Región",
        "年份" to "Año",
        "排序" to "Orden",
        "语言" to "Idioma",

        // Default
        "全部" to "Todos",

        // Sort
        "按更新时间" to "Más recientes",
        "按点击数" to "Más vistos",
        "按豆瓣评分" to "Calificación Douban",

        // Regions
        "大陆" to "China Continental",
        "香港" to "Hong Kong",
        "台湾" to "Taiwán",
        "美国" to "EE. UU.",
        "日本" to "Japón",
        "韩国" to "Corea del Sur",
        "英国" to "Reino Unido",
        "法国" to "Francia",
        "德国" to "Alemania",
        "意大利" to "Italia",
        "加拿大" to "Canadá",
        "印度" to "India",
        "澳大利亚" to "Australia",
        "泰国" to "Tailandia",
        "西班牙" to "España",
        "欧美" to "Occidente",

        // Categories / Genres
        "奇幻科幻" to "Fantasía / Sci-Fi",
        "战争犯罪" to "Guerra / Crimen",
        "悬疑恐怖惊悚" to "Suspenso / Terror",
        "爱情喜剧剧情" to "Romance / Comedia / Drama",
        "动作冒险灾难" to "Acción / Aventura",
        "动画电影" to "Películas Animadas",
        "国产动漫" to "Anime Chino",
        "日本动漫" to "Anime Japonés",
        "其它动漫" to "Otros Animes",
        "大陆剧" to "Series Chinas",
        "港台剧" to "Series HK y Taiwán",
        "日韩剧" to "Series Japón y Corea",
        "欧美剧" to "Series Occidentales",
        "其他剧" to "Otras Series",
        "大陆综艺" to "Variedades Chinas",
        "港台综艺" to "Variedades HK y Taiwán",
        "日韩综艺" to "Variedades Japón y Corea",
        "其他综艺" to "Otras Variedades",
        "4K影库" to "Películas 4K",
        "网络电影" to "Películas Web",
        "纪录片" to "Documentales",
        "自然" to "Naturaleza",
        "历史" to "Historia",
        "人物" to "Biografía",
        "科技" to "Tecnología",
        "探索" to "Exploración",
        "军事" to "Militar",
        "文化" to "Cultura",
        "解密" to "Misterios",
        "其他" to "Otros",

        // Languages
        "国语" to "Mandarín",
        "粤语" to "Cantonés",
        "英语" to "Inglés",
        "日语" to "Japonés",
        "韩语" to "Coreano",
        "泰语" to "Tailandés",
        "西班牙语" to "Español"
    )
}
