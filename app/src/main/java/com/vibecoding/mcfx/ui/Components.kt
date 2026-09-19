package com.vibecoding.mcfx.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.nio.ByteBuffer
import com.vibecoding.mcfx.R

// ---------------------------------------------------------------- flag assets

private val flagCache = HashMap<String, ImageBitmap?>()

/** Decodes `assets/flags/<cc>.png` once per country; they are 64px circles. */
private fun flagBitmap(context: Context, cc: String): ImageBitmap? {
    val key = cc.lowercase()
    synchronized(flagCache) {
        if (flagCache.containsKey(key)) return flagCache[key]
    }
    val bitmap = runCatching {
        context.assets.open("flags/$key.png").use { BitmapFactory.decodeStream(it) }
    }.getOrNull()?.asImageBitmap()
    synchronized(flagCache) { flagCache[key] = bitmap }
    return bitmap
}

/**
 * Circular country flag, the way Wise renders currency icons. The assets are
 * pre-rendered circles (MIT-licensed `circle-flags`), so nothing is distorted by
 * cropping a rectangular flag or an emoji glyph.
 */
@Composable
fun FlagIcon(cc: String, size: Dp = 28.dp) {
    val context = LocalContext.current
    val bitmap = remember(cc) { flagBitmap(context, cc) }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(WiseFill),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(
                text = cc.take(1).uppercase(),
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.SemiBold,
                color = WiseInkFaint,
            )
        }
    }
}

@Composable
fun MastercardMark(size: Dp = 30.dp) {
    Image(
        painter = painterResource(id = R.drawable.ic_mastercard_mark),
        contentDescription = "Mastercard",
        modifier = Modifier.size(size),
    )
}

/**
 * The app's loading indicator: the supplied Mastercard logo animation
 * (`assets/mastercard_loader.gif`), played directly rather than re-drawn.
 *
 * API 28+ decodes it as an [android.graphics.drawable.AnimatedImageDrawable] and
 * loops it; on API 26–27 the GIF's first frame is shown as a still, since the
 * platform has no animated-GIF decoder. One drawable instance per call site, so
 * the full-screen and inline indicators never share a callback.
 */
@Composable
fun MastercardLoadingMark(size: Dp = 56.dp) {
    val context = LocalContext.current

    val animated = remember { decodeAnimatedGif(context, GIF_ASSET) }
    val still = remember(animated) {
        if (animated != null) null else decodeFirstFrame(context, GIF_ASSET)
    }

    when {
        animated != null -> AndroidView(
            modifier = Modifier.size(size),
            factory = { ctx ->
                ImageView(ctx).apply {
                    setImageDrawable(animated)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    animated.start()
                }
            },
        )

        still != null -> Image(
            bitmap = still,
            contentDescription = null,
            modifier = Modifier.size(size),
            contentScale = ContentScale.Fit,
        )

        else -> Box(modifier = Modifier.size(size))
    }
}

private const val GIF_ASSET = "mastercard_loader.gif"

@RequiresApi(Build.VERSION_CODES.P)
private fun decodeAnimatedGif(context: Context, asset: String): AnimatedImageDrawable? =
    runCatching {
        val bytes = context.assets.open(asset).use { it.readBytes() }
        val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
        val drawable = ImageDecoder.decodeDrawable(source)
        (drawable as? AnimatedImageDrawable)?.apply {
            repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
        }
    }.getOrNull()

private fun decodeFirstFrame(context: Context, asset: String): ImageBitmap? = runCatching {
    context.assets.open(asset).use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
}.getOrNull()

// ------------------------------------------------------------------- controls

/** Currency selector pill: flag + code + chevron. */
@Composable
fun CurrencyChip(code: String, cc: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlagIcon(cc = cc, size = 28.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = code,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = WiseInk,
        )
        Spacer(Modifier.width(2.dp))
        Icon(
            imageVector = Icons.Filled.ExpandMore,
            contentDescription = "选择货币",
            tint = WiseInkFaint,
        )
    }
}

@Composable
fun WiseField(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, WiseBorder, RoundedCornerShape(10.dp))
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Lime circular swap button; the arrows spin half a turn on every swap. */
@Composable
fun SwapButton(onClick: () -> Unit, enabled: Boolean, turns: Int) {
    val rotation by animateFloatAsState(
        targetValue = turns * 180f,
        animationSpec = tween(durationMillis = WiseMotion.medium, easing = WiseMotion.easing),
        label = "swapRotation",
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = tween(WiseMotion.fast),
        label = "swapScale",
    )

    Box(
        modifier = Modifier
            .size(48.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(if (enabled) WiseGreen else WiseFill)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.SwapVert,
            contentDescription = "互换货币",
            tint = if (enabled) WiseOnGreen else WiseInkFaint,
            modifier = Modifier.rotate(rotation),
        )
    }
}

/** Wise's primary action: full-width lime pill with forest text. */
@Composable
fun PrimaryButton(
    text: String,
    loading: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val active = enabled && !loading
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && active) 0.975f else 1f,
        animationSpec = tween(WiseMotion.fast, easing = WiseMotion.easing),
        label = "buttonScale",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .scale(scale)
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) WiseGreen else WiseFill)
            .clickable(
                enabled = active,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = WiseOnGreen,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = if (loading) "查询中…" else text,
                color = if (active) WiseOnGreen else WiseInkFaint,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun InfoBanner(
    title: String,
    detail: String,
    actionText: String?,
    onAction: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(WiseInfoBg)
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text = "\u2708\uFE0F", fontSize = 24.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 13.sp, lineHeight = 20.sp, color = WiseInfoInk)
            if (detail.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = detail,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = WiseInfoInk.copy(alpha = 0.8f),
                )
            }
            if (actionText != null && onAction != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = actionText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WiseInfoInk,
                    modifier = Modifier.clickable(onClick = onAction),
                )
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = WiseInkSoft,
    )
}

@Composable
fun BreakdownRow(label: String, value: String, emphasised: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, fontSize = 13.sp, color = WiseInkSoft)
        Text(
            text = value,
            fontSize = if (emphasised) 15.sp else 13.sp,
            fontWeight = if (emphasised) FontWeight.SemiBold else FontWeight.Normal,
            color = if (emphasised) WiseInk else WiseInkSoft,
        )
    }
}

@Composable
fun HairlineDivider(color: Color = WiseBorder) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color),
    )
}
