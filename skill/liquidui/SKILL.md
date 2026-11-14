---
name: liquidui
description: Guía de diseño y montaje de LiquidUI (repo mkandreum/liquidUI): barra de cristal líquido y 13 piezas de cristal con lupa y muelles (isla, abanico de burbujas, lupa de gráficas, interruptor, deslizador, selector segmentado, menú de pulsación larga, hoja inferior, buscar que se transforma, chips sobre fotos, insignia, botón de cristal y tirar para recargar) en Jetpack Compose, web vanilla y React. Úsala siempre que se pida «aplica liquidbar / liquidUI», «pon la barra de cristal», «pon la isla / el abanico / la lupa…», cristal líquido o efecto Liquid Glass en una app Android o web, o se pegue la lista de propuestas de piezas de cristal, aunque no se nombre la skill.
---

# LiquidUI: diseño y montaje de piezas de cristal

El repo **mkandreum/liquidUI** (local: `~/Descargas/liquidUI`) contiene la barra de cristal (`LiquidBar` / `LiquidPillBar`) y 13 piezas que comparten el mismo cristal, los mismos muelles y la misma lupa. Cada pieza existe en tres sabores con los mismos valores:

| Sabor | Dónde está | Cómo se lleva a una app |
|---|---|---|
| Compose | `liquidbar/src/main/java/io/github/mkandreum/liquidbar/` (`glass/`, `ui/`) | `rsync` del paquete a la app (ver «Copiar») |
| Web vanilla | `web/liquidbar.js` + `.css`, `web/liquidui.js` + `.css` | copiar a `public/liquidbar/` y cargar con `<script>` |
| React | `web/react/LiquidUI.tsx` | copiar a `src/components/LiquidUI.tsx` |

Los nombres de paquete y de archivo (`io.github.mkandreum.liquidbar`, `liquidbar.js`) se mantienen aunque el repo se llame liquidUI: cambiarlos rompería todas las apps.

Documentación detallada en el repo: `docs/UI.md` (piezas), `docs/PORTAR.md` (barra en Compose), `docs/WEB.md` (barra en web), `docs/MIGRAR.md` (píldoras propias).

## Flujo de trabajo

1. **Leer la app antes de tocar nada.** Localiza: la raíz (donde está el `Box`/layout de pantallas), la barra inferior, el sistema de avisos (toast/snackbar), las hojas (`ModalBottomSheet` o modales), los interruptores, selectores y gráficas. Anota colores de acento, fondo y tipografía.
2. **Elegir piezas con la tabla de decisión** (abajo). No todas encajan en todas las apps: si no hay sitio natural, no se pone y se dice por qué.
3. **Copiar la librería** (ver «Copiar») sin editarla dentro de la app.
4. **Montar la raíz** una vez (isla, menú y hojas por portal en Compose; scripts en web).
5. **Sustituir pieza a pieza** conservando textos, acciones y colores de la app. Se cambia el material, nunca el comportamiento.
6. **Compilar y ver en marcha**: emulador o Chromium, con capturas. En web, `tsc` limpio.
7. **Enseñar al usuario antes de subir.** Él aprueba y entonces se hace commit, se sube la versión y, si lo pide, la release.
8. **Ajustes de las piezas, siempre en el repo liquidUI**, y luego se vuelve a copiar a todas las apps.

## Principios de diseño

- **El cristal es para lo que flota o se manipula**: barra, isla, abanico, menú, hoja, lentes. No conviertas tarjetas, listas o fondos enteros en cristal: pierde jerarquía y rendimiento.
- **Debe haber algo detrás.** El cristal sin contenido variable detrás (color plano) parece gris. Las lentes lucen sobre texto, iconos, fotos o gráficas.
- **Una lente se ve al tocar.** La lupa (crecer, refractar, ampliar) aparece mientras el dedo está; en reposo, la pieza es discreta.
- **Muelles, no curvas.** Todo movimiento usa `spring(dampingRatio, stiffness)` con los valores de la tabla; un rebote corto se nota, uno largo marea. No mezclar con `tween` salvo fundidos.
- **Hápticos en los hitos**: al abrir el abanico, al pasar de burbuja, al cruzar el umbral de recarga, al abrir un menú.
- **Color**: el tinte del cristal sale del fondo de la app (oscuro → `LiquidGlassStyle.Dark`, claro → `.Light`); el acento de la app solo en lo activo (lente del segmentado, raíl encendido, burbuja del valor). Destructivo en rojo `#FF6B60`.
- **Zonas del pulgar**: abanico y buscar abajo a la derecha, junto a la barra; isla arriba al centro, bajo la barra de estado; hojas desde abajo.
- **Accesibilidad**: cada pieza conserva rol y etiqueta (`onClickLabel`, `label`, `aria-*`); el gesto largo siempre tiene alternativa (el + del abanico también abre con toque; el menú repite acciones visibles). Respeta `prefers-reduced-motion` en web.
- **Rendimiento**: una sola capa `glassSource` por pantalla; no anidar cristales; en web el canvas se repinta solo mientras hay animación (`onFrame`).

## Tabla de decisión: qué pieza va dónde

| Si la app tiene… | Pon… | No lo pongas si… |
|---|---|---|
| avisos/toasts/snackbars, estados vivos (temporizador, pedido listo, sincronizando) | **Isla** | ya hay una barra superior fija que la tapa |
| un + que abre un menú de 2-5 acciones | **Abanico** (mantener el +) | no hay + o hay más de 5 acciones |
| gráficas de barras/líneas con valores | **Lupa de gráficas** | la gráfica ya tiene arrastre propio que no se puede ceder |
| interruptores (`Switch`) | **Interruptor** | — |
| selectores de rango (presupuesto, porcentaje, cantidad) | **Deslizador** | el valor exige teclear con precisión |
| pestañas de 2-4 opciones dentro de una pantalla | **Segmentado** | más de 4 opciones o etiquetas largas |
| filas con acciones (editar, duplicar, borrar) | **Menú de pulsación larga** | la fila ya usa pulsación larga para otra cosa |
| `ModalBottomSheet` / modales inferiores | **Hoja de cristal** (`LiquidPortalSheet`) | en web, si la hoja React ya anima bien |
| buscador junto a la barra plegada | **Buscar que se transforma** | el buscador va dentro de la cabecera |
| fotos con precio, stock o etiqueta | **Chips sobre foto** | la foto es pequeña (< 120 dp) |
| contadores (cesta, notificaciones) | **Insignia** | el número no cambia nunca |
| botones flotantes o de icono sobre contenido | **Botón de cristal** | botones dentro de formularios |
| listas sincronizables | **Tirar para recargar** | la lista no tiene nada que recargar |

## Valores comunes (no cambiar sin motivo)

| Pieza | Medida | Muelle(s) |
|---|---|---|
| Segmentado | alto 36 dp, lente ×1,15 × ×1,35 al arrastrar, etiquetas ×1,12 | (0.62, 420) |
| Interruptor | 64 × 28, bolita ×1,5, aumento ×1,35 | (0.6, 500) |
| Deslizador | lupa 58 × 38, ×1,6, burbuja del valor | (0.62, 420) |
| Botón | tinte 16 % → 34 % al pulsar, estiramiento elástico | (0.6, 250) / (0.7, 250) |
| Chip | canto 10 dp, «pop» con retardo escalonado | (0.5, 420) |
| Insignia | se aplasta y suelta una gotita al cambiar | (0.4, 520) |
| Isla | ancho y, 70 ms después, alto; 3,2 s | (0.62, 380) / (0.7, 300) |
| Abanico | radio 104 dp, burbujas 54 dp, arco 180°-270°, 30 ms entre burbujas | (0.55, 380) |
| Lupa de gráficas | 104 dp, ×1,8, pulsación larga | (0.6, 380) |
| Buscar | círculo 56 dp → campo | (0.7, 300) |
| Menú | tarjeta ×1,03, píldoras cada 35 ms, fondo desenfocado | (0.55, 420) / (0.6, 420) |
| Recargar | umbral 72 dp, estiramiento tanh, caída con rebote | (0.45, 300) |
| Hoja | alturas 0,45 / 0,9, asa elástica | (0.75, 300) |

## La regla de oro en Compose

**Un cristal no puede refractar algo que lo contiene.** El contenido refractado (`glassSource`) es hermano del cristal, nunca su padre.

- Piezas **dentro de pantallas** (segmentado sin `backdrop`, interruptor, deslizador, chip, lupa de gráficas, recargar) graban su propia capa: se usan en cualquier sitio.
- Piezas **flotantes** (isla, abanico, buscar, menú, hoja, botón flotante) usan el `backdrop` de la raíz y se colocan en el `Box` raíz, encima de las pantallas.

Raíz completa, chuleta de cada pieza (Compose, web y React) y errores conocidos: **`references/piezas.md`**. Cómo quedó en cada app real (ZaveBill, DiviFriends, ListPro, VoltBody, GastroOS), para copiar el patrón: **`references/apps.md`**.

## Copiar

```bash
# Compose (desde la raíz de la app; ListPro: añadir --exclude LiquidBar.kt, tiene la suya con insignias)
rsync -a ~/Descargas/liquidUI/liquidbar/src/main/java/io/github/mkandreum/liquidbar/ \
  app/src/main/java/io/github/mkandreum/liquidbar/
# Web
cp ~/Descargas/liquidUI/web/liquidbar.{js,css} ~/Descargas/liquidUI/web/liquidui.{js,css} public/liquidbar/
cp ~/Descargas/liquidUI/web/react/LiquidUI.tsx src/components/LiquidUI.tsx   # si es React
```

Release con R8: añade a `proguard-rules.pro`

```
-keep class io.github.mkandreum.liquidbar.** { *; }
-keepclassmembers class io.github.mkandreum.liquidbar.** { *; }
```

## Comprobación final

- [ ] Compila (`./gradlew :app:assembleRelease` con R8 / `npx tsc --noEmit` y `npm run build`).
- [ ] Cada pieza vista en marcha y capturada (emulador API 33+ para la refracción; Chromium en web).
- [ ] Sin cristales anidados ni `glassSource` en el padre del cristal.
- [ ] Textos, acciones y colores de la app intactos; hay alternativa a cada gesto largo.
- [ ] Sube `versionName`/`versionCode` y actualiza la tabla «Dónde está aplicada» de `docs/UI.md`.
- [ ] El usuario ha aprobado antes de hacer commit y subir.
