# Benchmark de repositorios y estándares (veterinaria) - 2026-10-15

Alcance: comparar `vet_system` (Fases 0-4 hechas) con proyectos abiertos y estándares, y extraer lo que no tenemos. No se modificó código ni migraciones.

Convención: **[V]** = verificado leyendo la URL citada (README, árbol de archivos o API de GitHub, 2026-10-15). **[S]** = suposición o conocimiento general no verificado en esta sesión. Estrellas/fechas salen de `api.github.com/repos/...` el 2026-10-15.

## 1. Punto de partida (qué ya tenemos)

Fuente: `docs/estado-y-roadmap.md`, `plan-producto.md`, `modelo-datos-v2.md`, `api-fase3.md`, `api-fase4.md`.

- Implementado (backend + UI): usuarios/roles/JWT, tutores, pacientes, razas FCI, agenda (parcial, ~35% al cierre de Fase 2 según el roadmap; sprint 1 planeaba cierre), consulta SOAP, historia, auditoría (triggers), vacunación con esquemas WSAVA y carnet PDF, recordatorios por email con consentimiento/opt-out, inventario FEFO, compras, kardex, controlados, facturación/caja/POS/cartera/notas crédito, dashboard. Puerto `FacturacionElectronicaPort` con adaptador simulado.
- Solo esquema (V12-V22, sin código): hospitalización, cirugía/anestesia, laboratorio con rangos, plantillas SOAP, peluquería, guardería, planes de salud, multi-sede, permisos finos, refresh token/2FA, anticipos, lista de espera, consentimientos versionados, `problema_activo`.
- No iniciado: DIAN real (Fase 5), portal del tutor, telemedicina, IA, BI avanzado, WhatsApp, DICOM.
- Nota: el roadmap ya tiene una tabla de referencias (7 fuentes); este documento no la repite, la amplía con repos nuevos y detalles de diseño.

## 2. Repositorios revisados

Fecha de revisión 2026-10-15. "Aporta" = lo útil para nosotros.

| # | Repo (URL) | Stack | Licencia | Estrellas / último push | Qué aporta |
|---|---|---|---|---|---|
| 1 | https://github.com/evangauer/openvpm (OpenVPM) | Next.js 15, tRPC, PostgreSQL 16 + Drizzle, Stripe, MinIO, Turborepo | AGPL-3.0 | 41 / 2026-10-12 | El más completo y activo. Ver sección 3. [V] |
| 2 | https://github.com/oldauntie/ababu (Ababu) | PHP/Laravel + MariaDB | AGPL-3.0 | 26 / 2025-03-21 | Historia "orientada a problemas", catálogos VeNom por país, tratamientos con lote y fecha de recall, tabla `watchdogs` de eventos de seguridad. [V] |
| 3 | https://github.com/espero451/hans (Hans LIS) | FastAPI, PostgreSQL 16, SQLAlchemy async | GPL-2.0 | 1 / 2026-03-29 | LIS veterinario: ciclo orden-muestra-resultado, código de barras por muestra, integración de analizadores por ASTM sobre TCP, verificación de resultados. [V] |
| 4 | https://github.com/jpabloglez/vetimage (VetImage) | Django + DRF, PostgreSQL, pydicom, OHIF/Cornerstone.js | GPL-3.0 | 0 / 2026-09-20 | Visor DICOM web, DICOMweb (QIDO-RS/WADO-RS), jerarquía Propietario > Paciente > Estudio, anotaciones y medidas, informe estructurado con IA como borrador. Proyecto muy joven. [V] |
| 5 | https://github.com/ardalis/ddd-vet-sample | C# / .NET (SQL Server) | sin licencia declarada en la API | 312 / 2023-05-31 | Referencia de diseño de dominio (DDD) para clínica veterinaria: separación FrontDesk vs sitio público y mensajería entre ambos para confirmar citas por correo. Solo educativo. [V parcial: README; no se leyó el modelo] |
| 6 | https://github.com/geosem42/PetCare (fork mielcarp/PetCare) | Laravel, Inertia, Vue 3 | MIT | 26 / 2023-12-13 | Clientes, pacientes, calendario, inventario, seeders de especies/razas. Poco más que lo que ya tenemos. [V] |
| 7 | https://github.com/semih-turan/Veterinary-Management-System-API | Spring Boot + PostgreSQL | MIT | 2 / 2024-05-22 | Mismo stack; agenda con disponibilidad por doctor y vacunas con fechas de protección. Ya citado en `modelo-datos-v2.md`. [V por API/README; ya referenciado] |
| 8 | https://github.com/deerborg/VeterinaryManagementAPI | Java, Spring Boot | MIT | 10 / 2024-06-12 | CRUD de citas/doctores/clientes/animales con excepciones de dominio (`AppointmentAlreadyExists`). Poco nuevo. [V] |
| 9 | https://github.com/Eddie000321/vet-chart (VetChart) | React + Express + Prisma/PostgreSQL | MIT | 1 / 2026-07-10 | Prototipo de portafolio; interesante solo por el modo demo "solo lectura en producción" (403 `PUBLIC_DEMO_READ_ONLY`) y métricas Prometheus. [V] |
| 10 | https://github.com/georgewangchn/VetVoice | Python, PySide6, FunASR, LLM | Apache-2.0 | 15 / 2026-04-03 | Escriba clínico por voz: reducción de ruido, separación de hablantes (veterinario/tutor), LLM que estructura la historia. Escritorio, en chino. [V] |
| 11 | https://github.com/MSubham06/HappyPaws | React + Spring Boot + MySQL | sin licencia | 19 / 2026-02-18 | Proyecto de curso basado en Spring PetClinic. Sin valor adicional. [V] |
| 12 | https://github.com/blingyplus/Vet-Management-System | PHP + MySQL (producto comercial; el repo es un README de ventas) | propietaria | 13 / 2026-09-06 | Solo lista de funciones: hospitalización, cirugía, laboratorio, pensión, POS, multi-sucursal, recordatorios SMS/email. No hay código público. [V] |
| 13 | https://github.com/AAVLD-Informatics-Committee/AAVLD-Interoperability-Framework | (marco de datos) | Apache-2.0 | 1 / 2026-08-26 | Repo vacío salvo descripción: marco de interoperabilidad de laboratorios diagnósticos veterinarios (datos, terminología, gobernanza). Sin contenido aún. [V] |
| 14 | https://github.com/TrivaWatlington/OpenPetCare | planeado: React, Django/Node, FHIR | MIT | 1 / 2025-03-26 | Solo propuesta (GSoC 2025): FHIR Patient/Immunization/Condition/Observation, QR de perfil, PWA offline. Sin código. [V] |
| 15 | OpenVPMS (no está en GitHub): listados en https://www.softwareadvice.com/veterinary/openvpms-profile y https://www.goodfirms.co/software/openvpms | Java, MySQL, JasperReports (según una reseña) | exige suscripción para usarlo | n/a | Referente maduro (Australia). Fuentes secundarias inconsistentes sobre alojamiento y precio. No se pudo leer código ni esquema. [V solo listados] |
| 16 | Módulos Odoo (ver `modelo-datos-v2.md` #4-5) y https://github.com/stferraro/veterinary_management | Python/Odoo | mixta | 1 / 2025-12-22 | Hospitalización/camas, laboratorio con parámetros, planes de salud. Ya incorporados en V20/V21. El repo no tiene README. [V parcial] |

Conclusión sobre el mercado abierto [V]: la mayoría son proyectos de curso. Solo OpenVPM (evangauer) y, en menor medida, Ababu y Hans LIS aportan diseño que nuestro modelo no tiene.

## 3. Lo que se aprendió de cada proyecto relevante

### OpenVPM (evangauer) - fuente: README y árbol `packages/db/schema/*.ts` [V]
Tablas/funciones con nombre propio en el repo que no tenemos o tenemos incompletas:
- `visit-closeouts`: cierre estructurado de visita (resumen diagnóstico, instrucciones de alta, signos de alarma, motivo de "sin instrucciones", seguimiento con responsable y fecha, snapshot de medicación, historial de enmiendas). Nosotros solo cerramos la consulta.
- `visit-work-items`: cola de "trabajo de la visita" que vincula vacuna, resultado de laboratorio, procedimiento y receta con renglón de factura o motivo explícito de "sin cargo". Evita cobros olvidados. Nosotros generamos cargos por evento `ConsultaCerrada`, pero sin cola de pendientes revisable ni "sin cargo" justificado.
- `dispense-charge-queue`: cola de dispensación -> cargo con snapshot del precio y resolución manual.
- `clinical-corrections`, `soap-note-replacements`, `lab-result-replacements`: corrección por "ingresado por error" con motivo permanente, reemplazo enlazado en ambas direcciones, sin sobrescribir. Nuestro addendum cubre texto; no cubre error de paciente (nota puesta en el paciente equivocado).
- `patient-merge-events`: fusión de pacientes duplicados con snapshot origen/destino. Está en nuestro plan (fusión de duplicados) pero sin tabla.
- `lab-result-events` + doc `docs/lab-result-safety.md`: bandeja de laboratorio (pendiente -> completado -> revisado), seguimiento asignado con vencimiento, historial append-only con valor, unidad, rango y bandera vigentes en ese momento.
- `care-reminders`: recordatorios clínicos manuales por paciente (fecha, completar, descartar). Nosotros no exponemos recordatorio manual (pendiente anotado en `estado-y-roadmap.md`).
- `consents`, `sms-consent-events`, `messaging-registration-events`: consentimiento por canal con eventos auditables.
- `portal-sessions`, `booking` (agenda en línea publicada con `config` JSON), `usage`/`conversion-milestones` (analítica de producto; omitible).
- Pizarra de clínica (whiteboard): tablero compartido de estado de pacientes (paciente, doctor, sala, estado, hora de entrada, procedimiento) con autorrefresco. [V README]
- Presupuestos con revisiones (`treatment-plan-evidence`: revisión numerada, renglones ofrecidos, precio unitario) y aceptación del plan por el tutor.
- Importador CSV con simulacro previo (dry run): clientes, pacientes, vacunas e historial; encabezados con sinónimos; reconciliación por ID de origen antes que por email (`docs/migrating-to-openvpm.md`). [V]
- Arquitectura: RLS de PostgreSQL por clínica + pruebas de RLS (`db:rls:test`), API pública `/api/v1` con API keys con alcance y límite de tasa, webhooks firmados, MFA TOTP con códigos de recuperación, runbooks de respaldo/restauración y de recuperación de objetos. [V README]
- Seguros de mascotas (`insurance`: póliza, deducible, % de cobertura, reclamaciones) y planes de bienestar con facturación recurrente (`wellness`: `next_billing_date`). [V por nombres de columnas]
Limitaciones: es mercado EE. UU. (federales/estatales, Stripe, Telnyx); no cubre DIAN ni ICA. Licencia AGPL: no copiar código; sí inspirarse en ideas.

### Ababu [V]
- Historia orientada a problemas: `problems` (diagnóstico, paciente, estado, `active_from`, `key_problem`, análisis subjetivo/objetivo) con `unique(diagnosis_id, pet_id)`; `treatments` con `drug_batch`, `drug_batch_expires_at`, `recall_at` y `print_notes`.
- Diagnósticos, pruebas diagnósticas y procedimientos son catálogos con `country_id` y `term_name`, sembrados desde SQL "VeNom" (`database/seeds/sql/VeNom/diagnoses.sql`): confirma el uso de VeNom como terminología en un sistema abierto. No se verificó la licencia del contenido VeNom allí.
- Tabla `watchdogs` (tipo, severidad, IP, URI, referer, variables): bitácora de eventos de seguridad separada de la auditoría clínica.

### Hans LIS [V]
- Flujo: orden con pruebas y servicios -> muestras con código de barras -> `test_runs` -> resultados (valor, unidades, bandera, estado, verificación). Resultados 1:N respecto a la corrida (conserva repeticiones).
- Analizadores: servidor TCP con listeners por YAML y manejador ASTM (registros R y Q; responde con registros O con lista de pruebas); emulador de instrumentos aparte (`astmkit`).
- Impresión de etiquetas de código de barras por red.

### VetImage [V]
- DICOMweb (QIDO-RS/WADO-RS), OHIF + Cornerstone.js, miniaturas en 3 tamaños, cuota de almacenamiento, búsquedas guardadas, conector de IA (REST+webhook o gRPC) con humano en el circuito y descargo de responsabilidad explícito.

### Otros
- VetVoice: pipeline voz -> diarización -> LLM -> historia estructurada; guarda audio WAV original (implica consentimiento y retención) [V].
- ddd-vet-sample: separar el sistema interno del sitio público y comunicarlos por mensajería para confirmaciones [V parcial].
- VetChart: bloqueo de mutaciones en demo público [V]; útil si se publica una demo.

## 4. Estándares abiertos y su encaje

| Estándar | Qué es / estado verificado | Aplicación para nosotros |
|---|---|---|
| VeNom Coding Group | Lista estándar británica de términos (diagnósticos, y también administrativos y preventivos); el uso requiere solicitar permiso. https://en.wikipedia.org/wiki/VeNom_Coding_Group [V]. Ababu siembra catálogos VeNom [V]. Costo y licencia exactos: no verificados. | Mantener columna `codigo_venom` (ya está) y no sembrar códigos sin permiso. Solicitar licencia si se quiere interoperar. |
| SNOMED CT Veterinary Extension (VetSCT) | Mantenida por el Virginia-Maryland College of Veterinary Medicine (VTS); versión de producción de marzo 2026, requiere SNOMED CT International de enero 2026, distribución por MLDS con login. https://forums.snomed.org/t/march-2026-veterinary-extension-to-snomed-ct-package-production-release-available/1112 [V]. Condiciones de licencia en Colombia: no verificadas. Los mantenedores dicen ponerla en dominio público según otra fuente (https://nlm.nih.gov/research/umls/sourcereleasedocs/current/SNOMEDCT_VET) [V], pero obtenerla pasa por UMLS/MLDS. | Añadir `catalogo_diagnostico.sistema` = `SNOMEDCT_VET` y `codigo_externo` (ya hay `sistema`). Importar solo si el cliente lo pide. Esfuerzo M. |
| HL7 FHIR y animales | El recurso Patient no soporta animales de serie; el IG de reporte de laboratorio define una extensión `patient-animal` (species, breed, genderStatus). https://build.fhir.org/ig/HL7/uv-lab-rep-ig/StructureDefinition-Patient-animal-uv-lab-definitions.html [V; leí solo los primeros 100k caracteres]. Charla de HL7: casi no hay implementaciones veterinarias (https://chat-archive.fhir.org/stream/179160-social/topic/Veterinary.3F.html) [V]. No encontré un "VetFHIR" publicado. | No adoptar FHIR ahora. Exponer un export opcional mapeado (Patient+animal, Immunization, Observation) solo si un lab o portal lo exige. Esfuerzo M, baja prioridad. |
| ISO 11784/11785 (microchip FDX-B) | 15 dígitos: 3 de país o fabricante + 12 de identificación; 900 compartido; ICAR certifica productos; FDX-A de 10 caracteres existe y no es ISO. https://wiki.icar.org/index.php/Identification_Device_Certification y https://wsava.org/wp-content/uploads/2020/01/Microchip-FAQs_0.pdf [V por resultados de búsqueda; no abrí los PDF]. | Validar el número (15 dígitos numéricos), guardar tipo (FDX-B/otro), fecha y sitio de implantación, y detectar duplicados entre pacientes. Ver 6.1. |
| WSAVA vacunación 2024 / AAHA registros | Ya usados en `modelo-datos-v2.md` (fuentes 6-7). | Sin cambio. |
| AAFP, ICD veterinaria | No se investigaron a fondo. [S] | Sin acción; AAFP es guía clínica felina, no de datos. |

## 5. Normativa colombiana (solo lo que se pudo respaldar)

| Tema | Hecho verificado | Implicación |
|---|---|---|
| Rabia - reporte mensual | La Circular 064 de 2014 (MinSalud) exige a prestadores privados reportar mensualmente a la secretaría de salud municipal/distrital cuántos perros y gatos vacunaron y notificar de inmediato casos sospechosos o confirmados. https://normograma.invima.gov.co/normograma/docs/pdf/circular_minsaludps_0064_2014.pdf [V por resumen de búsqueda; no abrí el PDF]. | Reporte mensual de vacunación antirrábica (lista y totales por especie) generable desde `inmunizacion`. |
| SIVIGILA | Agresiones por animales potencialmente transmisores de rabia: ficha INS código 300; rabia humana código 670. [V resumen de búsqueda]. | Banderas en la consulta: "agresión/mordedura" y "sospecha de rabia" con alerta al ADMIN y reporte. |
| Exportación/importación de mascotas | Resolución ICA 100164 de 2021 (7 jul 2021): perros menores de 12 meses que salen del país con microchip de 15 dígitos (desde 21 jul 2021); número en el CIS; certificado de veterinario con matrícula Comvezcol, expedido máximo 5 días antes; vacunas con producto, lote y fechas. https://www.portafolio.co/economia/gobierno/requisitos-nuevos-para-entrada-y-salida-de-colombia-de-perros-y-gatos-553861 [V en prensa; el PDF oficial dio 404 y no pude confirmar vigencia ni modificaciones]. | Documento "certificado de salud para viaje" con microchip, vacunas con lote y desparasitación; pendiente validar con el ICA. |
| Ley 1774 de 2016 | Reconoce a los animales como seres sintientes y modifica Ley 84 de 1989, Código Civil y Penal; tipo penal art. 339A. https://revistas.unilibre.edu.co/index.php/academia/article/view/5992 y artículos de Memorias Forenses [V]. No hallé una obligación explícita de denuncia para el veterinario. | Campo y flujo de "sospecha de maltrato" con evidencias (fotos, escala de condición corporal), de acceso restringido y exportable. Validar con asesor jurídico. |
| Ley 2054 de 2020 | Modifica la Ley 1801 de 2016 (Código de Policía): centros de bienestar, animales abandonados a los 30 días, correa y bozal en perros potencialmente peligrosos. https://www.cancilleria.gov.co/sites/default/files/Normograma/docs/pdf/ley_2054_2020.pdf [V por resumen]. | Marcar "perro potencialmente peligroso" en el paciente (raza/permiso); aviso en agenda y recepción. |
| Microchip nacional | No hallé una ley nacional vigente que lo obligue; sí un proyecto (cédula animal) en trámite y una plataforma de Bogotá ("Ciudadano de 4 Patas") con microchip de 15 dígitos. [V búsqueda]. Estado actual: no verificado. | Guardar el microchip siempre; integración con registros distritales solo como futuro. |
| Carné oficial de vacunación | No se encontró norma nacional del carné en la búsqueda. [No verificado]. | El carné PDF actual sirve; añadir código de verificación/QR con hash. |
| Ley 1581 de 2012, Ley 576 de 2000 | Ya cubiertas en el plan (habeas data y ética). | Sin cambio. |

## 6. Lo que no habíamos considerado (recomendaciones concretas)

Esfuerzo: S = menos de 1 semana; M = 1-3 semanas; L = más de 3. Valor: A/M/B.

### 6.1 Alto valor, bajo esfuerzo

| # | Recomendación | Qué tocar | Esfuerzo | Valor | Dependencias / fuente |
|---|---|---|---|---|---|
| 1 | Validación de microchip ISO 15 dígitos + unicidad + detalle (tipo, fecha y sitio de implantación, lector) | `paciente` (campos V13), validador, UI; índice único parcial en `microchip` | S | A | Falta microchip en Fase 1 (`estado-y-roadmap`); ISO 11784/5 |
| 2 | Cola "trabajo pendiente de la visita" con "sin cargo" justificado | tabla `visita_trabajo_pendiente` (consulta, tipo, origen id, renglón de factura o motivo), endpoint `GET /api/facturacion/pendientes-cargo`, tarjeta en dashboard | M | A | Fase 4; patrón `visit-work-items` de OpenVPM |
| 3 | Recordatorio clínico manual por paciente (fecha, completar, descartar) | `recordatorio` origen MANUAL, `POST /api/comunicaciones/recordatorios`, pantalla en historia | S | A | Ya pendiente en `api-fase3`; OpenVPM `care-reminders` |
| 4 | Reporte mensual de vacunación antirrábica y lista de perros/gatos vacunados | consulta sobre `inmunizacion`, `GET /api/inmunizacion/reportes/rabia?mes=`, CSV/PDF | S | A | Circular 064/2014 (verificar vigencia) |
| 5 | Bandera "perro potencialmente peligroso" y "agresión/mordedura" | `paciente.alerta_*`, `consulta.evento_notificable`, alerta | S | M | Ley 2054/2020; SIVIGILA 300 |
| 6 | Corrección por "ingresado por error" con reemplazo enlazado | `correccion_clinica`(origen, destino, motivo, actor), UI en historia; complementa `consulta_addendum` | M | A | V15; OpenVPM `clinical-corrections` |
| 7 | Cierre de visita estructurado: instrucciones de alta, signos de alarma, seguimiento con responsable y fecha | columnas/tabla `consulta_cierre` + PDF "indicaciones al tutor"; genera cita/recordatorio de control | M | A | Fase 2 (consulta); OpenVPM `visit-closeouts` |

### 6.2 Alto valor, esfuerzo medio o grande

| # | Recomendación | Qué tocar | Esfuerzo | Valor | Dependencias / fuente |
|---|---|---|---|---|---|
| 8 | Fusión de pacientes/tutores duplicados con snapshot reversible | `paciente_fusion_evento`, `POST /api/pacientes/{id}/fusionar`, UI de comparación, rehacer FKs en transacción | M | A | Plan M2; OpenVPM `patient-merge-events` |
| 9 | Importador CSV con simulacro (dry-run) para migrar desde otros sistemas | `importacion_lote`, endpoints `POST /api/importaciones/{tipo}/simulacro` y `/aplicar`, reporte de duplicados/rechazos | M | A | Clave para adopción por clínicas; OpenVPM `migrating-to-openvpm` |
| 10 | Bandeja de laboratorio con revisión (pendiente -> completado -> revisado) e historial append-only | usar V20 (`orden_examen`, `resultado_examen`), tabla `resultado_evento`; endpoints y pantalla "Bandeja" | L | A | Fase 6; OpenVPM `lab-result-events` |
| 11 | Hospitalización: hoja de tratamiento con tareas horarias, cargos diarios, pizarra de estado de pacientes en tiempo real | V20, módulo `hospitalizacion`, SSE/WebSocket para pizarra | L | A | Fases 2 y 4 ya hechas |
| 12 | Presupuesto con revisiones y aceptación del tutor (firma/enlace) | `presupuesto_revision`, estado ACEPTADO, enlace público firmado | M | M | Fase 4; dependería del portal |
| 13 | Planes de salud con cobro recurrente | V21 `plan_salud/suscripcion_plan/consumo_plan` + job de facturación mensual | M | M | Fase 4 |
| 14 | Integración de analizadores (ASTM sobre TCP) y etiquetas con código de barras | módulo `laboratorio-integracion` independiente | L | M | Solo si la clínica tiene analizador in-house; Hans LIS como referencia |
| 15 | Aislamiento por sede reforzado con RLS de PostgreSQL y pruebas | `SET LOCAL app.sede_id`, políticas, test | M | M | Ya listado como evolución en `modelo-datos-v2.md` |
| 16 | API pública `/api/v1` con API keys con alcance + webhooks firmados | `api_key`, `webhook_endpoint`, `webhook_entrega` | M | M | Habilita integraciones (laboratorio, contabilidad) |
| 17 | MFA TOTP con códigos de recuperación y bloqueo por intentos | V22 (2FA, bloqueo) ya modelado; implementar | M | A | Fase 0 pendiente |

### 6.3 Producto y datos que conviene decidir pronto

- **Alta de cumplimiento Colombia**: certificado de salud para viaje (microchip, vacunas con lote, desparasitación; Resolución ICA 100164/2021, validar vigencia) y QR de verificación del carné. S-M.
- **Maltrato animal (Ley 1774)**: flujo restringido con fotos, escala de condición corporal, bitácora de acceso y exportación. M. Asesor jurídico debe fijar obligaciones.
- **Historia "orientada a problemas"**: ya hay `problema_activo` (V13); vincularlo con diagnósticos y consultas con estado activo/resuelto/crónico (OpenVPM y Ababu coinciden). M.
- **Seguros de mascotas**: póliza, deducible, % de cobertura, reclamación (OpenVPM `insurance`). Mercado colombiano de seguros de mascotas: [S] no investigado. Prioridad baja.
- **Escriba por voz (VetVoice, OpenVPM AI-ready)**: M-L. Requiere consentimiento del tutor para grabar, revisión humana obligatoria y política de retención del audio. Después de portal y bandeja de laboratorio.
- **DICOM/PACS**: VetImage muestra el patrón (DICOMweb + OHIF). No integrar propio; si una clínica tiene rayos X digitales, enlazar a un PACS externo por URL en `adjunto` (S) antes de construir visor (L).
- **Pizarra de clínica / sala de espera**: tablero de estado por paciente. M, dependencia: agenda cerrada y estados de cita.
- **Demo pública**: modo solo lectura en producción como VetChart. S.

## 7. Priorización resumida (valor / esfuerzo)

1. Inmediato (S, valor alto): #1 microchip, #3 recordatorio manual, #4 reporte antirrábico, #5 banderas.
2. Siguiente sprint (M, alto): #2 cola de cargos pendientes, #6 corrección por error, #7 cierre de visita, #17 MFA.
3. Adopción comercial: #9 importador con dry-run, #8 fusión de duplicados.
4. Fases 6-8 ya planeadas: #10, #11, #13, #14, #12, #16, #15.
5. Explorar solo con demanda: FHIR, SNOMED VET, DICOM, seguros, voz.

## 8. Qué no pude leer / limitaciones

- No pude leer el esquema ni el código de OpenVPMS (no está en GitHub; solo listados de terceros).
- No abrí los PDF de ICA 100164/2021 (404 en normograma INVIMA), ni de Circular 064/2014, ni de ISO/ICAR/WSAVA; los datos vienen de resúmenes de búsqueda o de prensa.
- No leí el modelo de dominio de `ddd-vet-sample`, ni el código de Hans LIS más allá de README y árbol, ni los SQL VeNom de Ababu.
- No verifiqué licencias de VeNom ni de VetSCT para uso comercial en Colombia, ni la vigencia actual de microchip obligatorio, carné oficial ni ley 2054 más allá del resumen.
- WebSearch devolvió resúmenes sin estrellas; las estrellas/fechas salen de la API de GitHub (fecha: 2026-10-15). La búsqueda en la API se limitó por tasa antes de revisar temas como "appointment reminder", "pet passport" y NestJS; no se encontró repo NestJS.
- Nada aquí es asesoría legal ni clínica; validar con asesor jurídico y veterinario responsable.
