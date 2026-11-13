# LiquidBar web: aplicar la barra en una página o app web

Versión web de `LiquidBar` (la cápsula con plegado): **misma lupa, mismos muelles, mismo plegado y mismo cristal** que la librería nativa, en JavaScript sin dependencias. Vive en [`web/`](../web):

| Archivo | Qué es |
|---|---|
| `web/liquidbar.js` | la barra y el motor (muelles analíticos, lente, refracción con mapas SVG, brillo, estiramiento) |
| `web/liquidbar.css` | estructura y filos del cristal; todo lo que se anima lo escribe el JS en cada fotograma |
| `web/demo.html` | demo para probar (`python3 -m http.server --directory web` → `/demo.html`) |

Primer uso real: **JAMD2.0** (`mkandreum/JAMD2.0`, carpeta `liquidbar/`). GastroOS tiene una versión React anterior y aproximada (`src/components/LiquidBar.tsx`): para webs nuevas usa esta.

> Instrucciones para un asistente: si en un proyecto **web** se pide «aplica liquidbar», «pon la barra de cristal» o similar, sigue esta guía en orden, sin preguntar otra vez lo que ya está decidido aquí. Para Android/Compose sigue [PORTAR.md](PORTAR.md).

---

## 1. Copiar la librería

```bash
mkdir -p liquidbar
cp ~/Descargas/liquidbar/web/liquidbar.js ~/Descargas/liquidbar/web/liquidbar.css liquidbar/
```

(o `git clone https://github.com/mkandreum/liquidbar` y copiar desde `web/`). No se edita en el proyecto: los ajustes se hacen **en el repo `liquidbar`** y se vuelven a copiar, para que todas las webs se comporten igual.

Comprueba que el servidor la sirve:
- servidor Node propio: que sirva estáticos de esa carpeta; si hay `Dockerfile` que copia archivos sueltos, añade `COPY liquidbar ./liquidbar` (en JAMD hizo falta);
- Vite/React/Next: ponla en `public/liquidbar/` y cárgala con `<script src="/liquidbar/liquidbar.js">`, o impórtala (`import '/liquidbar/liquidbar.js'` deja `window.LiquidBar`; también exporta con `module.exports`).

## 2. Cargarla

```html
<link rel="stylesheet" href="/liquidbar/liquidbar.css">   <!-- en <head>, después de los estilos de la página -->
…
<script src="/liquidbar/liquidbar.js"></script>             <!-- antes del script de la página -->
```

## 3. El contenedor

La barra flota sobre el contenido: un `<nav>` fijo abajo, **fuera** de cualquier elemento con `filter`, `opacity < 1`, `mask`, `clip-path`, `backdrop-filter` o `mix-blend-mode` (si no, el cristal solo «ve» a ese padre y no la página). `transform` sí se puede.

```css
.lbar-dock{position:fixed;left:50%;bottom:calc(env(safe-area-inset-bottom,0px) + 16px);transform:translateX(-50%);
  width:min(calc(100vw - 32px),460px);z-index:60}
```

- Mismo margen que la nativa (`navigationBarsPadding().padding(16.dp)`), ancho de móvil y tope de 460 px en escritorio.
- **Siempre horizontal y abajo**, también en escritorio (es la barra nativa).
- Deja hueco abajo en el contenido (~7 rem) para que el final de la página no quede tapado.
- Dentro puedes dejar enlaces normales como respaldo sin JS; `LiquidBar.create` vacía el contenedor y pinta la barra. Estiliza el respaldo con `.lbar-dock:not(.lbw)`.

## 4. Crear la barra

```js
const lbar = LiquidBar.create(document.getElementById('lbar'), {
  items: [
    { label: 'Inicio', icon: '<svg viewBox="0 0 24 24" …>…</svg>', href: '#inicio' },
    { label: 'Proyectos', icon: '<svg …>', href: '#proyectos' },
    // …
  ],
  selectedIndex: 0,
  onSelect: (i) => irA(i),                       // el usuario elige (toque o lupa)
  action: { label: 'Contacto', icon: '<svg …>', onClick: () => …, iconTint: '#C8F03C' },
  detachAction: true,                            // acción siempre en su círculo a la derecha
  fold: { mode: 'direction' },                   // ver tabla
  colors: { accent: '#C8F03C', content: 'rgba(255,255,255,.86)', indicator: 'rgba(128,128,128,.55)' },
  glass: { surface: 'rgba(11,20,55,.55)', fallback: 'rgba(11,20,55,.94)' },
});
```

Iconos: SVG en línea con `stroke="currentColor"`/`fill="currentColor"` (el color lo pone la barra). Se pintan a 25,6 px en un hueco de 32 px; la etiqueta, 10 px / 12 px.

| Opción | Por defecto | Equivale en nativo |
|---|---|---|
| `items[]` `{label, icon, href?}` | — | `LiquidBarItem` (con `href` la pestaña es un `<a>`) |
| `selectedIndex` | 0 (−1 = ninguna) | `selectedIndex` |
| `onSelect(i, item)` | — | `onSelect` |
| `action` `{label, icon, onClick, href?, iconTint?, tint?}` | — | `LiquidBarAction` (`tint` tiñe su cristal) |
| `detachAction` | `false` | `detachAction` |
| `fold` | `null` (no se pliega) | `foldState` |
| `fold.mode` | `'top'` | `'top'` = `ExpandedAtTop` · `'direction'` = `ByDirection` (50 px abajo pliega, 8 px arriba abre) |
| `fold.target` | `window` | contenedor con scroll propio |
| `unfoldOnSelect` | `true` | `LaunchedEffect(tab) { fold.unfold() }` |
| `colors` | `{accent:'#0A84FF', content:'rgba(255,255,255,.82)', indicator:'rgba(128,128,128,.55)'}` | `LiquidBarDefaults.darkColors()` |
| `glass` | `LiquidBar.GlassStyle.Dark` | `LiquidGlassStyle` |
| `rowHeight` | 68 | `rowHeight` |

Métodos: `lbar.setSelected(i)` (cambio programático, sin `onSelect`), `lbar.fold()`, `lbar.unfold()`, `lbar.isFolded`, `lbar.selectedIndex`, `lbar.destroy()`.

### Colores

Igual que en [PORTAR.md §5](PORTAR.md#5-ajustar-el-cristal-a-la-paleta): **cambia solo el tinte** (`glass.surface` = color de superficie de la web al 45–60 %, `glass.fallback` = el mismo casi opaco) y el acento de la marca. No toques blur, saturación ni refracción. Para webs claras parte de `glass: LiquidBar.GlassStyle.Light` y `colors.content` oscuro.

### Webs de una sola página (secciones con scroll)

Si las pestañas son secciones de la misma página (caso JAMD):

```js
const secs = ['inicio', 'nosotros', 'proyectos', 'servicios'].map(id => document.getElementById(id));
let lock = -1, lockT = 0;
// en create(): onSelect: i => { lock = i; lockT = performance.now(); secs[i].scrollIntoView({ behavior: 'smooth' }); }
function spy() {                                // llamar en cada scroll (o en el bucle rAF de la página)
  let i = 0;
  secs.forEach((el, n) => { if (el.getBoundingClientRect().top <= innerHeight * .5) i = n; });
  if (lock >= 0) { if (i === lock || performance.now() - lockT > 1500) lock = -1; else return; }
  lbar.setSelected(i);
}
```

El bloqueo evita que, durante el desplazamiento suave, el scroll-spy devuelva la lente a las secciones intermedias. Usa `fold: { mode: 'direction' }`: con `'top'` solo se abriría al volver arriba del todo.

### Otros elementos de cristal

`LiquidBar.liquidGlass(elemento, estilo?, { tint?, radius? })` es `Modifier.liquidGlass`: cristal con canto refractado para botones flotantes, avisos o menús que **floten sobre el contenido** (mismas reglas que [PORTAR.md §7](PORTAR.md#7-otros-elementos-de-cristal-donde-tenga-sentido)). Devuelve una función para quitarlo.

## 5. Cómo está hecho (para ajustar o depurar)

Correspondencia con la nativa; los valores son los de [PORTAR.md §9](PORTAR.md#9-lupa-animaciones-y-muelles):

| Nativo | Web |
|---|---|
| `Animatable` + `spring(dampingRatio, stiffness)` | `Anim`: oscilador amortiguado resuelto de forma exacta en cada fotograma; animar a otro destino conserva la velocidad; se da por terminado con el umbral de Compose (velocidad < umbral × 62,5) |
| `tween(ms, SpringEasing(ζ))` | `springEasing(ζ)` con la misma fórmula |
| `LensDrag` | `LensDrag`: `follow` spring(1, 1000), presión spring(1, 1000), escala ×78/56 con spring(0.6/0.7, 250), velocidad medida en 100 ms, suelta al 2,5 % |
| `GlassGlow` / `elasticStretch` | `Glow` + capa con `mix-blend-mode: plus-lighter` / `elastic()` |
| `glassSurface` + AGSL | `backdrop-filter: blur() saturate() url(#filtro)`; el filtro SVG es un `feDisplacementMap` con el mapa del canto calculado con la misma distancia firmada y perfil circular |
| Cápsula que se pliega | el mapa del canto va troceado en 4 esquinas y 4 bordes de 1 px: se estira a cualquier ancho sin regenerarse |
| Lente (dispersion 0,5 + depthEffect) | tres mapas (rojo, verde, azul) generados a la medida de la lente pulsada, estirados a su tamaño vivo y mezclados con `screen` |
| Copia invisible de las pestañas (`glassSource(tabsBackdrop)`) | copia teñida dentro de la lente, con `filter: url(#lente)` y cada celda escalada ×1,2 |
| La lente no refracta las pestañas | `clip-path: path(evenodd, …)` abre un hueco en la fila bajo la lente |
| Radio de `RenderEffect`/`BlurMaskFilter` | se pasa a CSS como σ = 0,577·r + 0,25 |

Ajustes: los mismos de [PORTAR.md §9.4](PORTAR.md#94-cómo-ajustarlo), en `liquidbar.js` (`PRESSED_SCALE`, `LENS_ZOOM`, los `spring(…)`, `14 * lp` y `10` de la lente, `Motion`).

## 6. Errores conocidos

| Síntoma | Causa | Solución |
|---|---|---|
| La barra aparece arriba o a todo el ancho | una hoja de estilos con `position` en `.lbw` gana a la del contenedor | `liquidbar.css` usa `:where(.lbw)`; no pongas `position` en `.lbw`, ponla en tu contenedor |
| El cristal no refracta la página, solo un color plano | un antepasado tiene `opacity`, `filter`, `clip-path`, `mask`… (es su *backdrop root*) | saca la barra a un contenedor sin esas propiedades |
| Sin canto refractado | Safari/Firefox | es lo esperado (equivale al `fallback` nativo) |
| La lente vuelve a pestañas intermedias al tocar una lejana | el scroll-spy se adelanta | bloqueo del apartado «Webs de una sola página» |
| Animaciones a saltos en la vista móvil del panel de Claude | el panel limita los fotogramas al emular móvil | probar a tamaño normal o en un móvil real |

## 7. Comprobación final

1. Consola sin errores; `LiquidBar.support` da `{blur: true, refraction: true}` en Chrome.
2. En Chrome (escritorio y Android):
   - al hacer scroll por debajo, el canto de la cápsula dobla el contenido hacia dentro;
   - tocar otra pestaña: la lente crece, viaja y se asienta en gris con el icono en el acento;
   - mantener y arrastrar la lente: crece, arcoíris en el canto, iconos ampliados; al soltar se asienta en la más cercana y llama a `onSelect`;
   - bajar 50 px: se pliega en dos círculos con rebote; subir: se abre; tocar el círculo izquierdo la abre;
   - la acción responde en su círculo.
3. En Safari: se ve el cristal desenfocado sin canto y todo lo demás funciona.
4. Teclado: Tab recorre las pestañas, Intro selecciona, el foco abre la barra plegada.
