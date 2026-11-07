/*
 * LiquidBar — barra inferior flotante de cristal líquido para Jetpack Compose.
 *
 * Todo nativo: el cristal (captura, desenfoque, saturación y refracción AGSL) vive en el paquete `glass`.
 */
package io.github.mkandreum.liquidbar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import io.github.mkandreum.liquidbar.glass.GlassBackdrop
import io.github.mkandreum.liquidbar.glass.GlassGlow
import io.github.mkandreum.liquidbar.glass.GlassLook
import io.github.mkandreum.liquidbar.glass.GlassSource
import io.github.mkandreum.liquidbar.glass.LensDrag
import io.github.mkandreum.liquidbar.glass.glassSource
import io.github.mkandreum.liquidbar.glass.glassSurface
import io.github.mkandreum.liquidbar.glass.rememberGlassBackdrop
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/** Una pestaña. */
@Immutable
data class LiquidBarItem(val label: String, val icon: ImageVector)

/**
 * El botón de acción: última celda de la cápsula cuando la barra está abierta y círculo propio a la derecha
 * cuando se pliega. Si pasas [gestures] (por ejemplo un `pointerInput` con pulsación larga) sustituye al toque
 * simple y recibe tú todos los eventos.
 */
@Immutable
data class LiquidBarAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val gestures: Modifier? = null,
    val iconTint: Color = Color.Unspecified,
)

@Immutable
data class LiquidBarColors(
    /** Color de la pestaña activa (icono y etiqueta). */
    val accent: Color,
    /** Iconos y etiquetas en reposo. */
    val content: Color,
    /** Velo del indicador en reposo. */
    val indicator: Color,
)

object LiquidBarDefaults {
    val RowHeight: Dp = 68.dp
    val IconSlot: Dp = 32.dp
    val LabelStyle = TextStyle(fontSize = 10.sp, lineHeight = 12.sp)

    fun darkColors(
        accent: Color = Color(0xFF0A84FF),
        content: Color = Color.White.copy(alpha = 0.82f),
        indicator: Color = Color(0xFF808080).copy(alpha = 0.55f),
    ) = LiquidBarColors(accent, content, indicator)

    fun lightColors(
        accent: Color = Color(0xFF007AFF),
        content: Color = Color(0xFF1C1C1E).copy(alpha = 0.82f),
        indicator: Color = Color(0xFF4A4A4A).copy(alpha = 0.30f),
    ) = LiquidBarColors(accent, content, indicator)
}

/**
 * Barra flotante de cristal líquido.
 *
 * Abierta es una sola cápsula con todas las pestañas y, al final, el botón de acción. La pestaña activa vive
 * bajo un indicador de cristal: se puede arrastrar y, al pulsarlo, crece y refracta como una lupa. Al hacer
 * scroll hacia abajo (si pasas [foldState]) la cápsula se recoge sobre la pestaña activa hasta quedar en un
 * círculo a la izquierda, las etiquetas se desvanecen y la acción se separa en su propio círculo a la derecha;
 * todo con un muelle que se pasa un poco y vuelve. Tocar el círculo plegado la abre de nuevo.
 *
 * [backdrop] es lo que refracta el cristal: crea uno con `rememberGlassBackdrop()` y aplícalo con
 * `Modifier.glassSource(backdrop)` al contenido de la pantalla (hermano de la barra, no su padre).
 */
@Composable
fun LiquidBar(
    backdrop: GlassBackdrop,
    items: List<LiquidBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    foldState: LiquidBarFoldState? = null,
    action: LiquidBarAction? = null,
    colors: LiquidBarColors = LiquidBarDefaults.darkColors(),
    glass: LiquidGlassStyle = LiquidGlassStyle.Dark,
    rowHeight: Dp = LiquidBarDefaults.RowHeight,
    labelStyle: TextStyle = LiquidBarDefaults.LabelStyle,
    /** true: la acción es siempre un círculo propio a la derecha, solo con su icono. */
    detachAction: Boolean = false,
    actionSpacing: Dp = 10.dp,
) {
    val folded = foldState?.folded == true
    val fold = remember { Animatable(if (folded) 1f else 0f) }
    LaunchedEffect(folded) {
        fold.animateTo(
            if (folded) 1f else 0f,
            if (folded) tween(LiquidMotion.FoldMs, easing = LiquidMotion.FoldEasing)
            else tween(LiquidMotion.UnfoldMs, easing = LiquidMotion.UnfoldEasing)
        )
    }
    val scope = rememberCoroutineScope()
    val selectedGlow = remember(scope) { GlassGlow(scope) }
    val actionGlow = remember(scope) { GlassGlow(scope) }
    val surfaceBackdrop = rememberGlassBackdrop()
    val detached = action != null && detachAction
    val cells = items.size + if (action != null && !detached) 1 else 0
    // Opacidad del círculo de la acción: siempre visible si va separada; si no, aparece al plegar.
    val actionAlpha: () -> Float = { if (detached) 1f else fold.value.coerceIn(0f, 1f) }
    val selected = items.getOrNull(selectedIndex)
    val noRipple = remember { MutableInteractionSource() }

    val cell: @Composable (icon: ImageVector, label: String, tint: Color, bold: Boolean, isSelected: Boolean) -> Unit =
        { icon, label, tint, bold, isSelected ->
            Box(Modifier.size(LiquidBarDefaults.IconSlot), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(LiquidBarDefaults.IconSlot * 0.8f).glyphBounce(isSelected, folded))
            }
            Text(
                label, style = labelStyle.copy(fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium),
                color = tint, maxLines = 1
            )
        }

    Layout(
        modifier = modifier.fillMaxWidth().height(rowHeight),
        content = {
            // 0 · el cristal de la cápsula (y del círculo izquierdo cuando se pliega)
            Box(
                Modifier
                    .graphicsLayer(elasticStretch(selectedGlow))
                    .glassSource(surfaceBackdrop)
                    .liquidGlass(backdrop, CircleShape, glass)
                    .then(selectedGlow.drawModifier)
            )
            // 1 · todas las pestañas a su anchura de reposo, recortadas por la cápsula que encoge
            Box(
                Modifier
                    .graphicsLayer { alpha = (1f - fold.value.coerceIn(0f, 1f) * 3f).coerceIn(0f, 1f) }
                    .drawWithContent {
                        val p = fold.value.coerceIn(0f, 1f)
                        if (p == 0f) drawContent()
                        else {
                            val w = lerp(size.width, size.height, p)
                            val clip = Path().apply { addRoundRect(RoundRect(0f, 0f, w, size.height, CornerRadius(size.height / 2f))) }
                            clipPath(clip) { this@drawWithContent.drawContent() }
                        }
                    }
                    .then(if (folded) Modifier.clearAndSetSemantics {} else Modifier)
            ) {
                LiquidTabGroup(
                    backdrop = backdrop,
                    surfaceBackdrop = surfaceBackdrop,
                    tabsCount = items.size,
                    cellsCount = cells,
                    selectedIndex = selectedIndex,
                    onSelect = onSelect,
                    colors = colors,
                    height = rowHeight,
                ) {
                    items.forEachIndexed { i, item ->
                        val sel = i == selectedIndex
                        LiquidCell(onClick = { onSelect(i) }, label = item.label) {
                            cell(item.icon, item.label, if (sel) colors.accent else colors.content, sel, sel)
                        }
                    }
                    if (action != null && !detached) {
                        LiquidCell(onClick = action.onClick, label = action.label, gestures = action.gestures) {
                            cell(action.icon, action.label, if (action.iconTint != Color.Unspecified) action.iconTint else colors.content, false, false)
                        }
                    }
                }
            }
            // 2 · la pestaña activa sola, mientras está plegada
            Box(
                Modifier
                    .graphicsLayer(elasticStretch(selectedGlow))
                    .graphicsLayer { alpha = fold.value.coerceIn(0f, 1f) }
                    .then(
                        if (folded) Modifier.then(selectedGlow.gestureModifier)
                            .clickable(noRipple, null, onClickLabel = "Mostrar pestañas") { foldState?.unfold() }
                        else Modifier.clearAndSetSemantics {}
                    )
            ) {
                if (selected != null) FoldingCell(titleFraction = { 1f - fold.value.coerceIn(0f, 1f) }) {
                    cell(selected.icon, selected.label, colors.accent, true, true)
                }
            }
            // 3 · cristal propio de la acción, solo separada
            Box(
                Modifier
                    .graphicsLayer(elasticStretch(actionGlow))
                    .graphicsLayer { alpha = actionAlpha() }
                    .then(if (action != null) Modifier.liquidGlass(backdrop, CircleShape, glass) else Modifier)
                    .then(actionGlow.drawModifier)
            )
            // 4 · el control de la acción separada
            Box(
                Modifier
                    .graphicsLayer(elasticStretch(actionGlow))
                    .graphicsLayer { alpha = actionAlpha() }
                    .then(
                        if ((folded || detached) && action != null) Modifier.then(actionGlow.gestureModifier)
                            .then(action.gestures ?: Modifier.clickable(noRipple, null, onClickLabel = action.label, role = Role.Button, onClick = action.onClick))
                        else Modifier.clearAndSetSemantics {}
                    )
            ) {
                if (action != null) {
                    val tint = if (action.iconTint != Color.Unspecified) action.iconTint else colors.content
                    if (detached) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(action.icon, action.label, tint = tint, modifier = Modifier.size(LiquidBarDefaults.IconSlot * 0.9f).glyphBounce(false, folded))
                    } else FoldingCell(titleFraction = { 1f - fold.value.coerceIn(0f, 1f) }) {
                        cell(action.icon, action.label, tint, false, false)
                    }
                }
            }
        }
    ) { m, constraints ->
        val width = constraints.maxWidth
        val height = rowHeight.roundToPx()
        val circle = height.coerceAtMost(width)
        val inset = 4.dp.toPx()
        // Con la acción separada, la cápsula abierta deja sitio al círculo de la derecha.
        val openWidth = if (detached) (width - circle - actionSpacing.roundToPx()).coerceAtLeast(circle) else width
        val tabWidth = (openWidth - 2 * inset) / cells.coerceAtLeast(1)
        val p = fold.value
        val navWidth = lerp(openWidth.toFloat(), circle.toFloat(), p).roundToInt().coerceIn(circle, openWidth)
        val selectedX = lerp(inset + tabWidth * (selectedIndex + 0.5f) - circle / 2f, 0f, p).roundToInt()
        val actionX = if (detached) width - circle
        else lerp(inset + tabWidth * (cells - 0.5f) - circle / 2f, (width - circle).toFloat(), p).roundToInt()

        val surface = m[0].measure(Constraints.fixed(navWidth, height))
        val group = m[1].measure(Constraints.fixed(openWidth, height))
        val single = m[2].measure(Constraints.fixed(circle, height))
        val actionSurface = m[3].measure(Constraints.fixed(circle, height))
        val actionControl = m[4].measure(Constraints.fixed(circle, height))
        layout(width, height) {
            surface.placeRelative(0, 0)
            group.placeRelative(0, 0)
            single.placeRelative(selectedX, 0)
            if (action != null) {
                actionSurface.placeRelative(actionX, 0)
                actionControl.placeRelative(actionX, 0)
            }
        }
    }
}

/** Icono centrado que, a medida que [titleFraction] llega a 1, sube para dejar sitio a su etiqueta. */
@Composable
private fun FoldingCell(titleFraction: () -> Float, content: @Composable () -> Unit) {
    Layout(content = content) { m, c ->
        val icon = m[0].measure(Constraints.fixed(LiquidBarDefaults.IconSlot.roundToPx(), LiquidBarDefaults.IconSlot.roundToPx()))
        val title = m.getOrNull(1)?.measure(Constraints(maxWidth = c.maxWidth))
        val gap = 2.dp.roundToPx()
        val f = titleFraction()
        val centred = (c.maxHeight - icon.height) / 2f
        val stacked = (c.maxHeight - icon.height - gap - (title?.height ?: 0)) / 2f
        val top = lerp(centred, stacked, f).roundToInt()
        layout(c.maxWidth, c.maxHeight) {
            icon.placeRelative((c.maxWidth - icon.width) / 2, top)
            title?.placeRelativeWithLayer((c.maxWidth - title.width) / 2, top + icon.height + gap) { alpha = f }
        }
    }
}

/**
 * Rebote del glifo al aterrizar en una pestaña, al aparecer y cada vez que la barra se pliega o abre:
 * se encoge un instante y vuelve con un muelle elástico.
 */
@Composable
private fun Modifier.glyphBounce(selected: Boolean, replay: Boolean): Modifier {
    val scale = remember { Animatable(1f) }
    var last by remember { mutableStateOf<Pair<Boolean, Boolean>?>(null) }
    LaunchedEffect(selected, replay) {
        val before = last
        last = selected to replay
        val play = before == null || (selected && !before.first) || before.second != replay
        if (play) {
            scale.animateTo(0.8f, tween(90, easing = FastOutSlowInEasing))
            scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 520f))
        }
    }
    return graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}

internal val LocalCellScale = staticCompositionLocalOf { { 1f } }

/** Celda de la cápsula: icono sobre etiqueta, que crece con el indicador mientras se pulsa. */
@Composable
private fun RowScope.LiquidCell(
    onClick: () -> Unit,
    label: String,
    gestures: Modifier? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scale = LocalCellScale.current
    Column(
        Modifier
            .clip(CircleShape)
            .then(gestures ?: Modifier.clickable(interactionSource = null, indication = null, role = Role.Tab, onClickLabel = label, onClick = onClick))
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer { val s = scale(); scaleX = s; scaleY = s },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

/**
 * Las pestañas de la barra abierta con el indicador líquido. No dibuja cristal propio: la cápsula la pone
 * [LiquidBar]. Mientras se pulsa, el indicador también refracta esa misma cápsula ([surfaceBackdrop]),
 * fundida con la presión para que no salte al soltar.
 */
@Composable
private fun LiquidTabGroup(
    backdrop: GlassBackdrop,
    surfaceBackdrop: GlassBackdrop,
    tabsCount: Int,
    cellsCount: Int,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    colors: LiquidBarColors,
    height: Dp,
    content: @Composable RowScope.() -> Unit,
) {
    val tabsBackdrop = rememberGlassBackdrop()
    val selected by rememberUpdatedState(selectedIndex)
    val select by rememberUpdatedState(onSelect)
    val inner = height - 8.dp

    BoxWithConstraints(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.CenterStart) {
        val density = LocalDensity.current
        val tabWidth = with(density) { (constraints.maxWidth.toFloat() - 8.dp.toPx()) / cellsCount }
        val lastTab = (tabsCount - 1).coerceAtLeast(0)
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val dir = if (isLtr) 1f else -1f

        // Arrastrar tira de toda la fila unos dp hacia el dedo.
        val pull = remember { Animatable(0f) }
        val rowShift by remember(density) {
            derivedStateOf {
                val f = (pull.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) { 4.dp.toPx() * f.sign * EaseOut.transform(abs(f)) }
            }
        }

        val scope = rememberCoroutineScope()
        var current by remember { mutableIntStateOf(selectedIndex) }
        val drag = remember(scope, tabsCount, cellsCount, tabWidth) {
            LensDrag(
                scope = scope,
                initial = selectedIndex.toFloat(),
                range = 0f..lastTab.toFloat(),
                pressedScale = 78f / 56f,
                onDrag = { dx ->
                    follow(targetValue + dx / tabWidth * dir)
                    scope.launch { pull.snapTo(pull.value + dx) }
                },
                onRelease = {
                    val target = targetValue.fastRoundToInt().fastCoerceIn(0, lastTab)
                    if (target == current) select(target)
                    current = target
                    travelTo(target.toFloat())
                    scope.launch { pull.animateTo(0f, spring(1f, 300f, 0.5f)) }
                }
            )
        }
        LaunchedEffect(Unit) { snapshotFlow { selected }.collectLatest { current = it } }
        LaunchedEffect(drag) {
            snapshotFlow { current }.drop(1).collectLatest { i ->
                drag.travelTo(i.toFloat())
                if (i != selected) select(i)
            }
        }

        val glow = remember(scope, drag) {
            GlassGlow(scope) { size, _ ->
                Offset(
                    if (isLtr) (drag.value + 0.5f) * tabWidth + rowShift else size.width - (drag.value + 0.5f) * tabWidth + rowShift,
                    size.height / 2f
                )
            }
        }

        Row(
            Modifier
                .graphicsLayer { translationX = rowShift }
                .clip(CircleShape)
                .then(glow.drawModifier)
                .height(height)
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )

        // Las pestañas otra vez, teñidas e invisibles: lo que el indicador deja ver de la activa.
        CompositionLocalProvider(LocalCellScale provides { lerp(1f, 1.2f, drag.pressProgress) }) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .glassSource(tabsBackdrop)
                    .graphicsLayer { translationX = rowShift }
                    .height(inner)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(colors.accent)),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }

        val place = Modifier
            .padding(horizontal = 4.dp)
            .graphicsLayer {
                translationX = if (isLtr) drag.value * tabWidth + rowShift else size.width - (drag.value + 1f) * tabWidth + rowShift
            }
        // El indicador crece de verdad (no se escala) para que su lente tenga más superficie que refractar,
        // y se deforma con la velocidad: más ancho y bajo al acelerar.
        val grow = Modifier.layout { measurable, c ->
            val v = drag.velocity / 10f
            val sx = drag.scaleX / (1f - (v * 0.75f).fastCoerceIn(-0.2f, 0.2f))
            val sy = drag.scaleY * (1f - (v * 0.25f).fastCoerceIn(-0.2f, 0.2f))
            val w = (c.maxWidth * sx).roundToInt().coerceAtLeast(1)
            val h = (c.maxHeight * sy).roundToInt().coerceAtLeast(1)
            val child = measurable.measure(Constraints.fixed(w, h))
            layout(c.maxWidth, c.maxHeight) { child.placeRelative((c.maxWidth - w) / 2, (c.maxHeight - h) / 2) }
        }
        val visible by animateFloatAsState(if (selectedIndex in 0..lastTab) 1f else 0f, tween(220), label = "indicator")

        val screenOnly = remember(backdrop) { listOf(GlassSource(backdrop)) }
        val lensSources: () -> List<GlassSource> = {
            val p = drag.pressProgress.coerceIn(0f, 1f)
            if (p <= 0f) screenOnly else listOf(GlassSource(backdrop), GlassSource(surfaceBackdrop, p))
        }
        val tabsSources = remember(tabsBackdrop) { listOf(GlassSource(tabsBackdrop)) }

        // 1 · el cristal del indicador: escarcha en reposo, lupa con arcoíris al pulsar.
        Box(
            place.graphicsLayer { alpha = visible }
                .then(glow.gestureModifier)
                .then(drag.modifier)
                .height(inner)
                .fillMaxWidth(1f / cellsCount)
                .then(grow)
                .glassSurface(lensSources, CircleShape) {
                    val p = drag.pressProgress.coerceIn(0f, 1f)
                    GlassLook(
                        blur = 12.dp.toPx() * (1f - p),
                        saturation = 1.5f,
                        refractionHeight = 10.dp.toPx() * p,
                        refractionAmount = 14.dp.toPx() * p,
                        dispersion = 0.5f,
                        depthEffect = true,
                        surface = colors.indicator.copy(alpha = colors.indicator.alpha * (1f - p)).compositeOverOrSelf(Color.Black.copy(alpha = 0.03f * p)),
                        rim = Color.White.copy(alpha = 0.16f + 0.34f * p),
                        rimWidth = 0.5.dp.toPx(),
                        shadowAlpha = p,
                        innerShadowAlpha = p,
                        innerShadowRadius = 8.dp.toPx() * p,
                        fallback = colors.indicator,
                    )
                }
        )
        // 2 · la pestaña activa teñida, vista a través de la misma lente.
        Box(
            place.graphicsLayer { alpha = visible }
                .height(inner)
                .fillMaxWidth(1f / cellsCount)
                .then(grow)
                .glassSurface({ tabsSources }, CircleShape) {
                    val p = drag.pressProgress.coerceIn(0f, 1f)
                    GlassLook(
                        refractionHeight = 10.dp.toPx() * p,
                        refractionAmount = 14.dp.toPx() * p,
                        dispersion = 0.5f,
                        depthEffect = true,
                        fallback = Color.Transparent,
                    )
                }
        )
    }
}

private fun Color.compositeOverOrSelf(under: Color): Color =
    if (under.alpha <= 0f) this else this.compositeOver(under)
