# Documentación clínica avanzada (laboratorio, imágenes, procedimientos, fórmula y envío al tutor)

Decisión (2026-10-15): se deja facturación/DIAN en pausa y se prioriza que el veterinario documente
la historia clínica como en los sistemas de salud humana. Fuente: petición del usuario + benchmark
(docs/benchmark-github.md, docs/benchmark-empresas.md).

## Estado actual (contrastado con código y esquema)
| Capacidad | Esquema | Código/UI | Brecha |
|---|---|---|---|
| Fórmula (receta + ítems) | `receta`, `receta_item` (V1/V15) | API y panel en consulta | Sin documento imprimible/PDF con firma y tarjeta profesional; sin plan de tratamiento ni controles |
| Laboratorio | `examen_catalogo`, `examen_parametro`, `rango_referencia`, `orden_examen(_item)`, `resultado_examen` (V20; banderas N/L/H/LL/HH/A, origen MANUAL/ARCHIVO/INTEGRACION) | **Ninguno** | Todo: catálogo/paneles sembrados, órdenes, captura, validación, importación automática, informe, tendencias. Faltan códigos LOINC y unidades UCUM |
| Imágenes / ecografías | `examen_catalogo.tipo='IMAGEN'`, `adjunto` (V1/V20) | **Ninguno** (ni subida de archivos) | Almacenamiento seguro de archivos, informe estructurado (hallazgos/conclusión), galería |
| Patología / citología | tipo 'PATOLOGIA' | Ninguno | Informe de patología/histopatología |
| Procedimientos / cirugía | `cirugia`, `cirugia_monitoreo_anestesico`, procedimientos en V15 | Ninguno | Registro de procedimiento y cirugía, consentimiento informado firmado |
| Envío al tutor | `recordatorio`, `envio_log`, `CanalEnvio` (solo recordatorios; WhatsApp sin adaptador) | Solo recordatorios | Envío de documentos clínicos con PDF adjunto por correo y por WhatsApp, enlace seguro, bitácora, consentimiento |

## Diseño (equivalente a salud humana)
- **Laboratorio**: modelo orden → muestra → resultado → validación (como HL7 OBR/OBX / FHIR DiagnosticReport+Observation): parámetros con código LOINC y unidad UCUM, rango de referencia por especie/sexo/edad, bandera automática (L/H/LL/HH), valores críticos, interpretación del veterinario, histórico y tendencia por parámetro. Ingreso: captura manual por panel (hemograma, química, uroanálisis, coproscópico, pruebas rápidas…), importación automática (HL7 v2 ORU^R01, CSV/JSON, PDF adjunto) y endpoint para analizadores/LIS con API key.
- **Imágenes**: orden de imagen → informe estructurado (región/órgano, técnica, hallazgos, impresión diagnóstica, recomendaciones) + imágenes/PDF/DICOM como adjuntos.
- **Patología**: informe (muestra, descripción macro/micro, diagnóstico morfológico, márgenes).
- **Procedimientos/cirugía**: registro con técnica, anestesia, hallazgos, complicaciones; consentimiento informado.
- **Fórmula y plan de tratamiento**: documento PDF con membrete, paciente/tutor, medicamentos (dosis, vía, frecuencia, duración), indicaciones, controles y firma con tarjeta profesional.
- **Compartir con el tutor**: correo (PDF adjunto) y WhatsApp (WhatsApp Cloud API con proveedor configurable; enlace seguro con vencimiento si no hay credenciales o fuera de ventana; fallback wa.me). Requiere consentimiento del canal, bitácora y auditoría.

## Entrega por etapas
1. **Etapa A (backend)**: archivos seguros + laboratorio + imágenes + patología + procedimientos/cirugía.
2. **Etapa B (backend)**: fórmula/plan en PDF + módulo de compartir (correo/WhatsApp) para todos los documentos.
3. **Etapa C (frontend)**: pantallas de orden/resultados, informes, galería, fórmula y "Enviar al tutor"; integración en la historia clínica.

## Decisión sobre facturación (2026-10-15)
Se conserva lo ya construido (Fase 4) **solo como historial**: registro de presupuestos, facturas y pagos
consultable por paciente/tutor. No se avanza en DIAN/factura electrónica, cuentas por pagar ni nuevas
funciones de cobro. Pendiente menor (Etapa C): pestaña de solo lectura "Cobros" en la historia del paciente.
