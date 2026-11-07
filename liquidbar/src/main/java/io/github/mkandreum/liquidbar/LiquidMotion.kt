package io.github.mkandreum.liquidbar

import androidx.compose.animation.core.Easing
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Curva de un muelle subamortiguado comprimida en una duración fija.
 *
 * Un `spring()` de Compose no tiene duración conocida; la barra necesita que todos sus relojes (anchura,
 * fundidos, posiciones) terminen en el mismo fotograma, así que se anima con `tween(duración, easing = esto)`.
 * La curva es la respuesta al escalón de un oscilador amortiguado: cruza el destino, se pasa un poco y vuelve.
 *
 * [damping] (ζ) decide cuánto se pasa: 0,55 ≈ 12 %, 0,74 ≈ 3 %. [stiffness] es ω·duración: con 7,8 el
 * residuo al final es despreciable y se reparte linealmente para que la curva acabe exactamente en 1.
 */
class SpringEasing(private val damping: Float, private val stiffness: Float = 7.8f) : Easing {
    private val wd = stiffness * sqrt(1f - damping * damping)
    private val k = damping * stiffness / wd
    private val residual = 1f - step(1f)

    private fun step(t: Float): Float = 1f - exp(-damping * stiffness * t) * (cos(wd * t) + k * sin(wd * t))

    override fun transform(fraction: Float): Float =
        if (fraction >= 1f) 1f else step(fraction) + residual * fraction
}

/** Tiempos y curvas de la barra. */
object LiquidMotion {
    /** Plegado (al bajar con el scroll): algo más largo y con más rebote. */
    const val FoldMs = 560
    val FoldEasing = SpringEasing(damping = 0.55f)

    /** Despliegue: más corto, casi sin pasarse. */
    const val UnfoldMs = 470
    val UnfoldEasing = SpringEasing(damping = 0.74f)

    /** Distancia de scroll hacia abajo para plegar, y hacia arriba para desplegar (dp). */
    const val FoldThresholdDp = 50f
    const val UnfoldThresholdDp = 8f
}
