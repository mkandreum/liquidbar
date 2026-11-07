# LiquidBar

Barras inferiores de **cristal líquido** para Jetpack Compose, 100 % nativas (Kotlin + `RenderEffect` + shaders AGSL), sin dependencias más allá de Compose.

> **¿Tienes una barra normal y la quieres de cristal?** Sigue [docs/PORTAR.md](docs/PORTAR.md): pasos, código de antes y después (ZaveBill), errores conocidos y comprobación final. Para migrar cualquier píldora conservando el diseño de cada app (y dejar `+`/buscar a la derecha), usa [docs/MIGRAR.md](docs/MIGRAR.md).

## Estilos

### `LiquidBar` — cápsula con plegado
Una cápsula con todas las pestañas (icono sobre etiqueta) y, opcionalmente, un botón de acción.

- **Indicador lupa**: la pestaña activa vive bajo una lente de cristal que se arrastra; al pulsarla crece por encima de la barra, refracta lo que hay debajo con aberración cromática y amplía los iconos que pisa. Al soltar se asienta en la pestaña más cercana.
- **Plegado al hacer scroll** (`LiquidBarFoldState`): la cápsula se recoge sobre la pestaña activa hasta quedar en un círculo a la izquierda; las etiquetas se desvanecen y la acción se separa en su propio círculo a la derecha. Muelle de 560 ms al plegar y 470 ms al abrir, con rebote.
- `detachAction = true` deja la acción siempre como círculo propio, solo con el icono.
- Estiramiento elástico al pulsar los círculos, brillo que sigue al dedo y rebote de los iconos.

### `LiquidPillBar` — píldora fina
Píldora de 64 dp con pestañas de icono; la activa se expande en cápsula con su etiqueta (el icono hace «pop») y admite un hueco central (`center`) para un botón.

- La **cápsula activa es una lente**: viaja entre pestañas con un muelle que se pasa un poco y vuelve, estirándose con la velocidad; mantenida pulsada crece, se vuelve cristal y refracta el fondo y los iconos; se puede arrastrar de una pestaña a otra.

## Cristal

`Modifier.liquidGlass(backdrop, shape, style)` sirve para cualquier superficie redondeada (botones, avisos, menús). `LiquidGlassStyle` fija la física (la de Convx): desenfoque 4 dp, saturación ×1,5, canto que refracta 24 dp hacia dentro, tinte, filo de luz y contorno oscuro. Hay `LiquidGlassStyle.Dark` y `.Light`.

Debajo, el paquete `glass` es un motor propio:

| Pieza | Qué hace |
|---|---|
| `GlassBackdrop` / `glassSource` | graba el contenido de la pantalla en una capa de render compartida |
| `glassSurface` | repinta esa capa recortada a la forma: desenfoque y saturación (`RenderEffect`) y refracción del canto (shader AGSL con perfil circular y dispersión espectral) |
| `GlassGlow` | brillo bajo el dedo y desplazamiento para el estiramiento elástico |
| `LensDrag` | valor con muelle que persigue al dedo, presión y escala del indicador |

Requisitos: el desenfoque necesita Android 12 y la refracción Android 13; en versiones anteriores se pinta un relleno translúcido (`fallback`). `minSdk` 26.

## Uso

```kotlin
val backdrop = rememberGlassBackdrop()
val fold = rememberLiquidBarFoldState()

Box(Modifier.fillMaxSize()) {
    // El contenido que el cristal refracta: hermano de la barra, nunca su padre.
    Pantallas(Modifier.fillMaxSize().glassSource(backdrop).nestedScroll(fold))

    LiquidBar(
        backdrop = backdrop,
        items = listOf(LiquidBarItem("Inicio", Icons.Rounded.Home), /* … */),
        selectedIndex = tab,
        onSelect = { tab = it },
        foldState = fold,
        action = LiquidBarAction("Nuevo", Icons.Rounded.Add, onClick = { /* … */ }),
        detachAction = true,
        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)
    )
}
```

```kotlin
LiquidPillBar(
    backdrop = backdrop,
    items = tabs,
    selectedIndex = tab,
    onSelect = { tab = it },
    colors = LiquidPillDefaults.colors(accent = Color(0xFF8E1E33)),
)
```

> Un cristal no puede estar dentro del contenido que refracta (el árbol de render se llamaría a sí mismo): coloca barras, botones flotantes y avisos fuera del elemento con `glassSource`.

## Módulo

```
liquidbar/src/main/java/io/github/mkandreum/liquidbar/
├── LiquidBar.kt            estilo cápsula con plegado
├── LiquidPillBar.kt        estilo píldora fina
├── LiquidBarFoldState.kt   cuándo se pliega (arriba del todo o por dirección)
├── LiquidGlass.kt          estilo y modificador de cristal, estiramiento elástico
├── LiquidMotion.kt         curvas de muelle con duración fija y tiempos
└── glass/                  motor: captura, superficie AGSL, brillo y arrastre
```

Compilar: `./gradlew :liquidbar:assembleRelease` (JDK 17, SDK 36).
