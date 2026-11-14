package io.github.mkandreum.liquidbar.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import io.github.mkandreum.liquidbar.LiquidGlassStyle
import io.github.mkandreum.liquidbar.elasticStretch
import io.github.mkandreum.liquidbar.glass.GlassBackdrop
import io.github.mkandreum.liquidbar.glass.GlassGlow
import io.github.mkandreum.liquidbar.glass.GlassLook
import io.github.mkandreum.liquidbar.glass.GlassSource
import io.github.mkandreum.liquidbar.glass.glassSource
import io.github.mkandreum.liquidbar.glass.glassSurface
import io.github.mkandreum.liquidbar.glass.rememberGlassBackdrop
import io.github.mkandreum.liquidbar.liquidGlass
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh

/* ═════════════ 7 · Isla líquida ═════════════ */

@Immutable
data class LiquidIslandMessage(
    val title: String,
    val text: String? = null,
    val icon: ImageVector? = null,
    val accent: Color = Color(0xFF30D158),
    /** 0 = se queda hasta [LiquidIslandState.hide]. */
    val durationMillis: Long = 3200,
    val onTap: (() -> Unit)? = null,
    val id: Long = System.nanoTime(),
)

@Stable
class LiquidIslandState {
    var current by mutableStateOf<LiquidIslandMessage?>(null)
        private set
    fun show(message: LiquidIslandMessage) { current = message }
    fun show(title: String, text: String? = null, icon: ImageVector? = null, accent: Color = Color(0xFF30D158)) =
        show(LiquidIslandMessage(title, text, icon, accent))
    fun hide() { current = null }
}

@Composable
fun rememberLiquidIslandState() = remember { LiquidIslandState() }

/** Isla de la app, para mostrar avisos desde cualquier pantalla (la da la raíz). */
val LocalLiquidIsland = androidx.compose.runtime.staticCompositionLocalOf<LiquidIslandState?> { null }

/**
 * Píldora arriba que muestra un estado vivo («Mesa 7 lista», descanso, descarga). Crece con muelles desfasados:
 * primero el ancho (spring 0.62, 380) y 70 ms después el alto (spring 0.7, 300); al irse, al revés.
 * Va en el Box raíz, hermana del contenido con `glassSource(backdrop)`.
 */
@Composable
fun LiquidIsland(
    state: LiquidIslandState,
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
    glass: LiquidGlassStyle = LiquidGlassStyle.Dark,
    contentColor: Color = Color.White,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer()
    val titleStyle = TextStyle(fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, color = contentColor)
    val textStyle = TextStyle(fontSize = 12.sp, lineHeight = 15.sp, color = contentColor.copy(alpha = 0.75f))
    var shown by remember { mutableStateOf<LiquidIslandMessage?>(null) }
    val w = remember { Animatable(36f, 0.25f) }
    val h = remember { Animatable(36f, 0.25f) }
    val y = remember { Animatable(-70f, 0.25f) }
    val fade = remember { Animatable(0f) }
    val m = state.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val maxW = maxWidth.value - 24f
        LaunchedEffect(m) {
            if (m != null) {
                shown = m
                val tw = measurer.measure(m.title, titleStyle).size.width
                val xw = m.text?.let { measurer.measure(it, textStyle).size.width } ?: 0
                val target = (with(density) { max(tw, xw).toDp().value } + (if (m.icon != null) 36f else 0f) + 30f).coerceAtMost(maxW)
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                launch { y.animateTo(0f, spring(0.6f, 400f)) }
                launch { w.animateTo(target, spring(0.62f, 380f)) }
                launch { delay(70); h.animateTo(if (m.text != null) 60f else 40f, spring(0.7f, 300f)) }
                launch { delay(140); fade.animateTo(1f, spring(1f, 400f)) }
                if (m.durationMillis > 0) { delay(m.durationMillis); if (state.current === m) state.hide() }
            } else if (shown != null) {
                launch { fade.animateTo(0f, spring(1f, 600f)) }
                launch { h.animateTo(36f, spring(0.8f, 360f)) }
                launch { delay(90); w.animateTo(36f, spring(0.8f, 360f)) }
                delay(220)
                y.animateTo(-70f, spring(0.9f, 300f))
                shown = null
            }
        }
        val s = shown ?: return@BoxWithConstraints
        val scope = rememberCoroutineScope()
        val glow = remember(scope) { GlassGlow(scope) }
        Box(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 10.dp), contentAlignment = Alignment.TopCenter) {
            Box(
                Modifier
                    .layout { me, _ ->
                        val pw = w.value.dp.roundToPx().coerceAtLeast(1)
                        val ph = h.value.dp.roundToPx().coerceAtLeast(1)
                        val child = me.measure(Constraints.fixed(pw, ph))
                        layout(pw, ph) { child.place(0, y.value.dp.roundToPx()) }
                    }
                    .graphicsLayer(elasticStretch(glow, 0.6f))
                    .liquidGlass(backdrop, CircleShape, glass)
                    .clip(CircleShape)
                    .then(glow.gestureModifier)
                    .then(glow.drawModifier)
                    .clickable(interactionSource = null, indication = null, enabled = s.onTap != null) { s.onTap?.invoke() },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    Modifier.graphicsLayer { alpha = fade.value.coerceIn(0f, 1f) }.layout { me, c ->
                        val pl = me.measure(Constraints())
                        layout(c.maxWidth, c.maxHeight) { pl.place((c.maxWidth - pl.width) / 2, (c.maxHeight - pl.height) / 2) }
                    },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (s.icon != null) Icon(s.icon, null, tint = s.accent, modifier = Modifier.size(26.dp))
                    Column {
                        Text(s.title, style = titleStyle, maxLines = 1)
                        if (s.text != null) Text(s.text, style = textStyle, maxLines = 1)
                    }
                }
            }
        }
    }
}

/* ═════════════ 8 · Abanico de burbujas ═════════════ */

@Immutable
data class LiquidFanItem(val label: String, val icon: ImageVector, val color: Color = Color.Unspecified, val onClick: () -> Unit)

/**
 * Botón (+) de cristal que, mantenido pulsado, abre un abanico de burbujas en arco escalonadas 30 ms con
 * spring(0.55, 380). Arrastra el dedo hasta una: se hincha ×1,25 y se vuelve lupa; suelta para elegirla.
 * Un toque normal llama a [onClick]. Va flotando (hermano del `glassSource(backdrop)`).
 */
@Composable
fun LiquidFan(
    backdrop: GlassBackdrop,
    items: List<LiquidFanItem>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 60.dp,
    radius: Dp = 104.dp,
    startAngle: Float = 180f,
    endAngle: Float = 270f,
    bubbleSize: Dp = 54.dp,
    glass: LiquidGlassStyle = LiquidGlassStyle.Dark,
    tint: Color = Color.Unspecified,
    contentColor: Color = Color.White,
    onClickLabel: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val glow = remember(scope) { GlassGlow(scope) }
    val n = items.size
    val progress = remember(n) { List(n) { Animatable(0f, 0.002f) } }
    val hover = remember(n) { List(n) { Animatable(0f, 0.001f) } }
    val click by rememberUpdatedState(onClick)
    val latestItems by rememberUpdatedState(items)
    var hovered by remember { mutableIntStateOf(-1) }
    fun pos(i: Int): Offset {
        val a = (startAngle + (if (n == 1) 0.5f else i / (n - 1f)) * (endAngle - startAngle)) * PI.toFloat() / 180f
        val r = with(density) { radius.toPx() }
        return Offset(cos(a) * r, sin(a) * r)
    }
    val screen = remember(backdrop) { listOf(GlassSource(backdrop)) }

    Box(modifier.size(size)) {
        Box(
            Modifier.matchParentSize()
                .graphicsLayer(elasticStretch(glow))
                .liquidGlassTinted(backdrop, CircleShape, glass) {
                    if (tint == Color.Unspecified) Color.Transparent else tint.copy(alpha = tint.alpha * lerp(0.2f, 0.4f, glow.pressProgress))
                }
                .clip(CircleShape)
                .then(glow.gestureModifier)
                .then(glow.drawModifier)
                .semantics { role = Role.Button; onClick(onClickLabel) { click(); true } }
                .pointerInput(n) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        when (awaitHold(down, 280)) {
                            Hold.Up -> { click(); return@awaitEachGesture }
                            Hold.Cancel -> return@awaitEachGesture
                            Hold.Long -> Unit
                        }
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        progress.forEachIndexed { i, a -> scope.launch { delay(i * 30L); a.animateTo(1f, spring(0.55f, 380f)) } }
                        val c = Offset(this.size.width / 2f, this.size.height / 2f)
                        val reach = with(density) { bubbleSize.toPx() } * 0.9f
                        var pick = -1
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            ch.consume()
                            if (!ch.pressed) { pick = hovered; break }
                            val local = ch.position - c
                            var best = -1
                            var bd = reach
                            for (i in 0 until n) { val d = (pos(i) - local).getDistance(); if (d < bd) { bd = d; best = i } }
                            if (best != hovered) {
                                if (hovered >= 0) { val h0 = hovered; scope.launch { hover[h0].animateTo(0f, spring(0.6f, 420f)) } }
                                if (best >= 0) { scope.launch { hover[best].animateTo(1f, spring(0.5f, 400f)) }; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                                hovered = best
                            }
                        }
                        if (pick >= 0) { haptics.performHapticFeedback(HapticFeedbackType.LongPress); latestItems.getOrNull(pick)?.onClick?.invoke() }
                        hovered = -1
                        progress.forEachIndexed { i, a ->
                            scope.launch { delay((n - 1 - i) * 22L); hover[i].animateTo(0f, spring(0.8f, 500f)) }
                            scope.launch { delay((n - 1 - i) * 22L); a.animateTo(0f, spring(0.8f, 420f)) }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides contentColor) { content() }
        }
        // Burbujas: se dibujan fuera del botón (el Box no recorta).
        items.forEachIndexed { i, item ->
            val place = Modifier.layout { me, c ->
                val t = progress[i].value
                val hv = hover[i].value.coerceIn(0f, 1f)
                val d = (bubbleSize.toPx() * lerp(0.3f, 1f, t) * lerp(1f, 1.25f, hv)).roundToInt().coerceAtLeast(1)
                val child = me.measure(Constraints.fixed(d, d))
                val p = pos(i)
                layout(0, 0) {
                    child.place((c.maxWidth / 2f + p.x * t - d / 2f).roundToInt(), (c.maxHeight / 2f + p.y * t - d / 2f).roundToInt())
                }
            }
            Box(
                place.graphicsLayer { alpha = (progress[i].value * 1.6f).coerceIn(0f, 1f) }
                    .glassSurface({ screen }, CircleShape) {
                        val hv = hover[i].value.coerceIn(0f, 1f)
                        GlassLook(
                            blur = 6.dp.toPx() * (1f - hv), saturation = 1.6f,
                            refractionHeight = 10.dp.toPx() * hv, refractionAmount = 12.dp.toPx() * hv,
                            dispersion = 0.5f, depthEffect = true, zoom = lerp(1f, 1.3f, hv),
                            surface = glass.surface, rim = Color.White.copy(alpha = 0.22f + 0.3f * hv), rimWidth = 0.8.dp.toPx(),
                            shadowAlpha = 0.8f + 0.2f * hv, innerShadowAlpha = hv, innerShadowRadius = 6.dp.toPx() * hv,
                            fallback = glass.fallback,
                        )
                    },
                contentAlignment = Alignment.Center,
            ) { Icon(item.icon, item.label, tint = if (item.color == Color.Unspecified) contentColor else item.color, modifier = Modifier.size(26.dp)) }
            // Etiqueta de la burbuja bajo el dedo.
            Text(
                item.label, color = contentColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                modifier = Modifier.layout { me, c ->
                    val pl = me.measure(Constraints())
                    val t = progress[i].value
                    val hv = hover[i].value.coerceIn(0f, 1f)
                    val p = pos(i)
                    val d = bubbleSize.toPx() * lerp(0.3f, 1f, t) * lerp(1f, 1.25f, hv)
                    layout(0, 0) {
                        pl.placeWithLayer((c.maxWidth / 2f + p.x * t - pl.width / 2f).roundToInt(), (c.maxHeight / 2f + p.y * t - d / 2f - pl.height - 8.dp.toPx()).roundToInt()) {
                            alpha = (t * hv).coerceIn(0f, 1f)
                        }
                    }
                }.liquidGlass(backdrop, CircleShape, glass).padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

/* ═════════════ 9 · Lupa para gráficas ═════════════ */

@Immutable
data class LiquidChartPoint(val value: String, val label: String? = null, val y: Float? = null, val key: Any? = null)

/**
 * Envuelve una gráfica: mantén pulsado y una lupa de 104 dp amplía ×1,8 el punto bajo el dedo mientras una
 * burbuja enseña su valor. [valueAt] recibe la x (px) y el ancho; devuelve el punto (con su `y` en px para
 * centrar la lupa en el dato). La gráfica se graba en una capa propia: vale dentro de cualquier pantalla.
 */
@Composable
fun LiquidChartLens(
    valueAt: (x: Float, width: Float) -> LiquidChartPoint?,
    modifier: Modifier = Modifier,
    zoom: Float = 1.8f,
    lensSize: Dp = 104.dp,
    bubbleColor: Color = Color(0xE61C1C1E),
    contentColor: Color = Color.White,
    /** Avisa al activarse y soltarse la lupa (para pausar el arrastre propio de la gráfica). */
    onActiveChange: (Boolean) -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val chartBackdrop = rememberGlassBackdrop()
    val active by rememberUpdatedState(onActiveChange)
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val x = remember { Animatable(0f) }
    val y = remember { Animatable(0f) }
    val p = remember { Animatable(0f, 0.001f) }
    var point by remember { mutableStateOf<LiquidChartPoint?>(null) }
    val pointAt by rememberUpdatedState(valueAt)
    Box(
        modifier.pointerInput(Unit) {
            detectDragGesturesAfterLongPress(
                onDragStart = { o ->
                    val pt = pointAt(o.x, size.width.toFloat())
                    point = pt
                    scope.launch { x.snapTo(o.x) }
                    scope.launch { y.snapTo(pt?.y ?: o.y) }
                    scope.launch { p.animateTo(1f, spring(0.6f, 380f)) }
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    active(true)
                },
                onDrag = { change, _ ->
                    change.consume()
                    val ox = change.position.x.coerceIn(0f, size.width.toFloat())
                    val pt = pointAt(ox, size.width.toFloat())
                    if (pt?.key != point?.key) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    point = pt
                    scope.launch { x.animateTo(ox, spring(0.8f, 700f)) }
                    scope.launch { y.animateTo(pt?.y ?: change.position.y, spring(0.8f, 700f)) }
                },
                onDragEnd = { active(false); scope.launch { p.animateTo(0f, spring(1f, 500f)) } },
                onDragCancel = { active(false); scope.launch { p.animateTo(0f, spring(1f, 500f)) } },
            )
        }
    ) {
        Box(Modifier.glassSource(chartBackdrop), content = content)
        val chartOnly = remember(chartBackdrop) { listOf(GlassSource(chartBackdrop)) }
        Box(
            Modifier.layout { me, _ ->
                val d = (lensSize.toPx() * lerp(0.4f, 1f, p.value.coerceIn(0f, 1f))).roundToInt().coerceAtLeast(1)
                val child = me.measure(Constraints.fixed(d, d))
                layout(0, 0) { child.placeWithLayer((x.value - d / 2f).roundToInt(), (y.value - d / 2f).roundToInt()) { alpha = if (p.value <= 0.002f) 0f else 1f } }
            }.glassSurface({ chartOnly }, CircleShape) {
                val pv = p.value.coerceIn(0f, 1f)
                lensLook(pv, Color.White.copy(alpha = 0.04f * pv), zoom = zoom, band = 12.dp, amount = 14.dp, saturation = 1.15f, fallback = Color.White.copy(alpha = 0.12f))
            }
        )
        val pt = point
        if (pt != null) Column(
            Modifier.layout { me, _ ->
                val pl = me.measure(Constraints())
                val d = lensSize.toPx() * lerp(0.4f, 1f, p.value.coerceIn(0f, 1f))
                layout(0, 0) {
                    pl.placeWithLayer((x.value - pl.width / 2f).roundToInt(), (y.value - d / 2f - pl.height - 8.dp.toPx()).roundToInt()) { alpha = p.value.coerceIn(0f, 1f) }
                }
            }.background(bubbleColor, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(pt.value, color = contentColor, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (pt.label != null) Text(pt.label, color = contentColor.copy(alpha = 0.75f), fontSize = 11.sp, maxLines = 1)
        }
    }
}

/* ═════════════ 10 · Buscar que se transforma ═════════════ */

/**
 * Círculo de buscar que se estira con spring(0.7, 300) hasta campo de texto de cristal y vuelve con
 * spring(0.72, 320). Pensado para el círculo a la derecha de la barra: al abrir, pliega la barra.
 */
@Composable
fun LiquidSearchMorph(
    backdrop: GlassBackdrop,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    expandedWidth: Dp,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    placeholder: String = "Buscar",
    glass: LiquidGlassStyle = LiquidGlassStyle.Dark,
    contentColor: Color = Color.White,
    onSubmit: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val glow = remember(scope) { GlassGlow(scope) }
    val w = remember { Animatable(if (expanded) expandedWidth.value else size.value, 0.25f) }
    val t = remember { Animatable(if (expanded) 1f else 0f, 0.001f) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(expanded, expandedWidth) {
        if (expanded) {
            launch { w.animateTo(max(size.value, expandedWidth.value), spring(0.7f, 300f)) }
            launch { delay(120); runCatching { focus.requestFocus() } }
            t.animateTo(1f, spring(1f, 420f))
        } else {
            keyboard?.hide()
            launch { t.animateTo(0f, spring(1f, 600f)) }
            w.animateTo(size.value, spring(0.72f, 320f))
        }
    }
    Box(
        modifier
            .layout { me, _ ->
                val pw = w.value.dp.roundToPx().coerceAtLeast(1)
                val ph = size.roundToPx()
                val child = me.measure(Constraints.fixed(pw, ph))
                layout(pw, ph) { child.place(0, 0) }
            }
            .graphicsLayer(elasticStretch(glow, 0.8f))
            .liquidGlass(backdrop, CircleShape, glass)
            .clip(CircleShape)
            .then(glow.drawModifier)
    ) {
        Box(
            Modifier.size(size).then(glow.gestureModifier)
                .clickable(interactionSource = null, indication = null, role = Role.Button, onClickLabel = placeholder) {
                    if (expanded) onSubmit(query) else onExpandedChange(true)
                },
            contentAlignment = Alignment.Center,
        ) { Canvas(Modifier.size(24.dp)) { drawSearchGlyph(contentColor, 2.2.dp.toPx()) } }
        // Cerrado, ni el campo ni el aspa existen: en Compose un hermano encima (aunque desactivado) se queda los toques.
        if (expanded || t.value > 0.01f) BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            enabled = expanded,
            singleLine = true,
            textStyle = TextStyle(color = contentColor, fontSize = 16.sp),
            cursorBrush = SolidColor(contentColor),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit(query) }),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = size - 8.dp, end = 50.dp).fillMaxWidth()
                .graphicsLayer { alpha = t.value.coerceIn(0f, 1f) }.focusRequester(focus),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) Text(placeholder, color = contentColor.copy(alpha = 0.55f), fontSize = 16.sp, maxLines = 1)
                    inner()
                }
            },
        )
        if (expanded || t.value > 0.01f) Box(
            Modifier.align(Alignment.CenterEnd).padding(end = 10.dp).size(32.dp)
                .graphicsLayer { val v = t.value.coerceIn(0f, 1f); alpha = v; val s = lerp(0.5f, 1f, v); scaleX = s; scaleY = s }
                .clip(CircleShape).background(Color.White.copy(alpha = 0.14f))
                .clickable(enabled = expanded, role = Role.Button, onClickLabel = "Cerrar búsqueda") { onQueryChange(""); onExpandedChange(false) },
            contentAlignment = Alignment.Center,
        ) { Canvas(Modifier.size(18.dp)) { drawCloseGlyph(contentColor, 2.4.dp.toPx()) } }
    }
}

/* ═════════════ 11 · Menú de pulsación larga ═════════════ */

@Immutable
data class LiquidMenuItem(val label: String, val icon: ImageVector? = null, val destructive: Boolean = false, val onClick: () -> Unit)

internal class MenuRequest(val bounds: Rect, val items: List<LiquidMenuItem>, val content: @Composable () -> Unit, val key: Any)

@Stable
class LiquidMenuState {
    internal var request by mutableStateOf<MenuRequest?>(null)
    val isOpen: Boolean get() = request != null
    fun dismiss() { request = null }
}

@Composable
fun rememberLiquidMenuState() = remember { LiquidMenuState() }

/** Menú de pulsación larga de la app (lo da la raíz junto a su [LiquidMenuHost]). */
val LocalLiquidMenuState = androidx.compose.runtime.staticCompositionLocalOf<LiquidMenuState?> { null }

/**
 * Zona con menú de pulsación larga (una tarjeta de la lista). Al mantenerla pulsada, [LiquidMenuHost] la eleva
 * ×1,03 con sombra sobre el fondo desenfocado y despliega las opciones como píldoras de cristal en cascada.
 * Los toques normales siguen llegando a la tarjeta.
 */
@Composable
fun LiquidContextMenuArea(
    items: List<LiquidMenuItem>,
    modifier: Modifier = Modifier,
    state: LiquidMenuState? = LocalLiquidMenuState.current,
    content: @Composable () -> Unit,
) {
    if (state == null) { Box(modifier) { content() }; return }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val key = remember { Any() }
    val haptics = LocalHapticFeedback.current
    val latestItems by rememberUpdatedState(items)
    val latestContent by rememberUpdatedState(content)
    val open = state.request?.key === key
    Box(
        modifier
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .graphicsLayer { alpha = if (open) 0f else 1f }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (awaitHold(down, 380, PointerEventPass.Initial) != Hold.Long) return@awaitEachGesture
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    state.request = MenuRequest(bounds, latestItems, { latestContent() }, key)
                    // El resto del gesto es del menú: que la tarjeta no reciba el toque.
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        ev.changes.forEach { it.consume() }
                        if (ev.changes.none { it.pressed }) break
                    }
                }
            }
    ) { content() }
}

/**
 * Capa del menú: va en el Box raíz, encima de todo y hermana del contenido con `glassSource(backdrop)`.
 * [cardShape]: forma de las tarjetas (para la sombra al elevarlas).
 */
@Composable
fun LiquidMenuHost(
    state: LiquidMenuState,
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
    glass: LiquidGlassStyle = LiquidGlassStyle.Dark,
    contentColor: Color = Color.White,
    destructiveColor: Color = Color(0xFFFF6B60),
    cardShape: Shape = RoundedCornerShape(20.dp),
) {
    var shown by remember { mutableStateOf<MenuRequest?>(null) }
    val lift = remember { Animatable(0f) }
    val fade = remember { Animatable(0f) }
    val pills = remember { List(8) { Animatable(0f) } }
    val req = state.request
    LaunchedEffect(req) {
        if (req != null) {
            shown = req
            pills.forEach { it.snapTo(0f) }
            launch { lift.animateTo(1f, spring(0.55f, 420f)) }
            launch { fade.animateTo(1f, spring(1f, 400f)) }
            req.items.indices.take(8).forEach { i -> launch { delay(40L + i * 35L); pills[i].animateTo(1f, spring(0.6f, 420f)) } }
        } else if (shown != null) {
            pills.forEach { launch { it.animateTo(0f, spring(0.9f, 600f)) } }
            launch { fade.animateTo(0f, spring(1f, 500f)) }
            lift.animateTo(0f, spring(0.8f, 420f))
            shown = null
        }
    }
    val s = shown ?: return
    val density = LocalDensity.current
    val screen = remember(backdrop) { listOf(GlassSource(backdrop)) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        // Fondo: la pantalla desenfocada y oscurecida.
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = fade.value.coerceIn(0f, 1f) }
                .glassSurface({ screen }, RectangleShape) {
                    GlassLook(blur = 10.dp.toPx(), saturation = 1.2f, surface = Color.Black.copy(alpha = 0.28f), fallback = Color.Black.copy(alpha = 0.6f))
                }
                .pointerInput(Unit) { detectTapGestures { state.dismiss() } }
        )
        val b = s.bounds
        Box(
            Modifier.offset { IntOffset(b.left.roundToInt(), b.top.roundToInt()) }
                .size(with(density) { b.width.toDp() }, with(density) { b.height.toDp() })
                .graphicsLayer {
                    val l = lift.value
                    val sc = lerp(1f, 1.03f, l); scaleX = sc; scaleY = sc
                    shadowElevation = 18.dp.toPx() * l.coerceIn(0f, 1f)
                    shape = cardShape
                    clip = false
                }
                .pointerInput(Unit) { detectTapGestures { state.dismiss() } }
        ) { s.content() }
        val count = s.items.size.coerceAtMost(8)
        val colH = with(density) { (44.dp * count + 8.dp * (count - 1).coerceAtLeast(0)).toPx() }
        val maxH = constraints.maxHeight
        val below = b.bottom + with(density) { 12.dp.toPx() } + colH < maxH
        val maxWpx = constraints.maxWidth
        Column(
            Modifier.offset {
                val x = b.left.coerceIn(12.dp.toPx(), maxWpx - 232.dp.toPx())
                val yy = if (below) b.bottom + 12.dp.toPx() else b.top - 12.dp.toPx() - colH
                IntOffset(x.roundToInt(), yy.roundToInt())
            }.width(220.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            s.items.take(8).forEachIndexed { i, item ->
                val color = if (item.destructive) destructiveColor else contentColor
                Row(
                    Modifier.fillMaxWidth().height(44.dp)
                        .graphicsLayer {
                            val tt = pills[i].value
                            alpha = tt.coerceIn(0f, 1f)
                            translationY = (if (below) -10.dp else 10.dp).toPx() * (1f - tt)
                            val sc = lerp(0.8f, 1f, tt); scaleX = sc; scaleY = sc
                            transformOrigin = TransformOrigin(0.3f, 0.5f)
                        }
                        .liquidGlass(backdrop, CircleShape, glass)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { state.dismiss(); item.onClick() }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (item.icon != null) Icon(item.icon, null, tint = color, modifier = Modifier.size(20.dp))
                    Text(item.label, color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

/* ═════════════ 12 · Tirar para recargar con gota ═════════════ */

/**
 * Arriba del todo, tira hacia abajo: una gota de cristal cuelga, se estira con resistencia (tanh) y la flecha
 * gira; al soltar pasado [threshold] cae con rebote (spring 0.45, 300) y gira mientras [refreshing].
 * La gota refracta la propia lista (capa propia): vale dentro de cualquier pantalla.
 */
@Composable
fun LiquidPullRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    threshold: Dp = 72.dp,
    glass: LiquidGlassStyle = LiquidGlassStyle.Dark,
    contentColor: Color = Color.White,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val pull = remember { Animatable(0f, 0.25f) }
    var raw by remember { mutableFloatStateOf(0f) }
    val isRefreshing by rememberUpdatedState(refreshing)
    val refresh by rememberUpdatedState(onRefresh)
    val contentBackdrop = rememberGlassBackdrop()
    val th = with(density) { threshold.toPx() }
    val maxPull = with(density) { 130.dp.toPx() }
    val rest = with(density) { 64.dp.toPx() }
    fun curve(r: Float) = maxPull * tanh(r / (2f * maxPull))
    val connection = remember(th) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y < 0f && raw > 0f) {
                    val used = max(available.y, -raw)
                    raw += used
                    scope.launch { pull.snapTo(curve(raw)) }
                    return Offset(0f, used)
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f && !isRefreshing) {
                    val before = curve(raw)
                    raw += available.y
                    val after = curve(raw)
                    if (before < th && after >= th) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    scope.launch { pull.snapTo(after) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (raw <= 0f) return Velocity.Zero
                val trigger = curve(raw) >= th
                raw = 0f
                if (trigger) { refresh(); pull.animateTo(rest, LiquidSprings.drop) } else pull.animateTo(0f, spring(0.8f, 400f))
                return available
            }
        }
    }
    LaunchedEffect(refreshing) {
        if (refreshing) pull.animateTo(rest, LiquidSprings.drop) else if (raw == 0f) pull.animateTo(0f, spring(0.8f, 380f))
    }
    val spin = rememberInfiniteTransition(label = "pullSpin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "pullAngle")
    Box(modifier.nestedScroll(connection)) {
        Box(Modifier.fillMaxSize().glassSource(contentBackdrop), content = content)
        val listOnly = remember(contentBackdrop) { listOf(GlassSource(contentBackdrop)) }
        Box(
            Modifier.align(Alignment.TopCenter)
                .layout { me, _ ->
                    val s = 40.dp.roundToPx()
                    val child = me.measure(Constraints.fixed(s, s))
                    layout(s, s) {
                        child.placeWithLayer(0, (pull.value - s + 8.dp.toPx()).roundToInt()) {
                            val st = if (isRefreshing) 0f else (pull.value / 420f).coerceIn(0f, 0.18f)
                            scaleX = 1f - st * 0.6f; scaleY = 1f + st
                            transformOrigin = TransformOrigin(0.5f, 0f)
                            alpha = (pull.value / 30f).coerceIn(0f, 1f)
                        }
                    }
                }
                .glassSurface({ listOnly }, CircleShape) {
                    GlassLook(
                        blur = 4.dp.toPx(), saturation = 1.6f, refractionHeight = 10.dp.toPx(), refractionAmount = 10.dp.toPx(),
                        dispersion = 0.5f, depthEffect = true, zoom = 1.15f,
                        surface = glass.surface.copy(alpha = max(glass.surface.alpha, 0.62f)),
                        rim = Color.White.copy(alpha = 0.35f), rimWidth = 0.8.dp.toPx(), shadowAlpha = 1f, fallback = glass.fallback,
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            val k = (pull.value / th).coerceIn(0f, 1f)
            Canvas(Modifier.size(20.dp).rotate(if (refreshing) angle else k * 180f).graphicsLayer { val s = if (refreshing) 1f else lerp(0.7f, 1f, k); scaleX = s; scaleY = s }) {
                drawArrowGlyph(contentColor, 2.4.dp.toPx())
            }
        }
    }
}

/* ═════════════ 13 · Hoja inferior de cristal ═════════════ */

@Stable
class LiquidSheetState(initial: Int = -1) {
    /** Altura actual (índice en `detents`) o −1 cerrada. */
    var detent by mutableIntStateOf(initial)
        internal set
    val isOpen: Boolean get() = detent >= 0
    fun open(detent: Int = 0) { this.detent = detent }
    fun close() { detent = -1 }
}

@Composable
fun rememberLiquidSheetState() = remember { LiquidSheetState() }

/**
 * Hoja inferior propia (no `ModalBottomSheet`, que vive en otra ventana y no ve la capa): cristal con canto
 * que refracta, asa que se estira con la velocidad y alturas fijas [detents] (fracción de pantalla) con
 * spring(0.75, 300). Bajarla del todo la cierra. Va en el Box raíz, hermana del contenido.
 * El contenido gestiona su propio scroll (p. ej. `verticalScroll`); al tirar hacia abajo arriba del todo, baja la hoja.
 */
@Composable
fun LiquidSheet(
    state: LiquidSheetState,
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
    detents: List<Float> = listOf(0.45f, 0.9f),
    title: String? = null,
    glass: LiquidGlassStyle = LiquidGlassStyle.Dark.copy(blur = 12.dp),
    contentColor: Color = Color.White,
    onDismiss: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val y = remember { Animatable(1f, 0.0005f) }
    var visible by remember { mutableStateOf(false) }
    val dismiss by rememberUpdatedState(onDismiss)
    val sorted = remember(detents) { detents.sorted() }
    LaunchedEffect(state.detent) {
        if (state.detent >= 0) {
            visible = true
            y.animateTo(1f - sorted[state.detent.coerceIn(sorted.indices)], LiquidSprings.sheet)
        } else if (visible) {
            y.animateTo(1f, spring(0.9f, 320f))
            visible = false
            dismiss()
        }
    }
    if (!visible && state.detent < 0) return
    BoxWithConstraints(modifier.fillMaxSize()) {
        val H = constraints.maxHeight.toFloat()
        val density = LocalDensity.current
        val maxD = sorted.last()
        val top = 1f - maxD
        val sheetH = with(density) { (H * maxD).toDp() + 60.dp }
        fun settle(v: Float) {
            val cur = 1f - y.value
            var best = 0
            var bd = Float.MAX_VALUE
            sorted.forEachIndexed { i, d -> val dd = abs(d - cur - v / H * 0.25f); if (dd < bd) { bd = dd; best = i } }
            if (cur < sorted.first() * 0.6f || (v > 1800f && best == 0 && cur < sorted.first())) state.close()
            else if (state.detent == best) scope.launch { y.animateTo(1f - sorted[best], LiquidSprings.sheet) }
            else state.detent = best
        }
        fun move(dy: Float) {
            var ny = y.value + dy / H
            if (ny < top) ny = top - (top - ny) * 0.3f
            scope.launch { y.snapTo(ny) }
        }
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = 0.35f * ((1f - y.value) / maxD).coerceIn(0f, 1f) }
                .background(Color.Black)
                .pointerInput(Unit) { detectTapGestures { state.close() } }
        )
        val bodyConnection = remember(H) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    // Subir con la hoja a media altura: primero sube la hoja.
                    if (available.y < 0f && y.value > top + 0.001f && source == NestedScrollSource.UserInput) { move(available.y); return Offset(0f, available.y) }
                    return Offset.Zero
                }
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (available.y > 0f && source == NestedScrollSource.UserInput) { move(available.y); return Offset(0f, available.y) }
                    return Offset.Zero
                }
                override suspend fun onPreFling(available: Velocity): Velocity {
                    val atDetent = sorted.any { abs((1f - it) - y.value) < 0.002f }
                    if (atDetent) return Velocity.Zero
                    settle(available.y)
                    return available
                }
            }
        }
        Column(
            Modifier.padding(horizontal = 8.dp).fillMaxWidth().height(sheetH)
                .offset { IntOffset(0, (y.value * H).roundToInt()) }
                .liquidGlass(backdrop, RoundedCornerShape(28.dp), glass)
                .clip(RoundedCornerShape(28.dp))
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .draggable(rememberDraggableState { dy -> move(dy) }, Orientation.Vertical, onDragStopped = { v -> settle(v) })
                    .padding(top = 8.dp, bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.layout { me, _ ->
                        val v = (abs(y.velocity) * 0.6f).coerceIn(0f, 1f)
                        val w = (36.dp.toPx() + 18.dp.toPx() * v).roundToInt()
                        val child = me.measure(Constraints.fixed(w, 5.dp.roundToPx()))
                        layout(w, child.height) { child.place(0, 0) }
                    }.background(Color.White.copy(alpha = 0.4f), CircleShape)
                )
                if (title != null) Text(title, color = contentColor, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
            }
            Column(
                Modifier.fillMaxWidth()
                    .layout { me, c ->
                        val vis = ((1f - y.value) * H - 56.dp.toPx()).roundToInt().coerceIn(0, c.maxHeight)
                        val child = me.measure(Constraints(minWidth = c.maxWidth, maxWidth = c.maxWidth, minHeight = vis, maxHeight = vis))
                        layout(c.maxWidth, vis) { child.place(0, 0) }
                    }
                    .nestedScroll(bodyConnection)
                    .padding(horizontal = 20.dp),
                content = content,
            )
        }
    }
}

/* ── Portal de hojas: declara la hoja donde quieras (dentro de una pantalla) y se pinta en la raíz ── */

internal class PortalSheet(
    val title: String?,
    val detents: List<Float>,
    val onDismiss: () -> Unit,
    val content: @Composable ColumnScope.() -> Unit,
    val key: Any,
)

/** Dónde se pintan las [LiquidPortalSheet] de toda la app. Créalo en la raíz y dáselo con [LocalLiquidSheetPortal]. */
@Stable
class LiquidSheetPortal {
    internal var entry by mutableStateOf<PortalSheet?>(null)
    val isOpen: Boolean get() = entry != null
}

val LocalLiquidSheetPortal = androidx.compose.runtime.staticCompositionLocalOf<LiquidSheetPortal?> { null }

@Composable
fun rememberLiquidSheetPortal() = remember { LiquidSheetPortal() }

/**
 * Hoja de cristal declarativa, sustituta directa de `ModalBottomSheet`: mientras esté en composición se muestra en
 * el [LiquidSheetPortalHost] de la raíz (donde sí puede refractar la pantalla); al salir de la composición se cierra.
 * Bajarla del todo llama a [onDismiss].
 */
@Composable
fun LiquidPortalSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    detents: List<Float> = listOf(0.9f),
    content: @Composable ColumnScope.() -> Unit,
) {
    val portal = LocalLiquidSheetPortal.current ?: error("LiquidPortalSheet necesita LiquidSheetPortalHost en la raíz (LocalLiquidSheetPortal)")
    val key = remember { Any() }
    val latestDismiss by rememberUpdatedState(onDismiss)
    val latestContent by rememberUpdatedState(content)
    androidx.compose.runtime.DisposableEffect(portal) {
        portal.entry = PortalSheet(title, detents, { latestDismiss() }, { latestContent() }, key)
        onDispose { if (portal.entry?.key === key) portal.entry = null }
    }
}

/** Pinta las hojas del portal. Va en el Box raíz, encima del contenido y hermano del `glassSource(backdrop)`. */
@Composable
fun LiquidSheetPortalHost(
    portal: LiquidSheetPortal,
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
    glass: LiquidGlassStyle = LiquidGlassStyle.Dark.copy(blur = 12.dp),
    contentColor: Color = Color.White,
) {
    val state = rememberLiquidSheetState()
    var shown by remember { mutableStateOf<PortalSheet?>(null) }
    val e = portal.entry
    LaunchedEffect(e) {
        if (e != null) { shown = e; state.open(0) } else state.close()
    }
    val s = shown ?: return
    LiquidSheet(
        state, backdrop, modifier, detents = s.detents, title = s.title, glass = glass, contentColor = contentColor,
        onDismiss = {
            // Cerrada con el dedo: avisa a la app (que la quitará de la composición). Cerrada por la app: ya no está.
            if (portal.entry?.key === s.key) s.onDismiss()
            if (portal.entry == null) shown = null
        },
    ) { s.content(this) }
}

/** Espaciador para dejar sitio a la isla o a botones flotantes en cabeceras. */
@Composable
fun LiquidSpacer(height: Dp) = Spacer(Modifier.height(height))
