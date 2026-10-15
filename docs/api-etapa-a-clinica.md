# API Etapa A - Documentacion clinica avanzada (archivos, laboratorio, imagen, patologia, procedimientos)

Fecha: 2026-10-15. Backend Spring Boot 4.1.1 / Modulith. Migraciones V29 (estructura) y V30 (semillas).
Todas las rutas requieren `Authorization: Bearer <JWT>` salvo `/api/laboratorio/integracion/**` (API key).
Errores con el cuerpo comun `{timestamp, status, error, message, path, fieldErrors?}`:
400 (formato/validacion de campos), 401, 403, 404, 409 (estado/inmutabilidad/duplicado), 413 (tamano), 415 (tipo de archivo),
422 (regla de negocio).

## Modulos nuevos

| Modulo | Contenido | Depende de |
|---|---|---|
| `archivos` | Subida segura, descarga, miniaturas, baja logica (`adjunto`). API publica: `ArchivosApi`, `TipoArchivo` | `shared` |
| `laboratorio` | Catalogo, ordenes, resultados, validacion/correccion, importacion HL7/CSV/JSON, tendencia, informes de imagen y patologia, PDF | `archivos` (solo `ArchivosApi`), `shared` |
| `procedimientos` | Procedimientos ambulatorios y cirugias, monitoreo anestesico, consentimiento, reporte PDF | `archivos`, `shared` |
| `shared.pdf.DocumentoPdf` | Servicio reutilizable de PDF con membrete (lo usara la Etapa B) | - |
| `shared.clinico` | `PacienteLector`/`PacienteBasico` (paciente + tutor principal, especie, sexo, edad) y `ProfesionalLector` (nombre y tarjeta profesional) | - |

`clinica` (linea de tiempo) lee las tablas nuevas por SQL: no hay dependencia de codigo hacia los modulos nuevos. `ModularityTests` verde, sin ciclos.

## Matriz de roles

| Accion | ADMIN | VETERINARIO | AUXILIAR | RECEPCION |
|---|---|---|---|---|
| Archivos: subir / listar / descargar | si | si | si | no (403) |
| Archivos: baja logica | si | si | no | no |
| Catalogo de examenes: leer | si | si | si | no |
| Catalogo: crear / editar | si | si | no | no |
| Ordenar examenes, estado EN_PROCESO/CANCELADA | si | si | no | no |
| Registrar toma de muestra | si | si | si | no |
| Capturar resultados / importar (HL7, CSV, JSON) | si | si | si | no |
| Validar, corregir, informe de imagen, informe de patologia | si | si | no | no |
| Leer ordenes, bandeja, informe PDF, historial, tendencia | si | si | si | no |
| Procedimientos: crear, editar, estado, consentimiento, reporte PDF | si | si | no | no |
| Procedimientos: leer, registrar monitoreo anestesico | si | si | si | no |
| `/api/laboratorio/integracion/**` | solo API key (un JWT recibe 403) | | | |

## 1. Archivos (`/api/archivos`)

Configuracion: `vet.archivos.ruta` (env `VET_ARCHIVOS_RUTA`; Docker: `/data/archivos`, volumen `veterinaria_archivos`),
`vet.archivos.max-bytes` (50 MB), `vet.archivos.max-bytes-dicom` (256 MB), `vet.archivos.miniatura-px` (400).
Spring multipart: `spring.servlet.multipart.max-file-size=260MB` (tope tecnico).

Seguridad de almacenamiento: nombre en disco aleatorio `yyyy/MM/<uuid>.<ext>` (la extension sale del tipo REAL, no del nombre);
el nombre original solo se guarda para mostrar (se reduce al nombre base: sin `/`, `\`, `..` ni caracteres de control);
toda ruta se resuelve contra la raiz y se verifica que no escape; hash SHA-256 por streaming; sin DELETE fisico (trigger en BD);
cada lectura de contenido se registra en `auditoria` (`tipo_evento='LECTURA'`, accion `LEER_ARCHIVO`).

Validacion de subida (en este orden):
1. Extension permitida: `pdf, jpg, jpeg, png, webp, dcm, txt` -> si no, **415**.
2. Tamano por tipo (DICOM tiene limite mayor) -> **413**.
3. Tipo real por magic bytes (`%PDF-`, FFD8FF, PNG, RIFF/WEBP, `DICM` en el byte 128, texto UTF-8 sin controles) -> si no se reconoce o **no coincide con la extension**, **415**.
4. Coherencia con el tipo clinico: `IMAGEN|RADIOGRAFIA|ECOGRAFIA` aceptan jpg/png/webp/dcm; `DICOM` solo dcm; `PDF_LABORATORIO` solo pdf; el resto cualquiera -> **422**.
5. Vinculos (`consultaId`, `ordenExamenId`, `ordenItemId`, `cirugiaId`) deben existir y ser del paciente -> **422**.

| Metodo y ruta | Descripcion |
|---|---|
| `POST /api/archivos` (multipart) | Campos: `archivo` (parte), `pacienteId`, `tipo` (IMAGEN, RADIOGRAFIA, ECOGRAFIA, DICOM, PDF_LABORATORIO, INFORME, CONSENTIMIENTO, OTRO), `consultaId?`, `ordenExamenId?`, `ordenItemId?` (requiere orden), `cirugiaId?`, `descripcion?`. 201 + metadatos |
| `GET /api/archivos/{id}` | Metadatos |
| `GET /api/archivos/{id}/contenido` | Descarga autenticada con el `Content-Type` real, `X-Content-Type-Options: nosniff`, `Cache-Control: no-store, private`; DICOM como `attachment`. 404 si esta eliminado |
| `GET /api/archivos/{id}/miniatura` | JPEG (lado mayor 400 px) de JPG/PNG |
| `GET /api/archivos?consultaId=&ordenExamenId=&ordenItemId=&cirugiaId=` | Activos por vinculo (al menos un filtro) |
| `GET /api/pacientes/{id}/archivos?tipo=&incluirEliminados=false` | Archivos del paciente |
| `DELETE /api/archivos/{id}?motivo=...` | Baja logica; motivo obligatorio (>= 5 caracteres); 409 si ya estaba eliminado |

Respuesta (`ArchivoInfo`):
```json
{ "id": 7, "pacienteId": 3, "consultaId": null, "ordenExamenId": 12, "ordenItemId": 31, "cirugiaId": null,
  "tipo": "ECOGRAFIA", "nombreOriginal": "higado.jpg", "contentType": "image/jpeg", "tamanoBytes": 48213,
  "hashSha256": "9f2c...", "descripcion": "Corte longitudinal", "tieneMiniatura": true, "anchoPx": 640, "altoPx": 480,
  "subidoPorId": 2, "subidoPorNombre": "Dra. Perez", "subidoEn": "2026-10-15T20:11:03Z", "activo": true,
  "eliminadoEn": null, "motivoEliminacion": null,
  "urlContenido": "/api/archivos/7/contenido", "urlMiniatura": "/api/archivos/7/miniatura" }
```
Ejemplo: `curl -H "Authorization: Bearer $T" -F pacienteId=3 -F tipo=ECOGRAFIA -F ordenExamenId=12 -F ordenItemId=31 -F "archivo=@higado.jpg" http://localhost:9010/api/archivos`

## 2. Laboratorio

### 2.1 Catalogo (`/api/laboratorio/examenes`)

- `GET ?tipo=LAB|IMAGEN|PATOLOGIA|FUNCIONAL&q=&soloActivos=true` -> lista (sin parametros; `esPanel` = mas de un parametro activo).
- `GET /{id}` -> detalle con parametros y rangos.
- `POST` / `PUT /{id}` (ADMIN, VETERINARIO). `PUT` con `parametros` sincroniza el panel: se emparejan por `id` o `codigo`; los que ya no llegan se **desactivan** (los resultados historicos los referencian); `rangos` presente reemplaza los rangos del parametro.

```json
{ "codigo": "HEMO", "nombre": "Hemograma completo", "tipo": "LAB", "categoria": "HEMATOLOGIA", "muestra": "Sangre EDTA",
  "requiereAyuno": false, "tiempoEntregaHoras": 2, "esExterno": false, "itemId": null, "codigoLoinc": null,
  "loincEstado": "POR_VALIDAR", "activo": true,
  "parametros": [ { "codigo": "HCT", "nombre": "Hematocrito", "unidad": "%", "unidadUcum": "%", "tipoValor": "NUMERICO",
      "orden": 1, "codigoLoinc": "4544-3", "codificacionEstado": "POR_VALIDAR", "valorNormalTexto": null,
      "opciones": null, "decimales": null, "activo": true,
      "rangos": [ { "especieId": 1, "sexo": null, "edadMinMeses": null, "edadMaxMeses": null, "valorMin": 37, "valorMax": 55,
                    "criticoMin": 15, "criticoMax": null, "fuente": "Orientativo adultos (por validar con el laboratorio)" } ] } ] }
```
`tipoValor`: `NUMERICO`, `TEXTO`, `CUALITATIVO` (con `opciones` "NEGATIVO|POSITIVO" y `valorNormalTexto`). Errores: 409 codigo duplicado, 422 rango invalido (min>max, edad invertida, critico fuera del rango, rango duplicado por especie/sexo/edad minima, especie inexistente), 400 enum invalido.

**Semilla (V30)**: HEMO (hemograma con indices y formula), QUIM, HEPA, RENAL, ELEC, UROA, COPRO, PARVO, FELVFIV, HEMOPAR (4Dx/Ehrlichia), imagen (RX, ECO-ABD, ECO-CARD, ECG como FUNCIONAL) y patologia (CITO, HISTO).
**Por validar (documentado tambien en la migracion)**: los rangos de referencia son **orientativos** (adultos de perros y gatos, mas un rango juvenil de HCT/HGB hasta 3 meses) y los valores criticos tambien; deben validarse con el laboratorio/veterinario porque dependen del equipo y metodo. Los codigos LOINC son de medicina **humana**, solo para analitos conocidos con seguridad (los demas quedan NULL) y todos estan `POR_VALIDAR`; UCUM se deriva de la unidad.

### 2.2 Ordenes y bandeja (`/api/laboratorio/ordenes`)

Estados: `SOLICITADA -> MUESTRA_TOMADA -> EN_PROCESO -> COMPLETADA` o `CANCELADA`. Items: `SOLICITADO -> RESULTADO -> VALIDADO` (o `CANCELADO`).
- `POST` crea la orden: `{pacienteId, consultaId?, prioridad: RUTINA|URGENTE|STAT, indicacionClinica?, laboratorioExterno?, examenIds?[], examenCodigos?[]}` (al menos un examen, activo). Codigo `ORD-yyyyMMdd-NNNNN`. 201 + detalle.
- `GET ?estado=SOLICITADA,EN_PROCESO&prioridad=&pacienteId=&consultaId=&tipo=LAB|IMAGEN|PATOLOGIA|FUNCIONAL&desde=&hasta=&q=&page=&size=` -> bandeja: abiertas primero ordenadas por prioridad (STAT, URGENTE, RUTINA) y antiguedad; completadas/canceladas al final (mas recientes primero). Elemento: `{id, codigo, pacienteId, pacienteNombre, pacienteCodigo, especie, consultaId, prioridad, estado, solicitadaEn, solicitadoPor, laboratorioExterno, itemsTotal, itemsValidados, hayCriticos, examenes[]}`.
- `GET /{id}` -> detalle: items con `resultados` (vigentes) y `resultadosCorregidos` (historial), `informeImagen`/`informePatologia`, `archivos`, y `eventos` (bitacora append-only: CREADA, MUESTRA, ESTADO, CAPTURA, IMPORTACION, VALIDACION, CORRECCION, INFORME, CANCELACION).
- `POST /{id}/muestra` `{observaciones?}` (AUXILIAR incluido): SOLICITADA -> MUESTRA_TOMADA (409 en otro estado; 422 si ningun examen requiere muestra).
- `POST /{id}/estado` `{estado: EN_PROCESO|CANCELADA, motivo?}` (ADMIN, VETERINARIO). CANCELADA exige motivo y falla (409) si hay examenes validados. COMPLETADA es **automatica** al validar todos los items no cancelados. EN_PROCESO tambien se activa solo al capturar el primer resultado.
- `GET /{id}/informe.pdf[?itemId=]` -> PDF.

Regla: LAB y PATOLOGIA requieren muestra tomada para capturar; IMAGEN/FUNCIONAL no.

### 2.3 Resultados, validacion y correccion

`PUT /{id}/items/{itemId}/resultados` (ADMIN, VETERINARIO, AUXILIAR) - captura/actualiza **borradores**:
```json
{ "resultados": [ { "parametroCodigo": "HCT", "valorNumerico": 30.5, "unidad": "%", "observacion": "..." },
                  { "parametroId": 25, "valorTexto": "POS" } ] }
```
Respuesta = item: `{id, examenCodigo, examenNombre, tipo, estado, interpretacion, validadoPor, validadoEn, version, corregido, parametrosEsperados, resultados:[...], resultadosCorregidos:[...], archivos:[...]}`; cada resultado:
```json
{ "id": 91, "parametroCodigo": "HCT", "parametroNombre": "Hematocrito", "codigoLoinc": "4544-3", "valorNumerico": 30.5,
  "valorTexto": null, "unidad": "%", "refMin": 37, "refMax": 55, "refCriticoMin": 15, "refCriticoMax": null,
  "bandera": "L", "critico": false, "estado": "BORRADOR", "origen": "MANUAL", "observacion": null,
  "registradoEn": "...", "registradoPor": {"id": 4, "nombre": "..."}, "validacionId": null, "reemplazaA": null,
  "reemplazadoPor": null, "motivoCorreccion": null }
```
- **Bandera automatica**: rango de `rango_referencia` por la especie del paciente; el mas especifico gana (sexo fijado > edad acotada; edad en meses a la fecha de captura; sin fecha de nacimiento no aplican los rangos con limites de edad). `N` normal, `L`/`H` fuera de rango, `LL`/`HH` por debajo/encima del valor critico (`critico=true`), `A` cualitativo distinto del valor normal, sin bandera si no hay rango. Se guarda el **snapshot** del rango aplicado (`refMin/refMax/refCritico*`).
- Valores: numericos admiten coma o punto decimal (y prefijo `<`/`>` que se conserva en `valorTexto`); cualitativos aceptan sinonimos (POS/NEG/POSITIVE...) y se normalizan a las opciones; la unidad debe coincidir con la del parametro (`x10^3/uL` equivale a `10*3/uL`) -> 422.
- 409 si la orden no tiene muestra (LAB), esta cancelada, o el examen ya esta VALIDADO.

`POST /{id}/items/{itemId}/validar` `{interpretacion?}` (ADMIN, VETERINARIO): convierte los borradores en `VALIDADO` (inmutable), crea una version en `orden_item_validacion` (append-only), pasa el item a VALIDADO y completa la orden si era el ultimo. **Con valores criticos la interpretacion es obligatoria (422).** Se puede validar con parametros incompletos (hay al menos un resultado).

`POST /{id}/items/{itemId}/correccion` (ADMIN, VETERINARIO):
```json
{ "motivo": "Error de digitacion del hematocrito", "interpretacion": "opcional",
  "resultados": [ { "parametroCodigo": "HCT", "valorNumerico": 41 } ],
  "informeImagen": { ... }, "informePatologia": { ... } }
```
Correccion **enlazada append-only**: el registro original queda `CORREGIDO` (se conserva, con `reemplazadoPor`), el nuevo queda `VALIDADO` con `reemplazaA` y `motivoCorreccion`; el item sube de version. Solo los parametros enviados se reemplazan. Motivo obligatorio (>= 5 caracteres, 422); solo items VALIDADOS (409). La inmutabilidad la **impone la base de datos** (triggers): UPDATE/DELETE de un resultado validado o corregido se rechaza (409), y `orden_item_validacion`, `orden_examen_evento` y `lab_importacion` son append-only.

**Consulta cerrada (decision)**: una orden puede vincularse a cualquier consulta del paciente (abierta o cerrada). Si al validar o corregir la consulta ya esta `CERRADA`, se inserta un `consulta_addendum` (`RESULTADO_TARDIO` al validar, `CORRECCION` al corregir) con el examen, los parametros fuera de rango y la interpretacion; la consulta no se modifica. Con consulta abierta no se escribe addendum (los resultados se ven con `GET /api/laboratorio/ordenes?consultaId=`).

### 2.4 Importacion automatica

Los resultados importados entran como **BORRADOR** (origen `ARCHIVO` o `INTEGRACION`); el veterinario valida. Emparejamiento dentro de la orden por **LOINC** o por **codigo local** del parametro (si el mismo codigo existe en dos examenes de la orden, se aplica a ambos). Se rechaza una linea si el parametro no coincide (`SIN_COINCIDENCIA`), la unidad no coincide (`UNIDAD_NO_COINCIDE`), el valor es invalido (`VALOR_INVALIDO`), el examen esta validado (`ITEM_VALIDADO`) o cancelado, la orden esta cerrada (`ORDEN_CERRADA`) o sin muestra (`ORDEN_SIN_MUESTRA`), o la linea apunta a otra orden distinta de la indicada (`ORDEN_NO_COINCIDE`). La orden se identifica por (1) `ordenId`, (2) codigo de orden (HL7 OBR-2, columna `orden_codigo`, `ordenCodigo`), (3) codigo del paciente (HL7 PID-3) si tiene **una sola** orden abierta.
El mismo contenido no se importa dos veces (409; hash SHA-256 unico en `lab_importacion`, que ademas conserva el mensaje original).

`POST /api/laboratorio/importar` (ADMIN, VETERINARIO, AUXILIAR):
- JSON: `{formato: "HL7"|"CSV"|"JSON", contenido?: "<texto HL7/CSV o JSON>", resultados?: [{examenCodigo?, parametroCodigo?, loinc?, valor, unidad?, observacion?}], ordenId?, ordenCodigo?, dryRun?: false, origen?: "ARCHIVO"|"INTEGRACION"}`
- Multipart: `archivo` (hasta 2 MB; UTF-8 o ISO-8859-1), `formato`, `ordenId?`, `ordenCodigo?`, `dryRun=false`, `origen=ARCHIVO`.
- Respuesta: `{formato, origen, dryRun, total, importados, noCoinciden, rechazados, ordenIds[], importacionId (null en dryRun), advertencias[], lineas:[{linea, ordenCodigo, examenCodigo, parametroCodigo, loinc, valor, unidad, estado: IMPORTABLE|IMPORTADO|SIN_COINCIDENCIA|SIN_ORDEN|..., itemId, parametroId, parametro, bandera, critico, mensaje}]}`.
- `dryRun=true` no escribe nada; sin ninguna linea importable en modo real -> 422.
- `GET /api/laboratorio/plantilla-csv` descarga la plantilla.

**HL7 v2 (ORU^R01)** - soporta MSH/PID/OBR/OBX/NTE, delimitadores de MSH, secuencias de escape (`\F\ \S\ \T\ \R\ \E\ .br`), tipos NM/ST/TX/CE/CWE/SN. OBR-2 = codigo de nuestra orden (OBR-3, del laboratorio, se ignora). OBX-3: `codigo^nombre^sistema` (`LN`/`LOINC` = LOINC; otro = codigo local; alterno en componentes 4-6). OBX-6 unidades (**el `^` de `x10^3/uL` debe escaparse como `\S\`**; tambien vale `10*3/uL`). OBX-8 (bandera del equipo) se guarda como observacion (la bandera la calcula el sistema). OBX-11 `X`/`D` se omiten con advertencia.
```
MSH|^~\&|ANALIZADOR|LAB|VETSYS|CLINICA|20261015101500||ORU^R01|MSG0001|P|2.5
PID|1||PAC-20261015-0001^^^CLINICA^MR||Firulais
OBR|1|ORD-20261015-00001|FILL123|HEMO^Hemograma^L|||20261015100000
OBX|1|NM|4544-3^Hematocrit^LN||30.5|%|37-55|L|||F
OBX|2|NM|WBC^Leucocitos^L||22,3|10*3/uL|6-17|H|||F
OBX|3|NM|PLT^Plaquetas^L||350|x10\S\3/uL|||||F
OBX|4|NM|PARVO^Parvovirus^L||NEG||||||F
NTE|1||Muestra lipemica
```
Ejemplo dry-run: `curl -H "Authorization: Bearer $T" -H "Content-Type: application/json" -d '{"formato":"HL7","dryRun":true,"contenido":"MSH|^~\\&|..."}' .../api/laboratorio/importar`

**CSV** (coma o `;`, comillas dobles, `#` comentarios, BOM tolerado; columnas `orden_codigo, examen_codigo, parametro_codigo, loinc, valor, unidad, observacion`; solo `valor` y (`parametro_codigo` o `loinc`) son obligatorias):
```
orden_codigo,examen_codigo,parametro_codigo,loinc,valor,unidad,observacion
ORD-20261015-00001,HEMO,HCT,4544-3,41.5,%,
ORD-20261015-00001,HEMO,WBC,6690-2,22.3,x10^3/uL,Leucocitosis marcada
ORD-20261015-00001,PARVO,PARVO,,NEGATIVO,,
```

**Analizadores / LIS (API key)**: `vet.laboratorio.api-key` (env `VET_LAB_API_KEY`; vacia = integracion deshabilitada). Cabecera `X-API-Key`. La clave **solo** abre `/api/laboratorio/integracion/**` (autoridad tecnica `ROLE_INTEGRACION_LAB`) y no un usuario: los resultados quedan `origen=INTEGRACION` y `registrado_por` nulo. Un JWT en esas rutas recibe 403; sin clave o clave erronea, 401.
- `POST /api/laboratorio/integracion/hl7[?ordenId=&dryRun=false]` - cuerpo `text/plain` (mensaje HL7 con segmentos separados por `\r` o salto de linea).
- `POST /api/laboratorio/integracion/resultados[?dryRun=false]` - JSON `{ordenId?, ordenCodigo?, resultados:[{examenCodigo?, parametroCodigo?, loinc?, valor, unidad?, observacion?}]}`.
`curl -H "X-API-Key: $CLAVE" -H "Content-Type: text/plain" --data-binary @mensaje.hl7 http://localhost:9010/api/laboratorio/integracion/hl7`

PDF externo de un laboratorio: `POST /api/archivos` con `tipo=PDF_LABORATORIO`, `ordenExamenId` (y `ordenItemId`); aparece en `archivos` de la orden/item y en el informe PDF.

### 2.5 Historial y tendencia

- `GET /api/pacientes/{id}/laboratorio?tipo=&page=&size=` -> ordenes del paciente (mismo elemento de la bandeja).
- `GET /api/pacientes/{id}/laboratorio/tendencia?parametroId=` -> serie de **resultados validados** del analito (todos los paneles cuyo parametro tenga el mismo `codigo`; p. ej. ALT de QUIM y de HEPA), de la toma de muestra mas antigua a la mas reciente:
```json
{ "pacienteId": 3, "parametro": {"id": 5, "codigo": "HCT", "nombre": "Hematocrito", "unidad": "%", "tipoValor": "NUMERICO"},
  "puntos": [ {"resultadoId": 91, "fecha": "2026-10-01T15:00:00Z", "ordenId": 10, "ordenCodigo": "ORD-...", "valorNumerico": 30,
               "valorTexto": null, "unidad": "%", "bandera": "L", "critico": false, "refMin": 37, "refMax": 55} ],
  "resumen": {"puntos": 3, "ultimo": 44, "anterior": 36, "variacionAbsoluta": 8, "variacionPorcentual": 22.2,
              "direccion": "SUBE", "minimo": 30, "maximo": 44} }
```
`direccion`: `SUBE`, `BAJA`, `ESTABLE` (variacion < 5 %) o `SIN_DATOS`.

### 2.6 Informe PDF (`GET /api/laboratorio/ordenes/{id}/informe.pdf[?itemId=]`)
`application/pdf`, inline. Membrete (`vet.clinica.nombre`), paciente y tutor, datos de la orden, por examen: estado ("VALIDADO por ..." o **PRELIMINAR**), tabla Parametro / Resultado / Unidad / Referencia / Bandera con colores (ambar L/H, rojo LL/HH/A), interpretacion, informes de imagen/patologia con miniaturas, correcciones, pie con paginacion y firma del ultimo veterinario que valido (con tarjeta profesional si esta en `veterinario.numero_profesional`).

## 3. Imagenes y ecografias

Un examen de tipo `IMAGEN` o `FUNCIONAL` (ECG) se informa con `PUT /api/laboratorio/ordenes/{id}/items/{itemId}/informe-imagen` (ADMIN, VETERINARIO; no requiere muestra):
```json
{ "region": "Abdomen craneal", "tecnica": "...", "hallazgos": "...", "impresionDiagnostica": "...",
  "recomendaciones": "...", "equipo": "Ecografo Mindray DP-10", "fechaEstudio": "2026-10-15", "informadoPorId": null }
```
Solo `region` es obligatoria en el borrador (se puede reescribir). Para validar se exigen `hallazgos` e `impresionDiagnostica`; sin interpretacion explicita, la interpretacion de la validacion es la impresion diagnostica. `informadoPorId` (opcional) debe ser un veterinario/administrador activo; por defecto el usuario actual. Las imagenes se suben con `POST /api/archivos` (`ordenExamenId` + `ordenItemId`, tipo `ECOGRAFIA`/`RADIOGRAFIA`/`IMAGEN`/`DICOM`) y salen en el item (`archivos`) y en el PDF (miniaturas JPG/PNG; los demas por nombre). Correccion: `POST .../correccion` con `informeImagen` (nueva version enlazada, la anterior queda `CORREGIDO`).

## 4. Patologia y citologia

`PUT .../items/{itemId}/informe-patologia` (ADMIN, VETERINARIO; requiere muestra tomada):
```json
{ "tipoMuestra": "Aspirado con aguja fina", "sitioAnatomico": "...", "fechaToma": "2026-10-15",
  "descripcionMacroscopica": "...", "descripcionMicroscopica": "...", "diagnosticoMorfologico": "...",
  "margenes": "...", "comentarios": "...", "laboratorioExterno": "...", "numeroCasoExterno": "...",
  "patologo": "...", "fechaInforme": "2026-10-17", "informadoPorId": null }
```
Solo `tipoMuestra` es obligatorio en el borrador; validar exige `diagnosticoMorfologico`. Adjuntos (PDF del laboratorio externo, microfotografias) y PDF como en imagen. Correccion con `informePatologia`.

## 5. Procedimientos y cirugia (`/api/procedimientos`)

Tablas `cirugia` (con `tipo = PROCEDIMIENTO|CIRUGIA`), `cirugia_monitoreo_anestesico`, `cirugia_insumo`, `consentimiento`.
- `POST /api/procedimientos` (ADMIN, VETERINARIO): `{pacienteId, consultaId?, tipo?: PROCEDIMIENTO|CIRUGIA, itemId? (catalogo de servicios, p. ej. SRV-PRC-CURA), procedimiento? (nombre; obligatorio si no hay itemId), veterinarioId? (por defecto el usuario), ayudanteId?, anestesiologoId?, programadaPara?, inicioEn?, finEn? (fecha-hora local `yyyy-MM-ddTHH:mm:ss`), asa?, urgencia?, ayunoHoras?, pesoKg?, anestesiaTipo? (NINGUNA|LOCAL|SEDACION|GENERAL|EPIDURAL|OTRA), premedicacion?, induccion?, mantenimiento?, analgesia?, diagnosticoPreoperatorio?, descripcionTecnica?, hallazgos?, complicaciones?, materialSutura?, muestrasHistopatologia?, diagnosticoPostoperatorio?, resultado?, indicacionesPostoperatorias?, controles?, proximoControl?, estado?, insumos?: [{itemId?, descripcion, cantidad, lote?, notas?}]}`. Estado por defecto: `REALIZADA` (procedimiento) / `PROGRAMADA` (cirugia). Codigo `PRC-...` / `CIR-...`.
- `GET /api/procedimientos?pacienteId=&consultaId=&tipo=&estado=&page=&size=`, `GET /{id}` (con consentimiento, insumos, monitoreo y archivos), `GET /api/pacientes/{id}/procedimientos`.
- `PUT /{id}` edicion parcial (los campos ausentes no cambian; `insumos` reemplaza la lista). 409 si esta CANCELADO.
- `POST /{id}/estado` `{estado, motivo?}`: PROGRAMADA -> EN_CURSO | REALIZADA | CANCELADA | APLAZADA; APLAZADA -> PROGRAMADA | CANCELADA; EN_CURSO -> REALIZADA | CANCELADA; REALIZADA y CANCELADA son terminales (409). CANCELADA/APLAZADA exigen motivo. Fija `inicioEn`/`finEn` si faltan.
- **Regla**: una CIRUGIA no pasa a EN_CURSO/REALIZADA (ni se registra asi) sin consentimiento `FIRMADO`, salvo `urgencia=true` (422).
- `POST /{id}/monitoreo-anestesico` (ADMIN, VETERINARIO, AUXILIAR): `{registradoEn?, frecuenciaCardiaca?, frecuenciaRespiratoria?, spo2?, etco2?, presionSistolica?, temperatura?, agenteInhalatorioPct?, fluidoMlH?, notas?}` (al menos un parametro; no en CANCELADA/APLAZADA); `GET` lista.
- `POST /{id}/consentimiento`: `{tutorId? (por defecto el principal), texto? (por defecto la plantilla activa CIRUGIA_ANESTESIA/PROCEDIMIENTO), aceptado, firmanteNombre, firmanteDocumento?, metodoFirma?: TABLET|PAPEL_ESCANEADO|OTP|ENLACE, fecha?, archivoId? (adjunto tipo CONSENTIMIENTO del mismo paciente)}`. `aceptado=false` queda `RECHAZADO` (no habilita la cirugia). 409 si ya hay uno `FIRMADO`. El texto se copia a `texto_firmado` y se vincula a `cirugia.consentimiento_id`.
- `GET /{id}/reporte.pdf` (ADMIN, VETERINARIO): reporte con equipo, valoracion, anestesia, monitoreo, tecnica, insumos, resultado/cuidados/controles, consentimiento y firma con tarjeta profesional.

## 6. Linea de tiempo (`GET /api/pacientes/{id}/historia?tipo=...`)

Sin cambios de contrato; se agregan tipos (el filtro `tipo` admite uno o varios separados por coma):
`LABORATORIO` (un renglon por examen **validado**; `referenciaId` = id de la orden; `detalle` = "Orden X: n fuera de rango (valores criticos)"), `IMAGEN`, `PATOLOGIA` (informes validados; `referenciaId` = id de la orden), `PROCEDIMIENTO` y `CIRUGIA` (realizados o en curso; id del registro), `ARCHIVO` (archivos activos **sueltos**: sin orden ni cirugia, que se ven dentro de su evento). Las ordenes aun sin validar no aparecen.

## Migraciones

- **V29**: funciones `fn_no_delete` y `fn_validado_inmutable`; `adjunto` ampliada (nombre aleatorio, extension, miniatura, dimensiones, `orden_item_id`, sede, baja logica con motivo, check de tipo `NOT VALID`, sin DELETE); **correccion de V20**: `examen_parametro.tipo_valor` a VARCHAR(12) ('CUALITATIVO' no cabia en 10) y `orden_examen.estado` a VARCHAR(16) ('MUESTRA_TOMADA' no cabia en 12); LOINC/UCUM/`valor_normal_texto`/`opciones` en parametros; resultados con `estado BORRADOR/VALIDADO/CORREGIDO`, enlaces `reemplaza_a`/`reemplazado_por`, snapshot critico, `uq_resultado_vigente` (se elimina `uq_resultado`); tablas `orden_item_validacion`, `orden_examen_evento`, `lab_importacion` (append-only), `informe_imagen`, `informe_patologia`, `cirugia_insumo`; `cirugia` con `tipo`, anestesia, resultado, controles, etc.; triggers de auditoria (`fn_auditar_cambio`).
- **V30**: semillas de paneles, parametros, LOINC/UCUM y rangos orientativos.

## Pruebas (backend: `mvnw verify`)

`ArchivosApiTest` (6), `LaboratorioApiTest` (9), `LaboratorioImportacionTest` (6), `ImagenPatologiaApiTest` (3), `ProcedimientosApiTest` (4), `LaboratorioDominioTest` (6, unitarias), mas los 73 previos. Cubren bandera por especie/sexo/edad y criticos, validacion/inmutabilidad (a nivel API y trigger) y correccion enlazada, importacion HL7/CSV/JSON (dryRun, real, no coincidencias, duplicados), API key, subida (magic bytes, tamano, traversal), PDF valido (`%PDF` y texto), tendencia, roles e historia.

## Datos de prueba (humo manual)

Tras `docker compose up -d --build backend`: usuarios `admin/admin` (semilla de desarrollo); crear un VETERINARIO y un AUXILIAR con `POST /api/usuarios`. Examenes semillados listos para ordenar: `HEMO`, `QUIM`, `HEPA`, `RENAL`, `ELEC`, `UROA`, `COPRO`, `PARVO`, `FELVFIV`, `HEMOPAR`, `RX`, `ECO-ABD`, `ECO-CARD`, `ECG`, `CITO`, `HISTO`. Clave del LIS en Docker: variable `VET_LAB_API_KEY` (vacia por defecto = deshabilitada).

## Pendientes

- Validar con el laboratorio los rangos/criticos, los LOINC (humanos) y los textos de consentimiento (V13); ampliar semillas (otras especies, rangos por raza/talla, perfil tiroideo, coagulacion, gases).
- Cierre inmutable/firma digital del reporte de procedimiento (hoy editable, auditado por trigger) y cargos/descuento de inventario de los insumos (hoy solo registro).
- Notificar valores criticos (aviso al veterinario/tutor) y vista de pendientes de validacion por veterinario.
- Almacenamiento de objetos (S3/MinIO), antivirus y cifrado en reposo de los archivos; visor DICOM; miniaturas de WebP/DICOM (hoy sin miniatura).
- Conversion automatica de unidades entre analizadores y catalogo (hoy se rechaza la linea con `UNIDAD_NO_COINCIDE`); mapeo de codigos del equipo configurable por la clinica.
- Etapa B: formula/plan en PDF reutilizando `DocumentoPdf` y envio al tutor (correo/WhatsApp). Etapa C: pantallas.
