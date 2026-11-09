package com.purelive.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ==================== SHADCN/UI ZINC PALETTE ====================

// Standard Zinc Dark values
val Zinc950 = Color(0xFF09090B)
val Zinc900 = Color(0xFF121215)
val Zinc850 = Color(0xFF18181B)
val Zinc800 = Color(0xFF27272A)
val Zinc700 = Color(0xFF3F3F46)
val Zinc500 = Color(0xFF71717A)
val Zinc400 = Color(0xFFA1A1AA)
val Zinc300 = Color(0xFFD4D4D8)
val Zinc200 = Color(0xFFE4E4E7)
val Zinc100 = Color(0xFFF4F4F5)
val Zinc50 = Color(0xFFFAFAFA)
val PureWhite = Color(0xFFFFFFFF)

// Badges
val LiveBadgeBgDark = Color(0xFF052E16)
val LiveBadgeBorderDark = Color(0xFF14532D)
val LiveBadgeTextDark = Color(0xFF4ADE80)

val LiveBadgeBgLight = Color(0xFFDCFCE7)
val LiveBadgeBorderLight = Color(0xFF86EFAC)
val LiveBadgeTextLight = Color(0xFF15803D)

val ScoreBadgeBg = Color(0xFF27272A)
val ScoreBadgeText = Color(0xFFFACC15)

// Legacy compatibility aliases
val LiveBadgeBg = LiveBadgeBgDark
val LiveBadgeBorder = LiveBadgeBorderDark
val LiveBadgeText = LiveBadgeTextDark

data class ShadcnPalette(
    val isDark: Boolean,
    val canvasBg: Color,
    val cardBg: Color,
    val inputBg: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val primaryButtonBg: Color,
    val primaryButtonText: Color,
    val scoreBadgeBg: Color,
    val scoreBadgeText: Color,
    val liveBadgeBg: Color,
    val liveBadgeBorder: Color,
    val liveBadgeText: Color
)

val DarkShadcnPalette = ShadcnPalette(
    isDark = true,
    canvasBg = Zinc950,
    cardBg = Zinc900,
    inputBg = Zinc850,
    border = Zinc800,
    textPrimary = Zinc50,
    textSecondary = Zinc400,
    textMuted = Zinc500,
    primaryButtonBg = Zinc50,
    primaryButtonText = Zinc950,
    scoreBadgeBg = ScoreBadgeBg,
    scoreBadgeText = ScoreBadgeText,
    liveBadgeBg = LiveBadgeBgDark,
    liveBadgeBorder = LiveBadgeBorderDark,
    liveBadgeText = LiveBadgeTextDark
)

val LightShadcnPalette = ShadcnPalette(
    isDark = false,
    canvasBg = Zinc50,
    cardBg = PureWhite,
    inputBg = Zinc100,
    border = Zinc200,
    textPrimary = Zinc950,
    textSecondary = Zinc500,
    textMuted = Zinc400,
    primaryButtonBg = Zinc950,
    primaryButtonText = Zinc50,
    scoreBadgeBg = Zinc100,
    scoreBadgeText = Color(0xFFB45309), // Amber-700
    liveBadgeBg = LiveBadgeBgLight,
    liveBadgeBorder = LiveBadgeBorderLight,
    liveBadgeText = LiveBadgeTextLight
)

val LocalShadcnColors = staticCompositionLocalOf { LightShadcnPalette }

private val PureDarkColorScheme = darkColorScheme(
    primary = Zinc50,
    onPrimary = Zinc950,
    secondary = Zinc850,
    onSecondary = Zinc50,
    background = Zinc950,
    onBackground = Zinc50,
    surface = Zinc900,
    onSurface = Zinc50,
    outline = Zinc800
)

private val PureLightColorScheme = lightColorScheme(
    primary = Zinc950,
    onPrimary = Zinc50,
    secondary = Zinc100,
    onSecondary = Zinc950,
    background = Zinc50,
    onBackground = Zinc950,
    surface = PureWhite,
    onSurface = Zinc950,
    outline = Zinc200
)

@Composable
fun PureLiveTheme(
    isDarkMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val palette = if (isDarkMode) DarkShadcnPalette else LightShadcnPalette
    val colorScheme = if (isDarkMode) PureDarkColorScheme else PureLightColorScheme

    CompositionLocalProvider(LocalShadcnColors provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}

// ==================== SHADCN REUSABLE COMPONENTS ====================

@Composable
fun ShadcnCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(12.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalShadcnColors.current
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(8.dp),
        color = colors.cardBg,
        border = BorderStroke(1.dp, colors.border),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(padding),
            content = content
        )
    }
}

@Composable
fun ShadcnButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val colors = LocalShadcnColors.current
    val bgColor = when {
        !enabled -> colors.inputBg
        primary -> colors.primaryButtonBg
        else -> colors.inputBg
    }
    val contentColor = when {
        !enabled -> colors.textMuted
        primary -> colors.primaryButtonText
        else -> colors.textPrimary
    }
    val borderStroke = if (primary) null else BorderStroke(1.dp, colors.border)

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(6.dp),
        color = bgColor,
        border = borderStroke,
        shadowElevation = 0.dp,
        modifier = modifier.height(36.dp).wrapContentWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                color = contentColor,
                fontSize = 13.sp,
                fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ShadcnBadge(
    text: String,
    modifier: Modifier = Modifier,
    bgColor: Color? = null,
    borderColor: Color? = null,
    textColor: Color? = null
) {
    val colors = LocalShadcnColors.current
    val resolvedBg = bgColor ?: colors.inputBg
    val resolvedBorder = borderColor ?: colors.border
    val resolvedText = textColor ?: colors.textSecondary

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = resolvedBg,
        border = BorderStroke(1.dp, resolvedBorder),
        shadowElevation = 0.dp,
        modifier = modifier
    ) {
        Text(
            text = text,
            color = resolvedText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun ShadcnScrollableTabs(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalShadcnColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .background(colors.inputBg, RoundedCornerShape(8.dp))
            .border(1.dp, colors.border, RoundedCornerShape(8.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Surface(
                onClick = { onSelect(index) },
                shape = RoundedCornerShape(6.dp),
                color = if (selected) colors.primaryButtonBg else Color.Transparent,
                border = null,
                shadowElevation = 0.dp,
                modifier = Modifier.height(30.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
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

@Composable
fun ShadcnSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    placeholder: String = "搜索电影、剧集、综艺、动漫...",
    buttonText: String = "搜索",
    modifier: Modifier = Modifier
) {
    val colors = LocalShadcnColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = colors.textPrimary, fontSize = 13.sp),
            cursorBrush = SolidColor(colors.textPrimary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .background(colors.cardBg, RoundedCornerShape(6.dp))
                        .border(1.dp, colors.border, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(text = placeholder, color = colors.textMuted, fontSize = 13.sp, maxLines = 1)
                        }
                        innerTextField()
                    }
                    if (value.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "清除",
                            tint = colors.textSecondary,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onClear() }
                        )
                    }
                }
            }
        )
        ShadcnButton(
            text = buttonText,
            onClick = onSearch,
            primary = true,
            modifier = Modifier.height(38.dp)
        )
    }
}
