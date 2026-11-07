package io.github.mkandreum.liquidbar.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow
import kotlin.math.abs

/**
 * Brillo de cristal bajo el dedo: al tocar, la pieza se aclara un poco y una luz suave sigue al dedo.
 * [offset] (desplazamiento desde donde empezó el toque) también alimenta el estiramiento elástico.
 * No consume eventos: los toques siguen llegando a los `clickable` de debajo.
 */
class GlassGlow(
    private val scope: CoroutineScope,
    /** Dónde centrar la luz; por defecto, bajo el dedo. */
    private val anchor: (size: Size, finger: Offset) -> Offset = { _, finger -> finger },
) {
    private val press = Animatable(0f, 0.001f)
    private val finger = Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)
    private var start = Offset.Zero

    val pressProgress: Float get() = press.value
    val offset: Offset get() = finger.value - start

    val drawModifier: Modifier = Modifier.drawWithContent {
        val p = press.value
        if (p > 0f) {
            drawRect(Color.White.copy(alpha = 0.08f * p), blendMode = BlendMode.Plus)
            val c = anchor(size, finger.value).let { Offset(it.x.coerceIn(0f, size.width), it.y.coerceIn(0f, size.height)) }
            val radius = size.minDimension * 1.5f
            drawRect(
                Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.15f * p), 0.5f to Color.White.copy(alpha = 0.15f * p), 1f to Color.Transparent,
                    center = c, radius = radius
                ),
                blendMode = BlendMode.Plus
            )
        }
        drawContent()
    }

    val gestureModifier: Modifier = Modifier.pointerInput(this) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            start = down.position
            scope.launch { press.animateTo(1f, spring(0.5f, 300f, 0.001f)) }
            scope.launch { finger.snapTo(start) }
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                scope.launch { finger.snapTo(change.position) }
            }
            scope.launch { press.animateTo(0f, spring(0.5f, 300f, 0.001f)) }
            scope.launch { finger.animateTo(start, spring(0.5f, 300f, Offset.VisibilityThreshold)) }
        }
    }
}

/**
 * El indicador arrastrable: un valor continuo (la pestaña bajo él, con decimales mientras se arrastra) que
 * persigue al dedo con un muelle rígido, más la presión y la escala del cristal, cada una con su muelle.
 * La velocidad del valor se mide para deformar la lente (se ensancha al acelerar).
 */
internal class LensDrag(
    private val scope: CoroutineScope,
    initial: Float,
    private val range: ClosedFloatingPointRange<Float>,
    private val pressedScale: Float,
    private val onDrag: LensDrag.(dx: Float) -> Unit,
    private val onRelease: LensDrag.() -> Unit,
) {
    private val valueAnim = Animatable(initial, 0.001f)
    private val velocityAnim = Animatable(0f, 5f)
    private val pressAnim = Animatable(0f, 0.001f)
    private val scaleXAnim = Animatable(1f, 0.001f)
    private val scaleYAnim = Animatable(1f, 0.001f)
    private val tracker = VelocityTracker()
    private var releaseJob: Job? = null

    val value: Float get() = valueAnim.value
    val targetValue: Float get() = valueAnim.targetValue
    val pressProgress: Float get() = pressAnim.value
    val scaleX: Float get() = scaleXAnim.value
    val scaleY: Float get() = scaleYAnim.value
    val velocity: Float get() = velocityAnim.value

    val modifier: Modifier = Modifier.pointerInput(this) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            press()
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val dx = change.positionChange().x
                if (dx != 0f) { onDrag(dx); change.consume() }
            }
            onRelease()
            release()
        }
    }

    fun press() {
        releaseJob?.cancel()
        tracker.resetTracking()
        scope.launch { pressAnim.animateTo(1f, spring(1f, 1000f, 0.001f)) }
        scope.launch { scaleXAnim.animateTo(pressedScale, spring(0.6f, 250f, 0.001f)) }
        scope.launch { scaleYAnim.animateTo(pressedScale, spring(0.7f, 250f, 0.001f)) }
    }

    /** Suelta el cristal cuando el valor ya casi ha llegado a su destino, para que la lupa se vea viajar. */
    fun release() {
        releaseJob?.cancel()
        releaseJob = scope.launch {
            awaitFrame()
            val near = (range.endInclusive - range.start).coerceAtLeast(1f) * 0.025f
            if (abs(valueAnim.value - valueAnim.targetValue) >= near) {
                snapshotFlow { abs(valueAnim.value - valueAnim.targetValue) < near }.first { it }
            }
            launch { pressAnim.animateTo(0f, spring(1f, 1000f, 0.001f)) }
            launch { scaleXAnim.animateTo(1f, spring(0.6f, 250f, 0.001f)) }
            launch { scaleYAnim.animateTo(1f, spring(0.7f, 250f, 0.001f)) }
        }
    }

    fun follow(target: Float) {
        val t = target.coerceIn(range)
        scope.launch {
            valueAnim.animateTo(t, spring(1f, 1000f, 0.001f)) { trackVelocity() }
        }
    }

    /** Viaje programático (al tocar otra pestaña): presiona, viaja y suelta. */
    fun travelTo(target: Float) {
        press()
        val t = target.coerceIn(range)
        scope.launch {
            valueAnim.animateTo(t, spring(1f, 1000f, 0.001f))
            if (velocityAnim.value != 0f) velocityAnim.animateTo(0f, spring(0.5f, 300f, 0.05f))
        }
        release()
    }

    private fun trackVelocity() {
        tracker.addPosition(System.currentTimeMillis(), Offset(valueAnim.value, 0f))
        val span = (range.endInclusive - range.start).coerceAtLeast(1f)
        val v = tracker.calculateVelocity().x / span
        scope.launch { velocityAnim.animateTo(v, spring(0.5f, 300f, 0.05f)) }
    }
}
