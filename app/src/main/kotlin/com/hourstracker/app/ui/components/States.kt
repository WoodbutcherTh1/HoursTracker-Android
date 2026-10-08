package com.hourstracker.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hourstracker.app.R
import com.hourstracker.app.ui.theme.DsText
import com.hourstracker.app.ui.theme.Palette
import com.hourstracker.app.ui.theme.Radius
import com.hourstracker.app.ui.theme.Space

/**
 * A grey block that slowly pulses while content loads. A pulse (not a sweep) so it stays calm and
 * costs one animated float for the whole screen.
 */
@Composable
fun SkeletonBox(modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(Radius.sm)) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    Box(modifier = modifier.background(Palette.raised.copy(alpha = alpha), shape))
}

/** Placeholder rows shaped like the History table, shown while the first read of the shifts is in flight. */
@Composable
fun HistorySkeleton(modifier: Modifier = Modifier) {
    val loading = stringResource(R.string.state_loading)
    Column(
        modifier = modifier.semantics { contentDescription = loading },
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        repeat(6) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                SkeletonBox(Modifier.weight(1.2f).height(18.dp))
                SkeletonBox(Modifier.weight(1f).height(18.dp))
                SkeletonBox(Modifier.weight(1f).height(18.dp))
                SkeletonBox(Modifier.weight(1.2f).height(18.dp))
            }
        }
    }
}

/** A glyph on a soft glow, with a title, a hint and an optional action. Used for every "nothing here yet" screen. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    StateLayout(icon, Palette.accent, title, body, modifier, actionLabel, onAction)
}

/** The same layout in the warning colour: permission refused, report failed, no connection. */
@Composable
fun ErrorState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    StateLayout(icon, Palette.warning, title, body, modifier, actionLabel, onAction)
}

@Composable
private fun StateLayout(
    icon: ImageVector,
    tint: Color,
    title: String,
    body: String,
    modifier: Modifier,
    actionLabel: String?,
    onAction: (() -> Unit)?,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(Space.xl),
        verticalArrangement = Arrangement.spacedBy(Space.sm, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(listOf(tint.copy(alpha = 0.16f), Color.Transparent), radius = 72.dp.toPx()),
                        radius = 72.dp.toPx(),
                    )
                }
                .background(Palette.card, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(44.dp))
        }
        Text(text = title, style = DsText.headline, color = Palette.textPrimary, textAlign = TextAlign.Center)
        Text(text = body, style = DsText.sub, color = Palette.textSecondary, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = DsText.headline,
                color = Palette.ink,
                modifier = Modifier
                    .padding(top = Space.xs)
                    .background(tint, CircleShape)
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = Space.lg, vertical = Space.sm),
            )
        }
    }
}

/** A one-line strip for problems that do not block the screen (for example, notifications are off while a shift runs). */
@Composable
fun NoticeBanner(text: String, actionLabel: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Palette.card, RoundedCornerShape(Radius.lg))
            .padding(Space.md),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, style = DsText.sub, color = Palette.textSecondary, modifier = Modifier.weight(1f))
        Text(
            text = actionLabel,
            style = DsText.sub,
            color = Palette.warning,
            modifier = Modifier.clickable(role = Role.Button, onClick = onAction).padding(Space.xs),
        )
    }
}

/** Fills the space with a centred busy message, for full-screen waits. */
@Composable
fun LoadingBlock(modifier: Modifier = Modifier) {
    val loading = stringResource(R.string.state_loading)
    Box(modifier = modifier.fillMaxSize().semantics { contentDescription = loading }, contentAlignment = Alignment.Center) {
        SkeletonBox(Modifier.size(width = 160.dp, height = 24.dp))
    }
}
