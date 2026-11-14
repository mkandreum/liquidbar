# Cómo quedó en cada app (patrones para copiar)

Aplicado el 2026-11-13. Repos en `~/Descargas/repos/`. Si una app nueva se parece a una de estas, copia su patrón.

## ZaveBill (Android, `zavebill`, 3.6.0)

Finanzas personales, oscuro burdeos.

| Pieza | Dónde | Cómo |
|---|---|---|
| Raíz | `ui/AppRoot.kt` | proveedores de isla, menú y hojas + `LiquidMenuHost`, `LiquidSheetPortalHost`, `LiquidIsland` |
| Isla | `components/Effects.kt` | `ToastHost` reenvía cada aviso a la isla; los borrados llevan «Toca para deshacer» (`onTap`); meta de ahorro conseguida |
| Abanico | `components/BottomNav.kt` | sustituye a `AddOrb` y su menú: Variable / Fijo / Ahorro |
| Lupa de gráficas | `components/Charts.kt` (`BarChart`) | historial mensual |
| Interruptores | `HerramientasScreen`, `ExpenseEditor` | `Switch` → `LiquidToggle` |
| Deslizadores | `components/RuleEditor.kt` | reparto 50/30/20 |
| Segmentado | `components/Surfaces.kt` (`Segmented`) | todos los selectores de la app a la vez |
| Menú | `screens/DashboardScreen.kt` (`DetailRow`) | editar / borrar movimientos |
| Hojas | `Surfaces.kt` (`ZbSheet`) | `LiquidPortalSheet` |
| Botón | `components/GlassButton.kt` | `LiquidGlassButton` |

## DiviFriends (Android `DiviFriendsApp` 2.12.0 · web `DiviFriends`)

Gastos compartidos.

| Pieza | Android | Web |
|---|---|---|
| Isla | `DiviToastHost` → isla | `Toasts` → `showIsland` |
| Abanico | `DiviFloatingNav(fanItems)`: Gasto / Invitar / Unirme / Agenda | `useFan` en `.fan-anchor` (`App.tsx`) |
| Lupa | `WaveChart` (`lensOn` desactiva su arrastre) | `WaveChart` con `useChartLens` |
| Interruptor / segmentado | `DiviSwitch`, `SegmentedControl` (`Inputs.kt`) | `Switch`, `Segmented` (`ui.tsx`/`kit.tsx`) |
| Insignia | Agenda en `GroupListScreen` | — |
| Menú | gastos en `GroupDetailScreen` | `menuRef` en `.txwrap` |
| Recargar | `DiviNavHost` → `viewModel.syncCloud()` | `usePullRefresh(refresh)` |
| Hojas | `DiviSheet` → portal | — |

## ListPro (Android `listproapp` 2.8.0 · web `listpro/web`)

Lista de la compra. **Su `LiquidBar.kt` es propio (insignias): copiar con `--exclude LiquidBar.kt`.**

| Pieza | Android | Web |
|---|---|---|
| Isla | avisos (`MainActivity.kt`) | `Toasts` |
| Chips sobre foto | `ProductSheet` (`Chrome.kt`), ya como hoja de portal | `ProductSheet` (`.lp-chips`) |
| Segmentado + lupa | «Cuentas / Estadísticas» y barras mensuales (`ListScreens.kt`) | `Stats` |
| Deslizador | presupuesto (`PlanScreen.kt`) | `Plan` |
| Menú | `BasketRow` | líneas de `Basket` (`menuRef`) |
| Buscar | `LiquidSearchMorph` abajo a la derecha cuando la barra está plegada, con `imePadding` | — |
| Recargar | `vm.refresh()` | — |

## VoltBody (Android `VoltBodyApp` 1.8.0 · web `VoltBody`)

Entrenamiento y nutrición, acento eléctrico.

| Pieza | Android | Web |
|---|---|---|
| Isla | `ToastHost` y descanso de la sesión (`SessionV3.kt`, fija con cuenta atrás) | `ToastHost` y descanso de `Session` |
| Lupa | `MockBars(lensLabel)` (`Kit.kt`) | `MockBars` |
| Interruptor | recordatorios (`ProfileV3.kt`) | `Toggle` |
| Segmentado | «Diario / Semanal» (`ActivityV3.kt`) | — |
| Menú | `MealTimelineRow` (`NutritionV3.kt`) | — |
| Botón | `GlassIconButton` (`Glass.kt`) | — |
| Recargar | `repo.refreshAll()` (`Nav.kt`) | — |
| Hoja | `VoltModalSheet` → portal (`GlassSheet.kt`) | — |
| Deslizador | — | selector numérico de `Onboarding` |

## GastroOS (solo web, `GastroOS`)

TPV de restaurante: carta para clientes, sala y cocina.

| Pieza | Dónde |
|---|---|
| Isla | `ToastProvider`; en `CamareroView`, «Mesa X lista» y llamadas de sala |
| Chips sobre foto | precio y stock en la carta (`ClientView`, `.cv-chips`) |
| Insignias | cesta (`ClientView`), platos listos (`CamareroView`) |
| Interruptores | impresoras (`AdminView`) |
| Lupa | ventas por hora (`AdminView`, `hourChartRef`) |
| Recargar | sala (`CamareroView`) |

## Lo que no se puso, y por qué

- Abanico en ListPro y GastroOS: no hay botón +.
- Buscar que se transforma en las webs: sus buscadores van dentro de la cabecera.
- Hojas en las webs: ya son de React y animan; `<LiquidSheet>` está listo si se quiere.
