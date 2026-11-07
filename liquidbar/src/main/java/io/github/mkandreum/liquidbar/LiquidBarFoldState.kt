package io.github.mkandreum.liquidbar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs

/** Cuándo se pliega la barra. */
enum class LiquidFoldMode {
    /** Se pliega en cuanto la lista se mueve y solo se abre al volver arriba del todo. */
    ExpandedAtTop,
    /** Se pliega al bajar un trecho y se abre al subir un poco, en cualquier punto de la lista. */
    ByDirection,
}

/**
 * Decide cuándo la barra se pliega. Conéctalo al contenedor de las pantallas con
 * `Modifier.nestedScroll(foldState)`.
 */
@Stable
class LiquidBarFoldState internal constructor(
    private val mode: LiquidFoldMode,
    private val foldPx: Float,
    private val unfoldPx: Float,
    initiallyFolded: Boolean,
) : NestedScrollConnection {

    var folded by mutableStateOf(initiallyFolded)
        private set

    private var run = 0f

    fun unfold() { folded = false; run = 0f }
    fun fold() { folded = true; run = 0f }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (mode != LiquidFoldMode.ByDirection) return Offset.Zero
        val dy = available.y
        if (dy == 0f) return Offset.Zero
        run = if (run == 0f || (run > 0f) == (dy > 0f)) run + dy else dy
        when {
            !folded && run <= -foldPx -> fold()
            folded && run >= unfoldPx -> unfold()
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (mode != LiquidFoldMode.ExpandedAtTop) return Offset.Zero
        // Scroll hacia arriba que la lista ya no puede consumir: estamos arriba del todo.
        if (available.y > TopSlopPx) { if (folded) unfold() }
        else if (abs(consumed.y) > TopSlopPx && !folded) fold()
        return Offset.Zero
    }

    private companion object { const val TopSlopPx = 0.5f }
}

@Composable
fun rememberLiquidBarFoldState(
    mode: LiquidFoldMode = LiquidFoldMode.ExpandedAtTop,
    initiallyFolded: Boolean = false,
    foldThresholdDp: Float = LiquidMotion.FoldThresholdDp,
    unfoldThresholdDp: Float = LiquidMotion.UnfoldThresholdDp,
): LiquidBarFoldState {
    val density = LocalDensity.current.density
    return remember(mode, density, foldThresholdDp, unfoldThresholdDp) {
        LiquidBarFoldState(mode, foldThresholdDp * density, unfoldThresholdDp * density, initiallyFolded)
    }
}
