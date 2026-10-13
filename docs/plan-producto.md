# Plan de producto - Sistema de gestion veterinaria

Fecha: 2026-10-13. Objetivo: llevar el sistema actual a una plataforma de clase mundial (referencias: ezyVet, Provet Cloud, Shepherd, Digitail), adaptada a Colombia (DIAN, Ley 576/2000, Ley 1581/2012).

## 1. Benchmark

| Producto | Que lo distingue |
|---|---|
| Provet Cloud | Cloud, multi-sede y especialidad, abierto (150+ integraciones), independiente. Referente de escala. |
| ezyVet (IDEXX) | Muy completo, ecosistema de laboratorio IDEXX; orientado a grupos y hospitales. |
| Shepherd | Mid-size, precio plano, automatizacion y TranscribeAI (voz -> SOAP en vivo). |
| Digitail | Onboarding rapido, IA (intake, dictado, notas SOAP), portal/app de tutor, telemedicina y pagos. |
| Escribas IA (ScribVet, VetRec, etc.) | Graban la consulta y generan SOAP, instrucciones de alta y correos. Se venden como modulo aparte. |

Patrones comunes de una clinica "clase mundial":
1. Historia clinica unica por paciente (SOAP, plantillas, adjuntos, linea de tiempo), cloud y con acceso por rol.
2. Recordatorios automaticos (vacunas, desparasitacion, citas, controles) por WhatsApp/SMS/email, con confirmacion de cita.
3. Facturacion + inventario + caja integrados al acto clinico (un tratamiento descuenta stock y genera cargo).
4. Portal/app del tutor: agendar, ver vacunas, pagar, recibir documentos.
5. IA como asistente (escriba SOAP, resumenes, redaccion de mensajes), siempre con revision humana.
6. Reportes/BI (ingresos por veterinario/servicio, retencion, ocupacion de agenda) e integraciones (laboratorio, contabilidad, pagos).
7. Nube vs servidor: la nube da actualizaciones automaticas; el modo offline/continuidad se valora (considerar PWA para sala de consulta).

Nota Colombia: las fuentes revisadas hablan de salud humana (RIPS/EPS no aplican a veterinaria). Requisitos DIAN: RUT, firma digital, habilitacion y pruebas tecnicas con software autorizado o proveedor tecnologico autorizado. Recomendacion: integrar un proveedor tecnologico (Siigo, Alegra u otro autorizado) por API en vez de certificar software propio. Ley 576/2000 es el Codigo de Etica (incluye historia clinica y secreto profesional; los articulos exactos deben validarse con el texto oficial, no se pudieron leer en la busqueda).

## 2. Mapa de modulos objetivo

| # | Modulo (Modulith) | Alcance |
|---|---|---|
| M1 | seguridad | Usuarios, roles, JWT. Extender: permisos finos, 2FA, sesiones, sedes por usuario. |
| M2 | tutores | Tutores, pacientes, especies/razas, M:N tutor principal. Extender: consentimientos (habeas data), fotos, microchip, fallecimiento, fusion de duplicados. |
| M3 | citas / agenda | Agenda multi-profesional y multi-sala, bloqueos, tipos de cita, sala de espera, lista de espera, confirmacion. |
| M4 | clinica (historia clinica) | Consulta SOAP, signos vitales, peso, diagnosticos CIE/VeNom, alergias, adjuntos, recetas, plantillas, cierre/firma y addendum inmutable. |
| M5 | inmunizacion | Catalogo de vacunas y desparasitantes, esquemas por especie/edad, proxima dosis, certificado/carnet PDF. |
| M6 | recordatorios / comunicaciones | Motor de reglas y plantillas; canales email, WhatsApp Business API, SMS; bitacora de envios; opt-out. |
| M7 | hospitalizacion y cirugia | Ingresos, jaulas/camas, hoja de tratamiento y monitoreo, consentimiento informado, hoja anestesica, protocolo quirurgico, alta. |
| M8 | laboratorio e imagenes | Ordenes, resultados (manual/archivo/integracion), rangos de referencia, DICOM/imagenes como adjuntos, tendencia de valores. |
| M9 | farmacia e inventario | Items, lotes y vencimientos, bodegas, movimientos, proveedores, ordenes de compra, alertas de minimo, controlados. |
| M10 | facturacion y caja | Presupuesto -> factura, pagos mixtos, abonos, cierre de caja, cartera, factura electronica DIAN, notas credito. |
| M11 | portal del tutor | Login tutor, agendar, ver mascotas/vacunas/documentos, pagar, firmar consentimientos. |
| M12 | telemedicina | Videoconsulta (proveedor externo), triaje por chat, nota SOAP vinculada. |
| M13 | servicios complementarios | Peluqueria, guarderia/hotel, planes de salud/membresias, venta de productos (POS). |
| M14 | reportes / BI | Dashboards operativos y financieros, exportacion, cohortes de retencion. |
| M15 | auditoria y cumplimiento | Registro de cambios (tabla `auditoria`), acceso a historia, retencion, consentimientos, exportacion de datos. |
| M16 | IA asistida | Escriba SOAP por voz, resumen de historial, borrador de mensajes, sugerencia de codigos; con revision obligatoria. |
| M17 | multi-sede / administracion | Sedes, configuracion, catalogo de servicios y precios por sede, integraciones. |

## 3. Analisis de brecha

Hallazgos del repo: V1 ya define casi todo el esquema (consulta, signo_vital, diagnostico, adjunto, receta, inmunizacion, vacuna, bodega, lote, movimiento_stock, factura, pago, presupuesto, auditoria, veterinario, bloqueo_agenda). Hay migraciones V4-V10 que ajustan cita y consulta (activo, auditoria, fecha). En codigo solo existen los modulos `seguridad`, `tutores`, `citas` (api/application/domain/internal) y `shared`. El plan.md marca hechas Fase 0 y 1; citas/consultas no estan documentadas como completas.

| Capacidad | Estado | Brecha |
|---|---|---|
| Usuarios/roles/JWT | Hecho (Fase 0) | Permisos finos, 2FA, bloqueo por intentos, refresh token |
| Tutores, pacientes, catalogos | Hecho (Fase 1) | Microchip, fotos, consentimientos, fusion duplicados, estado fallecido |
| Citas | Parcial (modulo + migraciones) | Verificar UI/tests; agenda por profesional/sala, recordatorios, confirmacion, lista de espera |
| Consultas SOAP | Esquema + parcial (V5-V7) | Modulo clinica completo: signos vitales, diagnosticos, recetas, adjuntos, cierre inmutable, plantillas |
| Vacunacion/desparasitacion | Solo tablas | Esquemas, calculo proxima dosis, carnet PDF, recordatorios |
| Hospitalizacion/cirugia | No existe | Todo |
| Laboratorio/imagenes | Solo `adjunto` | Ordenes, resultados estructurados, rangos |
| Inventario/farmacia | Solo tablas | Servicios, descuento FEFO por lote, compras, alertas |
| Facturacion/caja/DIAN | Solo tablas | Todo el flujo y la integracion con proveedor DIAN |
| Portal tutor | No existe | Auth de tutor, agenda online, pagos |
| Comunicaciones | No existe | Motor de recordatorios y canales |
| Telemedicina, peluqueria/guarderia | No existe | Todo |
| Reportes/BI | No existe | Todo |
| Auditoria | Tabla sin escritura | Interceptor de escritura/lectura, visor |
| IA | No existe | Todo |
| Multi-sede | No existe (no hay `sede_id`) | Decision temprana: introducir `sede_id` pronto |

## 4. Hoja de ruta

Regla heredada: no avanzar sin `./mvnw verify` y `npm run build` verdes. Cada fase = migracion Flyway + modulo + tests de integracion (Testcontainers) + UI.

### Fase 2 - Agenda y consulta clinica (nucleo)
- Alcance: cerrar citas (agenda por veterinario/sala, bloqueos, estados, sala de espera); modulo `clinica`: consulta SOAP, signos vitales, peso, alergias, diagnosticos, recetas, adjuntos; cierre con addendum.
- Entregables: endpoints `/api/citas`, `/api/consultas`, `/api/pacientes/{id}/historia` (linea de tiempo); vistas Agenda (calendario) y Consulta; plantillas SOAP por especie.
- Dependencias: Fase 1. Anticipar `sede_id` (nullable, sede por defecto) en cita/consulta/factura/bodega.
- Modelo clave: cita, bloqueo_agenda, consulta, signo_vital, diagnostico, receta(_item), adjunto, peso_registro, alergia.
- Auditoria base: interceptor que escribe en `auditoria` (quien, que, cuando, antes/despues) para consulta y paciente.
- Aceptacion: crear cita sin solapes (409), iniciar consulta desde cita, guardar SOAP, cerrar consulta y que quede inmutable (solo addendum), historia del paciente ordenada por fecha, auditoria registrada, tests verdes.

### Fase 3 - Vacunacion, desparasitacion y recordatorios
- Alcance: catalogo de vacunas/antiparasitarios, esquemas por especie y edad, aplicacion vinculada al lote, proxima dosis automatica, carnet PDF; motor de recordatorios (email primero, WhatsApp despues).
- Entregables: modulos `inmunizacion` y `comunicaciones`; job programado (Spring scheduler/Modulith events); plantillas con variables; bitacora y opt-out; pantalla "pendientes de vacunar".
- Dependencias: Fase 2; consentimiento de contacto del tutor (M2).
- Modelo: vacuna, esquema_vacunal, inmunizacion (fecha, lote, proxima_fecha), plantilla_mensaje, recordatorio (estado, canal, programado_para), envio_log.
- Aceptacion: aplicar vacuna calcula proxima fecha; job genera recordatorios N dias antes sin duplicados; tutor sin consentimiento no recibe nada; carnet PDF descargable.
- Riesgo: WhatsApp Business API requiere proveedor y plantillas aprobadas; arrancar con email y abstraer canal.

### Fase 4 - Inventario, farmacia y facturacion
- Alcance: items/servicios con precio, lotes y vencimientos (FEFO), compras a proveedor; presupuesto -> factura interna, pagos mixtos, abonos, cierre de caja; cargos automaticos desde consulta/receta/vacuna.
- Entregables: modulos `inventario` y `facturacion`; POS de mostrador; alertas de stock minimo y vencimiento.
- Dependencias: Fases 2-3 (eventos de consulta e inmunizacion generan cargos y descuentan stock).
- Modelo: item, lote, bodega, movimiento_stock, proveedor, orden_compra, factura(_item), pago, presupuesto, caja_turno.
- Aceptacion: stock nunca negativo (409), descuento por lote mas proximo a vencer, factura cuadra con pagos, cierre de caja con diferencias reportadas, anulacion solo por nota credito.

### Fase 5 - Factura electronica DIAN y cumplimiento
- Alcance: integracion por API con proveedor tecnologico autorizado (Siigo/Alegra u otro), resoluciones de numeracion, CUFE, notas credito/debito, reintentos y estado DIAN; auditoria completa; consentimientos Ley 1581.
- Entregables: puerto `FacturacionElectronicaPort` + adaptador del proveedor; pantalla de estado DIAN; visor de auditoria; politicas de retencion y exportacion de historia clinica.
- Dependencias: Fase 4; RUT, firma digital y habilitacion del cliente (externo, plazo largo: iniciar tramite en paralelo a Fase 4).
- Aceptacion: factura emitida en ambiente de habilitacion con CUFE y PDF/XML guardados; rechazo se reintenta y se muestra; nota credito vinculada; tests con proveedor simulado.

### Fase 6 - Hospitalizacion, cirugia y laboratorio
- Alcance: ingresos con cama/jaula, hoja de tratamiento y monitoreo con tareas horarias, consentimiento informado firmado, protocolo quirurgico y anestesico, alta; ordenes de laboratorio e imagenes con resultados y rangos.
- Modelo: hospitalizacion, cama, tarea_tratamiento, registro_monitoreo, cirugia, consentimiento, orden_examen, resultado_examen(valor, unidad, rango).
- Dependencias: Fases 2 y 4 (cargos, insumos).
- Aceptacion: hospitalizacion genera cargos diarios y tareas; resultados fuera de rango se marcan; alta genera resumen imprimible.

### Fase 7 - Portal del tutor y comunicaciones omnicanal
- Alcance: autenticacion de tutor (enlace magico/OTP), agendar y cancelar cita, ver carnet y documentos, pagos en linea (pasarela como Wompi/PayU, a validar), firma de consentimientos; WhatsApp bidireccional basico.
- Dependencias: Fases 3-5.
- Aceptacion: tutor solo ve sus mascotas (pruebas de aislamiento de datos), agenda respetando disponibilidad, pago concilia con factura.

### Fase 8 - Reportes/BI, multi-sede y servicios complementarios
- Alcance: dashboards (ingresos, ocupacion, retencion, top servicios, vacunas vencidas), exportes; multi-sede completo (catalogos/precios/stock por sede); peluqueria, guarderia/hotel, planes de salud.
- Dependencias: datos de Fases 2-6; `sede_id` desde Fase 2.
- Aceptacion: reportes validan contra sumas de facturas; usuario de una sede no ve datos de otra; reservas de guarderia sin sobrecupo.

### Fase 9 - Telemedicina e IA asistida
- Alcance: videoconsulta (proveedor externo embebido) con nota SOAP; escriba IA (audio -> borrador SOAP), resumen de historial, redaccion de mensajes; siempre borrador revisable y marcado como generado.
- Dependencias: Fase 2 estable; politica de privacidad y consentimiento de grabacion; evaluar proveedor LLM y retencion de audio (no persistir audio por defecto).
- Aceptacion: el veterinario aprueba/edita antes de guardar; registro de auditoria indica contenido generado por IA; medicion de tiempo ahorrado por nota.

Priorizacion: valor alto/riesgo bajo primero (Fases 2-4); la Fase 5 es critica legal y de plazo externo, por eso se inicia el tramite DIAN al comenzar Fase 4; IA y telemedicina al final por riesgo de privacidad y dependencia de terceros.

## 5. Requisitos no funcionales

- Seguridad: JWT con expiracion corta + refresh, BCrypt, RBAC por rol y permiso, 2FA para ADMIN/VETERINARIO, rate limiting en login, cabeceras seguras, secretos por variables de entorno (no commitear `VET_JWT_SECRET`), desactivar seed `admin/admin` fuera de dev, validacion de subida de archivos (tipo, tamano, antivirus).
- Datos personales: Ley 1581/2012 y Decreto 1377/2013 (habeas data): autorizacion de tratamiento, finalidad, derecho de supresion/correccion (validar con asesor legal).
- Auditoria: quien/que/cuando/IP para escritura y lectura de historia clinica; registros de consulta cerrada inmutables (addendum); retencion de historia clinica segun criterio juridico (confirmar plazo con asesor; no se verifico en las fuentes).
- Backups: pg_dump/WAL (PITR) diario cifrado fuera del servidor, retencion 30+ dias, prueba de restauracion trimestral; RPO <= 15 min, RTO <= 4 h objetivo.
- Multi-sede: `sede_id` en entidades transaccionales, filtro por sede en capa de servicio, usuarios con sedes permitidas; evaluar RLS de Postgres.
- Rendimiento: p95 < 300 ms en listados paginados, indices por (paciente_id, fecha), busqueda por trigramas (`pg_trgm`) para tutores/pacientes, jobs asincronos para correos/PDF/DIAN.
- Observabilidad: Actuator + metricas, logs estructurados con correlation id, alertas de jobs fallidos.
- Calidad: Testcontainers en CI, `ModularityTests` para limites de modulos, comunicacion entre modulos por eventos de Modulith, migraciones Flyway inmutables (nuevas V12+).
- Disponibilidad: despliegue contenedorizado, healthchecks, modo degradado de agenda (PWA) opcional.
- Accesibilidad/UX: responsive para tablet en sala de consulta, interfaz en espanol, atajos de teclado en consulta.

## 6. Fuentes consultadas

- Provet, "Best Veterinary Practice Management Software (2026)": https://www.provet.com/blog/best-veterinary-practice-management-software
- Cal.com, "best veterinary practice management software": https://cal.com/blog/best-veterinary-practice-management-software
- Layer3 Labs, comparativa (jul 2026): https://www.layer3labs.io/comparisons/best-veterinary-practice-management-software
- SourceForge, software veterinario: https://sourceforge.net/software/veterinary/integrates-with-vet-s8/
- Unite.AI, herramientas de IA veterinaria: https://www.unite.ai/best-ai-veterinary-tools
- G2 ScribVet: https://www.g2.com/products/scribvet/discuss
- Puppilot, IA en clinicas: https://articles.puppilot.co/ai-in-veterinary-clinics-transforming-animal-healthcare
- Medesk, facturacion electronica Colombia: https://www.medesk.net/es/blog/software-de-facturacion-electronica-colombia/
- Siigo, facturacion electronica DIAN: https://www.siigo.com/facturacion-electronica/
- Portafolio, DIAN y software de FE: https://www.portafolio.co/negocios/dian-no-pedira-software-especifico-para-expedir-fe-513649
- Ley 576 de 2000 (MinEducacion): https://www.mineducacion.gov.co/portal/normativa/Leyes/105017:Ley-0576-de-Febrero-15-de-2000
- Codigo de Etica veterinaria (encolombia): https://encolombia.com/veterinaria/tv-interes/etica-veterinaria/
- Faltas frecuentes Ley 576 (Univ. La Salle): https://veterinaria.lasalle.edu.co/article/download/1072/1046

Limitaciones: informacion de competidores proviene en buena parte de blogs de vendedores; Ley 1581, plazos de retencion y pasarelas de pago se citan de conocimiento general y deben validarse.
