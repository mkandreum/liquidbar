package io.github.mkandreum.liquidbar.ui

import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import io.github.mkandreum.liquidbar.LiquidGlassStyle
import io.github.mkandreum.liquidbar.glass.GlassBackdrop
import io.github.mkandreum.liquidbar.glass.GlassLook
import io.github.mkandreum.liquidbar.glass.GlassSource
import io.github.mkandreum.liquidbar.glass.glassSurface
import kotlin.math.max

/** Muelles de LiquidUI: los mismos (dampingRatio, stiffness) que la barra y la versión web. */
internal object LiquidSprings {
    val travel = spring<Float>(dampingRatio = 0.62f, stiffness = 420f)
    val stretch = spring<Float>(dampingRatio = 0.5f, stiffness = 420f)
    val follow = spring<Float>(dampingRatio = 1f, stiffness = 1000f, visibilityThreshold = 0.001f)
    val press = spring<Float>(dampingRatio = 1f, stiffness = 1000f, visibilityThreshold = 0.001f)
    val release = spring<Float>(dampingRatio = 1f, stiffness = 600f, visibilityThreshold = 0.001f)
    val scaleX = spring<Float>(dampingRatio = 0.6f, stiffness = 250f, visibilityThreshold = 0.001f)
    val scaleY = spring<Float>(dampingRatio = 0.7f, stiffness = 250f, visibilityThreshold = 0.001f)
    val pop = spring<Float>(dampingRatio = 0.4f, stiffness = 520f)
    val toggle = spring<Float>(dampingRatio = 0.6f, stiffness = 500f)
    val sheet = spring<Float>(dampingRatio = 0.75f, stiffness = 300f)
    val drop = spring<Float>(dampingRatio = 0.45f, stiffness = 300f)
}

/**
 * Aspecto de una lente de LiquidUI con la presión [p] (0 reposo · 1 pulsada): aumenta lo de debajo hasta [zoom],
 * refracta el canto con arcoíris, el relleno se aclara y aparecen las sombras.
 */
internal fun Density.lensLook(
    p: Float,
    fill: Color,
    zoom: Float,
    band: Dp = 10.dp,
    amount: Dp = 14.dp,
    blur: Dp = 0.dp,
    saturation: Float = 1.5f,
    restShadow: Float = 0f,
    fallback: Color = fill,
) = GlassLook(
    blur = blur.toPx() * (1f - p),
    saturation = saturation,
    refractionHeight = band.toPx() * p,
    refractionAmount = amount.toPx() * p,
    dispersion = 0.5f,
    depthEffect = true,
    zoom = lerp(1f, zoom, p),
    surface = fill,
    rim = Color.White.copy(alpha = 0.18f + 0.34f * p),
    rimWidth = 0.6.dp.toPx(),
    shadowAlpha = max(p, restShadow),
    innerShadowAlpha = p,
    innerShadowRadius = 8.dp.toPx() * p,
    fallback = fallback,
)

/** `liquidGlass` con un tinte que se lee en cada fotograma (p. ej. que sube al pulsar) sin recomponer. */
internal fun Modifier.liquidGlassTinted(
    backdrop: GlassBackdrop,
    shape: Shape,
    style: LiquidGlassStyle,
    tint: () -> Color,
): Modifier {
    val sources = listOf(GlassSource(backdrop))
    return glassSurface({ sources }, shape) {
        val t = tint()
        GlassLook(
            blur = style.blur.toPx(),
            saturation = style.saturation,
            refractionHeight = style.refractionHeight.toPx(),
            refractionAmount = style.refractionAmount.toPx(),
            depthEffect = style.depthEffect,
            surface = if (t.alpha <= 0f) style.surface else t.compositeOver(style.surface),
            rim = style.rim,
            rimWidth = style.rimWidth.toPx(),
            darkRimAlpha = style.darkRimAlpha,
            darkRimWidth = style.darkRimWidth.toPx(),
            lightAngle = style.lightAngle,
            fallback = if (t.alpha <= 0f) style.fallback else t.copy(alpha = 1f).compositeOver(style.fallback),
        )
    }
}

/** Resultado de esperar una pulsación larga. */
internal enum class Hold { Long, Up, Cancel }

/** Espera [timeoutMs] con el dedo quieto: Long si se cumple, Up si se levanta antes, Cancel si se mueve. */
internal suspend fun AwaitPointerEventScope.awaitHold(
    down: PointerInputChange,
    timeoutMs: Long,
    pass: PointerEventPass = PointerEventPass.Main,
    slop: Float = viewConfiguration.touchSlop,
): Hold {
    var result = Hold.Long
    withTimeoutOrNull(timeoutMs) {
        while (true) {
            val ev = awaitPointerEvent(pass)
            val ch = ev.changes.firstOrNull { it.id == down.id }
            if (ch == null) { result = Hold.Cancel; break }
            if (!ch.pressed) { result = Hold.Up; break }
            if ((ch.position - down.position).getDistance() > slop) { result = Hold.Cancel; break }
        }
    }
    return result
}

/** Lupa (icono de buscar) dibujada a mano, para no depender de material-icons. */
internal fun DrawScope.drawSearchGlyph(color: Color, stroke: Float) {
    val r = size.minDimension * 0.3f
    val c = Offset(size.width * 0.45f, size.height * 0.45f)
    drawCircle(color, r, c, style = Stroke(stroke))
    val a = c + Offset(r * 0.72f, r * 0.72f)
    drawLine(color, a, a + Offset(r * 0.75f, r * 0.75f), stroke, StrokeCap.Round)
}

/** Aspa de cerrar dibujada a mano. */
internal fun DrawScope.drawCloseGlyph(color: Color, stroke: Float) {
    val m = size.minDimension * 0.3f
    drawLine(color, Offset(m, m), Offset(size.width - m, size.height - m), stroke, StrokeCap.Round)
    drawLine(color, Offset(size.width - m, m), Offset(m, size.height - m), stroke, StrokeCap.Round)
}

/** Flecha hacia abajo dibujada a mano (tirar para recargar). */
internal fun DrawScope.drawArrowGlyph(color: Color, stroke: Float) {
    val cx = size.width / 2f
    val top = size.height * 0.22f
    val bottom = size.height * 0.78f
    val w = size.width * 0.25f
    drawLine(color, Offset(cx, top), Offset(cx, bottom), stroke, StrokeCap.Round)
    drawLine(color, Offset(cx - w, bottom - w), Offset(cx, bottom), stroke, StrokeCap.Round)
    drawLine(color, Offset(cx + w, bottom - w), Offset(cx, bottom), stroke, StrokeCap.Round)
}
