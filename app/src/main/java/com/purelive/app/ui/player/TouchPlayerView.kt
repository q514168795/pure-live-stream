package com.purelive.app.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.purelive.app.data.model.AppLanguage
import com.purelive.app.data.model.Strings
import com.purelive.app.player.ExoPlayerManager
import com.purelive.app.ui.theme.Zinc200
import com.purelive.app.ui.theme.Zinc400
import com.purelive.app.ui.theme.Zinc50
import com.purelive.app.ui.theme.Zinc800
import com.purelive.app.ui.theme.Zinc900
import com.purelive.app.ui.theme.Zinc950
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs

private enum class GestureType {
    NONE, BRIGHTNESS, VOLUME, SEEK
}

fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

fun formatDurationMs(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = ms / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

@OptIn(UnstableApi::class)
@Composable
fun TouchPlayerView(
    playerManager: ExoPlayerManager,
    title: String,
    subtitle: String = "",
    isLive: Boolean = false,
    isLoadingStream: Boolean = false,
    isFullscreen: Boolean,
    onToggleFullscreen: (Boolean) -> Unit,
    onBackClick: (() -> Unit)? = null,
    onNextEpisode: (() -> Unit)? = null,
    onProgressUpdate: ((Long, Long) -> Unit)? = null,
    isPlaybackStarted: Boolean = true,
    coverUrl: String = "",
    onStartPlay: (() -> Unit)? = null,
    currentLang: AppLanguage = AppLanguage.ZH,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var selectedSpeed by remember { mutableFloatStateOf(1.0f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var isPortraitVideo by remember { mutableStateOf(false) }

    // Auto-Pause when app is minimized or backgrounded
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                playerManager.pause()
                isPlaying = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Gesture state
    var gestureType by remember { mutableStateOf(GestureType.NONE) }
    var gestureHudText by remember { mutableStateOf("") }
    var seekTargetMs by remember { mutableLongStateOf(0L) }
    var brightnessLevel by remember {
        mutableFloatStateOf(
            activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f
        )
    }
    var volumeFraction by remember {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        mutableFloatStateOf(curVol.toFloat() / maxVol.toFloat())
    }

    // Handle fullscreen orientation and system bars
    LaunchedEffect(isFullscreen, isPortraitVideo) {
        if (activity != null) {
            val window = activity.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (isFullscreen) {
                activity.requestedOrientation = if (isPortraitVideo) {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler(enabled = isFullscreen) {
        onToggleFullscreen(false)
    }

    // Poll player progress & state
    LaunchedEffect(playerManager.exoPlayer) {
        val player = playerManager.exoPlayer
        var tick = 0
        while (true) {
            isPlaying = player.isPlaying
            isBuffering = player.playbackState == Player.STATE_BUFFERING
            val vs = player.videoSize
            if (vs.width > 0 && vs.height > 0) {
                isPortraitVideo = vs.height > vs.width
            }
            val pos = player.currentPosition.coerceAtLeast(0L)
            val dur = player.duration.coerceAtLeast(0L)
            if (gestureType != GestureType.SEEK) {
                currentPosMs = pos
            }
            durationMs = dur

            if (!isLive && isPlaying && dur > 0L && tick % 10 == 0) {
                onProgressUpdate?.invoke(pos, dur)
            }
            tick++
            delay(500L)
        }
    }

    // Auto-hide controls after 4 seconds
    LaunchedEffect(controlsVisible, isPlaying, showSpeedMenu) {
        if (controlsVisible && isPlaying && !showSpeedMenu) {
            delay(4000L)
            controlsVisible = false
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(isLive, durationMs, isPlaybackStarted) {
                if (!isPlaybackStarted) return@pointerInput
                detectTapGestures(
                    onTap = {
                        if (showSpeedMenu) {
                            showSpeedMenu = false
                        } else {
                            controlsVisible = !controlsVisible
                        }
                    },
                    onDoubleTap = {
                        if (playerManager.exoPlayer.isPlaying) {
                            playerManager.pause()
                            isPlaying = false
                            controlsVisible = true
                        } else {
                            playerManager.resume()
                            isPlaying = true
                        }
                    }
                )
            }
            .pointerInput(isLive, durationMs, isPlaybackStarted) {
                if (!isPlaybackStarted) return@pointerInput
                var startX = 0f
                var totalDx = 0f
                var totalDy = 0f
                detectDragGestures(
                    onDragStart = { offset ->
                        startX = offset.x
                        totalDx = 0f
                        totalDy = 0f
                        gestureType = GestureType.NONE
                        seekTargetMs = playerManager.exoPlayer.currentPosition.coerceAtLeast(0L)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDx += dragAmount.x
                        totalDy += dragAmount.y
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)

                        if (gestureType == GestureType.NONE) {
                            if (abs(totalDx) > 24f && abs(totalDx) > abs(totalDy) * 1.3f && !isLive && durationMs > 0L) {
                                gestureType = GestureType.SEEK
                            } else if (abs(totalDy) > 24f && abs(totalDy) > abs(totalDx) * 1.3f) {
                                gestureType = if (startX < w / 2f) {
                                    GestureType.BRIGHTNESS
                                } else {
                                    GestureType.VOLUME
                                }
                            }
                        }

                        when (gestureType) {
                            GestureType.BRIGHTNESS -> {
                                val delta = -dragAmount.y / (h * 0.75f)
                                brightnessLevel = (brightnessLevel + delta).coerceIn(0.05f, 1.0f)
                                activity?.window?.let { win ->
                                    val lp = win.attributes
                                    lp.screenBrightness = brightnessLevel
                                    win.attributes = lp
                                }
                                gestureHudText = "亮度 ${(brightnessLevel * 100).toInt()}%"
                            }
                            GestureType.VOLUME -> {
                                val delta = -dragAmount.y / (h * 0.75f)
                                volumeFraction = (volumeFraction + delta).coerceIn(0f, 1f)
                                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                val targetVol = (volumeFraction * maxVol).toInt().coerceIn(0, maxVol)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                gestureHudText = "音量 ${(volumeFraction * 100).toInt()}%"
                            }
                            GestureType.SEEK -> {
                                val maxSeekSpan = minOf(durationMs, 180_000L) // full swipe = up to 3 mins
                                val deltaMs = ((dragAmount.x / w) * maxSeekSpan).toLong()
                                seekTargetMs = (seekTargetMs + deltaMs).coerceIn(0L, durationMs)
                                currentPosMs = seekTargetMs
                                gestureHudText = "${formatDurationMs(seekTargetMs)} / ${formatDurationMs(durationMs)}"
                            }
                            GestureType.NONE -> {}
                        }
                    },
                    onDragEnd = {
                        if (gestureType == GestureType.SEEK && !isLive && durationMs > 0L) {
                            playerManager.exoPlayer.seekTo(seekTargetMs)
                        }
                        gestureType = GestureType.NONE
                    },
                    onDragCancel = {
                        gestureType = GestureType.NONE
                    }
                )
            }
    ) {
        // 1. Video Surface (Active when playback started)
        if (isPlaybackStarted) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = playerManager.exoPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        keepScreenOn = true
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    if (view.player !== playerManager.exoPlayer) {
                        view.player = playerManager.exoPlayer
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // 2. Non-Autoplay Static Poster Preview with Prominent Play Button
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(0.55f)
                    )
                }

                Surface(
                    onClick = { onStartPlay?.invoke() },
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xCC09090B),
                    border = BorderStroke(1.dp, Color(0x88FFFFFF)),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "播放",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = if (isLive) Strings.get("start_live", currentLang) else Strings.get("play_now", currentLang),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Buffering / Stream Resolving Indicator
        if (isPlaybackStarted && (isBuffering || isLoadingStream)) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xCC09090B),
                border = BorderStroke(1.dp, Zinc800),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        color = Zinc50,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (isLoadingStream) "正在连接高速线路..." else Strings.get("buffering", currentLang),
                        color = Zinc50,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Gesture HUD Pill (Brightness / Volume / Seek)
        if (gestureType != GestureType.NONE) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xD909090B),
                border = BorderStroke(1.dp, Zinc800),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val icon = when (gestureType) {
                        GestureType.BRIGHTNESS -> Icons.Default.BrightnessMedium
                        GestureType.VOLUME -> Icons.AutoMirrored.Filled.VolumeUp
                        else -> Icons.Default.FastForward
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Zinc50,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = gestureHudText,
                        color = Zinc50,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Top & Bottom Overlay Controls
        AnimatedVisibility(
            visible = controlsVisible && isPlaybackStarted,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xCC09090B), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isFullscreen || onBackClick != null) {
                        Surface(
                            onClick = {
                                if (isFullscreen) {
                                    onToggleFullscreen(false)
                                } else {
                                    onBackClick?.invoke()
                                }
                            },
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0x9918181B),
                            border = BorderStroke(1.dp, Zinc800),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "返回",
                                    tint = Zinc50,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = Zinc50,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                color = Zinc400,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (!isLive) {
                        Surface(
                            onClick = { showSpeedMenu = !showSpeedMenu },
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0x9918181B),
                            border = BorderStroke(1.dp, Zinc800),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            ) {
                                Text(
                                    text = "${selectedSpeed}x",
                                    color = Zinc50,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Speed Selector Popup
                if (showSpeedMenu && !isLive) {
                    val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xEE121215),
                        border = BorderStroke(1.dp, Zinc800),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 46.dp, end = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            speeds.forEach { spd ->
                                val active = spd == selectedSpeed
                                Surface(
                                    onClick = {
                                        selectedSpeed = spd
                                        playerManager.setSpeed(spd)
                                        showSpeedMenu = false
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (active) Zinc50 else Color.Transparent,
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 10.dp)
                                    ) {
                                        Text(
                                            text = "${spd}x",
                                            color = if (active) Zinc950 else Zinc200,
                                            fontSize = 11.sp,
                                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xD909090B))
                            )
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    if (!isLive && durationMs > 0L) {
                        Slider(
                            value = currentPosMs.toFloat().coerceIn(0f, durationMs.toFloat()),
                            onValueChange = { newVal ->
                                currentPosMs = newVal.toLong()
                            },
                            onValueChangeFinished = {
                                playerManager.exoPlayer.seekTo(currentPosMs)
                            },
                            valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Zinc50,
                                activeTrackColor = Zinc50,
                                inactiveTrackColor = Zinc800
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                onClick = {
                                    if (playerManager.exoPlayer.isPlaying) {
                                        playerManager.pause()
                                        isPlaying = false
                                    } else {
                                        playerManager.resume()
                                        isPlaying = true
                                    }
                                },
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0x9918181B),
                                border = BorderStroke(1.dp, Zinc800),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "播放/暂停",
                                        tint = Zinc50,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            if (!isLive && onNextEpisode != null) {
                                Surface(
                                    onClick = onNextEpisode,
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0x9918181B),
                                    border = BorderStroke(1.dp, Zinc800),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.SkipNext,
                                            contentDescription = "下一集",
                                            tint = Zinc50,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = if (isLive) {
                                    "LIVE 实时直播"
                                } else {
                                    "${formatDurationMs(currentPosMs)} / ${formatDurationMs(durationMs)}"
                                },
                                color = Zinc200,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Fullscreen Toggle Button
                            Surface(
                                onClick = { onToggleFullscreen(!isFullscreen) },
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0x9918181B),
                                border = BorderStroke(1.dp, Zinc800),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                        contentDescription = "全屏切换",
                                        tint = Zinc50,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
