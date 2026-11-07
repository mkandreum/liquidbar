package io.github.mkandreum.liquidbar.glass

import android.graphics.BlurMaskFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/** Aspecto de una superficie de cristal en un fotograma. Todo en píxeles. */
@Immutable
data class GlassLook(
    val blur: Float = 0f,
    val saturation: Float = 1f,
    /** Anchura de la franja del canto que refracta. */
    val refractionHeight: Float = 0f,
    /** Cuánto se desplaza el fondo en el filo exterior de esa franja. */
    val refractionAmount: Float = 0f,
    /** Separación de canales en la refracción (0 = sin arcoíris). */
    val dispersion: Float = 0f,
    val surface: Color = Color.Transparent,
    val rim: Color = Color.Transparent,
    val rimWidth: Float = 0f,
    val darkRimAlpha: Float = 0f,
    val darkRimWidth: Float = 0f,
    /** Ángulo de la luz, en grados (45 = desde arriba a la izquierda). */
    val lightAngle: Float = 45f,
    val shadowAlpha: Float = 0f,
    val innerShadowAlpha: Float = 0f,
    val innerShadowRadius: Float = 0f,
    /** Relleno cuando el sistema no puede desenfocar (Android 11 o anterior). */
    val fallback: Color = Color(0xEB1C1C1E),
)

/** Android 12 desenfoca; Android 13 además ejecuta shaders (la refracción). */
val glassBlurSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
val glassRefractionSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/**
 * Pinta una superficie de cristal con la forma [shape]: el fondo de [sources] desenfocado, saturado y
 * refractado en el canto, un velo, el filo de luz y el contorno oscuro; después el contenido del elemento.
 * [look] se lee en la fase de dibujo, así que puede depender de animaciones sin recomponer nada.
 */
fun Modifier.glassSurface(
    sources: () -> List<GlassSource>,
    shape: Shape,
    look: Density.() -> GlassLook,
): Modifier = this then GlassElement(sources, shape, look)

private class GlassElement(
    val sources: () -> List<GlassSource>,
    val shape: Shape,
    val look: Density.() -> GlassLook,
) : ModifierNodeElement<GlassNode>() {
    override fun create() = GlassNode(sources, shape, look)
    override fun update(node: GlassNode) {
        node.sources = sources; node.shape = shape; node.look = look
        node.invalidateDraw()
    }
    override fun InspectorInfo.inspectableProperties() { name = "glassSurface" }
    override fun equals(other: Any?) =
        other is GlassElement && other.sources === sources && other.shape == shape && other.look === look
    override fun hashCode() = (sources.hashCode() * 31 + shape.hashCode()) * 31 + look.hashCode()
}

private class GlassNode(
    var sources: () -> List<GlassSource>,
    var shape: Shape,
    var look: Density.() -> GlassLook,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {

    private var layer: GraphicsLayer? = null
    private var coordinates: LayoutCoordinates? = null
    private var shader: Any? = null // RuntimeShader solo existe en Android 13+
    private val sourcePaint = Paint()

    override fun onAttach() {
        layer = requireGraphicsContext().createGraphicsLayer()
    }

    override fun onDetach() {
        layer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        layer = null
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
        invalidateDraw()
    }

    override fun ContentDrawScope.draw() {
        val l = look(this)
        val outline = shape.createOutline(size, layoutDirection, this)
        if (l.shadowAlpha > 0f) drawDropShadow(outline, l.shadowAlpha)

        val glassLayer = layer
        val me = coordinates
        if (!glassBlurSupported || glassLayer == null || me == null || !me.isAttached) {
            drawOutline(outline, l.fallback)
        } else {
            val pad = ceil(l.blur * 2f + l.refractionAmount + 2f)
            val w = (size.width + pad * 2).toInt().coerceAtLeast(1)
            val h = (size.height + pad * 2).toInt().coerceAtLeast(1)
            glassLayer.record(IntSize(w, h)) {
                for (s in sources()) {
                    val src = s.backdrop
                    val srcCoords = src.coordinates ?: continue
                    if (!srcCoords.isAttached || s.alpha <= 0f) continue
                    val origin = srcCoords.localPositionOf(me, Offset.Zero)
                    translate(pad - origin.x, pad - origin.y) {
                        if (s.alpha >= 1f) drawLayer(src.layer)
                        else drawIntoCanvas { c ->
                            sourcePaint.alpha = s.alpha
                            c.saveLayer(Rect(origin.x - pad, origin.y - pad, origin.x - pad + w, origin.y - pad + h), sourcePaint)
                            drawLayer(src.layer)
                            c.restore()
                        }
                    }
                }
            }
            glassLayer.renderEffect = buildEffect(l, w, h, pad)
            val clip = Path().apply { addOutline(outline) }
            clipPath(clip) {
                translate(-pad, -pad) { drawLayer(glassLayer) }
                if (l.surface.alpha > 0f) drawRect(l.surface)
            }
        }
        if (l.innerShadowAlpha > 0f && l.innerShadowRadius > 0f) drawInnerShadow(outline, l.innerShadowRadius, l.innerShadowAlpha)
        if (l.rim.alpha > 0f && l.rimWidth > 0f) drawRim(outline, l.rim, l.rimWidth, l.lightAngle, light = true)
        if (l.darkRimAlpha > 0f && l.darkRimWidth > 0f) drawRim(outline, Color.Black.copy(alpha = l.darkRimAlpha), l.darkRimWidth, l.lightAngle + 90f, light = false)
        drawContent()
    }

    private fun DrawScope.buildEffect(l: GlassLook, w: Int, h: Int, pad: Float): androidx.compose.ui.graphics.RenderEffect? {
        var effect: RenderEffect? = null
        if (l.blur > 0.5f) effect = RenderEffect.createBlurEffect(l.blur, l.blur, Shader.TileMode.CLAMP)
        if (l.saturation != 1f) {
            val filter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(l.saturation) })
            effect = if (effect == null) RenderEffect.createColorFilterEffect(filter) else RenderEffect.createColorFilterEffect(filter, effect)
        }
        if (glassRefractionSupported && l.refractionHeight > 0.5f && l.refractionAmount > 0.5f) {
            val refraction = refraction(l, pad)
            effect = if (effect == null) refraction else RenderEffect.createChainEffect(refraction, effect)
        }
        return effect?.asComposeRenderEffect()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun DrawScope.refraction(l: GlassLook, pad: Float): RenderEffect {
        val s = (shader as? RuntimeShader) ?: RuntimeShader(RefractionAgsl).also { shader = it }
        val r = cornerRadii()
        // El canto se limita a una fracción del lado corto: en superficies pequeñas (una píldora de 48 dp)
        // una franja absoluta de 28,8 dp se come el centro y arrastra el contenido de fuera como un fantasma.
        val band = l.refractionHeight.coerceAtMost(size.minDimension * 0.16f)
        val amount = l.refractionAmount.coerceAtMost(size.minDimension * 0.20f)
        s.setFloatUniform("size", size.width, size.height)
        s.setFloatUniform("pad", pad)
        s.setFloatUniform("radii", r[0], r[1], r[2], r[3])
        s.setFloatUniform("band", band)
        s.setFloatUniform("amount", amount)
        s.setFloatUniform("dispersion", l.dispersion)
        return RenderEffect.createRuntimeShaderEffect(s, "content")
    }

    /** Radios de las esquinas (sup. izq., sup. der., inf. der., inf. izq.), limitados a media altura. */
    private fun DrawScope.cornerRadii(): FloatArray {
        val sh = shape as? CornerBasedShape ?: return floatArrayOf(0f, 0f, 0f, 0f)
        val max = size.minDimension / 2f
        return floatArrayOf(
            sh.topStart.toPx(size, this).coerceAtMost(max),
            sh.topEnd.toPx(size, this).coerceAtMost(max),
            sh.bottomEnd.toPx(size, this).coerceAtMost(max),
            sh.bottomStart.toPx(size, this).coerceAtMost(max),
        )
    }
}

/**
 * Refracción del canto. Dentro de la forma, a más de `band` del borde, el fondo pasa tal cual. En la franja
 * del borde se muestrea más hacia fuera siguiendo la normal de la forma, con un perfil circular (como el
 * canto pulido de un vidrio): casi nada al empezar la franja y `amount` en el filo. Con `dispersion` cada
 * canal se desvía un poco distinto y aparece un leve arcoíris.
 */
private const val RefractionAgsl = """
uniform shader content;
uniform float2 size;
uniform float pad;
uniform float4 radii;
uniform float band;
uniform float amount;
uniform float dispersion;

float cornerFor(float2 p) {
    if (p.x < 0.0) return p.y < 0.0 ? radii.x : radii.w;
    return p.y < 0.0 ? radii.y : radii.z;
}

float shapeDistance(float2 p) {
    float r = cornerFor(p);
    float2 q = abs(p) - size * 0.5 + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

half4 main(float2 coord) {
    float2 p = coord - pad - size * 0.5;
    float d = shapeDistance(p);
    if (d > 0.0 || -d >= band) return content.eval(coord);
    float t = 1.0 - (-d / band);
    float bend = (1.0 - sqrt(max(1.0 - t * t, 0.0))) * amount;
    float e = 0.75;
    float2 n = float2(
        shapeDistance(p + float2(e, 0.0)) - shapeDistance(p - float2(e, 0.0)),
        shapeDistance(p + float2(0.0, e)) - shapeDistance(p - float2(0.0, e))
    );
    float len = length(n);
    n = len > 0.0001 ? n / len : float2(0.0);
    float2 refracted = coord + n * bend;
    if (dispersion <= 0.0) return content.eval(refracted);
    // Espectro: cinco muestras repartidas a lo largo de la normal. La separación crece hacia las
    // diagonales y cambia de signo por cuadrante (n.x·n.y), así rojo y azul se cruzan en cada esquina.
    float2 spread = n * bend * dispersion * clamp(2.0 * n.x * n.y, -1.0, 1.0);
    half4 c0 = content.eval(refracted + spread);
    half4 c1 = content.eval(refracted + spread * 0.5);
    half4 c2 = content.eval(refracted);
    half4 c3 = content.eval(refracted - spread * 0.5);
    half4 c4 = content.eval(refracted - spread);
    return half4(
        c0.r * 0.5 + c1.r * 0.5,
        c1.g * 0.3 + c2.g * 0.4 + c3.g * 0.3,
        c3.b * 0.5 + c4.b * 0.5,
        c2.a
    );
}
"""

/** Filo de la luz o de la sombra: más intenso en las dos esquinas del eje [angle], nulo en el centro. */
private fun DrawScope.drawRim(outline: Outline, color: Color, width: Float, angle: Float, light: Boolean) {
    val rad = Math.toRadians(angle.toDouble())
    val reach = size.maxDimension / 2f
    val dir = Offset(cos(rad).toFloat(), sin(rad).toFloat()) * reach
    val c = Offset(size.width / 2f, size.height / 2f)
    val mid = if (light) 0.42f else 0.35f
    val brush = Brush.linearGradient(
        0f to color, mid to Color.Transparent, 1f - mid to Color.Transparent, 1f to color.copy(alpha = color.alpha * if (light) 0.6f else 1f),
        start = c - dir, end = c + dir
    )
    drawOutline(outline, brush, style = Stroke(width))
}

private fun DrawScope.drawDropShadow(outline: Outline, alpha: Float) {
    val radius = 24f * density
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            color = Color.Black.copy(alpha = 0.1f * alpha)
            asFrameworkPaint().maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.save()
        canvas.translate(0f, radius / 6f)
        canvas.drawOutline(outline, paint)
        canvas.restore()
    }
}

private fun DrawScope.drawInnerShadow(outline: Outline, radius: Float, alpha: Float) {
    val clip = Path().apply { addOutline(outline) }
    clipPath(clip) {
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                color = Color.Black.copy(alpha = 0.15f * alpha)
                style = androidx.compose.ui.graphics.PaintingStyle.Stroke
                strokeWidth = radius * 2f
                asFrameworkPaint().maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.save()
            canvas.translate(0f, radius)
            canvas.drawOutline(outline, paint)
            canvas.restore()
        }
    }
}

@Suppress("unused")
private fun Color.argb() = toArgb()
