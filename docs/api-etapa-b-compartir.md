# API Etapa B - Formula medica, plan de tratamiento y compartir con el tutor

Fecha: 2026-10-16. Backend Spring Boot 4.1.1 / Modulith. Migracion nueva: `V31__etapa_b_formula_plan_compartir.sql`.
Base `http://localhost:9010`. Errores con el cuerpo comun `{timestamp,status,error,message,path,fieldErrors?}`:
400 validacion, 401, 403, 404, 409 (estado/inmutabilidad), 410 (enlace no disponible), 422 (regla de negocio).
Sin facturacion/DIAN.

## Modulos

| Modulo | Contenido |
|---|---|
| `clinica` (ampliado) | Formula PDF, resumen de consulta PDF, plan de tratamiento, controles de seguimiento, verificacion publica, evento `ControlSeguimientoCreado` |
| `compartir` (nuevo) | Envio al tutor por EMAIL/WHATSAPP, enlaces seguros, bitacora. Depende solo de `shared` y del puerto `comunicaciones.CanalEnvio` |
| `comunicaciones` (ampliado) | `CanalEnvio` con adjuntos y documentos por URL; adaptador `WhatsappCloudApiCanalEnvio`; normalizacion E.164; recordatorio de controles; plantillas `DOC_*` |
| `shared.documentos.DocumentoCompartible` | Puerto que implementan `clinica` (FORMULA, RESUMEN_CONSULTA), `laboratorio` (LABORATORIO, IMAGEN, PATOLOGIA), `procedimientos` (PROCEDIMIENTO), `inmunizacion` (CARNET). Asi `compartir` no depende de ellos |
| `seguridad` (ampliado) | Perfil profesional (tarjeta profesional) |

## Roles

| Accion | ADMIN | VETERINARIO | AUXILIAR | RECEPCION |
|---|---|---|---|---|
| `GET /api/consultas/{id}/formula.pdf`, `GET /api/recetas/{id}/formula.pdf`, `GET /api/consultas/{id}/resumen.pdf` | si | si | si | no (403) |
| Plan de tratamiento / controles: leer | si | si | si | no |
| Plan de tratamiento / controles: crear, editar, cancelar | si | si | no | no |
| `PUT /api/perfil/profesional` (propio) | si | si | no | no |
| `PUT /api/usuarios/{id}/perfil-profesional` | si | no | no | no |
| `/api/compartir/**` (enviar, previsualizar, historial, reenviar) | si | si | **no por defecto** (ver abajo) | **no** |
| `POST /api/compartir/{id}/revocar` | si | si | no | no |
| `GET /api/verificar/{codigo}`, `GET /api/publico/documentos/{token}` | publico | publico | publico | publico |

**Decisiones de rol**
- RECEPCION nunca comparte documentos clinicos (igual que no accede a la historia clinica).
- AUXILIAR: por defecto NO. Con `VET_COMPARTIR_AUXILIAR=true` puede enviar solo FORMULA, LABORATORIO, IMAGEN, PATOLOGIA y CARNET (ya validados/cerrados, se exige igual) y debe indicar `veterinarioResponsableId` (VETERINARIO activo), que queda en la bitacora. Nunca PROCEDIMIENTO ni RESUMEN_CONSULTA ni revocar (403).
- Solo se comparten documentos VALIDADOS/CERRADOS: formula y resumen de consultas CERRADAS (formula no anulada), resultados con todos los examenes del tipo VALIDADOS, procedimientos REALIZADOS, carnet. Un borrador responde 422.

## 1. Perfil profesional (tarjeta profesional)

`veterinario.numero_profesional` ya existia (V1) pero no tenia API. Se crea/actualiza la fila de `veterinario` del usuario.
- `GET /api/perfil/profesional`, `PUT /api/perfil/profesional` `{ "numeroProfesional": "TP-12345", "especialidad": "Medicina interna" }` (campo null = sin cambio, "" limpia).
- `PUT /api/usuarios/{id}/perfil-profesional` (ADMIN), mismo cuerpo. Respuesta `{usuarioId,nombre,numeroProfesional,especialidad}`. Solo usuarios VETERINARIO/ADMIN (422 si no).

## 2. Formula medica (PDF)

- `GET /api/consultas/{id}/formula.pdf`: formula de la receta vigente (no cancelada) mas reciente de la consulta (404 si no tiene).
- `GET /api/recetas/{id}/formula.pdf`: formula de esa receta.
- `GET /api/consultas/{id}/resumen.pdf`: resumen de consulta (motivo, hallazgos, diagnosticos, tratamiento, plan, controles). Solo consulta CERRADA (422 si esta abierta).

Contenido de la formula: membrete (`VET_CLINICA_NOMBRE`), numero `FOR-yyyyMMdd-NNNNN`, fecha, paciente y tutor, diagnosticos, tabla de medicamentos
(nombre, concentracion/presentacion, dosis, via, frecuencia, duracion, cantidad, indicaciones), indicaciones generales, recomendaciones y cuidados,
plan de tratamiento (fases con fechas), controles y proxima cita sugerida, advertencias (las del plan + texto general), firma con nombre y
**T.P.** del veterinario ("T.P. no registrada" si falta), codigo de verificacion y URL de verificacion. Marcas: "BORRADOR" si la consulta sigue abierta, "FORMULA ANULADA" si la receta esta CANCELADA.
El numero y el codigo se asignan una sola vez (primera emision) y no cambian.

Ampliacion del modelo de receta (`RecetaItemDto`): `concentracion` y `presentacion` (opcionales) por medicamento, en POST/PUT `/api/consultas/{id}/recetas`.

### Verificacion publica
`GET /api/verificar/{codigo}` (sin autenticacion; el codigo se acepta con o sin guion y en cualquier caso):
```json
{ "valido": true, "tipoDocumento": "FORMULA_MEDICA", "numero": "FOR-20261016-00001", "fecha": "2026-10-16",
  "clinica": "Clinica Veterinaria", "veterinario": "Dra. Perez", "tarjetaProfesional": "TP-12345", "estado": "VIGENTE" }
```
`estado`: VIGENTE, CUMPLIDA, VENCIDA (`validaHasta` pasada), ANULADA, BORRADOR (consulta abierta). No devuelve paciente, tutor, medicamentos ni diagnosticos. Codigo desconocido o mal formado: **404** `{"valido":false}`.

## 3. Plan de tratamiento y controles (`/api/consultas/{id}/plan-tratamiento`)

Un plan por consulta. Tablas `plan_tratamiento`, `plan_tratamiento_item`, `control_seguimiento`.
- `GET` (404 si no hay), `POST` (201; 409 si ya existe), `PUT` (campos null = sin cambio; `items` null conserva, lista reemplaza), `DELETE` (204).
```json
{ "objetivo": "Resolver el cuadro digestivo", "recomendaciones": "Reposo y agua fresca", "advertencias": "Consultar si hay sangre",
  "estado": "ACTIVO|COMPLETADO|CANCELADO",
  "items": [ { "orden": 1, "fase": "Fase 1", "tipo": "MEDICACION|PROCEDIMIENTO|DIETA|CUIDADO|EXAMEN|CONTROL|OTRO",
               "descripcion": "Dieta blanda 3 dias", "fechaInicio": "2026-10-16", "fechaFin": "2026-10-19" } ] }
```
Respuesta: lo mismo con `id`, `consultaId`, `pacienteId`, `consultaCerrada`, `items[].id`, `controles[]`, `creadoEn`, `actualizadoEn`. `fechaFin < fechaInicio` -> 422.
- Controles: `GET /controles`, `POST /controles` (201), `PUT /controles/{id}`, `DELETE /controles/{id}` (cancela: estado CANCELADO, no borra; 409 si ya REALIZADO).
```json
{ "fechaSugerida": "2026-10-26", "motivo": "Control de hidratacion", "generarRecordatorio": true,
  "estado": "PENDIENTE|AGENDADO|REALIZADO|CANCELADO", "citaId": null }
```
Respuesta: `{id,consultaId,planId,pacienteId,fechaSugerida,motivo,estado,citaId,creadoEn,citaSugerida:{pacienteId,veterinarioId,fecha,motivo},recordatorio:{solicitado,generado,recordatorioId,detalle}}`.
`fechaSugerida` anterior a la consulta o falta de motivo -> 422. `citaSugerida` sirve para precargar `POST /api/citas` (no crea la cita). Con `citaId` el control pasa a AGENDADO (la cita debe ser del paciente).
- **Recordatorio**: con `generarRecordatorio=true` `clinica` publica el evento `ControlSeguimientoCreado`; `comunicaciones` programa un correo (plantilla `CONTROL_SEGUIMIENTO`) un dia antes a la hora de envio, solo si el tutor tiene `aceptaEmail`, correo y no esta dado de baja; si no, `recordatorio.generado=false` con el motivo en `detalle`. Aparece en `GET /api/comunicaciones/recordatorios?tipo=CONTROL`. Se cancela solo si el control deja de estar PENDIENTE.

### Inmutabilidad de la consulta cerrada
- El plan y sus items se editan solo con la consulta ABIERTA; tras cerrar, POST/PUT/DELETE -> **409** (y un trigger de BD lo impide aunque se salte la API). Correcciones: addendum (`POST /api/consultas/{id}/addendums`).
- Los **controles** si se pueden crear/actualizar tras el cierre (el seguimiento nace despues): crear uno en una consulta CERRADA registra automaticamente un addendum `SEGUIMIENTO` (append-only).

## 4. Compartir con el tutor (`/api/compartir`)

### Pre-requisitos (todos o 422 con mensaje claro y nada se envia)
Tutor activo, `autorizaTratamientoDatos=true`, consentimiento del canal (`aceptaEmail` / `aceptaWhatsapp`), sin opt-out del canal (ni `TODOS`, categoria TODAS/SERVICIO), y contacto valido (correo; telefono normalizable a E.164, Colombia +57 por defecto con `VET_WHATSAPP_PAIS`). Documentos del paciente indicado y validados. Tutor: el principal salvo `tutorId` (debe estar vinculado al paciente).

### `POST /api/compartir` -> 201
```json
{ "pacienteId": 3, "tutorId": null, "canal": "EMAIL|WHATSAPP",
  "documentos": [ { "tipo": "FORMULA|LABORATORIO|IMAGEN|PATOLOGIA|PROCEDIMIENTO|CARNET|RESUMEN_CONSULTA", "id": 12, "itemId": null } ],
  "mensaje": "nota opcional (<=500)", "veterinarioResponsableId": null }
```
`id` segun tipo: receta (FORMULA), **orden** (LABORATORIO/IMAGEN/PATOLOGIA; `itemId` opcional limita a un examen; el tipo debe coincidir con los examenes de la orden), procedimiento, **paciente** (CARNET), consulta (RESUMEN_CONSULTA).
`mensaje` es una nota libre que se agrega al texto: no escribir datos clinicos. Respuesta:
```json
{ "id": 7, "pacienteId": 3, "tutorId": 5, "canal": "WHATSAPP", "destinatario": "+57******1234", "asunto": null,
  "mensaje": "Hola Ana, te compartimos la formula medica de Rocky: [enlace seguro] - Clinica", "estado": "ENVIADO|ENVIADO_SIMULADO|FALLIDO",
  "intentos": 1, "ultimoError": null, "proveedor": "WHATSAPP_CLOUD_API|WHATSAPP_SIMULADO|SMTP|LOG_SIMULADO",
  "creadoPor": 2, "creadoPorNombre": "...", "responsableId": null, "creadoEn": "...", "enviadoEn": "...", "revocado": false, "revocadoEn": null,
  "documentos": [ { "id": 9, "tipo": "FORMULA", "documentoId": 12, "itemId": null, "titulo": "Formula medica FOR-...", "nombreArchivo": "FOR-....pdf", "tamanoBytes": 4120, "hashSha256": "..." } ],
  "enlaces": [ { "id": 4, "documentoEnvioId": 9, "tokenPrefijo": "aB3dE9", "url": "https://clinica.example/api/publico/documentos/<token>", "expiraEn": "...", "maxDescargas": 5, "descargas": 0, "revocado": false, "ultimoAcceso": null } ],
  "urlWaMe": "https://wa.me/573001234567?text=..." }
```
- **EMAIL**: un correo con todos los PDF adjuntos (SMTP si `VET_MAIL_HOST`; si no, `ENVIADO_SIMULADO`/`LOG_SIMULADO` con bitacora). Incluye enlace de baja. Sin enlaces.
- **WHATSAPP**: un enlace seguro por documento y un mensaje por documento (Cloud API) o simulado. `urlWaMe` se devuelve siempre (tambien si falla o es simulado) para que recepcion/veterinario lo envie manualmente desde su WhatsApp; incluye los enlaces de todos los documentos.
- El texto solo lleva saludo, nombre de la mascota, tipo de documento y enlace/adjunto. La bitacora guarda el texto con `[enlace seguro]`; el token completo y `url` solo existen en la respuesta del envio/reenvio (en BD solo el hash SHA-256 y un prefijo de 6 caracteres).
- Si el proveedor falla: **201 con `estado: FALLIDO`** y `ultimoError`; use `reenviar`.
- Se guarda la instantanea del PDF enviado (`compartir_envio_documento`, inmutable, con hash): lo que se descarga es exactamente lo que se envio.
- Errores: 422 (consentimiento/datos/baja/contacto, documento no validado, documento ajeno al paciente, tutor ajeno), 404 (documento inexistente), 403 (rol), 400 (canal/tipo invalido, lista vacia).

### `POST /api/compartir/previsualizar` -> 200
Mismo cuerpo; no envia ni persiste (no revela el contacto completo):
```json
{ "tutorId": 5, "tutor": "Ana Perez", "canal": "EMAIL", "destinatario": "a***@correo.com", "consentimientoCanal": true,
  "autorizaTratamientoDatos": false, "bajaComunicaciones": false, "contactoValido": true,
  "faltantes": ["El tutor no ha autorizado el tratamiento de datos personales (autorizaTratamientoDatos)"], "puedeEnviar": false,
  "asunto": "...", "mensaje": "... [enlace seguro] ...",
  "documentos": [ { "tipo": "LABORATORIO", "id": 4, "titulo": null, "enviable": false, "problema": "Solo se comparten resultados VALIDADOS; ..." } ] }
```

### Otras rutas
- `GET /api/compartir/historial?pacienteId=&page=&size=` -> pagina (`{items,page,size,totalElements,totalPages,first,last}`) de envios, mas reciente primero (misma forma que arriba, `enlaces[].url` siempre null).
- `GET /api/compartir/{id}` -> un envio.
- `POST /api/compartir/{id}/reenviar` -> 200: revalida consentimientos, **revoca los enlaces anteriores y crea nuevos**, reenvia la misma instantanea, `intentos+1`.
- `POST /api/compartir/{id}/revocar` `{ "motivo": "opcional" }` -> 200: revoca todos los enlaces vigentes (`revocado=true`). 409 si no hay enlaces activos (los envios EMAIL llevan el PDF adjunto y no se pueden revocar).

### Enlace publico
`GET /api/publico/documentos/{token}` (sin autenticacion) -> `application/pdf` inline, `Cache-Control: no-store`, `X-Robots-Tag: noindex`.
Token de 256 bits (SecureRandom, base64url); vigencia `VET_COMPARTIR_VIGENCIA_HORAS` (72); `VET_COMPARTIR_MAX_DESCARGAS` (5; Meta descarga el archivo al enviar, por eso no es 1). **404** token desconocido; **410** revocado, vencido o con el limite alcanzado (mismo mensaje generico). Cada intento queda en `compartir_acceso` (OK, EXPIRADO, REVOCADO, LIMITE, NO_ENCONTRADO con IP y user-agent) y cada descarga exitosa en `auditoria` (`DESCARGA_PUBLICA_DOCUMENTO`, tipo LECTURA).

### Auditoria
`compartir_envio` y `compartir_enlace` tienen trigger de auditoria (usuario que envia/revoca/reenvia). `compartir_acceso` y `compartir_envio_documento` son append-only; no hay DELETE fisico.

## 5. Plantillas de mensaje por documento

Sembradas en V31, editables con `/api/comunicaciones/plantillas` (ADMIN): `DOC_FORMULA`, `DOC_LABORATORIO`, `DOC_IMAGEN`, `DOC_PATOLOGIA`, `DOC_PROCEDIMIENTO`, `DOC_CARNET`, `DOC_RESUMEN_CONSULTA` (un documento) y `DOC_VARIOS` (varios en EMAIL / un unico texto wa.me), en EMAIL y WHATSAPP, mas `CONTROL_SEGUIMIENTO` (EMAIL).
Variables nuevas: `{{documento}}` (tipo legible, p. ej. "formula medica") y `{{enlace}}`; se mantienen `{{tutor}} {{paciente}} {{clinica}} {{fecha}}...`. Una variable desconocida -> 422.

## 6. Configuracion (variables de entorno)

| Variable | Defecto | Uso |
|---|---|---|
| `VET_PUBLIC_URL` | `http://localhost:9010` | Base de los enlaces publicos, verificacion y baja. **En produccion debe ser https y accesible desde Internet** (Meta descarga los documentos) |
| `VET_CLINICA_NOMBRE` | Clinica Veterinaria | Membrete y `{{clinica}}` |
| `VET_MAIL_HOST/PORT/USER/PASSWORD/FROM/STARTTLS` | vacio = simulado | Correo SMTP |
| `VET_WHATSAPP_TOKEN` | vacio | Token de acceso (permanente) de la Cloud API |
| `VET_WHATSAPP_PHONE_ID` | vacio | Phone number ID del numero emisor |
| `VET_WHATSAPP_API_VERSION` | `v21.0` | Version de Graph API |
| `VET_WHATSAPP_BASE_URL` | `https://graph.facebook.com` | Solo pruebas/proxy |
| `VET_WHATSAPP_TEMPLATE` | vacio | Plantilla aprobada; vacio = mensajes `document` directos |
| `VET_WHATSAPP_TEMPLATE_LANG` | `es` | Idioma de la plantilla |
| `VET_WHATSAPP_TEMPLATE_PARAMS` | `tutor,paciente,enlace` | Variables (en orden) del cuerpo `{{1}}..{{n}}`; admite `tutor,paciente,clinica,documento,enlace` |
| `VET_WHATSAPP_TEMPLATE_HEADER_DOC` | `false` | Envia el documento tambien en la cabecera de la plantilla |
| `VET_WHATSAPP_PAIS` | `57` | Codigo de pais para numeros sin prefijo |
| `VET_WHATSAPP_MAX_REINTENTOS` / `VET_WHATSAPP_BACKOFF_MS` | `2` / `500` | Reintentos con backoff exponencial ante red/429/5xx (4xx no se reintenta) |
| `VET_COMPARTIR_VIGENCIA_HORAS` / `VET_COMPARTIR_MAX_DESCARGAS` | `72` / `5` | Enlaces seguros |
| `VET_COMPARTIR_AUXILIAR` | `false` | Habilita al AUXILIAR (con veterinario responsable) |

### Configurar WhatsApp Cloud API paso a paso
1. Crear una app tipo "Business" en developers.facebook.com y agregar el producto **WhatsApp**; vincular una cuenta de WhatsApp Business (WABA).
2. En *WhatsApp > API Setup* tomar el **Phone number ID** (`VET_WHATSAPP_PHONE_ID`). Registrar el numero real de la clinica (verificacion por codigo) para salir de modo prueba.
3. Crear un **usuario del sistema** en Business Settings, asignarle la WABA y generar un **token permanente** con permiso `whatsapp_business_messaging` (`VET_WHATSAPP_TOKEN`). No use el token temporal de 24 h en produccion.
4. Mensajes iniciados por la clinica fuera de la ventana de 24 h exigen **plantilla aprobada**: en WhatsApp Manager crear una plantilla de categoria *Utility*, idioma `es`, cuerpo p. ej. `Hola {{1}}, te compartimos el documento de {{2}}: {{3}}` (variables = tutor, paciente, enlace) y esperar la aprobacion. Configurar `VET_WHATSAPP_TEMPLATE=<nombre>`, `VET_WHATSAPP_TEMPLATE_LANG=es` y `VET_WHATSAPP_TEMPLATE_PARAMS=tutor,paciente,enlace`. Con el enlace en el cuerpo no necesita cabecera de documento. Si la plantilla lleva cabecera tipo documento, active `VET_WHATSAPP_TEMPLATE_HEADER_DOC=true`.
5. Sin plantilla (`VET_WHATSAPP_TEMPLATE` vacio) se envian mensajes `document` por URL, que **solo se entregan si el tutor escribio a la clinica en las ultimas 24 h**; sirve para pruebas.
6. Fijar `VET_PUBLIC_URL` a la URL https publica del backend (los enlaces deben abrirse desde Internet; Meta descarga el PDF desde ahi).
7. Verificar el numero destino del tutor (el sistema lo normaliza a E.164) y que tenga `aceptaWhatsapp=true` y `autorizaTratamientoDatos=true`.
8. Probar con `POST /api/compartir/previsualizar` y luego un envio; revise `estado` (`ENVIADO`) y `ultimoError` (mensaje de Meta) en el historial. Mientras falte token o phone id el modo es simulado (`ENVIADO_SIMULADO`) y `urlWaMe` permite el envio manual.

Peticion que emite el adaptador: `POST https://graph.facebook.com/v21.0/<PHONE_ID>/messages` con `Authorization: Bearer <TOKEN>` y
`{"messaging_product":"whatsapp","recipient_type":"individual","to":"573001234567","type":"template","template":{"name":"...","language":{"code":"es"},"components":[{"type":"body","parameters":[{"type":"text","text":"Ana"},...]}]}}`
(o `"type":"document","document":{"link":"...","filename":"...","caption":"..."}`).

## 7. Migracion V31 (justificacion)
1. `receta`: `numero_formula`, `codigo_verificacion` (unicos), `formula_emitida_en`; `receta_item`: `concentracion`, `presentacion`; secuencia `seq_formula_medica`.
2. `plan_tratamiento`, `plan_tratamiento_item` (con trigger de inmutabilidad de V15), `control_seguimiento` (sin congelar) y auditoria por trigger.
3. `compartir_envio`, `compartir_envio_documento` (PDF enviado), `compartir_enlace` (hash del token), `compartir_acceso` (bitacora publica) con triggers append-only/no-delete y auditoria.
4. Semillas de plantillas `DOC_*` y `CONTROL_SEGUIMIENTO`.

## 8. Pruebas
`FormulaPlanApiTest` (PDF con dosis/firma/T.P., plan e inmutabilidad, verificacion publica, recordatorio de control), `CompartirApiTest` (422 sin consentimiento/datos/baja, EMAIL simulado con adjuntos y bitacora, WhatsApp simulado con enlace, vencido/revocado/limite/desconocido, no validado/ajeno, roles, plantillas), `CompartirWhatsappApiTest` (servidor HTTP falso de la Cloud API: payload, Bearer, fallo y reenvio, AUXILIAR habilitado), `WhatsappCloudApiCanalEnvioTest`, `TelefonosTest` (E.164), `EmailCanalEnvioTest` (adjuntos MIME).

## 9. Datos de prueba / humo
`admin/admin`; crear VETERINARIO (`POST /api/usuarios`), registrar su tarjeta (`PUT /api/perfil/profesional`), tutor con `aceptaEmail`, `aceptaWhatsapp`, `autorizaTratamientoDatos` y telefono `3001234567`; paciente; consulta + receta + cierre; `GET /api/consultas/{id}/formula.pdf`; `POST /api/compartir`.

## 10. Pendientes
- Estado de entrega de WhatsApp (webhooks de Meta: entregado/leido/fallido) y respuesta del tutor.
- Recordatorios automaticos por WhatsApp con plantillas por regla (hoy el canal no entra al motor de recordatorios).
- Limite de tasa/captcha en `/api/verificar` y `/api/publico`; QR en la formula; firma digital del PDF.
- Almacenar las instantaneas fuera de la BD (S3/MinIO) y purga por antiguedad.
- Cita automatica desde un control (hoy solo `citaSugerida`).
- Validar con la clinica los textos de plantillas, advertencias de la formula y consentimientos.
- Etapa C (frontend): pantallas de formula, plan y "Enviar al tutor".
