package io.github.mkandreum.liquidbar

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import io.github.mkandreum.liquidbar.glass.GlassBackdrop
import io.github.mkandreum.liquidbar.glass.GlassGlow
import io.github.mkandreum.liquidbar.glass.GlassLook
import io.github.mkandreum.liquidbar.glass.GlassSource
import io.github.mkandreum.liquidbar.glass.glassSurface
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Física y colores del cristal de la barra: fondo desenfocado 3 dp y saturado ×2, 28,8 dp de canto que
 * refracta hasta 33 dp, un tinte #171717 al 50 % (oscuro) o #FBFBFB al 40 % (claro), filo de luz gris
 * claro arriba a la izquierda y un contorno oscuro en las otras esquinas.
 */
@Immutable
data class LiquidGlassStyle(
    val blur: Dp = 3.dp,
    val saturation: Float = 1.5f,
    val refractionHeight: Dp = 24.dp,
    val refractionAmount: Dp = 24.dp,
    /** Lente abombada: suma la dirección radial a la normal (más aumento en el centro del canto). */
    val depthEffect: Boolean = false,
    val surface: Color = Color(0xFF121212).copy(alpha = 0.40f),
    val rim: Color = Color(0xFFE8E8E8).copy(alpha = 0.18f),
    val rimWidth: Dp = 0.8.dp,
    val lightAngle: Float = 45f,
    val darkRimAlpha: Float = 0.19f,
    val darkRimWidth: Dp = 1.6.dp,
    val fallback: Color = Color(0xFF1C1C1E).copy(alpha = 0.92f),
) {
    companion object {
        val Dark = LiquidGlassStyle()
        val Light = LiquidGlassStyle(
            surface = Color(0xFFFBFBFB).copy(alpha = 0.40f),
            rim = Color.White.copy(alpha = 0.44f),
            darkRimAlpha = 0.19f,
            fallback = Color(0xFFF5F5F7).copy(alpha = 0.94f),
        )
    }
}

/** Superficie de cristal líquido con la física de la barra, para cualquier forma redondeada. */
fun Modifier.liquidGlass(
    backdrop: GlassBackdrop,
    shape: Shape,
    style: LiquidGlassStyle = LiquidGlassStyle.Dark,
    tint: Color = Color.Unspecified,
): Modifier {
    val sources = listOf(GlassSource(backdrop))
    return glassSurface({ sources }, shape) {
        GlassLook(
            blur = style.blur.toPx(),
            saturation = style.saturation,
            refractionHeight = style.refractionHeight.toPx(),
            refractionAmount = style.refractionAmount.toPx(),
            depthEffect = style.depthEffect,
            surface = if (tint == Color.Unspecified) style.surface else tint.compositeOver(style.surface),
            rim = style.rim,
            rimWidth = style.rimWidth.toPx(),
            darkRimAlpha = style.darkRimAlpha,
            darkRimWidth = style.darkRimWidth.toPx(),
            lightAngle = style.lightAngle,
            fallback = if (tint == Color.Unspecified) style.fallback else tint.copy(alpha = 1f).compositeOver(style.fallback),
        )
    }
}

/**
 * Estiramiento elástico al pulsar y arrastrar: la pieza se infla unos dp y sigue al dedo con resistencia
 * (tanh), alargándose en la dirección del arrastre.
 */
internal fun elasticStretch(glow: GlassGlow, strength: Float = 1f): GraphicsLayerScope.() -> Unit = {
    val w = size.width
    val h = size.height
    if (w > 0f && h > 0f) {
        val p = glow.pressProgress
        val inflate = lerp(1f, 1f + strength * 4.dp.toPx() / h, p)
        val o = glow.offset
        val limit = size.minDimension
        translationX = strength * limit * tanh(0.05f * o.x / limit)
        translationY = strength * limit * tanh(0.05f * o.y / limit)
        val pull = strength * 4.dp.toPx() / h
        val a = atan2(o.y, o.x)
        scaleX = inflate + pull * abs(cos(a) * o.x / size.maxDimension) * (w / h).fastCoerceAtMost(1f)
        scaleY = inflate + pull * abs(sin(a) * o.y / size.maxDimension) * (h / w).fastCoerceAtMost(1f)
    }
}
