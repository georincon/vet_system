# Contrato API - Fase 2, Sprint 1 (backend)

Base: `http://localhost:9010`. Todas las rutas requieren `Authorization: Bearer <jwt>` salvo `/api/auth/login`.
OpenAPI vivo: `/v3/api-docs`, `/swagger-ui.html`. Migraciones nuevas: `V24__agenda_estado_sync_bloqueos.sql`.

## Convenciones

- **Errores** (formato existente): `{timestamp, status, error, message, path, fieldErrors?}`.
  400 validacion/JSON invalido; 401 sin token; 403 rol sin permiso; 404 no existe; **409 conflicto** (solape de agenda,
  transicion de estado invalida, consulta cerrada, duplicado); **422 regla de negocio** (p. ej. "bloqueo sin veterinario ni sala").
- **Edicion parcial (PUT)**: un campo ausente o `null` NO se modifica; una cadena vacia `""` en un campo de texto LIMPIA el valor
  (queda `null`). Aplica a todos los PUT de este documento y de `/api/citas`, `/api/consultas` (documentado tambien en OpenAPI).
- Fechas `YYYY-MM-DD`; horas `HH:mm` o `HH:mm:ss`; `inicio/fin` de bloqueos: `YYYY-MM-DDTHH:mm:ss` en hora de la clinica (America/Bogota, sin zona).
  Instantes (`horaLlegada`, `cerradaEn`, `creadoEn`...) en ISO-8601 UTC.
- Paginas: `{items, page, size, totalElements, totalPages, first, last}` con `?page=0&size=20` (max 200).
- El JWT ahora incluye el claim `uid` (id de usuario). Sesiones antiguas (sin `uid`) funcionan, pero para registrar auditoria
  o addendums hay que iniciar sesion de nuevo.

## Roles

| Recurso | ADMIN | VETERINARIO | RECEPCION | AUXILIAR |
|---|---|---|---|---|
| `GET /api/veterinarios`, `GET /api/dashboard/resumen` | si | si | si | si |
| Agenda lectura (`GET /api/citas/**`, `GET /api/agenda/**`) | si | si | si | si |
| `POST/PUT /api/citas`, `POST /api/citas/{id}/estado` | si | si | si | no |
| Salas, bloqueos, lista de espera (escritura) | si | no | si | no |
| `POST /api/citas/{id}/iniciar-consulta` | si | si | no | no |
| Clinica lectura (`/api/consultas`, `/api/clinica/**`, `/api/pacientes/{id}/{alergias,pesos,historia}`) | si | si | **no (403)** | si |
| Signos vitales (POST/PUT/DELETE) y `POST /api/pacientes/{id}/pesos` | si | si | no | si |
| Resto de escritura clinica (consulta, dx, recetas, cerrar, addendums, alergias) | si | si | no | no |
| `GET /api/auditoria`, `/api/usuarios/**` | si | no | no | no |

## 1. Veterinarios (desplegables)

`GET /api/veterinarios` -> `[{ "id": 2, "nombre": "Vet Smoke" }]` (usuarios activos con rol VETERINARIO, orden por nombre).

## 2. Citas (cambios)

`POST /api/citas`, `PUT /api/citas/{id}` - request (todo opcional en PUT; en alta obligatorios pacienteId, veterinarioId, fecha, horaInicio):

```json
{ "pacienteId": 1, "veterinarioId": 2, "fecha": "2026-10-14", "horaInicio": "10:00", "horaFin": "10:30",
  "tipoCitaId": 1, "salaId": 1, "quitarSala": false, "motivo": "Control", "notas": "", "estadoCitaId": 1 }
```
- Sin `horaFin`: se usa `duracionMinutos` del tipo de cita (30 min si no hay tipo). En PUT, al cambiar `horaInicio` o `tipoCitaId` sin `horaFin` se recalcula.
- `sede_id` = sede principal (automatico). `quitarSala:true` desasigna la sala.
- Solape -> **409** con mensaje: `"El veterinario ya tiene una cita en ese horario"`, `"El paciente ya tiene una cita en ese horario"`,
  `"La sala <nombre> ya esta reservada en ese horario"`, `"El veterinario tiene un bloqueo de agenda en ese horario (TIPO: motivo)"`,
  `"La sala esta bloqueada en ese horario (...)"`. Cancelada/NoAsistio/Reagendada liberan el horario (se puede reagendar encima).
- Cambiar `estadoCitaId` via PUT valida la misma maquina de estados (409 si es invalida).

Response (`CitaResponse`, tambien dentro de agenda/consultas):

```json
{ "id": 1, "codigo": "CIT-20261013-0001",
  "paciente": { "id": 1, "codigo": "...", "nombre": "Mateo", "especie": "Perro", "raza": "...", "sexo": "M", "fechaNacimiento": "...", "activo": true },
  "veterinario": { "id": 2, "username": "...", "nombre": "...", "email": null, "rol": "VETERINARIO", "activo": true },
  "fecha": "2026-10-14", "horaInicio": "10:00:00", "horaFin": "10:30:00",
  "estadoCita": { "id": 1, "codigo": "PROGRAMADA", "nombre": "Programada", "activo": true },
  "tipoCita": { "id": 1, "nombre": "Consulta general", "duracionMinutos": 30, "color": "#2E7D32" },
  "sala": { "id": 1, "nombre": "Consultorio 1", "tipo": "CONSULTORIO", "capacidad": 1, "activo": true },
  "motivo": "Control", "notas": null, "horaLlegada": null, "atencionInicio": null, "activo": true }
```
`tipoCita` y `sala` son `null` si no aplican.

### Estados y check-in: `POST /api/citas/{id}/estado`

Body `{ "estado": "EN_SALA", "motivo": "opcional (motivo de cancelacion)" }` -> `CitaResponse`.
Codigos del catalogo (`GET /api/citas/estados`): PROGRAMADA, CONFIRMADA, EN_SALA, EN_CURSO, ATENDIDA, CANCELADA, NO_ASISTIO, REAGENDADA.
Alias aceptados en este endpoint: `EN_CONSULTA`=EN_CURSO, `COMPLETADA`=ATENDIDA, `AGENDADA`=PROGRAMADA.

```
PROGRAMADA -> CONFIRMADA | EN_SALA | CANCELADA | NO_ASISTIO | REAGENDADA
CONFIRMADA -> EN_SALA | CANCELADA | NO_ASISTIO | REAGENDADA
EN_SALA    -> EN_CURSO | CANCELADA | NO_ASISTIO
EN_CURSO   -> ATENDIDA
ATENDIDA / CANCELADA / NO_ASISTIO / REAGENDADA: finales
```
Transicion invalida -> **409** `"Transicion de estado invalida: PROGRAMADA -> ATENDIDA"`; estado desconocido -> 422.
Efectos: EN_SALA fija `horaLlegada`; EN_CURSO fija `atencionInicio`; cancelacion registra quien/cuando/motivo.

## 3. Agenda

- `GET /api/agenda?desde=2026-10-14&hasta=2026-10-20&veterinarioId=&salaId=&incluirCanceladas=false`
  (`hasta` inclusivo, maximo 62 dias, si no 422). Citas: AND de filtros; bloqueos: los del veterinario **o** de la sala indicados.
  ```json
  { "desde": "2026-10-14", "hasta": "2026-10-20", "citas": [ CitaResponse ],
    "bloqueos": [ { "id": 1, "veterinario": {"id":2,"nombre":".."}|null, "sala": {SalaResponse}|null,
                    "inicio": "2026-10-14T12:00:00", "fin": "2026-10-14T13:00:00", "tipo": "ALMUERZO", "motivo": "..." } ] }
  ```
- `GET /api/agenda/tipos-cita` -> `[{id, nombre, duracionMinutos, color}]` (activos).
- **Salas** `/api/agenda/salas`: `GET ?todas=false`, `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}` (baja logica, 204).
  Body `{ "nombre": "Sala 3" (obligatorio en alta, unico), "tipo": "CONSULTORIO|QUIROFANO|HOSPITALIZACION|PELUQUERIA|IMAGENES|LABORATORIO|GUARDERIA|ESPERA", "capacidad": 1, "activo": true }`.
  Respuesta `{id, nombre, tipo, capacidad, activo}`. Nombre repetido -> 409.
- **Bloqueos** `/api/agenda/bloqueos`: `GET ?desde&hasta[&veterinarioId&salaId]` (lista), `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}` (204).
  Body `{ "veterinarioId": 2, "salaId": null, "inicio": "2026-10-14T12:00:00", "fin": "2026-10-14T14:00:00", "tipo": "VACACIONES|CAPACITACION|ALMUERZO|MANTENIMIENTO|CIRUGIA|PERSONAL|OTRO", "motivo": ".." }`.
  Debe indicar veterinario y/o sala (422), `fin > inicio` (422). Si ya hay citas vigentes en el rango -> **409**.
- **Lista de espera** `/api/agenda/lista-espera`: `GET ?estado=ACTIVA|ASIGNADA|CANCELADA|VENCIDA` (orden prioridad, antiguedad), `GET /{id}`, `POST`, `PUT /{id}`,
  `POST /{id}/asignar?citaId=` (estado ASIGNADA), `DELETE /{id}` (baja logica: estado CANCELADA, 204).
  Body `{ "pacienteId": 1 (obligatorio), "tutorId": null (defecto: tutor principal), "tipoCitaId": 1, "veterinarioId": 2, "fechaDeseadaDesde": "2026-10-15", "fechaDeseadaHasta": "2026-10-20", "franjaPreferida": "MANANA|TARDE|CUALQUIERA", "prioridad": 1-5 (defecto 3), "estado": "(solo PUT)", "notas": ".." }`.
  Respuesta `{id, paciente: PacienteResumen, tutor: {id, nombre, telefono}, tipoCita, veterinario: {id,nombre}|null, fechaDeseadaDesde, fechaDeseadaHasta, franjaPreferida, prioridad, estado, citaAsignadaId, notas, creadoEn}`.

## 4. Clinica

### Consulta (`/api/consultas`, rutas existentes conservadas)

`GET /api/consultas?fecha&pacienteId&estado=ABIERTA|CERRADA&page&size`, `GET /{id}`, `POST`, `PUT /{id}` (409 si esta CERRADA).
Request (campos nuevos ademas de los existentes `citaId, pacienteId, veterinarioId, fecha, motivo, sintomatologia, diagnostico, tratamiento, observaciones, pesoKg, temperaturaC, frecuenciaCardiaca, frecuenciaRespiratoria, medicamentos[], vacunas[]`):
`tipoConsulta` (GENERAL|CONTROL|URGENCIA|VACUNACION|PREQUIRURGICA|POSTQUIRURGICA|TELEMEDICINA|DOMICILIO|HOSPITALIZACION), `triaje` (ROJO|NARANJA|AMARILLO|VERDE|AZUL), `proximaRevision`, `plantillaId`,
`anamnesis`, `subjetivo`, `objetivo`, `evaluacion`, `plan`, `pronostico`. Si `pesoKg` cambia tambien se registra en el historial de peso.

Response `ConsultaResponse` = campos anteriores + `estado` ("ABIERTA"|"CERRADA"), `cerrada` (bool), `cerradaEn`, `firmaHash`, `tipoCita`/`sala` (de la cita vinculada, o null),
`tipoConsulta, triaje, proximaRevision, plantillaId, anamnesis, subjetivo, objetivo, evaluacion, plan, pronostico` y las listas
`signosVitales[]`, `diagnosticos[]`, `recetas[]` (ademas de `medicamentos[]`, `vacunas[]`).

- `POST /api/citas/{id}/iniciar-consulta` (201) body opcional `{ "plantillaId": 1, "tipoConsulta": "CONTROL" }` -> `ConsultaResponse` ABIERTA
  (paciente, veterinario y motivo de la cita; precarga SOAP si hay plantilla; cita pasa a EN_CURSO). 409 si la cita ya tiene consulta o esta cancelada/atendida/no asistio.
- `POST /api/consultas/{id}/cerrar` (sin body) -> `ConsultaResponse` CERRADA (+`cerradaEn`, `firmaHash` SHA-256). Respeta el trigger
  (hijos volcados primero, luego UPDATE a CERRADA). La cita vinculada pasa a ATENDIDA. 409 si ya estaba cerrada.
- **Inmutabilidad**: PUT consulta, signos, diagnosticos y recetas sobre una consulta CERRADA -> **409** `"La consulta CON-... esta CERRADA y es inmutable; registre un addendum"`.
- `POST /api/consultas/{id}/addendums` (201) `{ "tipo": "ACLARACION|CORRECCION|RESULTADO_TARDIO|SEGUIMIENTO", "texto": ".." }` -> `{id, tipo, texto, autor:{id,nombre}, creadoEn}`.
  Solo en consultas CERRADAS (422 si esta abierta). `GET /api/consultas/{id}/addendums` -> lista ascendente por fecha. Append-only (sin PUT/DELETE).

### Signos vitales `/api/consultas/{id}/signos-vitales` (ADMIN, VETERINARIO, AUXILIAR)
`POST` (201), `PUT /{signoId}`, `DELETE /{signoId}` (204). Hay varias tomas por consulta. Todos los campos opcionales, al menos uno (422):
```json
{ "temperatura": 38.6, "frecuenciaCardiaca": 110, "frecuenciaRespiratoria": 24, "pesoKg": 12.4,
  "condicionCorporal": "Ideal", "puntajeCondicionCorporal": 5, "mucosas": "Rosadas", "tiempoLlenadoCapilar": 1.5,
  "escalaDolor": 2, "hidratacionPct": 5.0, "presionSistolica": 120, "spo2": 98, "glucosaMgDl": 90, "notas": ".." }
```
Rangos validados (400): temperatura 20-50, escalaDolor 0-10, puntajeCondicionCorporal 1-9, spo2 0-100, etc. Respuesta: mismos campos + `id, registradoEn, registradoPor`.
Si trae `pesoKg`: crea `peso_registro` y actualiza `paciente.pesoActual`.

### Diagnosticos `/api/consultas/{id}/diagnosticos`
`POST` (201), `PUT /{dxId}`, `DELETE /{dxId}`. Body `{ "catalogoId": 3, "descripcion": "libre (obligatoria si no hay catalogoId)", "codigoCie": "..", "tipo": "PRESUNTIVO(defecto)|DIFERENCIAL|DEFINITIVO", "orden": 1, "lateralidad": "..", "resuelto": false }`.
Respuesta `{id, catalogoId, codigoCatalogo, descripcion, codigoCie, tipo, orden, lateralidad, resuelto}`.
Catalogo: `GET /api/clinica/diagnosticos?q=` -> `[{id, codigo, nombre, categoria}]`.

### Recetas `/api/consultas/{id}/recetas`
`POST` (201), `PUT /{recetaId}` (items null conserva, lista reemplaza), `DELETE /{recetaId}` (anula: estado CANCELADA, 204).
Body `{ "indicacionesGenerales": "..", "validaHasta": "2026-11-01", "esControlada": false, "estado": "ACTIVA|CUMPLIDA|CANCELADA (solo PUT)",
"items": [ { "itemId": null, "medicamento": "Metronidazol", "dosis": "15 mg/kg", "via": "Oral", "frecuencia": "cada 12 h", "duracion": "5 dias", "cantidad": 10, "indicaciones": "..", "orden": 1 } ] }`
(alta exige al menos un item: 422). Respuesta `{id, fecha, validaHasta, estado, esControlada, indicacionesGenerales, items:[{id,itemId,medicamento,dosis,via,frecuencia,duracion,cantidad,indicaciones,orden}]}`.

### Plantillas SOAP
`GET /api/clinica/plantillas?especieId=` -> `[{id, nombre, especieId, tipoConsulta, subjetivo, objetivo, evaluacion, plan}]` (activas; con `especieId` incluye las de esa especie y las generales).

### Paciente: alergias, pesos, historia
- `GET /api/pacientes/{id}/alergias?incluirInactivas=false`; `POST` (ADMIN/VETERINARIO) `{ "descripcion": "Penicilina" (oblig.), "severidad": "BAJA|MEDIA|ALTA", "tipo": "MEDICAMENTO|ALIMENTO|AMBIENTAL|VACUNA|OTRA", "reaccion": "..", "activo": true }`;
  `PUT /{alergiaId}`; `DELETE /{alergiaId}` (baja logica, 204). Respuesta `{id, descripcion, severidad, tipo, reaccion, registradaEn, activo}`.
- `GET /api/pacientes/{id}/pesos` -> `[{id, pesoKg, fecha, registradoPor}]` (reciente primero). `POST` (ADMIN/VETERINARIO/AUXILIAR) `{ "pesoKg": 8.4, "fecha": "2026-10-13" (defecto hoy; futura -> 422) }`.
  Actualiza `pesoActual` del paciente salvo que la fecha sea anterior al ultimo registro.
- `GET /api/pacientes/{id}/historia?tipo=CONSULTA|VACUNA|PESO|ALERGIA&page=0&size=20` -> pagina de
  `{ "tipo": "CONSULTA", "fecha": "2026-10-13", "referenciaId": 1, "titulo": "Control", "detalle": "..", "estado": "CERRADA" }`,
  ordenada por `fecha` descendente (a igual fecha: consulta, vacuna, peso, alergia). Vacunas = `inmunizacion` aplicadas + `consulta_vacuna`.

## 5. Auditoria (solo ADMIN)

`GET /api/auditoria?entidad=consulta&entidadId=1&usuarioId=1&desde=2026-10-01&hasta=2026-10-31&page=0&size=20` (hasta inclusivo) ->
pagina de `{ id, usuarioId, usuario (username), accion: INSERT|UPDATE|DELETE, entidad (tabla), entidadId, detalle (JSON {antes?,despues?}), ip, ocurridoEn }`, mas reciente primero.
Cada escritura fija `app.usuario_id` y `app.ip` en la transaccion (`set_config(...,true)` = SET LOCAL), asi el trigger registra quien. Tablas auditadas: paciente, tutor, consulta, receta, factura, pago, **cita**, **bloqueo_agenda** (V24).
Escrituras sin sesion (seeders, tareas) quedan con `usuarioId: null`.

## 6. Dashboard

`GET /api/dashboard/resumen` (cualquier rol):
```json
{ "citasHoy": 3, "citasPendientes": 7, "pacientesActivos": 120, "tutoresActivos": 98, "consultasAbiertas": 2, "consultasMes": 41,
  "vacunasPorVencer": 5,
  "proximasCitas": [ { "id": 1, "codigo": "CIT-..", "fecha": "2026-10-14", "horaInicio": "10:00:00", "horaFin": "10:30:00",
                       "paciente": {"id":1,"nombre":"Mateo"}, "veterinario": {"id":2,"nombre":".."}, "estado": "PROGRAMADA", "motivo": ".." } ] }
```
`citasHoy` excluye canceladas/no asistio/reagendadas; `citasPendientes` = PROGRAMADA/CONFIRMADA desde hoy; `pacientesActivos` excluye fallecidos;
`tutoresActivos` excluye el tutor generico "Consumidor final"; `vacunasPorVencer` = proximas dosis (inmunizacion + consulta_vacuna) en los siguientes 30 dias;
`proximasCitas` = hasta 5 citas futuras PROGRAMADA/CONFIRMADA/EN_SALA.

## Notas de implementacion

- Modulos Modulith: `citas` (agenda), nuevo `clinica` (Consulta movida aqui; depende de `citas`), nuevos `auditoria` y `dashboard` (SQL directo).
- `V24`: sincroniza `cita.estado` (texto legado en el que se apoyan las restricciones EXCLUDE de V14) con `estado_cita_id`, sin lo cual cancelar no liberaba
  el horario en la BD; indice unico parcial `uq_cita_vet_inicio_vigente` (la restriccion global `uq_cita_vet_inicio` ya la habia retirado V14);
  `bloqueo_agenda.veterinario_id` opcional (bloqueos de sala); trigger de auditoria en `cita`/`bloqueo_agenda`; `consulta_addendum.tipo` a VARCHAR(20)
  (V15 lo creo de 15 y el CHECK admite `RESULTADO_TARDIO`, 16 caracteres).
- Nombres de estado: se conservaron los del catalogo existente; "EN_CONSULTA" = `EN_CURSO` y "COMPLETADA" = `ATENDIDA`.
