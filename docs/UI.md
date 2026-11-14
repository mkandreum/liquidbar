# LiquidUI: piezas de cristal para la interfaz (Android y web)

Trece piezas con **el mismo cristal, los mismos muelles y la misma lupa** que la barra. Cada una existe en Compose (`io.github.mkandreum.liquidbar.ui`) y en web (`web/liquidui.js` + `web/liquidui.css`, sobre `liquidbar.js` ≥ 1.2.0), con los mismos valores. Para React hay un adaptador único: `web/react/LiquidUI.tsx`.

> Instrucciones para un asistente: si se pide «aplica las piezas de cristal», «pon la isla / el abanico / la lupa…» o se pega la lista de propuestas, sigue esta guía. Cambia el material, no el comportamiento: cada app conserva sus colores, textos y acciones. Ajustes de las piezas, siempre en este repo, y luego se vuelve a copiar.

Skill de diseño (para asistentes): [`skill/liquidui/`](../skill/liquidui/SKILL.md) — instalar con `ln -s ~/Descargas/liquidUI/skill/liquidui ~/.claude/skills/liquidui`.

Demos: Android → módulo `sample/` (no se sube; `./gradlew :sample:assembleDebug`). Web → `web/demo-ui.html`.

---

## 1. Las piezas

| # | Pieza | Compose | Web (vanilla) | React | Qué hace |
|---|---|---|---|---|---|
| 1 | Selector segmentado | `LiquidSegmented` | `LiquidUI.segmented` | `<LiquidSegmented>` | lente de acento que viaja con spring(0.62, 420); arrastrada crece ×1,15·×1,35 y es lupa (etiquetas ×1,12) |
| 2 | Interruptor | `LiquidToggle` | `LiquidUI.toggle` | `<LiquidToggle>` | 64×28; la bolita crece ×1,5 y amplía el raíl ×1,35; rebota con spring(0.6, 500) |
| 3 | Deslizador | `LiquidSlider` | `LiquidUI.slider` | `<LiquidSlider>` | bolita que al arrastrar es lupa de 58×38 (×1,6) con burbuja del valor |
| 4 | Botón de cristal | `LiquidGlassButton` | `LiquidUI.glassButton(el)` | `useGlassButton(ref)` | canto refractado, brillo bajo el dedo, estiramiento elástico, tinte 16 → 34 % al pulsar |
| 5 | Chip sobre foto | `LiquidChip` | `LiquidUI.glassChip(el)` | `<LiquidChip>` | etiqueta de cristal (canto 10 dp) que entra con «pop» spring(0.5, 420) |
| 6 | Insignia | `LiquidBadge` | `LiquidUI.badge(el)` | `<LiquidBadge>` | aparece con spring(0.4, 520); al cambiar se aplasta y suelta una gotita |
| 7 | Isla | `LiquidIsland` + `LiquidIslandState` | `LiquidUI.island().show()` | `showIsland()` | píldora arriba: ancho spring(0.62, 380) y 70 ms después alto spring(0.7, 300) |
| 8 | Abanico | `LiquidFan` | `LiquidUI.fan(btn)` | `useFan(ref)` | mantener el +: burbujas en arco (30 ms, spring 0.55, 380); arrastrar y soltar elige |
| 9 | Lupa de gráficas | `LiquidChartLens` | `LiquidUI.chartLens(el)` | `useChartLens(ref)` | pulsación larga: lupa de 104 dp (×1,8) sobre el dato y burbuja con el valor |
| 10 | Buscar que se transforma | `LiquidSearchMorph` | `LiquidUI.searchMorph(el)` | `<LiquidSearch>` | círculo que se estira a campo con spring(0.7, 300); al abrir, pliega la barra |
| 11 | Menú de pulsación larga | `LiquidContextMenuArea` + `LiquidMenuHost` | `LiquidUI.contextMenu(el)` | `useContextMenu` / `menuRef` | la tarjeta se eleva ×1,03 sobre el fondo desenfocado y salen píldoras de cristal (35 ms) |
| 12 | Tirar para recargar | `LiquidPullRefresh` | `LiquidUI.pullRefresh()` | `usePullRefresh()` | gota de cristal que se estira (tanh) y cae con rebote spring(0.45, 300) |
| 13 | Hoja inferior | `LiquidSheet` / `LiquidPortalSheet` | `LiquidUI.sheet()` | `<LiquidSheet>` | hoja propia con canto refractado, asa elástica y alturas con spring(0.75, 300) |

Las lentes que amplían (interruptor, deslizador, abanico, gráficas, recargar) usan el aumento real: en Android, `GlassLook.zoom` del shader; en web, `MagnifyFilter` (mapa SVG que amplía y refracta a la vez).

## 2. La regla del cristal en Android (la que más falla)

Un cristal no puede refractar algo que lo contiene. Por eso hay dos familias:

| Vive… | Piezas | Qué refracta |
|---|---|---|
| **Dentro de las pantallas** | segmentado (sin `backdrop`), interruptor, deslizador, chip, lupa de gráficas, recargar | una capa propia y hermana: el raíl, la foto (`glassSource(fotoBackdrop)`), la gráfica, la lista |
| **Flotando (Box raíz)** | isla, abanico, buscar, menú (`LiquidMenuHost`), hoja (`LiquidSheet` / `LiquidSheetPortalHost`), botón flotante | el `backdrop` de la pantalla (`glassSource` en el contenedor de pantallas) |

### Raíz de la app

```kotlin
val backdrop = rememberGlassBackdrop()
val island = rememberLiquidIslandState()
val menu = rememberLiquidMenuState()
val sheets = rememberLiquidSheetPortal()
CompositionLocalProvider(LocalLiquidIsland provides island, LocalLiquidMenuState provides menu, LocalLiquidSheetPortal provides sheets) {
    Box(Modifier.fillMaxSize()) {
        Pantallas(Modifier.fillMaxSize().glassSource(backdrop).background(fondo))
        // barra, botones flotantes…
        LiquidMenuHost(menu, backdrop, glass = estilo, contentColor = texto)
        LiquidSheetPortalHost(sheets, backdrop, glass = estilo.copy(blur = 12.dp), contentColor = texto)
        LiquidIsland(island, backdrop, Modifier.align(Alignment.TopCenter), glass = estilo, contentColor = texto)
    }
}
```

Desde cualquier pantalla: `LocalLiquidIsland.current?.show(...)`, `LiquidContextMenuArea(items) { fila }` y `LiquidPortalSheet(onDismiss) { … }` (sustituye a `ModalBottomSheet` sin mover nada de sitio).

### Errores conocidos (Android)

| Síntoma | Causa | Solución |
|---|---|---|
| Un botón no recibe toques | un hermano encima con `clickable`/`pointerInput`, aunque esté desactivado o invisible, se queda los toques | componerlo solo cuando haga falta (`LiquidSearchMorph` ya lo hace) |
| El campo de buscar queda bajo el teclado | el flotante no se aparta del teclado | `navigationBarsPadding().imePadding()` en su contenedor |
| La lupa de una gráfica se corta al arrastrar | la gráfica tiene su propio arrastre | `onActiveChange = { lupa = it }` y no instalar el arrastre propio mientras `lupa` |
| Al copiar se pierde una mejora local | una app tiene su copia modificada de la librería (ListPro: `LiquidBar.kt` con insignias) | `rsync --exclude LiquidBar.kt` y subir la mejora al repo cuando toque |

## 3. Web

```html
<link rel="stylesheet" href="/liquidbar/liquidbar.css"><link rel="stylesheet" href="/liquidbar/liquidui.css">
<script src="/liquidbar/liquidbar.js"></script><script src="/liquidbar/liquidui.js"></script>
```

React: copia `web/react/LiquidUI.tsx` a la web (p. ej. `src/components/LiquidUI.tsx`). Si la librería no carga, los componentes caen a controles normales (`<input type="checkbox">`, `range`…) y `showIsland()` devuelve `false` (la web enseña su aviso de siempre).

- Avisos: `if (showIsland({ title, text, icon: icons.check, accent })) return;` al principio de la función de toast de la app.
- Filas de una lista: `<div ref={menuRef([{ label: 'Editar', icon: icons.edit, onSelect }])}>`.
- Elementos que aparecen después (pestañas): pasa `deps` a `useChartLens(ref, fn, opts, [pestaña])`.
- `window.LiquidBar` lo declara cada web a su manera: el adaptador solo declara `window.LiquidUI`.

## 4. Dónde está aplicada

| App | Android | Web |
|---|---|---|
| ZaveBill | isla (avisos con deshacer y metas), abanico en el + (Variable / Fijo / Ahorro), lupa en las barras del historial, interruptores, deslizadores del reparto, `Segmented` líquido, menú en los movimientos, hojas por portal, botón de ajustes | — (no tiene web) |
| DiviFriends | isla (avisos), abanico en el + (Gasto / Invitar / Unirme / Agenda), lupa en la gráfica de onda, `DiviSwitch` y `SegmentedControl` líquidos, insignia de Agenda, menú en los gastos, tirar para sincronizar, hojas por portal | isla, abanico en el +, lupa en la gráfica de onda, `Switch` y `Segmented` líquidos, menú en los gastos, tirar para recargar |
| ListPro | isla (avisos), ficha de producto como hoja con chips sobre la foto, «Cuentas / Estadísticas» segmentado, lupa en las barras mensuales, deslizador del presupuesto, menú en la cesta, buscar junto a la barra plegada, tirar para recargar | isla, chips en la ficha, segmentado y lupa en estadísticas, deslizador del presupuesto, menú en la cesta |
| VoltBody | isla (avisos y descanso de la sesión), lupa en las barras (`MockBars`), interruptor de recordatorios, «Diario / Semanal» segmentado, menú en las comidas, botones de cristal, tirar para recargar, hoja modal por portal | isla (avisos y descanso), interruptor, lupa en las barras, deslizador de la bienvenida |
| GastroOS | — (no tiene app) | isla (avisos, «Mesa X lista», llamadas de sala), chips de precio y stock sobre las fotos de la carta, insignias (cesta, platos listos), interruptores de impresoras, lupa en ventas por hora, tirar para recargar en sala |

No aplicadas, a propósito: abanico en ListPro y GastroOS (no hay botón +), buscar que se transforma en las webs (sus buscadores están dentro de la cabecera), hojas en las webs (son de React y ya animan; `<LiquidSheet>` queda listo).
