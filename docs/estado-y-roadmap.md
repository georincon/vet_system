# Estado del proyecto y hoja de ruta (dirección de proyecto) — 2026-10-13

Base: `docs/plan-producto.md` (fases 2-9) contrastado con la API real (`/v3/api-docs`) y el esquema Flyway V1-V11.

## Estado por fase
| Fase | Alcance | Estado | Evidencia / brecha |
|---|---|---|---|
| 0 | Seguridad (JWT, usuarios) | HECHA | `/api/auth/*`, `/api/usuarios`. Falta: refresh token, bloqueo por intentos, desactivar seed admin/admin en prod |
| 1 | Tutores, pacientes, catálogos | HECHA | `/api/tutores`, `/api/pacientes`, `/api/catalogos/*`. Falta: microchip, foto, consentimiento habeas data, fallecido |
| 1.5 | Rediseño visual | HECHA (esta sesión) | tema `vet-theme.css`, patita, login dividido |
| 2 | Agenda + consulta clínica | PARCIAL (~35%) | Hay `/api/citas` y `/api/consultas` (CRUD simple) y vistas. Faltan: sin solapes (409), bloqueos, sala de espera, signos vitales, diagnósticos, recetas, adjuntos, cierre inmutable/addendum, historia/línea de tiempo, auditoría real, `sede_id` |
| 3 | Vacunación + recordatorios | HECHA (backend, 2026-10-15) | Módulos `inmunizacion` y `comunicaciones`: catálogos, esquemas, aplicación con lote/stock, próxima dosis, sugerido, pendientes, carnet PDF, plantillas/reglas, job con idempotencia, consentimiento/opt-out, bitácora. Contrato: `docs/api-fase3.md`. UI HECHA (2026-10-15). Falta: WhatsApp, adjuntos, backfill de consultas cerradas previas |
| 4 | Inventario + facturación | HECHA (backend, 2026-10-15) | Módulos `inventario` y `facturacion`: items/lotes/FEFO/compras/kardex/alertas/controlados; presupuesto→factura, pagos mixtos, notas crédito, caja, POS, cartera, reportes, cargos desde `ConsultaCerrada`. Contrato: `docs/api-fase4.md`. UI HECHA (2026-10-15) |
| 5 | DIAN | NO INICIADA | Depende de Fase 4 y trámite externo |
| 6-9 | Hospitalización, portal, BI, IA | NO INICIADAS | Sin esquema |

## Hitos cerrados (2026-10-13, tarde)
- Backend reescrito (shared, seguridad, tutores, citas) con paridad de contrato; `mvnw verify` 12/12 verde.
- Migraciones V12-V22 incorporadas (+59 tablas, 93 en total; `docs/modelo-datos-v2.md`), aplicadas en la BD de desarrollo (Flyway v22).
- Stack arriba con Dockerfile normal: web 8090, API 9010, Postgres 5256. `docker-compose.jar.yml` ya no es necesario.

## Bloqueo técnico (RESUELTO)
El backend en `backend/src/main/java` solo tiene la clase principal; corre desde el jar compilado (`docker-compose.jar.yml`). Para desarrollar Fase 2+ hace falta el código fuente (original o decompilado).

## Plan inmediato (Sprint 1 = cierre de Fase 2)
1. Recuperar fuentes del backend y verificar `./mvnw verify` verde.
2. Agenda: validación de solapes, bloqueos, estados, vista calendario.
3. Módulo clínica: signos vitales, diagnósticos, recetas, peso/alergias, cierre inmutable + addendum, historia del paciente.
4. Auditoría: interceptor que escribe en `auditoria`.
5. `sede_id` por defecto en cita/consulta (migración V12).
6. Dashboard con métricas reales (citas hoy, pacientes, consultas abiertas).
Luego: Sprint 2 = Fase 3 (vacunación + recordatorios email); Sprint 3 = Fase 4.

## Fase 3 backend (2026-10-15)
- Migración V25: `inmunizacion.consulta_vacuna_id` (idempotencia del evento de cierre) + datos de anulación; tabla `antiparasitario` (catálogo con intervalo) y anulación/lote en `desparasitacion`; `ENVIADO_SIMULADO` en `recordatorio`/`envio_log` (estado ampliado a VARCHAR(20)); triggers de auditoría de las tablas nuevas.
- Evento `clinica.ConsultaCerrada` -> `inmunizacion` (tras el commit, idempotente); eventos `InmunizacionRegistrada/Anulada` -> `comunicaciones` cancela recordatorios obsoletos. Dependencias en un solo sentido: comunicaciones -> inmunizacion -> clinica(evento)/tutores; dashboard -> inmunizacion.
- Tests: 51 en total (32 previos + `InmunizacionApiTest` 9, `ComunicacionesApiTest` 6, `CalculadoraProximaDosisTest` 4); `mvnw verify` verde.
- Decisiones: AUXILIAR registra con veterinario supervisor y no anula; RECEPCION gestiona recordatorios/pendientes pero no ve datos clínicos (carnet incluido); consentimiento = `acepta_<canal>` del tutor + sin opt-out; vacunas de consulta sin match en catálogo se crean como `AUTO-*`.
- Pendientes: adaptador WhatsApp (interfaz `CanalEnvio` lista), adjuntos del carnet por correo, backfill de `consulta_vacuna` de consultas cerradas antes de V25 (hoy solo se ven en historia), inventario completo (Fase 4: hoy solo se descuenta/devuelve el lote usado), eventos con registro persistente de Modulith (spring-modulith-events-jpa) si se requiere reentrega garantizada.

## Fase 3 UI (2026-10-15)
- Rutas: `/pacientes/:id/historia?tab=vacunacion` (pestaña Vacunación; `/pacientes/:id/vacunacion` redirige; solo ADMIN/VET/AUX), `/vacunacion/pendientes` (todos los roles; RECEPCION sin enlace a datos clínicos), `/comunicaciones` (ADMIN/RECEPCION; escritura y "Ejecutar ahora" solo ADMIN), `/inmunizacion/catalogos` (ADMIN). Tarjeta "vacunas por vencer" del dashboard enlaza a pendientes.
- Componentes: `components/vacunacion/{VacunacionPanel,AplicarInmunizacionDialog,EsquemaSugeridoPanel}`, `components/comunicaciones/{Recordatorios,Plantillas,Reglas,Bitacora}Tab`, APIs `api/inmunizacion.ts`, `api/comunicaciones.ts`, tipos `types/fase3.ts`; permisos en `auth.can`.
- Tutores: 4 consentimientos + texto habeas data (Ley 1581) + chips en lista; aviso en la consulta si el tutor principal no acepta correo.
- E2E verificado en navegador (admin, recepcionsmoke, auxiliarsmoke): esquema sugerido, aplicar con lote (422 de lote vencido mostrado), próxima fecha, carnet 200 application/pdf, pendientes, recordatorios, job manual y bitácora, anulación.
- Pendientes: selector de lotes de inventario (no hay endpoint hasta Fase 4: hoy `loteId` se escribe a mano), crear recordatorio manual (el backend no lo expone), verificación visual real a ancho tablet (el resize del navegador no cambió el viewport).

## Fase 4 backend (2026-10-15)
- Migración V28 (stock_minimo, origen de presupuesto, estado FACTURADO, cantidad_stock_cubierta, series, kardex append-only, auditoría, items `VAC-*`). Módulos cerrados `inventario` y `facturacion` (sin ciclos; inmunizacion->inventario vía `StockApi`; dashboard suma ventasHoy/ventasMes/stockBajo/lotesPorVencer).
- Tests: 73 (51 previos + `InventarioApiTest` 9, `FacturacionApiTest` 10, `FacturacionNumeracionTest` 3); `mvnw verify` verde.
- Decisiones: stock se descuenta al emitir la factura (FEFO, 409 sin stock, lotes vencidos excluidos) sin doble descuento si la vacuna ya descontó el lote; anulación solo por nota crédito (ADMIN); pagos exigen turno de caja abierto; cierre reporta diferencia; compras/ajustes/precios/NC solo ADMIN (sin rol bodega). Puerto `FacturacionElectronicaPort` con adaptador simulado (estado DIAN PENDIENTE).
- Pendientes: Fase 5 DIAN, traslados entre bodegas, mínimos por bodega, cuentas por pagar, anticipos/reversión de pagos, cargos de hospitalización, backfill de presupuestos, UI de Fase 4 (selector de lotes: `GET /api/inventario/lotes?vacunaId=&soloVigentes=true` ya disponible).

## Fase 4 UI (2026-10-15)
- Rutas: `/inventario?tab=items|lotes|kardex|alertas|controlados` (todos los roles; controlados ADMIN/VET; escritura, costos y ajustes solo ADMIN; VET/AUX pueden "Registrar uso"), `/compras` (ADMIN: proveedores, órdenes, recepción total/parcial con lote/vencimiento/costo), `/facturacion?tab=pos|documentos|cartera|caja|reportes` (ADMIN/RECEPCION; VETERINARIO solo `documentos` en lectura de presupuestos de sus consultas; reportes de ventas y nota crédito solo ADMIN). Enlaces profundos `?presupuesto=ID` / `?factura=ID`. Menú Operación: Inventario, Compras, Caja y facturas según rol; `auth.can.*` alineado con la matriz.
- Componentes: `components/inventario/*`, `components/compras/*`, `components/facturacion/*` (TurnoPanel, PosPanel, LineasEditor, PagosEditor, FacturarDialog, PresupuestoDialog, NuevaFacturaDialog, FacturaDetalleDialog, NotaCreditoDialog, CarteraTab, ReportesTab con gráfico SVG propio sin librerías), `stores/caja.ts`, `api/{inventario,facturacion}.ts`, `types/fase4.ts`, `utils/{dinero,lineas}.ts`.
- Vacunación: `AplicarInmunizacionDialog` usa `GET /inventario/lotes?vacunaId|antiparasitarioId&soloVigentes=true` (FEFO preseleccionado, opción "sin lote"), el 409 de stock se muestra con título y mensaje y recarga la lista. ConsultaView muestra "Cargo de la consulta" (enlace al presupuesto) para ADMIN/VET. Dashboard: ventasHoy/ventasMes (si no null), stockBajo, lotesPorVencer.
- E2E verificado en navegador (admin): crear ítem -> OC creada/enviada/recibida en 2 recepciones (lotes con vencimientos distintos) -> kardex y alerta "por vencer" -> abrir caja -> POS con pago mixto efectivo+tarjeta (FEFO: primero el lote que vence antes) -> 409 "Stock insuficiente de X: disponible 10, solicitado 50" resaltando el renglón -> factura a crédito + abono desde cartera -> nota crédito total con reintegro de stock y egreso en caja -> cierre con faltante (observaciones obligatorias) -> reporte de ventas -> vacuna eligiendo lote (y 409 de stock agotado). También presupuesto de consulta cerrada editado (descuento) y facturado. RECEPCION (venta a precio de catálogo, sin Compras/reportes/nota crédito) y AUXILIAR (solo inventario, sin ventas) verificados; VET ve su presupuesto en lectura.
- Pendientes: verificación visual a ancho tablet (el resize del navegador no cambia el viewport; solo se revisó el HTML responsive con flex-wrap/v-table), el `GET /caja/turnos/actual` devuelve 404 esperado sin turno (aparece como fallo de red en la consola), edición de bodegas/categorías y resoluciones desde la UI, desglose de pagos en el reporte por método neto de devoluciones, impresión/PDF de factura, chunk principal >500 kB (sin code-splitting adicional).
