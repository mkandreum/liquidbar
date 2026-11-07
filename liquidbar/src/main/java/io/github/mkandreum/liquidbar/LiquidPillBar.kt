package io.github.mkandreum.liquidbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import io.github.mkandreum.liquidbar.glass.GlassBackdrop
import io.github.mkandreum.liquidbar.glass.GlassGlow
import io.github.mkandreum.liquidbar.glass.GlassLook
import io.github.mkandreum.liquidbar.glass.GlassSource
import io.github.mkandreum.liquidbar.glass.glassSource
import io.github.mkandreum.liquidbar.glass.glassSurface
import io.github.mkandreum.liquidbar.glass.rememberGlassBackdrop
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Immutable
data class LiquidPillColors(
    /** Relleno de la cápsula activa en reposo. */
    val accent: Color,
    /** Icono y etiqueta sobre la cápsula activa. */
    val onAccent: Color,
    /** Iconos en reposo. */
    val content: Color,
)

object LiquidPillDefaults {
    val TabHeight: Dp = 48.dp
    val LabelStyle = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    fun colors(
        accent: Color = Color(0xFF0A84FF),
        onAccent: Color = Color.White,
        content: Color = Color.White.copy(alpha = 0.72f),
    ) = LiquidPillColors(accent, onAccent, content)
}

/**
 * Barra fina en píldora: pestañas de icono que, activas, se expanden en cápsula con su etiqueta, y un hueco
 * central opcional ([center], p. ej. un botón +). La cápsula activa es una lente de cristal: al tocar otra
 * pestaña viaja con un muelle que se pasa un poco y vuelve, estirándose con la velocidad; mantenida pulsada
 * crece, se vuelve transparente y refracta (con aberración cromática) el fondo y los iconos que pisa; y se
 * puede arrastrar de una pestaña a otra: al soltar se asienta en la más cercana.
 *
 * Las pestañas se reparten mitad a la izquierda del hueco central y mitad a la derecha.
 */
@Composable
fun LiquidPillBar(
    backdrop: GlassBackdrop,
    items: List<LiquidBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    colors: LiquidPillColors = LiquidPillDefaults.colors(),
    glass: LiquidGlassStyle = LiquidGlassStyle.Dark,
    labelStyle: TextStyle = LiquidPillDefaults.LabelStyle,
    center: (@Composable () -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val select by rememberUpdatedState(onSelect)
    val glow = remember(scope) { GlassGlow(scope) }
    val surfaceBackdrop = rememberGlassBackdrop()
    val tabsBackdrop = rememberGlassBackdrop()
    val half = items.size / 2

    // Posición y anchura de cada pestaña dentro de la fila (px), medidas en cada fotograma.
    val lefts = remember(items.size) { mutableStateListOf(*Array(items.size) { 0f }) }
    val widths = remember(items.size) { mutableStateListOf(*Array(items.size) { 0f }) }
    var centerWidth by remember { mutableIntStateOf(0) }
    var measured by remember { mutableStateOf(false) }

    // La lente: centro x, anchura, presión. Muelles con algo de rebote; la presión, rígida.
    val x = remember { Animatable(0f) }
    val w = remember { Animatable(0f) }
    val press = remember { Animatable(0f, 0.001f) }
    var dragging by remember { mutableStateOf(false) }
    var hovered by remember { mutableIntStateOf(selectedIndex) }
    val travel = spring<Float>(dampingRatio = 0.62f, stiffness = 420f)
    val stretch = spring<Float>(dampingRatio = 0.5f, stiffness = 420f)

    fun centreOf(i: Int) = lefts[i] + widths[i] / 2f
    fun nearestTo(cx: Float) = items.indices.minByOrNull { abs(centreOf(it) - cx) } ?: 0

    // Seguir a la pestaña activa (su anchura cambia mientras la etiqueta se despliega).
    LaunchedEffect(selectedIndex, items.size) {
        var first = !measured
        snapshotFlow { Triple(selectedIndex, centreOf(selectedIndex.coerceIn(items.indices)), widths[selectedIndex.coerceIn(items.indices)]) }
            .collect { (_, cx, tw) ->
                if (tw <= 0f || dragging) return@collect
                if (first) { x.snapTo(cx); w.snapTo(tw); first = false; measured = true }
                else {
                    launch { x.animateTo(cx, travel) }
                    launch { w.animateTo(tw, stretch) }
                }
            }
    }
    // La lente está «pulsada» mientras se arrastra o mientras viaja hacia su pestaña; al llegar, se suelta.
    val settleSlop = with(density) { 8.dp.toPx() }
    val currentSelected by rememberUpdatedState(selectedIndex)
    val holding by remember {
        derivedStateOf {
            val i = currentSelected.coerceIn(items.indices)
            // Solo cuenta como viaje si la lente se está moviendo: nunca se queda en lupa estando quieta.
            dragging || (measured && x.isRunning && abs(x.value - centreOf(i)) > settleSlop)
        }
    }
    // Salvaguarda: quieta, sin arrastre y fuera de su sitio (p. ej. la etiqueta cambió de ancho) → a su pestaña.
    LaunchedEffect(Unit) {
        snapshotFlow {
            val i = currentSelected.coerceIn(items.indices)
            !dragging && measured && !x.isRunning && abs(x.value - centreOf(i)) > 1f
        }.collect { off ->
            if (off) {
                val i = currentSelected.coerceIn(items.indices)
                launch { x.animateTo(centreOf(i), travel) }
                launch { w.animateTo(widths[i], stretch) }
            }
        }
    }
    // Un único observador permanente: siempre aplica el último estado, sin depender de recomponer.
    LaunchedEffect(Unit) {
        snapshotFlow { holding }.collectLatest { h ->
            press.animateTo(if (h) 1f else 0f, if (h) spring(1f, 1000f, 0.001f) else spring(1f, 600f, 0.001f))
        }
    }

    val lens = Modifier.pointerInput(items.size) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            dragging = true
            val minX = lefts.firstOrNull() ?: 0f
            val maxX = (lefts.lastOrNull() ?: 0f) + (widths.lastOrNull() ?: 0f)
            while (true) {
                val ev = awaitPointerEvent()
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                if (!ch.pressed) break
                val dx = ch.positionChange().x
                if (dx != 0f) {
                    ch.consume()
                    val nx = (x.value + dx).coerceIn(minX, maxX)
                    scope.launch { x.snapTo(nx) }
                    hovered = nearestTo(nx)
                    scope.launch { w.animateTo(widths[hovered], stretch) }
                }
            }
            dragging = false
            val target = nearestTo(x.value)
            hovered = target
            scope.launch { x.animateTo(centreOf(target), travel) }
            scope.launch { w.animateTo(widths[target], stretch) }
            // Siempre avisa: selectedIndex puede estar desfasado dentro del pointerInput y, si la app
            // arrancó en la primera pestaña, soltar sobre ella no seleccionaba nada.
            select(target)
        }
    }

    val tabRow: @Composable (interactive: Boolean) -> Unit = { interactive ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items.forEachIndexed { i, item ->
                if (i == half && center != null) {
                    if (interactive) Box(Modifier.onSizeChanged { centerWidth = it.width }) { center() }
                    else Spacer(Modifier.width(with(density) { centerWidth.toDp() }))
                }
                PillTab(
                    item = item,
                    selected = i == selectedIndex,
                    colors = colors,
                    labelStyle = labelStyle,
                    interactive = interactive,
                    onClick = { if (i != selectedIndex) select(i) },
                    modifier = if (interactive) Modifier.onGloballyPositioned {
                        lefts[i] = it.positionInParent().x; widths[i] = it.size.width.toFloat()
                    } else Modifier
                )
            }
        }
    }

    Box(modifier.then(glow.gestureModifier)) {
    // El cristal de la píldora en su propia capa sin hijos: la lente lo refracta, y si grabara a sus
    // hijos (la lente incluida) el árbol de render se llamaría a sí mismo.
    Box(
        Modifier.matchParentSize()
            .glassSource(surfaceBackdrop)
            .liquidGlass(backdrop, CircleShape, glass)
            .clip(CircleShape)
            .then(glow.drawModifier)
    )
    Box(Modifier.padding(8.dp), contentAlignment = Alignment.CenterStart) {
        tabRow(true)
        // Copia invisible de las pestañas: lo que la lente deja ver (y amplía) por encima de su relleno.
        Box(
            Modifier.clearAndSetSemantics {}.alpha(0f).glassSource(tabsBackdrop)
                .graphicsLayer { val s = lerp(1f, 1.12f, press.value); scaleX = s; scaleY = s; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(x.value / size.width.coerceAtLeast(1f), 0.5f) }
                // Pulsada, la copia se tiñe como en la lente; en reposo el icono/etiqueta ya van sobre el acento.
                .graphicsLayer { colorFilter = if (press.value > 0.01f) ColorFilter.tint(colors.accent) else null }
        ) { tabRow(false) }

        if (measured && selectedIndex in items.indices) {
            val tabH = LiquidPillDefaults.TabHeight
            val place = Modifier
                .offset { IntOffset((x.value - w.value / 2f).roundToInt(), 0) }
                .layout { m, c ->
                    // Crece al pulsar y se estira con la velocidad del viaje: ancha y baja al acelerar.
                    val v = (abs(x.velocity) / 5000f).coerceAtMost(0.22f)
                    val p = press.value
                    val gw = (w.value * lerp(1f, 1.18f, p) * (1f + v)).roundToInt().coerceAtLeast(1)
                    val gh = (tabH.toPx() * lerp(1f, 1.3f, p) * (1f - v * 0.45f)).roundToInt().coerceAtLeast(1)
                    val child = m.measure(Constraints.fixed(gw, gh))
                    val bw = w.value.roundToInt().coerceAtLeast(1)
                    val bh = tabH.roundToPx()
                    layout(bw, bh) { child.place((bw - gw) / 2, (bh - gh) / 2) }
                }
            val sources: () -> List<GlassSource> = {
                val p = press.value.coerceIn(0f, 1f)
                if (p <= 0f) listOf(GlassSource(backdrop)) else listOf(GlassSource(backdrop), GlassSource(surfaceBackdrop, p))
            }
            val tabsOnly = remember(tabsBackdrop) { listOf(GlassSource(tabsBackdrop)) }
            // 1 · cuerpo de la lente: relleno de acento en reposo que se aclara a cristal al pulsar.
            Box(
                place.then(lens).glassSurface(sources, CircleShape) {
                    val p = press.value.coerceIn(0f, 1f)
                    GlassLook(
                        blur = 3.dp.toPx() * (1f - p),
                        saturation = 1.6f,
                        refractionHeight = 10.dp.toPx() * p,
                        refractionAmount = 14.dp.toPx() * p,
                        dispersion = 0.5f,
                        depthEffect = true,
                        surface = colors.accent.copy(alpha = colors.accent.alpha * (1f - 0.82f * p)),
                        rim = Color.White.copy(alpha = 0.18f + 0.32f * p),
                        rimWidth = 0.8.dp.toPx(),
                        shadowAlpha = p,
                        innerShadowAlpha = p,
                        innerShadowRadius = 8.dp.toPx() * p,
                        fallback = colors.accent,
                    )
                }
            )
            // 2 · las pestañas vistas a través de la lente.
            Box(
                place.glassSurface({ tabsOnly }, CircleShape) {
                    val p = press.value.coerceIn(0f, 1f)
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
    }
}

/** Pestaña de la píldora: icono que hace «pop» al activarse y etiqueta que se despliega con muelle. */
@Composable
private fun PillTab(
    item: LiquidBarItem,
    selected: Boolean,
    colors: LiquidPillColors,
    labelStyle: TextStyle,
    interactive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint by animateColorAsState(if (selected) colors.onAccent else colors.content, label = "pillTint")
    val pop = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            pop.snapTo(0.6f)
            pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
        }
    }
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val squeeze by animateFloatAsState(if (pressed) 0.95f else 1f, spring(0.6f, 800f), label = "pillPress")
    Row(
        modifier
            .height(LiquidPillDefaults.TabHeight)
            .graphicsLayer { scaleX = squeeze; scaleY = squeeze }
            .then(
                if (interactive) Modifier.clickable(source, null, role = Role.Tab, onClickLabel = item.label, onClick = onClick)
                else Modifier
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(item.icon, item.label, tint = tint, modifier = Modifier.size(24.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value })
        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally(spring(0.75f, 500f)) + fadeIn(),
            exit = shrinkHorizontally(spring(0.9f, 500f)) + fadeOut()
        ) {
            Text(item.label, style = labelStyle, color = tint, maxLines = 1, modifier = Modifier.padding(start = 7.dp))
        }
    }
}
