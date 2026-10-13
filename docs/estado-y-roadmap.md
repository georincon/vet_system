# Estado del proyecto y hoja de ruta (dirección de proyecto) — 2026-10-13

Base: `docs/plan-producto.md` (fases 2-9) contrastado con la API real (`/v3/api-docs`) y el esquema Flyway V1-V11.

## Estado por fase
| Fase | Alcance | Estado | Evidencia / brecha |
|---|---|---|---|
| 0 | Seguridad (JWT, usuarios) | HECHA | `/api/auth/*`, `/api/usuarios`. Falta: refresh token, bloqueo por intentos, desactivar seed admin/admin en prod |
| 1 | Tutores, pacientes, catálogos | HECHA | `/api/tutores`, `/api/pacientes`, `/api/catalogos/*`. Falta: microchip, foto, consentimiento habeas data, fallecido |
| 1.5 | Rediseño visual | HECHA (esta sesión) | tema `vet-theme.css`, patita, login dividido |
| 2 | Agenda + consulta clínica | PARCIAL (~35%) | Hay `/api/citas` y `/api/consultas` (CRUD simple) y vistas. Faltan: sin solapes (409), bloqueos, sala de espera, signos vitales, diagnósticos, recetas, adjuntos, cierre inmutable/addendum, historia/línea de tiempo, auditoría real, `sede_id` |
| 3 | Vacunación + recordatorios | NO INICIADA | Existen tablas `vacuna`, `inmunizacion`, `consulta_vacuna`; sin módulo ni job |
| 4 | Inventario + facturación | NO INICIADA | Tablas `item, lote, bodega, movimiento_stock, factura, pago, presupuesto` sin código |
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
