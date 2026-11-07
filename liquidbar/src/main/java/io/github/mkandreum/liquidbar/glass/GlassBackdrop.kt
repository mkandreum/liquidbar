package io.github.mkandreum.liquidbar.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Lo que hay detrás del cristal. El contenido marcado con [glassSource] se graba en una capa de render en
 * cada fotograma; cualquier superficie de cristal la vuelve a pintar (desenfocada y refractada) recortada a
 * su forma y desplazada a su posición. Como la capa es un nodo de render compartido, el scroll del
 * contenido se ve a través del cristal sin volver a grabar nada en él.
 */
@Stable
class GlassBackdrop internal constructor(internal val layer: GraphicsLayer) {
    internal var coordinates: LayoutCoordinates? = null
}

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

/** Graba lo que dibuja este elemento (y sus hijos) en [backdrop], y lo pinta con normalidad. */
fun Modifier.glassSource(backdrop: GlassBackdrop): Modifier = this
    .onGloballyPositioned { backdrop.coordinates = it }
    .drawWithContent {
        backdrop.layer.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop.layer)
    }

/** Una fuente que una superficie de cristal refracta, con su opacidad (para fundir fuentes extra). */
class GlassSource(val backdrop: GlassBackdrop, val alpha: Float = 1f)
