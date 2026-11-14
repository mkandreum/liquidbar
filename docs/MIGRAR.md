# Migrar cualquier píldora o barra a LiquidBar

Guía para pasar una barra inferior existente a **LiquidBar** conservando el diseño de cada app (colores,
tipografía, altura, iconos y marca) y ganando todas las funciones de la librería: lente arrastrable con
aumento y tinte, muelles, plegado, estiramiento elástico, brillo y botones de acción separados.

> ¿Es una web? La versión web (misma cápsula, lupa y muelles) se aplica con [WEB.md](WEB.md).

> Regla de oro: **se cambia el material y el comportamiento, no la identidad**. El acento, los iconos y las
> etiquetas son de la app; el cristal, la lente y los gestos son de LiquidBar.

---

## 0. Inventario antes de tocar código

Apunta de la barra actual:

| Qué | Por qué |
|---|---|
| Forma y altura (píldora fina, cápsula ancha, barra completa) | decide el componente |
| Pestañas: icono solo, icono+etiqueta, etiqueta solo | `LiquidPillBar` o `LiquidBar` |
| Botones extra (`+`, buscar, escanear, menú) y su sitio | van **siempre a la derecha, en círculo separado** |
| Gestos (pulsación larga, arrastre, doble toque) | se conservan con `gestures` |
| Plegado al hacer scroll o al cambiar de pestaña | `LiquidBarFoldState` |
| Colores del tema (acento, contenido, superficie) | se mapean a `LiquidPillColors` / `LiquidBarColors` |
| Navegación (Navigation, pager, estado propio) | la barra solo llama a `onSelect` |

---

## 1. Copiar la librería

Dos opciones; elige una y mantenla sincronizada con `mkandreum/liquidUI`:

**Módulo Gradle** (recomendado si el proyecto es multi-módulo):

```kotlin
// settings.gradle.kts
include(":liquidbar")

// app/build.gradle.kts
implementation(project(":liquidbar"))
```

**Paquete copiado** (apps de un solo módulo):

```bash
rsync -a --delete ~/Descargas/liquidUI/liquidbar/src/main/java/io/github/mkandreum/liquidbar/ \
  app/src/main/java/io/github/mkandreum/liquidbar/
```

No actives R8 en el release hasta probar el cristal en un dispositivo real (`isMinifyEnabled = false`).
Si más adelante quieres R8, añade reglas `-keep` para `io.github.mkandreum.liquidbar.**` y prueba el APK
firmado, no solo el debug.

---

## 2. Marcar lo que refracta el cristal

```kotlin
val backdrop = rememberGlassBackdrop()

Box(Modifier.fillMaxSize()) {
    // El contenido de las pantallas, HERMANO de la barra. glassSource ANTES de background.
    Pantallas(
        Modifier
            .fillMaxSize()
            .glassSource(backdrop)
            .background(colorDeFondo)
    )

    // La barra y los botones flotantes van fuera, nunca dentro de Pantallas.
    MiBarra(backdrop, modifier = Modifier.align(Alignment.BottomCenter))
}
```

- `glassSource` **antes** de `background(...)`, o el fondo no se graba y el cristal enseña el contenido
  nítido por debajo (se ve sucio y con fantasmas).
- La barra **nunca** puede ser hija del elemento con `glassSource`: el árbol de render se llamaría a sí
  mismo y la app se cierra.
- Los `ModalBottomSheet`, diálogos y menús viven en otra ventana: no ven la capa; pinta ahí un relleno
  propio.

---

## 3. Elegir el componente

| La barra original es… | Usa | Qué obtienes |
|---|---|---|
| Píldora fina de iconos; la activa se expande con su etiqueta; `+` o buscar al lado | `LiquidPillBar` | lente que viaja con muelle, se estira con la velocidad, crece al pulsar, refracta y aumenta los iconos |
| Cápsula ancha con icono **sobre** etiqueta en todas las pestañas; se pliega al hacer scroll | `LiquidBar` | lente arrastrable, plegado a círculo, acción que se separa, rebote de glifos |
| Otra forma (una barra completa, esquinas propias…) | tu `Row` + `Modifier.liquidGlass` | mismo cristal; la lente se monta con la receta de `PORTAR.md` §9.5 |

**No cambies la altura ni el reparto de pestañas de la app** para que “quepa” la librería: los componentes
aceptan `height`, `labelStyle`, iconos y colores. Si la barra original era más alta o más ancha, pásalo.

---

## 4. Mantener el diseño original

### Colores

```kotlin
// Píldora
colors = LiquidPillDefaults.colors(
    accent = paleta.acento,                 // cápsula activa, igual que antes
    onAccent = paleta.sobreAcento,          // icono/etiqueta sobre la cápsula
    content = paleta.texto2,                // iconos en reposo
)

// Cápsula ancha
colors = LiquidBarColors(
    accent = paleta.acento,                 // pestaña activa
    content = paleta.texto2,                // iconos/etiquetas en reposo
    indicator = Color.White.copy(alpha = 0.55f), // velo del indicador en reposo
    badge = paleta.alerta,                  // opcional: contador
)
```

- El **acento** debe ser el de la marca. No uses el azul por defecto salvo que sea el de la app.
- El **velo del indicador** decide cuánto se ve el fondo dentro del botón en reposo: súbelo si el diseño
  original era más sólido (p. ej. `Color(0xFF808080).copy(alpha = 0.55f)`).
- El **cristal** se tiñe con el color de superficie del tema:

```kotlin
val miCristal = LiquidGlassStyle(
    surface = colorSuperficie.copy(alpha = 0.45f), // tinte de la app
    fallback = colorSuperficie.copy(alpha = 0.96f), // Android < 12
    // blur, saturación y refracción: los del cristal por defecto
)
```

### Tipografía e iconos

- `labelStyle` = el `TextStyle` que ya usaba la barra (tamaño, peso, familia).
- Los iconos son `ImageVector`; si la app usa `Painter`, conviértelo antes o pinta tu propio contenido.
- Mantén el tamaño de icono (24 dp, 26 dp…) y el espaciado entre pestañas que ya tenía.

### Forma

- Para píldoras y cápsulas, no hace falta tocar la forma: ambas usan `CircleShape`.
- Si la barra original tenía esquinas propias, usa `liquidGlass(backdrop, tuShape, tuEstilo)` en tu `Row`
  y conserva tu forma; la lente se puede añadir después.

---

## 5. Botones extra: siempre a la derecha y separados

Regla de diseño de la librería:

- `+`, buscar, escanear, ajustes… van **fuera de la píldora, a la derecha**, en un **círculo propio** de
  la misma altura (64–68 dp), con **10 dp de separación**.
- Si el diseño original tenía el `+` centrado, se **saca a la derecha** al migrar (es lo que se hizo en
  ZaveBill y ListPro).
- El círculo es de cristal, con un toque del acento y el gesto original (incluida pulsación larga).

```kotlin
Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp)
) {
    LiquidPillBar(/* … */)

    OrbAccion(
        backdrop = backdrop,
        icon = Icons.Rounded.Add,
        label = "Añadir",
        accent = paleta.acento,
        onClick = { /* abre el alta */ },
        onLongPress = { /* menú, si lo tenía */ },
    )
}
```

```kotlin
@Composable
fun OrbAccion(
    backdrop: GlassBackdrop,
    icon: ImageVector,
    label: String,
    accent: Color,
    estilo: LiquidGlassStyle,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
) {
    Box(
        Modifier
            .size(64.dp)
            .clip(CircleShape)
            .liquidGlass(backdrop, CircleShape, estilo, tint = accent.copy(alpha = 0.28f))
            .pointerInput(onLongPress) {
                detectTapGestures(onTap = { onClick() }, onLongPress = { onLongPress?.invoke() })
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(26.dp))
    }
}
```

- Si la acción era un `FloatingActionButton`, conserva su icono y añade `elasticStretch` solo si quieres
  el efecto elástico; el cristal ya lleva filo y sombra.
- Para un buscador, el círculo abre el campo o la pantalla de búsqueda; no metas el campo dentro de la
  píldora salvo que el diseño original ya lo tuviera (`center` de `LiquidPillBar`).
- Badges: `LiquidPillBar` acepta `badge` por pestaña; en `LiquidBar` usa `LiquidBarItem.badge` si tu versión
  lo incluye.

---

## 6. Funciones que debes dejar activas

| Función | Dónde se configura |
|---|---|
| Lente que viaja con muelle y se pasa un poco | automática en `LiquidPillBar` |
| Mantener pulsada: crece, se vuelve cristal, refracta y aumenta el icono | automática (usa `holding` si montas la lupa a mano) |
| Arrastrar la lente entre pestañas y asentarla al soltar | automática |
| Estiramiento por velocidad y elasticidad al pulsar | automática |
| Brillo bajo el dedo | `GlassGlow` (ya va dentro) |
| Plegado al hacer scroll / al cambiar de pestaña | `LiquidBarFoldState` + `nestedScroll` |
| Acción que se separa al plegar | `detachAction = true` en `LiquidBar` |
| Rebote del glifo al activar | automático |
| Hápticos | los pone la app en `onSelect` |
| Pulsación larga en una pestaña o en la acción | `LiquidBarAction.gestures` |

### Plegado

```kotlin
val fold = rememberLiquidBarFoldState(mode = LiquidFoldMode.ByDirection)
LaunchedEffect(tab) { fold.unfold() }

Pantallas(Modifier.fillMaxSize().glassSource(backdrop).nestedScroll(fold))

LiquidBar(
    backdrop = backdrop,
    items = tabs,
    selectedIndex = tab,
    onSelect = { tab = it },
    foldState = fold,
    action = LiquidBarAction("Añadir", Icons.Rounded.Add, onClick = { … }),
    detachAction = true,
)
```

- `AtTop`: se pliega al bajar y solo se abre al volver arriba del todo.
- `ByDirection`: se pliega al bajar 50 dp y se abre al subir 8 dp en cualquier punto.

---

## 7. Verificación (obligatoria)

En un dispositivo real o emulador API 33+:

1. **No hay crash** al cambiar de pestaña ni al abrir un menú (si lo hay, hay un cristal dentro del
   `glassSource`).
2. **El fondo se ve desenfocado** a través de la barra; no se ve el contenido nítido debajo.
3. **La lente no arrastra texto de fuera** de la barra: mantén pulsada la pestaña activa; el borde debe
   aumentar lo que hay debajo, sin fantasmas del contenido de arriba/abajo.
4. **Arrastrar** la lente la lleva entre pestañas y vuelve sola a la más cercana.
5. **Plegar/desplegar** con scroll; los círculos de izquierda (pestaña) y derecha (acción) quedan
   separados y con su gesto.
6. **Botón extra** a la derecha, separado, con su pulsación corta y larga.
7. **Tema claro/oscuro**, RTL y fuente grande: las etiquetas no se cortan y la barra no cambia de forma.
8. **Release sin R8** instalado en el móvil: el cristal se ve igual que en debug.

---

## 8. Errores típicos

| Síntoma | Causa | Arreglo |
|---|---|---|
| La app se cierra al cambiar de pestaña (`RenderNode::prepareTreeImpl`) | un cristal refracta una capa que lo contiene | barra fuera del `glassSource`; superficies que se refractan a sí mismas en capa sin hijos |
| El cristal se ve nítido/“sucísimo” y con fantasmas | `glassSource` después de `background` | `glassSource` antes del fondo |
| El botón en reposo se ve demasiado transparente | velo del indicador bajo | sube el alfa de `indicator` (p. ej. 0,45–0,55) y el blur solo de la lente |
| La lente arrastra el texto de encima | shader antiguo hacia fuera | usa la versión actual (muestreo hacia dentro) |
| Sin efecto en un móvil | Android < 12/13 | es el `fallback`; no es un fallo |
| R8 rompe el cristal | minificación agresiva | release con `isMinifyEnabled = false` o reglas `-keep` |

---

## 9. Checklist final

- [ ] `glassSource` en el contenido, antes del fondo, y barra hermana.
- [ ] Componente elegido (PillBar / LiquidBar / custom + liquidGlass) y altura original respetada.
- [ ] Colores de marca mapeados (acento, contenido, indicador, badge).
- [ ] `labelStyle` e iconos originales.
- [ ] Botones extra a la derecha, en círculo separado, con sus gestos.
- [ ] Lente: pulsar, aumentar, teñir, arrastrar, asentar.
- [ ] Plegado/desplegado y acción separada (si aplica).
- [ ] Probado en claro/oscuro, RTL, fuente grande y release sin R8.
