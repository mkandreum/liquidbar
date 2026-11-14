# Chuleta de las 13 piezas

Imports Compose: `io.github.mkandreum.liquidbar.ui.*`, `io.github.mkandreum.liquidbar.glass.*`, `io.github.mkandreum.liquidbar.LiquidGlassStyle`.
Web vanilla: `const U = window.LiquidUI;` (cada llamada devuelve un controlador con `destroy()`).
React: `import { … } from './LiquidUI'`. Si la librería no carga, los componentes caen a controles normales.

## Raíz (una sola vez)

### Compose

```kotlin
val backdrop = rememberGlassBackdrop()
val island = rememberLiquidIslandState()
val menu = rememberLiquidMenuState()
val sheets = rememberLiquidSheetPortal()
val glass = LiquidGlassStyle.Dark   // .Light en apps claras; ajusta tint al fondo de la app
CompositionLocalProvider(
    LocalLiquidIsland provides island,
    LocalLiquidMenuState provides menu,
    LocalLiquidSheetPortal provides sheets,
) {
    Box(Modifier.fillMaxSize()) {
        Pantallas(Modifier.fillMaxSize().glassSource(backdrop).background(fondo))
        Barra(/* LiquidBar o LiquidPillBar con backdrop */)
        LiquidMenuHost(menu, backdrop, glass = glass, contentColor = texto)
        LiquidSheetPortalHost(sheets, backdrop, glass = glass.copy(blur = 12.dp), contentColor = texto)
        LiquidIsland(island, backdrop, Modifier.align(Alignment.TopCenter).statusBarsPadding(), glass = glass, contentColor = texto)
    }
}
```

Orden de pintado (de abajo arriba): pantallas → barra/flotantes → menú → hojas → isla.

### Web

```html
<link rel="stylesheet" href="/liquidbar/liquidbar.css"><link rel="stylesheet" href="/liquidbar/liquidui.css">
<script src="/liquidbar/liquidbar.js"></script><script src="/liquidbar/liquidui.js"></script>
```

La isla y la gota de recargar se crean solas sobre `document.body`. Refracción y aumento solo en Chromium; en Safari/Firefox queda cristal desenfocado.

---

## 1 · Selector segmentado

Para 2-4 vistas de una misma pantalla («Diario / Semanal», «Cuentas / Estadísticas»). Sustituye a `TabRow`, `SegmentedButton` o pestañas propias.

```kotlin
LiquidSegmented(
    items = listOf("Diario", "Semanal"), selectedIndex = vista, onSelect = { vista = it },
    colors = LiquidSegmentedColors(accent = Acento, onAccent = Color.White, content = Texto, track = Raíl),
)
```
```js
U.segmented(el, { items: ['Día', 'Semana'], selectedIndex: 0, accent: '#0A84FF', onSelect: (i) => {} });
```
```tsx
<LiquidSegmented items={['Día', 'Semana']} selected={i} onSelect={setI} accent="#0A84FF" />
```

## 2 · Interruptor

Sustituye a todo `Switch`. Etiqueta siempre (accesibilidad).

```kotlin
LiquidToggle(checked = on, onCheckedChange = { on = it }, accent = Acento, label = "Recordatorios")
```
```js
U.toggle(button, { checked: true, label: 'Notificaciones', accent: '#30D158', onChange: (v) => {} });  // .setChecked(v)
```
```tsx
<LiquidToggle checked={on} onChange={setOn} label="Notificaciones" accent="#30D158" />
```

## 3 · Deslizador

Para importes, porcentajes y cantidades. Con `format` la burbuja muestra el valor real.

```kotlin
LiquidSlider(
    value = presupuesto, onValueChange = { presupuesto = it }, valueRange = 0f..1000f, step = 10f,
    tickCount = 5, format = { "${it.roundToInt()} €" }, label = "Presupuesto",
)
```
```js
U.slider(el, { min: 0, max: 100, value: 40, step: 1, ticks: 11, label: 'Volumen', format: (v) => `${v} %`, onInput, onChange });  // .setValue(v)
```
```tsx
<LiquidSlider value={v} min={0} max={100} onInput={setV} format={(v) => `${v} %`} label="Volumen" />
```

## 4 · Botón de cristal

Botones flotantes o de icono sobre contenido (ajustes, favorito, el +). Flotante → `backdrop` de la raíz.

```kotlin
LiquidGlassButton(onClick = { … }, backdrop = backdrop, shape = CircleShape, tint = Acento, onClickLabel = "Ajustes") {
    Icon(Icons.Rounded.Settings, null, tint = Color.White)
}
```
```js
U.glassButton(el, { tint: '#0A84FF', radius: null /* null = círculo */ });
```
```tsx
const ref = useRef<HTMLButtonElement>(null); useGlassButton(ref, { tint: '#0A84FF' });
```

## 5 · Chip sobre foto

Precio, stock o categoría encima de una foto. La foto es su propia capa: `Modifier.glassSource(fotoBackdrop)` en la imagen y el chip encima como hermano.

```kotlin
val foto = rememberGlassBackdrop()
Box {
    AsyncImage(…, Modifier.fillMaxWidth().glassSource(foto))
    Row(Modifier.align(Alignment.BottomStart).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LiquidChip("3,49 €", foto)
        LiquidChip("Quedan 3", foto, tint = Color(0xFFFF9F0A), popDelayMillis = 80)
    }
}
```
```js
document.querySelectorAll('.chip').forEach((c, i) => U.glassChip(c, { delay: 200 + i * 80 }));
```
```tsx
<div className="chips"><LiquidChip delay={200}>3,49 €</LiquidChip></div>
```

## 6 · Insignia

Contadores que cambian (cesta, platos listos, avisos). `dot = true` para un punto sin número.

```kotlin
Box { Icon(…); LiquidBadge(count = n, Modifier.align(Alignment.TopEnd).offset(6.dp, (-6).dp), color = Rojo) }
```
```js
const b = U.badge(el, { value: 3 }); b.set(4);
```
```tsx
<LiquidBadge value={cesta.length} color="#FF453A" />
```

## 7 · Isla

Sustituye a toasts y snackbars, y muestra estados vivos (descanso, «Mesa 7 lista», sincronizando). Con `onTap` sirve para deshacer. `durationMillis = 0` la deja fija hasta `hide()`.

```kotlin
LocalLiquidIsland.current?.show(LiquidIslandMessage(
    title = "Gasto borrado", text = "Toca para deshacer", icon = Icons.Rounded.Undo,
    accent = Acento, onTap = { deshacer() },
))
```
```js
U.island().show({ title: 'Mesa 7 lista', text: 'Cocina → sala', icon: svgString, accent: '#30D158', duration: 3200, onTap });
```
```tsx
// al principio de la función de toast de la app:
if (showIsland({ title, text, icon: icons.check, accent })) return;
```

## 8 · Abanico

El + de la barra: toque = acción principal; mantener = burbujas en arco; arrastrar y soltar = elegir. Máximo 5 burbujas. Va en el `Box` raíz con el `backdrop`.

```kotlin
LiquidFan(
    backdrop = backdrop, onClick = { nuevoGasto() }, onClickLabel = "Nuevo",
    items = listOf(
        LiquidFanItem("Gasto", Icons.Rounded.Add) { nuevoGasto() },
        LiquidFanItem("Invitar", Icons.Rounded.PersonAdd) { invitar() },
    ),
    tint = Acento, modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp),
) { Icon(Icons.Rounded.Add, null, tint = Color.White) }
```
```js
U.fan(btn, { items: [{ label: 'Gasto', icon: svg }], onSelect: (it) => {} });
```
```tsx
useFan(ref, [{ label: 'Gasto', icon: icons.plus, onSelect: nuevo }], { angles: [180, 270] });
```

## 9 · Lupa de gráficas

Envuelve una gráfica: pulsación larga → lupa ×1,8 sobre el dato + burbuja con el valor. `valueAt` traduce x a dato; `y` (0-1) centra la lupa en la altura del dato.

```kotlin
var lupa by remember { mutableStateOf(false) }
LiquidChartLens(
    valueAt = { x, w -> val i = ((x / w) * datos.size).toInt().coerceIn(datos.indices)
        LiquidChartPoint("${datos[i].total} €", datos[i].mes, y = 1f - datos[i].frac, key = i) },
    onActiveChange = { lupa = it },
) { BarChart(datos, arrastreActivo = !lupa) }
```
```js
U.chartLens(el, { valueAt: (x, rect) => ({ key: i, value: '120 €', label: 'Mar', y: 0.4 }) });
```
```tsx
useChartLens(ref, (x, rect) => ({ key, value, label }), {}, [pestaña]);
```

## 10 · Buscar que se transforma

Círculo junto a la barra plegada que se estira a campo y pliega la barra. Necesita `imePadding()` en su contenedor.

```kotlin
LiquidSearchMorph(
    backdrop = backdrop, expanded = buscando, onExpandedChange = { buscando = it; if (it) fold.fold() },
    query = q, onQueryChange = { q = it }, expandedWidth = maxWidth - 32.dp, placeholder = "Buscar productos",
    modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().imePadding().padding(16.dp),
)
```
```js
U.searchMorph(el, { placeholder: 'Buscar', onInput: (q) => {}, onSubmit, onOpen, onClose });
```
```tsx
<LiquidSearch placeholder="Buscar" onInput={setQ} width={() => window.innerWidth - 32} />
```

## 11 · Menú de pulsación larga

Filas con acciones. La fila se eleva ×1,03 sobre el fondo desenfocado y salen píldoras. Destructivo, la última y en rojo. Necesita `LiquidMenuHost` en la raíz.

```kotlin
LiquidContextMenuArea(items = listOf(
    LiquidMenuItem("Editar", Icons.Rounded.Edit) { editar(g) },
    LiquidMenuItem("Borrar", Icons.Rounded.Delete, destructive = true) { borrar(g) },
)) { FilaGasto(g) }
```
```js
U.contextMenu(row, { items: [{ label: 'Editar', icon }, { label: 'Eliminar', icon, destructive: true }], onSelect: (it) => {} });
```
```tsx
<div ref={menuRef([{ label: 'Editar', icon: icons.edit, onSelect: editar }, { label: 'Borrar', icon: icons.trash, destructive: true, onSelect: borrar }])}>…</div>
```

## 12 · Tirar para recargar

Gota de cristal que se estira y cae con rebote. Envuelve el contenedor con scroll.

```kotlin
LiquidPullRefresh(refreshing = vm.cargando, onRefresh = { vm.refresh() }) { Lista() }
```
```js
U.pullRefresh({ onRefresh: () => fetch(…) /* devuelve promesa */ });
```
```tsx
usePullRefresh(() => recargar(), { scroller: listRef.current });
```

## 13 · Hoja inferior

Sustituta directa de `ModalBottomSheet`: se declara donde estaba y se pinta en la raíz (donde el cristal ve la pantalla).

```kotlin
if (abierta) LiquidPortalSheet(onDismiss = { abierta = false }, title = "Filtros", detents = listOf(0.5f, 0.9f)) {
    // mismo contenido que tenía la ModalBottomSheet
}
```
```js
const s = U.sheet({ title: 'Filtros', content: nodo, detents: [0.5, 0.9] }); s.open(); s.close();
```
```tsx
<LiquidSheet open={open} onClose={() => setOpen(false)} title="Filtros">…</LiquidSheet>
```

---

## Errores conocidos

| Síntoma | Causa | Solución |
|---|---|---|
| Cristal gris o vacío | `glassSource` en el padre del cristal, o nada detrás | el contenido es hermano del cristal; pon algo detrás |
| La hoja no refracta | `ModalBottomSheet` vive en otra ventana | `LiquidPortalSheet` + `LiquidSheetPortalHost` |
| Un botón no recibe toques | un hermano encima con `pointerInput`, aunque esté invisible | componerlo solo cuando haga falta |
| El buscador queda bajo el teclado | sin `imePadding()` | `navigationBarsPadding().imePadding()` |
| La lupa de la gráfica se corta | la gráfica tiene arrastre propio | `onActiveChange` y desactivar el arrastre mientras haya lupa |
| Release rota o sin cristal | R8 renombra los `Modifier.Node` | reglas `-keep` del paquete liquidbar |
| Web: el adaptador choca con tipos | `window.LiquidBar` declarado dos veces | el adaptador solo declara `window.LiquidUI` |
| Web: la lupa no ve elementos nuevos | se montaron tras el efecto | pasa `deps` (`[pestaña]`) |
| Web lenta al arrastrar | bucles `requestAnimationFrame` duplicados | liquidbar.js ≥ 1.0.1 (`onFrame` único) |
