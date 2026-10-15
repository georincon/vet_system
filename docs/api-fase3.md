# Contrato API - Fase 3 (vacunacion, desparasitacion y recordatorios)

Base `http://localhost:9010`. Todas las rutas requieren `Authorization: Bearer <jwt>` salvo `GET /api/comunicaciones/optout`.
Migracion nueva: `V25__fase3_inmunizacion_comunicaciones_ajustes.sql`. Convenciones de errores, paginas (`{items,page,size,totalElements,totalPages,first,last}`),
fechas `YYYY-MM-DD` y edicion parcial (PUT: campo ausente/null = sin cambio; `""` limpia texto) iguales a `docs/api-sprint1.md`.
Errores: 400 validacion/enum invalido, 401, 403, 404, **409** conflicto (stock agotado, ya anulada, duplicado, no cancelable), **422** regla de negocio (lote vencido, fecha futura...).

## Roles

| Recurso | ADMIN | VETERINARIO | AUXILIAR | RECEPCION |
|---|---|---|---|---|
| `GET /api/inmunizacion/{vacunas,antiparasitarios,esquemas}`, `GET /api/inmunizacion/pendientes` | si | si | si | si |
| `POST/PUT` catalogos y esquemas | si | no | no | no |
| `POST /api/pacientes/{id}/inmunizaciones` | si | si | **si, con `veterinarioId` supervisor obligatorio** | no |
| `POST .../inmunizaciones/{id}/anular` | si | si | no | no |
| `GET /api/pacientes/{id}/{inmunizaciones,esquema-sugerido,carnet.pdf}` | si | si | si | **no (403)**: sin acceso clinico (igual que historia) |
| `/api/comunicaciones/recordatorios*`, `/envios`, `/optouts*` | si | no | no | si |
| `/api/comunicaciones/plantillas`, `/reglas` | lectura+escritura | no | no | solo lectura |
| `POST /api/comunicaciones/ejecutar` | si | no | no | no |
| `GET /api/comunicaciones/optout?token=` | publico | publico | publico | publico |

Decision AUXILIAR: aplica vacunas "bajo supervision" (debe indicar un veterinario activo; 422 si falta o si el usuario no es VETERINARIO) pero no anula.
Auditoria: `inmunizacion`, `desparasitacion`, `vacuna`, `antiparasitario`, `esquema_vacunal`, `plantilla_mensaje`, `regla_recordatorio` y `comunicacion_optout` quedan en `/api/auditoria` (trigger + `app.usuario_id`).

## 1. Catalogos (`/api/inmunizacion`)

- `GET /vacunas?especieId=&activo=&q=` -> lista. Con `especieId` incluye las de esa especie y las genericas.
- `GET /vacunas/{id}`, `POST /vacunas` (ADMIN, 201), `PUT /vacunas/{id}` (ADMIN).
  ```json
  { "codigo": "CAN-RAB", "nombre": "Antirrabica canina", "especieId": 1, "periodoEficaciaDias": 365,
    "clasificacion": "NUCLEO|NO_NUCLEO|NO_RECOMENDADA", "enfermedadesObjetivo": "Rabia", "fabricante": "", "viaAdministracion": "SC",
    "dosisMl": 1.0, "itemId": null, "obligatoriaLey": true, "activo": true }
  ```
  Alta exige `codigo`, `nombre`, `periodoEficaciaDias`. Response = mismos campos + `id`, `especie` (nombre). Codigo repetido -> 409.
  `itemId` (item de inventario): si se informa, el lote usado debe ser de ese item (422).
- `GET /antiparasitarios?especieId=&activo=`, `POST`, `PUT /{id}` (ADMIN):
  `{ "codigo","nombre","tipo":"INTERNA|EXTERNA|DIROFILARIA|COMBINADA","principioActivo","especieId","intervaloDias":90,"itemId","activo" }`
  (alta exige codigo, nombre, tipo, intervaloDias). `intervaloDias` calcula la proxima aplicacion.
- `GET /esquemas?especieId=&vacunaId=&activo=`, `GET /esquemas/{id}`, `POST`, `PUT /{id}` (ADMIN):
  ```json
  { "codigo": "ESQ-CAN-DHPP", "nombre": "Cachorro: polivalente nucleo", "vacunaId": 1, "especieId": 1, "fuente": "WSAVA 2024", "notas": "", "activo": true,
    "dosis": [ { "orden": 1, "nombreDosis": "Dosis 1", "edadMinimaSemanas": 6, "intervaloDiasPrevia": null, "esRefuerzo": false, "repetirCadaDias": null },
               { "orden": 5, "nombreDosis": "Refuerzo 12 meses", "intervaloDiasPrevia": 365, "esRefuerzo": true, "repetirCadaDias": 1095 } ] }
  ```
  `dosis` en PUT reemplaza el calendario por `orden` (actualiza, agrega, elimina las ausentes; eliminar una dosis ya usada por aplicaciones -> 409).
  Response: lo mismo con `id`, `vacuna`, `especie` y `dosis[].id`.

Regla de proxima fecha (tras aplicar la dosis N): existe dosis N+1 -> `fecha + intervaloDiasPrevia`, sin adelantarse a `nacimiento + edadMinimaSemanas` de esa dosis;
era la ultima -> `fecha + repetirCadaDias` (refuerzo periodico) o ninguna (serie completa, `proximaFecha: null`); vacuna sin esquema -> `fecha + periodoEficaciaDias`.
Para especie se prefiere el esquema de la especie del paciente sobre uno generico.

## 2. Aplicaciones del paciente

`POST /api/pacientes/{id}/inmunizaciones` (201):
```json
{ "tipo": "VACUNA", "vacunaId": 1, "antiparasitarioId": null, "fechaAplicacion": "2026-10-15", "loteId": 7, "lote": "L-123",
  "fechaVencimientoLote": "2027-03-01", "dosisNumero": 2, "veterinarioId": 3, "sitioAplicacion": "Region dorsal", "viaAdministracion": "SC",
  "pesoKg": 12.5, "laboratorio": "", "dosis": "1 tableta", "observaciones": "", "reaccionAdversa": "", "externa": false, "consultaId": null,
  "proximaFecha": "2026-11-05" }
```
Solo `tipo` es siempre obligatorio (`VACUNA` exige `vacunaId`; `ANTIPARASITARIO` exige `antiparasitarioId`). Defaults: fecha = hoy; `dosisNumero` = aplicadas vigentes del paciente y producto + 1;
`veterinarioId` = el usuario si es VETERINARIO (AUXILIAR: obligatorio; ADMIN: opcional); `viaAdministracion` = la de la vacuna; `pesoKg` = peso actual; `proximaFecha` = calculada (si se envia, manda).
- Con `loteId`: se bloquea el lote y se valida: inexistente 404; vencido (`vencimiento < fechaAplicacion`) o estado CUARENTENA/RETIRADO/VENCIDO **422**; producto distinto al de la vacuna/antiparasitario 422; **stock < 1 -> 409**.
  Descuenta 1 unidad (movimiento_stock `SALIDA`, referencia `INMUNIZACION:<id>`/`DESPARASITACION:<id>`), copia `lote`/`fechaVencimientoLote` del lote. `externa:true` (aplicada en otra clinica) no admite `loteId`.
- Sin `loteId`: `lote` y `fechaVencimientoLote` son texto libre; si el vencimiento es anterior a la aplicacion -> 422.
- 422 tambien: fecha futura o anterior al nacimiento, `proximaFecha` < fecha, producto inactivo, producto de otra especie, paciente fallecido.

Response (`InmunizacionResponse`, igual para vacuna y antiparasitario; `(tipo,id)` es la clave porque los ids son de tablas distintas):
```json
{ "tipo": "VACUNA", "id": 16, "pacienteId": 10, "productoId": 2, "producto": "Antirrabica canina", "codigoProducto": "CAN-RAB",
  "clasificacion": "NUCLEO", "dosisNumero": 1, "dosisNombre": "Primovacunacion", "dosis": null,
  "fechaAplicacion": "2026-10-13", "proximaFecha": "2027-10-13", "loteId": null, "lote": "LR-9", "fechaVencimientoLote": null,
  "veterinario": { "id": 10, "nombre": "..." }, "sitioAplicacion": null, "viaAdministracion": "SC", "pesoKg": null, "laboratorio": null,
  "observaciones": null, "reaccionAdversa": null, "externa": false, "consultaId": 1, "estado": "APLICADA", "motivoAnulacion": null, "anuladoEn": null }
```
Para ANTIPARASITARIO `clasificacion` lleva el tipo (INTERNA/EXTERNA/...), `dosis` el texto libre y `dosisNumero`/`dosisNombre` son null.

- `GET /api/pacientes/{id}/inmunizaciones?tipo=VACUNA|ANTIPARASITARIO&incluirAnuladas=false` -> lista (mas reciente primero, ambos tipos mezclados si no hay `tipo`).
- `POST /api/pacientes/{id}/inmunizaciones/{id}/anular?tipo=VACUNA` (tipo por defecto VACUNA) body `{ "motivo": "obligatorio" }` -> aplicacion con `estado:"ANULADA"`.
  No se borra; devuelve la dosis al lote (movimiento `DEVOLUCION`); ya anulada -> 409. Si era la ultima, la anterior vuelve a ser la vigente (reaparece en pendientes).
- `GET /api/pacientes/{id}/esquema-sugerido`:
  ```json
  { "pacienteId": 2, "paciente": "Humito", "especie": "Perro", "fechaNacimiento": "2026-07-07", "edadSemanas": 14,
    "items": [ { "esquemaId": 1, "esquema": "Cachorro: polivalente nucleo", "vacunaId": 1, "vacuna": "...", "clasificacion": "NUCLEO", "obligatoriaLey": false,
                 "dosisAplicadas": 1, "ultimaAplicacion": "2026-10-15", "proximaDosisNumero": 2, "proximaDosisNombre": "Dosis 2",
                 "fechaSugerida": "2026-10-18", "estado": "PROXIMA" } ] }
  ```
  `estado`: `COMPLETA` (serie terminada, sin fecha), `VENCIDA`, `TOCA_AHORA` (hoy o sin aplicar y ya en edad), `PROXIMA` (<= 30 dias), `AL_DIA` (ya aplicada, proxima lejana), `FUTURA` (sin aplicar, edad minima lejana).
  Solo esquemas activos de la especie del paciente (o genericos).
- `GET /api/pacientes/{id}/carnet.pdf` -> `application/pdf` (inline, `CAR-YYYYMMDD-NNNNN.pdf`): datos del paciente y tutor principal, tabla de vacunas y de desparasitaciones
  (fecha, producto, dosis, lote, vence lote, proxima, veterinario) y firma del veterinario. Cada emision se registra en `certificado_vacunacion` (numero, usuario, SHA-256).
  Para mostrarlo en el front: `fetch` con el Bearer -> `blob` -> `URL.createObjectURL` (un `<a href>` directo no envia el token).

### Integracion con la consulta
Al **cerrar** una consulta (`POST /api/consultas/{id}/cerrar`) se publica el evento `clinica.ConsultaCerrada`; `inmunizacion` lo escucha *despues del commit* y crea una
inmunizacion por cada `consulta_vacuna` (idempotente por renglon: indice unico `inmunizacion.consulta_vacuna_id`). Vacuna = coincidencia por nombre/codigo del catalogo; si no existe se crea
automaticamente `AUTO-<NOMBRE>` (NO_NUCLEO, 365 dias, especie del paciente) para no perder el dato - el admin puede depurarlo. `proximaFecha` = la del renglon si viene, si no la del esquema.
La consulta cerrada no se toca (sigue inmutable) y un fallo del evento no revierte el cierre (se registra en el log). La **historia** (`/api/pacientes/{id}/historia`) muestra esas vacunas una sola vez
(los renglones `consulta_vacuna` ya convertidos se excluyen) e incorpora `tipo: "DESPARASITACION"` (filtrable con `?tipo=`). Mientras la consulta esta abierta se ven los renglones de la consulta.

## 3. Pendientes

`GET /api/inmunizacion/pendientes?desde=&hasta=&dias=30&especieId=&tipo=VACUNA|ANTIPARASITARIO&estado=VENCIDA|POR_VENCER&page=0&size=20`
Solo cuenta la **ultima aplicacion vigente** (no anulada) por paciente y producto; pacientes inactivos/fallecidos excluidos. Sin `hasta`: hoy + `dias` (30); sin `desde`: incluye todas las vencidas. Orden: proxima fecha.
```json
{ "items": [ { "tipo": "VACUNA", "aplicacionId": 1, "pacienteId": 2, "paciente": "Humito", "especie": "Perro", "productoId": 1, "producto": "...",
               "ultimaAplicacion": "2026-10-15", "proximaFecha": "2026-10-18", "diasRestantes": 3, "estado": "POR_VENCER",
               "tutor": { "id": 3, "nombre": "Humo Fase3", "telefono": "300...", "email": "humo@correo.test", "aceptaEmail": true, "aceptaWhatsapp": false } } ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1, "first": true, "last": true }
```
`diasRestantes` negativo = vencida. `GET /api/dashboard/resumen` -> `vacunasPorVencer` ahora = proximas dosis reales (vacunas y antiparasitarios) con vencimiento en los proximos 30 dias
(las ya vencidas no cuentan) + vacunas anotadas en consultas todavia ABIERTAS con `proximaFecha` en esa ventana (al cerrar pasan a inmunizacion, sin doble conteo).

## 4. Consentimiento del tutor (cambio en `/api/tutores`)

`TutorRequest` y `TutorResponse` ganan `aceptaEmail`, `aceptaWhatsapp`, `aceptaSms`, `autorizaTratamientoDatos` (booleanos; opcionales al escribir: ausente = sin cambio, false al crear).
Se guardan en las columnas de V13; `fecha_autorizacion_datos` se fija al activar `autorizaTratamientoDatos`. **Sin `aceptaEmail=true` el sistema no crea ni envia recordatorios por correo.**

## 5. Comunicaciones (`/api/comunicaciones`)

### Plantillas `/plantillas` (GET lista `?canal=&activo=`, GET `/{id}`, POST 201, PUT `/{id}`)
```json
{ "codigo": "VACUNA_PROXIMA", "canal": "EMAIL|WHATSAPP|SMS|PUSH", "idioma": "es", "asunto": "{{paciente}} tiene una vacuna proxima",
  "cuerpo": "Hola {{tutor}}, a {{paciente}} le corresponde {{vacuna}} el {{fecha}}. {{clinica}}", "activo": true }
```
Variables soportadas: `{{tutor}} {{paciente}} {{vacuna}} {{fecha}} {{hora}} {{clinica}} {{sede}}` (`{{clinica}}`=`{{sede}}`=`vet.clinica.nombre`; `{{vacuna}}` es el producto: vacuna o antiparasitario;
`{{hora}}` solo en citas). Una variable desconocida -> 422. La respuesta agrega `variables: [..]` calculado. (`codigo`,`canal`,`idioma`) unico -> 409. Los correos reciben al final un enlace de baja.

### Reglas `/reglas` (GET `?evento=&activo=`, GET `/{id}`, POST 201, PUT `/{id}`)
`{ "codigo","evento","diasOffset","canal","plantillaId","activo" }`; `diasOffset` negativo = antes del evento (`-7`), positivo = despues. Eventos que el job procesa:
`VACUNA_PROXIMA`, `DESPARASITACION_PROXIMA` (sobre `proximaFecha`) y `CITA` (citas PROGRAMADA/CONFIRMADA); los demas se pueden registrar pero aun no generan. La plantilla debe ser del mismo canal (422).
Response agrega `plantillaCodigo` y `canalDisponible` (false para WHATSAPP/SMS/PUSH: sin adaptador, la regla se ignora y se cuenta en `reglasSinCanal`). Semillas V17: `VAC_7D_EMAIL`, `CITA_24H_EMAIL`, etc.

### Job y recordatorios
- `POST /api/comunicaciones/ejecutar` (solo ADMIN) -> genera lo que toque hoy y procesa la cola:
  `{ "fecha","generados","yaExistian","omitidosSinConsentimiento","omitidosSinContacto","reglasSinCanal","enviados","simulados","fallidos","reintentosPendientes","cancelados","omitidosOptout" }`
- Programado: `@Scheduled` diario (`vet.recordatorios.cron`, por defecto `0 0 8 * * *` hora Bogota) + revision de cola/reintentos cada `vet.recordatorios.intervalo-envio-ms` (5 min). Desactivable con `vet.recordatorios.enabled=false`.
- Generacion: para cada regla activa con canal disponible, el aviso toca cuando `fecha_evento + diasOffset <= hoy` (antes del evento sigue vigente hasta el evento; despues, 7 dias). Clave de idempotencia
  `(referencia_tabla, referencia_id, regla_id, canal)` -> ejecutar N veces no duplica (aunque se cancele o falle). Origen: `inmunizacion`/`desparasitacion` (ultima aplicacion vigente) o `cita`.
  Se crea solo si el tutor principal esta activo, tiene consentimiento del canal (`aceptaEmail` para EMAIL), no esta dado de baja (`comunicacion_optout` canal/TODOS con categoria TODAS/SERVICIO) y tiene dato de contacto (email).
  Al enviar se **revalida** todo; si el tutor revoco -> `OMITIDO_OPTOUT`; si el origen ya no aplica (anulado, reemplazado por una dosis posterior, cita cancelada) -> `CANCELADO`.
- Envio: puerto `CanalEnvio` -> EMAIL por SMTP si `vet.mail.host` esta configurado; si no, adaptador simulado (log) con estado `ENVIADO_SIMULADO` y proveedor `LOG_SIMULADO`. **WhatsApp: interfaz documentada (`CanalEnvio`), no implementado** (requiere proveedor y plantillas aprobadas).
- Reintentos: falla -> `intentos+1`, sigue `PENDIENTE` con backoff (`reintento-minutos` x intentos); al llegar a `max-intentos` (3) -> `FALLIDO`. Cada intento deja una fila en `envio_log`.
- `GET /recordatorios?estado=&tipo=&canal=&tutorId=&pacienteId=&desde=&hasta=&page=&size=` (ADMIN, RECEPCION), `GET /recordatorios/{id}`:
  `{ "id","reglaId","regla","tutorId","tutor","pacienteId","paciente","tipo","referenciaTabla","referenciaId","canal","programadoPara","estado","intentos","ultimoError","enviadoEn","creadoEn" }`
  `estado`: PENDIENTE, ENVIADO, ENVIADO_SIMULADO, FALLIDO, CANCELADO, OMITIDO_OPTOUT.
- `POST /recordatorios/{id}/cancelar` -> recordatorio CANCELADO; solo PENDIENTE (si no, 409; 404 si no existe).
- `GET /recordatorios/{id}/bitacora` -> `[ { "id","recordatorioId","tutorId","canal","destinatario","asunto","cuerpo","estado","proveedor","proveedorMensajeId","error","enviadoEn" } ]`;
  `GET /envios?estado=&tutorId=&page=&size=` -> bitacora global paginada.

### Opt-out
- `GET /api/comunicaciones/optout?token=...` (**publico**, devuelve HTML): token firmado HMAC (`tutorId:canal`) incluido en cada correo (`{vet.public-url}/api/comunicaciones/optout?token=...`). Registra baja EMAIL/TODAS (idempotente)
  y omite los pendientes del tutor. Token invalido -> 400 (HTML).
- `GET /optouts?tutorId=`, `POST /optouts` `{ "tutorId", "canal": "EMAIL|WHATSAPP|SMS|PUSH|TODOS", "categoria": "TODAS|SERVICIO|MARKETING", "motivo" }` (201; por defecto TODOS/TODAS), `DELETE /optouts/{id}` (204) - ADMIN y RECEPCION.
  Response `{ id, tutorId, canal, categoria, desde, motivo }`. Una baja solo de MARKETING no afecta los recordatorios (categoria SERVICIO).

## 6. Configuracion (variables de entorno)

`VET_CLINICA_NOMBRE`, `VET_PUBLIC_URL` (base del enlace de baja), `VET_MAIL_HOST/PORT/USER/PASSWORD/FROM/STARTTLS` (sin host = simulado), `VET_RECORDATORIOS_ENABLED`, `VET_RECORDATORIOS_CRON`,
`VET_RECORDATORIOS_HORA_ENVIO` (08:00), `VET_RECORDATORIOS_INTERVALO_MS`, `VET_RECORDATORIOS_MAX_INTENTOS`, `VET_RECORDATORIOS_REINTENTO_MIN`.
