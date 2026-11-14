package io.github.mkandreum.liquidbar.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/* ═════════════ 1 · Selector segmentado líquido ═════════════ */

@Immutable
data class LiquidSegmentedColors(
    /** Relleno de la lente en reposo. */
    val accent: Color,
    /** Texto sobre la lente. */
    val onAccent: Color,
    /** Texto en reposo. */
    val content: Color,
    /** Fondo del selector cuando no flota sobre la pantalla (sin `backdrop`). */
    val track: Color,
)

object LiquidSegmentedDefaults {
    val Height: Dp = 40.dp
    val TextStyle = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
    fun colors(
        accent: Color = Color(0xFF0A84FF),
        onAccent: Color = Color.White,
        content: Color = Color.White.copy(alpha = 0.78f),
        track: Color = Color.White.copy(alpha = 0.08f),
    ) = LiquidSegmentedColors(accent, onAccent, content, track)
}

/**
 * La pestaña activa de la píldora como selector (Día / Semana / Mes): lente de acento que viaja con
 * spring(0.62, 420), se estira con la velocidad y, arrastrada, crece ×1,15 · ×1,35 y se vuelve lupa con las
 * etiquetas ampliadas ×1,12.
 *
 * Dentro de una pantalla deja [backdrop] a null (el fondo es [LiquidSegmentedColors.track]); si el selector
 * flota sobre el contenido (hermano del `glassSource`), pásale el backdrop y su fondo será cristal.
 */
@Composable
fun LiquidSegmented(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    colors: LiquidSegmentedColors = LiquidSegmentedDefaults.colors(),
    backdrop: GlassBackdrop? = null,
    glass: LiquidGlassStyle = LiquidGlassStyle.Dark,
    height: Dp = LiquidSegmentedDefaults.Height,
    textStyle: TextStyle = LiquidSegmentedDefaults.TextStyle,
) {
    val n = items.size
    if (n == 0) return
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val select by rememberUpdatedState(onSelect)
    val current by rememberUpdatedState(selectedIndex.coerceIn(0, n - 1))
    val surfaceBackdrop = rememberGlassBackdrop()
    val rowBackdrop = rememberGlassBackdrop()
    BoxWithConstraints(modifier.fillMaxWidth().height(height)) {
        val pad = with(density) { 3.dp.toPx() }
        val segW = (constraints.maxWidth - 2 * pad) / n
        val segH = with(density) { (height - 6.dp).toPx() }
        val x = remember { Animatable(segW * (current + 0.5f)) }
        val press = remember { Animatable(0f, 0.001f) }
        var dragging by remember { mutableStateOf(false) }
        val slop = with(density) { 8.dp.toPx() }
        LaunchedEffect(segW) {
            snapshotFlow { dragging to segW * (current + 0.5f) }.collectLatest { (d, cx) -> if (!d) x.animateTo(cx, LiquidSprings.travel) }
        }
        val holding by remember(segW) { derivedStateOf { dragging || (x.isRunning && abs(x.value - segW * (current + 0.5f)) > slop) } }
        LaunchedEffect(Unit) {
            snapshotFlow { holding }.collectLatest { h -> press.animateTo(if (h) 1f else 0f, if (h) LiquidSprings.press else LiquidSprings.release) }
        }

        // Fondo en su propia capa sin hijos (la lente lo refracta al pulsar).
        Box(
            Modifier.matchParentSize().glassSource(surfaceBackdrop)
                .then(if (backdrop != null) Modifier.liquidGlass(backdrop, CircleShape, glass) else Modifier.background(colors.track, CircleShape))
        )
        val row: @Composable (Boolean) -> Unit = { interactive ->
            Row(Modifier.fillMaxSize().padding(3.dp)) {
                items.forEachIndexed { i, label ->
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clip(CircleShape).then(
                            if (interactive) Modifier.clickable(interactionSource = null, indication = null, role = Role.Tab, onClickLabel = label) {
                                if (i != current) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); select(i) }
                            } else Modifier
                        ),
                        contentAlignment = Alignment.Center,
                    ) { Text(label, style = textStyle, color = if (interactive) colors.content else colors.onAccent, maxLines = 1) }
                }
            }
        }
        row(true)
        // Copia de las etiquetas: lo que la lente deja ver (en acento y ampliado al pulsar).
        Box(
            Modifier.matchParentSize().clearAndSetSemantics {}.alpha(0f).glassSource(rowBackdrop)
                .graphicsLayer {
                    val s = lerp(1f, 1.12f, press.value); scaleX = s; scaleY = s
                    transformOrigin = TransformOrigin((pad + x.value) / size.width.coerceAtLeast(1f), 0.5f)
                }
                .graphicsLayer { colorFilter = if (press.value > 0.01f) ColorFilter.tint(colors.accent) else null }
        ) { row(false) }

        val place = Modifier
            .offset { IntOffset((pad + x.value - segW / 2f).roundToInt(), pad.roundToInt()) }
            .layout { m, _ ->
                val p = press.value
                val v = (abs(x.velocity) / 5000f).coerceAtMost(0.22f)
                val gw = (segW * lerp(1f, 1.15f, p) * (1f + v)).roundToInt().coerceAtLeast(1)
                val gh = (segH * lerp(1f, 1.35f, p) * (1f - 0.45f * v)).roundToInt().coerceAtLeast(1)
                val child = m.measure(Constraints.fixed(gw, gh))
                val bw = segW.roundToInt().coerceAtLeast(1)
                val bh = segH.roundToInt().coerceAtLeast(1)
                layout(bw, bh) { child.place((bw - gw) / 2, (bh - gh) / 2) }
            }
        val screen = remember(backdrop) { backdrop?.let { listOf(GlassSource(it)) } ?: emptyList() }
        val rowOnly = remember(rowBackdrop) { listOf(GlassSource(rowBackdrop)) }
        Box(
            place.glassSurface({
                val p = press.value.coerceIn(0f, 1f)
                if (p <= 0f) screen else screen + GlassSource(surfaceBackdrop, p)
            }, CircleShape) {
                val p = press.value.coerceIn(0f, 1f)
                lensLook(p, colors.accent.copy(alpha = colors.accent.alpha * (1f - 0.82f * p)), zoom = 1f, blur = 3.dp, saturation = 1.6f, fallback = colors.accent)
            }
        )
        Box(place.glassSurface({ rowOnly }, CircleShape) {
            val p = press.value.coerceIn(0f, 1f)
            GlassLook(refractionHeight = 8.dp.toPx() * p, refractionAmount = 10.dp.toPx() * p, dispersion = 0.5f, depthEffect = true, fallback = Color.Transparent)
        })
        // Zona táctil de la lente: su hueco, no el dibujo ampliado.
        Box(
            Modifier.offset { IntOffset((pad + x.value - segW / 2f).roundToInt(), pad.roundToInt()) }
                .size(with(density) { segW.toDp() }, height - 6.dp)
                .clearAndSetSemantics {}
                .pointerInput(n, segW) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        dragging = true
                        try {
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                val dx = ch.positionChange().x
                                if (dx != 0f) {
                                    ch.consume()
                                    val nx = (x.value + dx).coerceIn(segW / 2f, segW * (n - 0.5f))
                                    scope.launch { x.snapTo(nx) }
                                }
                            }
                            val target = (x.value / segW).toInt().coerceIn(0, n - 1)
                            if (target != current) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            select(target)
                        } finally { dragging = false }
                    }
                }
        )
    }
}

/* ═════════════ 2 · Interruptor líquido ═════════════ */

/**
 * Interruptor de 64×28 dp. Al pulsar, la bolita blanca crece ×1,5 por encima del raíl y se vuelve una lupa que
 * amplía el raíl ×1,35 con el canto en arcoíris; se arrastra, se estira con la velocidad y rebota al llegar
 * con spring(0.6, 500). Va dentro de las pantallas: solo refracta su propio raíl.
 */
@Composable
fun LiquidToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Color(0xFF34C759),
    off: Color = Color(0x5C787880),
    enabled: Boolean = true,
    label: String? = null,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val change by rememberUpdatedState(onCheckedChange)
    val isOn by rememberUpdatedState(checked)
    val pos = remember { Animatable(if (checked) 1f else 0f, 0.001f) }
    val press = remember { Animatable(0f, 0.001f) }
    val sx = remember { Animatable(1f, 0.001f) }
    val sy = remember { Animatable(1f, 0.001f) }
    val trackBackdrop = rememberGlassBackdrop()
    var dragging by remember { mutableStateOf(false) }
    var settleJob by remember { mutableStateOf<Job?>(null) }

    fun pressed(on: Boolean) {
        scope.launch { press.animateTo(if (on) 1f else 0f, if (on) LiquidSprings.press else LiquidSprings.release) }
        scope.launch { sx.animateTo(if (on) 1.5f else 1f, LiquidSprings.scaleX) }
        scope.launch { sy.animateTo(if (on) 1.5f else 1f, LiquidSprings.scaleY) }
    }
    // Viaja al lado elegido y suelta la lupa cuando casi ha llegado (como LensDrag.release).
    fun settle(on: Boolean) {
        settleJob?.cancel()
        settleJob = scope.launch {
            val t = if (on) 1f else 0f
            launch { pos.animateTo(t, LiquidSprings.toggle) }
            snapshotFlow { abs(pos.value - t) < 0.08f }.first { it }
            pressed(false)
        }
    }
    LaunchedEffect(checked) { if (!dragging && pos.targetValue != (if (checked) 1f else 0f)) settle(checked) }

    Box(
        modifier
            .size(64.dp, 28.dp)
            .semantics {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
                label?.let { stateDescription = it }
                onClick { if (enabled) change(!isOn); true }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val range = (64.dp - 4.dp - 38.dp).toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    settleJob?.cancel()
                    dragging = true
                    pressed(true)
                    var moved = false
                    var total = 0f
                    var finished = false
                    try {
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            val dx = ch.positionChange().x
                            total += dx
                            if (abs(total) > viewConfiguration.touchSlop) moved = true
                            if (moved && dx != 0f) {
                                ch.consume()
                                scope.launch { pos.snapTo((pos.value + dx / range).coerceIn(0f, 1f)) }
                            }
                        }
                        val target = if (moved) pos.value > 0.5f else !isOn
                        if (target != isOn) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); change(target) }
                        settle(target)
                        finished = true
                    } finally {
                        dragging = false
                        if (!finished) settle(isOn)
                    }
                }
            }
    ) {
        Box(
            Modifier.matchParentSize().glassSource(trackBackdrop).drawBehind {
                drawRoundRect(lerpColor(off, accent, pos.value.coerceIn(0f, 1f)), cornerRadius = CornerRadius(size.height / 2f))
            }
        )
        val trackOnly = remember(trackBackdrop) { listOf(GlassSource(trackBackdrop)) }
        Box(
            Modifier
                .layout { m, c ->
                    val v = (pos.velocity / 12f).coerceIn(-0.2f, 0.2f)
                    val w = (38.dp.toPx() * sx.value * (1f + abs(v))).roundToInt().coerceAtLeast(1)
                    val h = (24.dp.toPx() * sy.value * (1f - 0.4f * abs(v))).roundToInt().coerceAtLeast(1)
                    val cx = 2.dp.toPx() + 19.dp.toPx() + pos.value * (64.dp - 4.dp - 38.dp).toPx()
                    val cy = c.maxHeight / 2f
                    val child = m.measure(Constraints.fixed(w, h))
                    layout(c.maxWidth, c.maxHeight) { child.place((cx - w / 2f).roundToInt(), (cy - h / 2f).roundToInt()) }
                }
                .glassSurface({ if (press.value <= 0f) emptyList() else trackOnly }, CircleShape) {
                    val p = press.value.coerceIn(0f, 1f)
                    lensLook(p, Color.White.copy(alpha = 1f - 0.9f * p), zoom = 1.35f, band = 8.dp, amount = 9.dp, saturation = 1.3f, restShadow = 0.6f, fallback = Color.White)
                }
        )
    }
}

/* ═════════════ 3 · Deslizador de cristal ═════════════ */

@Immutable
data class LiquidSliderColors(val accent: Color, val track: Color, val ticks: Color, val bubble: Color, val onBubble: Color)

object LiquidSliderDefaults {
    fun colors(
        accent: Color = Color(0xFF0A84FF),
        track: Color = Color(0x52787880),
        ticks: Color = Color.White.copy(alpha = 0.45f),
        bubble: Color = Color(0xE61C1C1E),
        onBubble: Color = Color.White,
    ) = LiquidSliderColors(accent, track, ticks, bubble, onBubble)
}

/**
 * Deslizador: la bolita es una lente que, mientras arrastras, crece a 58×38 dp, amplía ×1,6 el raíl y sus
 * marcas y enseña el valor en una burbuja. Un toque en el raíl hace viajar la bolita con spring(0.62, 420).
 * [ticks]: etiquetas bajo el raíl (o vacío) y [tickCount] marcas sin texto.
 */
@Composable
fun LiquidSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    step: Float = 0f,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: LiquidSliderColors = LiquidSliderDefaults.colors(),
    ticks: List<String> = emptyList(),
    tickCount: Int = ticks.size,
    format: (Float) -> String = { ((it * 100).roundToInt() / 100f).toString() },
    label: String? = null,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    fun snap(v: Float): Float {
        val c = v.coerceIn(valueRange)
        return if (step > 0f) (((c - valueRange.start) / step).roundToInt() * step + valueRange.start).coerceIn(valueRange) else c
    }
    val latestValue by rememberUpdatedState(value)
    val change by rememberUpdatedState(onValueChange)
    val finished by rememberUpdatedState(onValueChangeFinished)
    val f = remember { Animatable((value - valueRange.start) / span, 0.0005f) }
    val press = remember { Animatable(0f, 0.001f) }
    val sx = remember { Animatable(1f, 0.001f) }
    val sy = remember { Animatable(1f, 0.001f) }
    var dragging by remember { mutableStateOf(false) }
    val trackBackdrop = rememberGlassBackdrop()
    LaunchedEffect(value) { if (!dragging) f.animateTo((value - valueRange.start) / span, LiquidSprings.travel) }
    fun pressed(on: Boolean) {
        scope.launch { press.animateTo(if (on) 1f else 0f, if (on) LiquidSprings.press else LiquidSprings.release) }
        scope.launch { sx.animateTo(if (on) 58f / 28f else 1f, LiquidSprings.scaleX) }
        scope.launch { sy.animateTo(if (on) 38f / 28f else 1f, LiquidSprings.scaleY) }
    }

    BoxWithConstraints(
        modifier.fillMaxWidth().height(44.dp)
            .semantics {
                label?.let { stateDescription = "$it ${format(value)}" } ?: run { stateDescription = format(value) }
                setProgress { t -> change(snap(t)); true }
            }
            .pointerInput(valueRange, step) {
                val padPx = 14.dp.toPx()
                fun fracAt(x: Float) = ((x - padPx) / (size.width - 2 * padPx)).coerceIn(0f, 1f)
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    dragging = true
                    pressed(true)
                    fun apply(fr: Float, travel: Boolean) {
                        scope.launch { f.animateTo(fr, if (travel) LiquidSprings.travel else LiquidSprings.follow) }
                        val v = snap(valueRange.start + fr * span)
                        if (v != latestValue) {
                            if (step > 0f && span / step <= 40f) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            change(v)
                        }
                    }
                    apply(fracAt(down.position.x), true)
                    try {
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            if (ch.positionChange().x != 0f) { ch.consume(); apply(fracAt(ch.position.x), false) }
                        }
                    } finally {
                        dragging = false
                        pressed(false)
                        scope.launch { f.animateTo((latestValue - valueRange.start) / span, LiquidSprings.travel) }
                        finished?.invoke()
                    }
                }
            }
    ) {
        val pad = 14.dp
        val tw = maxWidth - pad * 2
        // Raíl, relleno y marcas: capa propia que la lente amplía.
        Box(Modifier.matchParentSize().glassSource(trackBackdrop).drawBehind {
            val p = pad.toPx()
            val w = size.width - 2 * p
            val top = 19.dp.toPx()
            val th = 6.dp.toPx()
            drawRoundRect(colors.track, androidx.compose.ui.geometry.Offset(p, top), androidx.compose.ui.geometry.Size(w, th), CornerRadius(th / 2))
            drawRoundRect(colors.accent, androidx.compose.ui.geometry.Offset(p, top), androidx.compose.ui.geometry.Size(w * f.value.coerceIn(0f, 1f), th), CornerRadius(th / 2))
            val nt = tickCount
            for (i in 0 until nt) {
                val x = p + w * i / (nt - 1).coerceAtLeast(1)
                drawRoundRect(colors.ticks, androidx.compose.ui.geometry.Offset(x - 1.dp.toPx(), 28.dp.toPx()), androidx.compose.ui.geometry.Size(2.dp.toPx(), 5.dp.toPx()), CornerRadius(1.dp.toPx()))
            }
        }) {
            if (ticks.isNotEmpty()) Box(Modifier.fillMaxSize()) {
                ticks.forEachIndexed { i, t ->
                    Text(
                        t, fontSize = 10.sp, color = colors.ticks.copy(alpha = 0.9f), maxLines = 1,
                        modifier = Modifier.layout { m, c ->
                            val pl = m.measure(Constraints())
                            val x = pad.toPx() + tw.toPx() * i / (ticks.size - 1).coerceAtLeast(1) - pl.width / 2f
                            layout(c.maxWidth, c.maxHeight) { pl.place(x.roundToInt(), 34.dp.roundToPx()) }
                        }
                    )
                }
            }
        }
        val trackOnly = remember(trackBackdrop) { listOf(GlassSource(trackBackdrop)) }
        val center: () -> Float = { with(density) { pad.toPx() + tw.toPx() * f.value.coerceIn(0f, 1f) } }
        Box(
            Modifier.layout { m, c ->
                val v = (f.velocity / 8f).coerceIn(-0.2f, 0.2f)
                val w = (28.dp.toPx() * sx.value * (1f + abs(v))).roundToInt().coerceAtLeast(1)
                val h = (28.dp.toPx() * sy.value * (1f - 0.4f * abs(v))).roundToInt().coerceAtLeast(1)
                val child = m.measure(Constraints.fixed(w, h))
                layout(c.maxWidth, c.maxHeight) { child.place((center() - w / 2f).roundToInt(), (c.maxHeight / 2f - h / 2f).roundToInt()) }
            }.glassSurface({ if (press.value <= 0f) emptyList() else trackOnly }, CircleShape) {
                val p = press.value.coerceIn(0f, 1f)
                lensLook(p, Color.White.copy(alpha = 1f - 0.92f * p), zoom = 1.6f, band = 9.dp, amount = 11.dp, saturation = 1.4f, restShadow = 0.7f, fallback = Color.White)
            }
        )
        // Burbuja con el valor, sobre la bolita mientras se arrastra.
        Text(
            format(value), color = colors.onBubble, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            modifier = Modifier
                .layout { m, c ->
                    val pl = m.measure(Constraints())
                    layout(c.maxWidth, c.maxHeight) {
                        pl.placeWithLayer((center() - pl.width / 2f).roundToInt(), (-30.dp.toPx() - 8.dp.toPx() * press.value).roundToInt()) {
                            alpha = press.value.coerceIn(0f, 1f)
                            val s = lerp(0.6f, 1f, press.value.coerceIn(0f, 1f)); scaleX = s; scaleY = s
                        }
                    }
                }
                .background(colors.bubble, CircleShape)
                .padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

/* ═════════════ 4 · Botón de cristal ═════════════ */

/**
 * Botón de cristal líquido que flota sobre el contenido: canto refractado, brillo bajo el dedo, estiramiento
 * elástico al pulsar y arrastrar, y [tint] que sube del 16 % al 34 % al pulsar.
 */
@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    glass: LiquidGlassStyle = LiquidGlassStyle.Dark,
    tint: Color = Color.Unspecified,
    contentColor: Color = Color.White,
    onClickLabel: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val glow = remember(scope) { GlassGlow(scope) }
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(
            modifier
                .graphicsLayer(elasticStretch(glow))
                .liquidGlassTinted(backdrop, shape, glass) {
                    if (tint == Color.Unspecified) Color.Transparent else tint.copy(alpha = tint.alpha * lerp(0.16f, 0.34f, glow.pressProgress))
                }
                .clip(shape)
                .then(glow.gestureModifier)
                .then(glow.drawModifier)
                .clickable(interactionSource = null, indication = null, role = Role.Button, onClickLabel = onClickLabel, onClick = onClick),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

/* ═════════════ 5 · Chip de cristal ═════════════ */

/**
 * Etiqueta de cristal (canto de 10 dp) que entra con un «pop» spring(0.5, 420), escalonable con [popDelayMillis].
 * Sobre una foto: graba la foto con `Modifier.glassSource(fotoBackdrop)` y pasa ese backdrop aquí (hermanos).
 */
@Composable
fun LiquidChip(
    text: String,
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    glass: LiquidGlassStyle = LiquidGlassStyle.Dark.copy(blur = 6.dp, refractionHeight = 10.dp, refractionAmount = 10.dp),
    tint: Color = Color.Unspecified,
    contentColor: Color = Color.White,
    popDelayMillis: Int = 0,
    textStyle: TextStyle = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold),
) {
    val pop = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { delay(popDelayMillis.toLong()); pop.animateTo(1f, spring(0.5f, 420f)) }
    Row(
        modifier
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = ((pop.value - 0.6f) / 0.3f).coerceIn(0f, 1f) }
            .liquidGlass(backdrop, CircleShape, glass, tint)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = contentColor, modifier = Modifier.size(16.dp))
        Text(text, style = textStyle, color = contentColor, maxLines = 1)
    }
}

/* ═════════════ 6 · Insignia que salta ═════════════ */

/**
 * Contador que aparece con spring(0.4, 520) como el icono de la barra; al cambiar se aplasta y suelta una gotita
 * que sube y se desvanece. [dot]: punto sin número.
 */
@Composable
fun LiquidBadge(
    count: Int,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFFF3B30),
    contentColor: Color = Color.White,
    max: Int = 99,
    dot: Boolean = false,
) {
    val visibleNow = count > 0 || dot
    val scale = remember { Animatable(if (visibleNow) 1f else 0f, 0.002f) }
    val squash = remember { Animatable(1f) }
    val drop = remember { Animatable(1f) }
    var last by remember { mutableIntStateOf(count) }
    var shown by remember { mutableIntStateOf(count) }
    LaunchedEffect(count, dot) {
        val prev = last
        last = count
        when {
            !(count > 0 || dot) -> scale.animateTo(0f, spring(0.8f, 600f))
            prev <= 0 && !dot -> { shown = count; scale.snapTo(0f); scale.animateTo(1f, LiquidSprings.pop) }
            count != prev -> {
                shown = count
                if (scale.value < 1f) launch { scale.animateTo(1f, LiquidSprings.pop) }
                launch { squash.snapTo(1.28f); squash.animateTo(1f, LiquidSprings.pop) }
                launch { drop.snapTo(0f); drop.animateTo(1f, spring(0.7f, 220f)) }
            }
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(8.dp).graphicsLayer {
                val t = drop.value
                translationY = -22.dp.toPx() * t
                val s = lerp(0.9f, 0.3f, t); scaleX = s; scaleY = s
                alpha = 1f - t
            }.background(color, CircleShape)
        )
        Box(
            Modifier
                .graphicsLayer {
                    val s = scale.value.coerceAtLeast(0f)
                    scaleX = s * squash.value
                    scaleY = s * (2f - squash.value)
                    alpha = if (s <= 0.001f) 0f else 1f
                }
                .defaultMinSize(if (dot) 10.dp else 18.dp, if (dot) 10.dp else 18.dp)
                .background(color, CircleShape)
                .padding(horizontal = if (dot) 0.dp else 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (!dot) Text(if (shown > max) "$max+" else "$shown", fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, color = contentColor)
        }
    }
}

/** Espacio fijo (para dejar sitio a piezas flotantes en las cabeceras). */
@Composable
internal fun Gap(w: Dp) = Spacer(Modifier.width(w))
