# Cómo portar una barra normal a cristal líquido

Esta guía convierte una barra inferior Compose «normal» (fondo sólido, borde, sombra) en una barra de cristal líquido con `liquidbar`, conservando su forma y su forma de responder. Es el mismo proceso que se siguió en **ZaveBill 3.5**, que se usa como ejemplo real en cada paso.

La lupa, los muelles y las animaciones están explicados a fondo en la [sección 9](#9-lupa-animaciones-y-muelles).

Está pensada también como instrucciones para un asistente: si en otro proyecto se pide «aplica liquidbar», hay que seguir estos pasos en orden.

---

## 0. Antes de empezar

| Requisito | Valor |
|---|---|
| Jetpack Compose | probado con BOM `2025.09.01` (Compose 1.9) y Kotlin `2.0.21`; vale cualquier versión posterior |
| `minSdk` | 26 o más |
| Efecto completo | desenfoque desde Android 12 (API 31), refracción desde Android 13 (API 33); por debajo se pinta un relleno translúcido |
| Dependencias nuevas | ninguna: solo `ui`, `ui-graphics`, `foundation` y `material3` |

Primero hay que mirar la barra actual y apuntar qué hace:
- su **forma** (píldora fina, cápsula ancha, pestañas con etiqueta…), su altura y padding;
- **cómo responde**: escala al pulsar, «pop» del icono, cápsula que se expande con la etiqueta, hápticos…;
- los **botones especiales** (un `+` central, menús de pulsación larga…);
- **dónde se pinta**: casi siempre un `Box` raíz con el contenido de las pantallas y la barra alineada abajo.

La regla es **cambiar el material, no el comportamiento**: lo que el usuario ya conoce (tamaño, respuesta, gestos) se mantiene.

---

## 1. Copiar el código

Copia el paquete entero al proyecto, con el mismo nombre de paquete:

```
liquidbar/src/main/java/io/github/mkandreum/liquidbar/   →   app/src/main/java/io/github/mkandreum/liquidbar/
```

En ZaveBill se copió así y se mantiene sincronizado con:

```bash
rsync -a --delete ~/Documentos/liquidbar/liquidbar/src/main/java/io/github/mkandreum/liquidbar/ \
  app/src/main/java/io/github/mkandreum/liquidbar/
```

(Otra opción es añadir `liquidbar` como módulo Gradle e `implementation(project(":liquidbar"))`.)

Si el proyecto usa una versión de Compose muy antigua y falla algo de `GraphicsLayer` o `requireGraphicsContext`, sube el BOM a 2024.09 o posterior.

---

## 2. Marcar lo que el cristal refracta

El cristal no «ve» la pantalla por sí solo: hay que grabar el contenido en una capa compartida.

1. Crea un `GlassBackdrop` en el composable raíz.
2. Pon `Modifier.glassSource(backdrop)` en el contenedor de las pantallas (el que tiene el fondo de la app), **antes** del `background`, para que el fondo también se grabe.

**Antes (ZaveBill 3.4):**

```kotlin
Box(Modifier.fillMaxSize().background(Ink.bg)) {
    AnimatedContent(targetState = tab, /* … */) { t -> /* pantallas */ }

    AnimatedVisibility(visible = editor == null, modifier = Modifier.align(Alignment.BottomCenter)
        .navigationBarsPadding().padding(bottom = 12.dp)) {
        FloatingNav(Tabs, tab, { tab = it }, onAdd = { c, o -> openEditor(c, o) })
    }
}
```

**Después (ZaveBill 3.5):**

```kotlin
val backdrop = rememberGlassBackdrop()

Box(Modifier.fillMaxSize().background(Ink.bg)) {
    AnimatedContent(
        targetState = tab,
        modifier = Modifier.fillMaxSize().glassSource(backdrop).background(Ink.bg),
        /* … */
    ) { t -> /* pantallas */ }

    AnimatedVisibility(/* igual que antes */) {
        FloatingNav(backdrop, Tabs, tab, { tab = it }, onAdd = { c, o -> openEditor(c, o) })
    }
}
```

### ⚠️ La regla más importante

**Un cristal nunca puede estar dentro del elemento con `glassSource` que refracta.** El árbol de render se llamaría a sí mismo y la app se cierra con un *stack overflow* nativo en `libhwui` (`RenderNode::prepareTreeImpl` repetido cientos de veces en el logcat de `crash`).

- La barra, los botones flotantes y los avisos van como **hermanos** del contenido, nunca como hijos.
- Si una superficie de cristal tiene que refractarse a sí misma (como la cápsula de la barra, que la lente vuelve a refractar al pulsar), su `glassSource` va en una **capa sin hijos** (`Box(Modifier.matchParentSize().glassSource(…).liquidGlass(…))`), y el contenido en otra capa encima. `LiquidPillBar` ya lo hace así.

---

## 3. Elegir el estilo

| La barra original es… | Usa | Por qué |
|---|---|---|
| Píldora fina de iconos, la activa se expande con etiqueta, `+` en medio o al lado (ZaveBill) | `LiquidPillBar` | conserva la píldora y la respuesta; la cápsula activa se convierte en lente |
| Cápsula ancha con icono **sobre** etiqueta en todas las pestañas (estilo iOS 26 / Convx) | `LiquidBar` | lupa arrastrable, plegado al hacer scroll, acción que se separa |
| Cualquier otra forma | `Modifier.liquidGlass(backdrop, shape, style)` sobre tu propio `Row` | solo cambia el fondo; el resto es tuyo |

---

## 4. Sustituir el fondo de la barra

Lo que hay que quitar de la barra original:

```kotlin
.shadow(24.dp, CircleShape)
.clip(CircleShape)
.background(Ink.raised)
.border(1.dp, Ink.line, CircleShape)
```

y cambiarlo por cristal. Hay dos caminos:

### 4a. Solo el material (la barra queda igual)

```kotlin
Row(
    Modifier
        .liquidGlass(backdrop, CircleShape, ZaveGlass)   // fondo de cristal con canto que refracta
        .clip(CircleShape)
        .then(glow.gestureModifier)                       // brillo bajo el dedo (opcional)
        .then(glow.drawModifier)
        .padding(8.dp)
) { /* pestañas de siempre */ }
```

con `val glow = remember(scope) { GlassGlow(scope) }`. Sin sombra: el filo de luz y el contorno oscuro del cristal ya lo despegan del fondo.

### 4b. Con la lente y los muelles (lo que lleva ZaveBill)

Las pestañas propias desaparecen y `LiquidPillBar` las pinta con el mismo aspecto (icono de 24 dp que hace «pop», etiqueta que se despliega, pulsación con escala 0,95):

```kotlin
LiquidPillBar(
    backdrop = backdrop,
    items = items.map { LiquidBarItem(it.label, it.icon) },
    selectedIndex = selected,
    onSelect = { haptics.tick(); onSelect(it) },          // los hápticos los pone la app
    colors = LiquidPillDefaults.colors(
        accent = p.accent,          // relleno de la cápsula activa en reposo (el de la barra antigua)
        onAccent = Ink.onAccent,    // icono y etiqueta encima
        content = Ink.text2,        // iconos en reposo
    ),
    glass = ZaveGlass,
    labelStyle = MaterialTheme.typography.labelLarge,
)
```

Comportamiento que se obtiene sin escribir nada más (detalle, valores y cómo ajustarlo en la [sección 9](#9-lupa-animaciones-y-muelles)):
- en reposo, la cápsula activa es sólida del color de acento, como antes;
- al tocar otra pestaña viaja con un muelle (`dampingRatio 0.62`, `stiffness 420`) que se pasa un poco y vuelve, y se estira con la velocidad;
- mantenida pulsada crece (×1,18 de ancho, ×1,3 de alto), su relleno se aclara a cristal y refracta con aberración cromática el fondo y los iconos, que se ven ampliados ×1,12;
- se arrastra entre pestañas y, al soltar, se asienta en la más cercana y vuelve a ser sólida.

---

## 5. Ajustar el cristal a la paleta

`LiquidGlassStyle.Dark` reproduce el cristal oscuro de iOS 26: tinte `#171717` al 50 %, desenfoque de 3 dp, saturación ×2, canto de 28,8 dp que refracta hasta 33 dp, filo `#E8E8E8` al 18 % y contorno oscuro al 19 %. Para una app con color propio, cambia solo el tinte:

```kotlin
/** Cristal de ZaveBill: el de la barra de pestañas, con el velo teñido de tinta vino. */
val ZaveGlass = LiquidGlassStyle(
    surface = Color(0xFF1D1316).copy(alpha = 0.55f),   // color de superficie de la app al ~55 %
    fallback = Color(0xFF2A1A1F).copy(alpha = 0.96f),  // Android 11 o anterior: casi opaco
)
```

Pautas:
- `surface`: el color de superficie del tema con un 45–60 % de alfa. Menos alfa es más transparente, pero los iconos se leen peor sobre fondos claros.
- `fallback`: el mismo color casi opaco, porque sin desenfoque un fondo translúcido ensucia.
- No toques la física (blur, saturación, refracción) salvo que se pida: es lo que hace que se vea «líquido».
- Para un tema claro, parte de `LiquidGlassStyle.Light`.

---

## 6. Botones especiales

### Un `+` u otra acción aparte

En ZaveBill el `+` salió de la píldora y quedó a la derecha, en su propio círculo de cristal de la misma altura (64 dp), conservando su gesto y su animación:

```kotlin
Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    LiquidPillBar(/* … */)
    AddOrb(backdrop = backdrop, /* … */)
}
```

y dentro del botón, el `background(accent)` se cambia por cristal con un toque de acento que sube al pulsar. Como la forma es animada (círculo → cuadrado redondeado), se pasa la forma viva:

```kotlin
.size(64.dp)
.graphicsLayer { scaleX = scale; scaleY = scale }
.liquidGlass(backdrop, RoundedCornerShape(percent = corner), ZaveGlass,
    tint = p.accent.copy(alpha = if (pressed || open) 0.32f else 0.16f))
.clip(RoundedCornerShape(percent = corner))
.pointerInput(Unit) { /* el gesto de siempre */ }
```

Para que vaya **dentro** de la píldora en lugar de aparte, pásalo en el hueco `center = { … }` de `LiquidPillBar`.

### Menús que salen de la barra

Cada opción puede ser cristal, con el color de la categoría como tinte al pasar el dedo:

```kotlin
.liquidGlass(backdrop, RoundedCornerShape(26.dp), ZaveGlass, tint = if (hovered) tint else Color.Transparent)
```

Si el menú se ancla a la posición del botón, calcula esa posición con `boundsInRoot()` (raíz del botón menos raíz del contenedor), porque el botón ya no es hijo directo de la fila.

---

## 7. Otros elementos de cristal (donde tenga sentido)

Solo lo que **flota sobre el contenido**:

| Elemento | Cómo | En ZaveBill |
|---|---|---|
| Botón de ajustes, cerrar… | un `GlassIconButton(backdrop, …)` fijo en el `Box` raíz, fuera del contenido | el engranaje de cada pantalla pasó a ser uno solo, flotante arriba a la derecha (en las cabeceras se dejó un `Spacer(46.dp)` en su sitio) |
| Avisos / toasts | `Modifier.liquidGlass(backdrop, RoundedCornerShape(26.dp), ZaveGlass)` en lugar de `background` + `border` | `ToastHost` recibe el `backdrop` |
| Velo de la barra de estado | no es cristal: un degradado del color de fondo a transparente, de la altura de la barra de estado más 22 dp | evita que el texto se mezcle con el reloj |

**Dónde no hay que ponerlo:**
- tarjetas, botones o campos *dentro* de las pantallas (están dentro del `glassSource`: provocaría el ciclo del paso 2);
- pantallas completas superpuestas (editor, ajustes): taparían justo lo que el cristal refracta;
- `ModalBottomSheet` y diálogos: viven en otra ventana y no ven la capa.

---

## 8. Si se quiere el estilo con plegado (`LiquidBar`)

```kotlin
val fold = rememberLiquidBarFoldState()           // por defecto: se pliega al moverse y se abre arriba del todo
LaunchedEffect(tab) { fold.unfold() }             // al cambiar de pestaña, abierta

AnimatedContent(
    modifier = Modifier.fillMaxSize().glassSource(backdrop).background(bg).nestedScroll(fold),
    /* … */
)

LiquidBar(
    backdrop = backdrop,
    items = tabs,
    selectedIndex = tab,
    onSelect = { tab = it },
    foldState = fold,
    action = LiquidBarAction("Añadir", Icons.Rounded.Add, onClick = { … }, gestures = miGesto),
    detachAction = true,                          // la acción siempre como círculo propio sin texto
    colors = LiquidBarDefaults.darkColors(accent = …, content = …),
)
```

- `LiquidFoldMode.ByDirection` pliega al bajar 50 dp y abre al subir 8 dp en cualquier punto de la lista.
- `LiquidBarAction.gestures` sustituye al toque simple: ahí va un `pointerInput` propio (pulsación larga con menú, etc.).

---

## 9. Lupa, animaciones y muelles

Esta es la parte que hace que la barra se sienta «líquida». Todo está ya dentro de `LiquidPillBar` y `LiquidBar`: al portar no hay que programarlo, pero sí entender qué hace cada pieza para poder **ajustarla** o **montarla en una barra propia**.

### 9.1 Cómo funciona la lupa

La lente son **dos capas de cristal apiladas en el mismo sitio**, más un truco:

1. **Copia invisible de las pestañas.** Las pestañas se componen dos veces. La segunda copia tiene `alpha(0f)` (no se ve) y lleva `glassSource(tabsBackdrop)`, así que queda grabada en una capa. En `LiquidBar` esa copia además va teñida del color de acento (`ColorFilter.tint`) y escalada ×1,2 al pulsar; en `LiquidPillBar`, escalada ×1,12 alrededor del centro de la lente.
2. **Capa 1, cuerpo de la lente** (`glassSurface`): pinta el fondo de la pantalla y, mientras se pulsa, también la propia cápsula de la barra (`surfaceBackdrop`) con opacidad igual a la presión, para que al soltar no haya salto. Encima, el relleno: en reposo es el acento sólido (píldora) o un velo blanco del 10 % (cápsula), y se aclara a cristal al pulsar.
3. **Capa 2, iconos a través de la lente**: pinta la copia invisible del paso 1 con la misma refracción. Por eso los iconos que pisa la lente se ven ampliados y doblados por el canto.

**El shader de refracción** (`glass/GlassSurface.kt`, AGSL, Android 13+):
- calcula la distancia firmada al borde de la forma redondeada;
- fuera de la franja del canto (`refractionHeight`) el fondo pasa tal cual;
- dentro, muestrea más hacia fuera siguiendo la normal del borde, con un perfil circular `(1 − √(1 − t²)) × refractionAmount`, como el canto pulido de un vidrio: casi nada al empezar la franja y el máximo en el filo;
- **aberración cromática** (`dispersion = 1`): cinco muestras repartidas a lo largo de la normal (rojo, amarillo, verde, cian, azul). La separación crece hacia las diagonales y cambia de signo por cuadrante (`n.x · n.y`), así rojo y azul se cruzan en cada esquina, como en iOS.

**Presión `p` (0 → 1).** Todo lo que cambia al pulsar se interpola con `p`:

| Propiedad | Reposo (`p = 0`) | Pulsada (`p = 1`) | Píldora | Cápsula |
|---|---|---|---|---|
| Refracción del canto | 0 | 10 dp de franja, 14 dp de desvío | ✔ | ✔ |
| Aberración cromática | — | `dispersion = 1` | ✔ | ✔ |
| Desenfoque del fondo | 6 dp (píldora) / 8 dp (cápsula) | 0 | ✔ | ✔ |
| Saturación | ×1,6 / ×1,5 | igual | ✔ | ✔ |
| Relleno | acento sólido | acento al 18 % | ✔ | — |
| Velo | blanco 10 % | negro 3 % | — | ✔ |
| Filo de luz | blanco 18 % / 0 % | blanco 50 % | ✔ | ✔ |
| Sombra exterior | 0 | 24 dp, negro 10 % | ✔ | ✔ |
| Sombra interior | 0 | 8 dp, negro 15 % | ✔ | ✔ |
| Tamaño | pestaña | ×1,18 ancho y ×1,3 alto (píldora) · ×78/56 (cápsula) | ✔ | ✔ |
| Iconos bajo la lente | ×1 | ×1,12 (píldora) · ×1,2 (cápsula) | ✔ | ✔ |

El crecimiento se hace con `Modifier.layout` (la lente **mide más**, no se escala), para que la refracción tenga más superficie de verdad y no se pixele.

### 9.2 Los muelles

Valores exactos (`dampingRatio`, `stiffness`). Cuanto menor el amortiguamiento, más se pasa y rebota; cuanto mayor la rigidez, más rápido.

**Píldora (`LiquidPillBar`)**

| Qué se mueve | Muelle | Efecto |
|---|---|---|
| Centro de la lente al viajar | `spring(0.62, 420)` | se pasa ~8 % y vuelve |
| Anchura de la lente | `spring(0.5, 420)` | rebota más que la posición: «gelatina» |
| Presión al pulsar / al viajar | `spring(1, 1000)` | instantánea, sin rebote |
| Presión al soltar | `spring(1, 600)` | suelta algo más despacio |
| Estiramiento por velocidad | `ancho × (1 + v)`, `alto × (1 − 0,45·v)`, con `v = |velocidad| / 5000` y tope 0,22 | se alarga y aplana al acelerar |
| Se suelta cuando… | está a menos de 8 dp de su pestaña **y** ya no se mueve | nunca se queda «en lupa» |
| «Pop» del icono al activarse | salta a 0,6 y vuelve con `spring(0.35, Medium)` | rebote marcado |
| Etiqueta al desplegarse / plegarse | `spring(0.75, 500)` / `spring(0.9, 500)` | abre con algo de rebote, cierra seca |
| Pulsar una pestaña | escala 0,95 con `spring(0.6, 800)` | respuesta táctil |

**Cápsula (`LiquidBar`) e indicador arrastrable (`glass/LensDrag`)**

| Qué se mueve | Muelle | Efecto |
|---|---|---|
| Posición del indicador (sigue al dedo) | `spring(1, 1000)` | pegado al dedo |
| Velocidad (para deformar) | `spring(0.5, 300)` | la deformación tiembla un poco al parar |
| Presión | `spring(1, 1000)` | |
| Escala X / Y al pulsar | `spring(0.6, 250)` / `spring(0.7, 250)` | ancho y alto rebotan desfasados: parece líquido |
| Deformación por velocidad | `escalaX / (1 − 0,75·v)`, `escalaY × (1 − 0,25·v)`, `v` entre −0,2 y 0,2 | más ancha y baja al acelerar |
| Se suelta cuando… | el valor está a menos del 2,5 % del recorrido de su destino | la lupa se ve viajar antes de soltarse |
| Tirón de la fila al arrastrar | la cápsula entera se desplaza hasta 4 dp hacia el dedo (`EaseOut`) y vuelve con `spring(1, 300)` | sensación de que tiras de ella |
| Plegar con el scroll | `tween(560 ms, SpringEasing(0.55))` | se pasa ~12 % |
| Abrir | `tween(470 ms, SpringEasing(0.74))` | casi sin pasarse |
| Etiquetas al plegar | opacidad `1 − 3·pliegue` | desaparecen en el primer tercio |
| Rebote del icono (al activar, aparecer, plegar o abrir) | encoge a 0,8 en 90 ms y vuelve con `spring(0.4, 520)` | |

**`SpringEasing`** (`LiquidMotion.kt`) es un muelle real comprimido en una duración fija. Se usa para el plegado porque anchura, posiciones y fundidos tienen que terminar en el **mismo fotograma**, y un `spring()` normal no tiene duración conocida. `SpringEasing(0.55)` ≈ 12 % de rebote; `SpringEasing(0.74)` ≈ 3 %.

### 9.3 Brillo y estiramiento al tocar

- **`GlassGlow`**: al tocar, la pieza se aclara un 8 % (mezcla `Plus`) y una luz radial del 15 % sigue al dedo. Entra y sale con `spring(0.5, 300)`. No consume eventos, así que los `clickable` de debajo siguen funcionando. Se aplica a la cápsula entera, a los círculos y a los botones de cristal.
- **Estiramiento elástico** (`elasticStretch`): al pulsar, la pieza se infla 4 dp y sigue al dedo con resistencia (`tanh`), alargándose en la dirección del arrastre. Lo usan los círculos plegados de `LiquidBar`.

### 9.4 Cómo ajustarlo

| Quiero… | Cambia | Dónde |
|---|---|---|
| Más rebote al cambiar de pestaña | baja `dampingRatio` de `travel` (0,62 → 0,5) | `LiquidPillBar.kt` |
| Lente más «gelatina» | baja el de `stretch` (0,5 → 0,4) | `LiquidPillBar.kt` |
| Lupa más grande al pulsar | `1.18f` / `1.3f` del bloque `layout`, y `pressedScale` en `LiquidBar` | `LiquidPillBar.kt`, `LiquidBar.kt` |
| Más o menos aumento de los iconos | `1.12f` (píldora) / `1.2f` (cápsula) | idem |
| Refracción más fuerte en la lente | `10.dp` (franja) y `14.dp` (desvío) | las dos capas de la lente |
| Menos arcoíris | `dispersion = 1f` → `0.4f`; `0f` lo quita | idem |
| Plegado más o menos elástico | `FoldEasing` / `UnfoldEasing` y sus duraciones | `LiquidMotion.kt` |
| Sin lupa (solo el viaje con muelle) | la presión no subirá si pones `holding = false`; o usa la opción 4a con tus pestañas | `LiquidPillBar.kt` |

Ajusta **en la librería** (repo `liquidbar`) y vuelve a copiarla al proyecto, para que todas las apps compartan el mismo comportamiento.

### 9.5 Montar la lupa en una barra propia

Si la barra no encaja con ninguno de los dos estilos, la receta es la misma que usan ellos:

```kotlin
val scope = rememberCoroutineScope()
val tabsBackdrop = rememberGlassBackdrop()      // la copia de las pestañas
val surfaceBackdrop = rememberGlassBackdrop()   // la superficie de la barra
val x = remember { Animatable(0f) }             // centro de la lente (px)
val press = remember { Animatable(0f) }         // 0 reposo · 1 pulsada

Box(barModifier) {
    // 1 · superficie en capa SIN hijos (si no, ciclo de render)
    Box(Modifier.matchParentSize().glassSource(surfaceBackdrop).liquidGlass(backdrop, CircleShape, estilo))
    // 2 · pestañas visibles
    MisPestañas(interactive = true)
    // 3 · copia invisible que la lente amplía
    Box(Modifier.alpha(0f).glassSource(tabsBackdrop)
        .graphicsLayer { val s = lerp(1f, 1.12f, press.value); scaleX = s; scaleY = s }) {
        MisPestañas(interactive = false)          // sin clickable: si no, roba los toques
    }
    // 4 · la lente: dos glassSurface en el mismo sitio
    val sitio = Modifier.offset { IntOffset((x.value - ancho / 2).roundToInt(), 0) }.size(anchoDp, altoDp)
    Box(sitio.pointerInput(Unit) { /* arrastre: snapTo de x; press a 1 al bajar y a 0 al llegar */ }
        .glassSurface({ listOf(GlassSource(backdrop), GlassSource(surfaceBackdrop, press.value)) }, CircleShape) {
            val p = press.value
            GlassLook(blur = 6.dp.toPx() * (1 - p), refractionHeight = 10.dp.toPx() * p,
                refractionAmount = 14.dp.toPx() * p, dispersion = 1f, surface = acento.copy(alpha = 1 - 0.82f * p),
                shadowAlpha = p, innerShadowAlpha = p, innerShadowRadius = 8.dp.toPx() * p)
        })
    Box(sitio.glassSurface({ listOf(GlassSource(tabsBackdrop)) }, CircleShape) {
        GlassLook(refractionHeight = 10.dp.toPx() * press.value, refractionAmount = 14.dp.toPx() * press.value, dispersion = 1f)
    })
}
```

Tres reglas que salen de los fallos reales:
1. **La superficie que la lente refracta va en una capa sin hijos** (paso 1); si no, la app se cierra.
2. **La copia invisible no puede ser clicable**, o roba los toques a las pestañas visibles y desaparece su animación de pulsado.
3. **La presión se maneja con un único observador permanente**: `LaunchedEffect(Unit) { snapshotFlow { arrastrando || viajando }.collectLatest { press.animateTo(if (it) 1f else 0f, …) } }`. Con `LaunchedEffect(estado)` a veces no se recompone y la lente se queda pulsada.

---

## 10. Errores conocidos y cómo se resolvieron

| Síntoma | Causa | Solución |
|---|---|---|
| La app se cierra al cambiar de pestaña; en `logcat -b crash`, `RenderNode::prepareTreeImpl` repetido | un cristal refracta una capa que lo contiene | separar la superficie con `glassSource` en una capa sin hijos (paso 2) |
| Tras soltar, la lente se queda transparente («en lupa») | la animación de presión colgaba de `LaunchedEffect(estado)` y a veces no se recomponía | observar con `LaunchedEffect(Unit) { snapshotFlow { estado }.collectLatest { … } }` (ya corregido en la librería) |
| El cristal se ve gris y lavado | velo blanco en lugar de tinte oscuro | `surface` con el color de superficie al ~50 %, no blanco |
| Sin efecto en un móvil antiguo | Android < 12 | es lo esperado: se pinta `fallback` |
| El contenido no se ve a través | falta `glassSource`, o está después del `background` | `glassSource(backdrop)` antes de `background(...)` en el contenedor de pantallas |
| El texto del logotipo de la intro se salía | tamaño calculado a ojo por el ancho | medir con `rememberTextMeasurer` y escalar al ancho disponible |

---

## 11. Comprobación final

1. `./gradlew :app:assembleDebug` y `:app:assembleRelease` (R8 no necesita reglas extra).
2. En emulador o móvil con Android 13 o superior:
   - la barra deja ver el contenido desenfocado y con el canto refractado al hacer scroll por debajo;
   - tocar otra pestaña: la cápsula viaja con rebote y se queda sólida;
   - mantener y arrastrar la cápsula: crece, se vuelve lupa con arcoíris en el canto, se asienta al soltar y vuelve a ser sólida;
   - repetirlo en todas las pestañas, sobre todo en las que tengan animaciones infinitas (fue donde apareció el fallo de la lente);
   - los botones especiales (el `+` y su menú) siguen respondiendo igual que antes;
   - no hay cierres: `adb logcat -b crash` vacío.
3. Si existe un dispositivo con Android 11 o anterior, comprobar que se ve el relleno `fallback`.
