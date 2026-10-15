# Benchmark de empresas de software veterinario (global y LATAM/Colombia)

Fecha: 2026-10-15. Autor: análisis de producto. Contrasta el mercado con lo ya construido (Fases 0-4, ver `docs/estado-y-roadmap.md`).

## 0. Cómo leer este documento y límites de la evidencia
- **[V] Verificado**: aparece en una página consultada (URL en la columna/nota). Aun así, casi todas son páginas de los propios vendedores o de comparadores afiliados: **son marketing, no medición independiente**.
- **[S] Suposición/inferencia**: razonamiento propio o conocimiento general sin fuente consultada en esta sesión. Validar antes de decidir.
- **Cobertura incompleta**: la búsqueda NO devolvió datos útiles de Vetspire (más allá de una línea), Rhapsody, Vetstoria (solo como integración de Neo), Pawfinity, ClinicPlus, VetBlue, Vetoquinol Cloud, Vetlogic, VetControl, Veterinario Online, Sivet, Vettix, Pawer, Petnow ni Mascotas Plus. Se listan como "sin datos" para no inventar. Las reseñas de Capterra/G2 se leyeron solo en resumen (muestras pequeñas por producto).
- Precios: siempre indicativos, en USD salvo nota, sin confirmar vigencia.

## 1. Matriz comparativa

| Empresa | Foco | Funciones clave | IA | Precio (público) | URL / fuente |
|---|---|---|---|---|---|
| ezyVet (IDEXX) | Cloud para hospitales/grupos | Automatización, comunicación con clientes, ecosistema IDEXX (lab, imagen) | Notas asistidas por voz -> SOAP (en piloto) [V] | Desde ~$260/mes [V, listado de terceros]; usuarios reportan costo alto | https://software.idexx.com/top-veterinary-software-solutions-a-2026-comparison-guide ; https://shepherd.vet/blog/8-best-ai-powered-veterinary-practice-management-software-platforms-2026-comparison-guide |
| Provet Cloud (Nordhealth) | Multi-sede/especialidad, Europa | Flujos configurables, API abierta, seguridad de datos | Resúmenes clínicos, instrucciones de alta, SOAP [V] | Desde ~$99/vet/mes + cuota de plataforma (texto ambiguo) [V] | mismas fuentes |
| Shepherd | Clínicas pequeñas/medianas | Precio plano, SMS bidireccional gratis [V] | TranscribeAI (voz -> SOAP), DiagnoseAI (diferenciales) [V, fuente = el propio vendedor]; IDEXX afirma <1.000 clínicas [V] | "Personalizado" | https://shepherd.vet/blog/8-best-ai-powered-veterinary-practice-management-software-platforms-2026-comparison-guide |
| Digitail | Onboarding rápido, todo-en-uno | App móvil del tutor, reservas y chat, tablero de flujo de pacientes, telemedicina (Whereby), SMS bidireccional, VoIP, solicitud de reseñas [V] | "Tails AI": SOAP, resúmenes, intake [V] | Suscripción por veterinario; ~$289/usuario/mes en GetApp [V]; no hay precio oficial | https://help.digitail.io/en/articles/14327864-telemedicine-appointments-with-whereby ; https://www.g2.com/products/digitail/pricing |
| Covetrus Pulse (ex eVetPractice) | PIMS cloud EE.UU./Canadá | Historia, receta electrónica, inventario, cobros, portal, API; conexión a distribución Covetrus [V] | Escriba SOAP en beta [V] | No público | https://www.capterra.com/p/130107/eVetPractice-com/reviews/ |
| Cornerstone (IDEXX) | PIMS de servidor, clínicas establecidas | Historia, inventario, facturación [V reseñas] | Sin datos | Sin datos | https://www.capterra.com/p/99976/Cornerstone-Practice-Management/reviews |
| Avimark (Covetrus) | PIMS de servidor | Sin datos en esta sesión | Sin datos | Sin datos | - |
| ImproMed (IDEXX) | PIMS de servidor | Sin datos | Sin datos | Sin datos | - |
| Vetspire | Grupos grandes, especialidad y urgencias | Registros entre sedes de la red [V] | AI Scribe y resumen de historia [V] | No público | Shepherd (comparativa de vendedor) |
| NectarVet | Ingresos y cumplimiento del cliente | Portal de comunicación; integra Vetcove (pedidos) [V]; sin app móvil (los usuarios la piden) | Dictado -> SOAP, asistente de historia [V] | No público | Shepherd (comparativa de vendedor) |
| DaySmart Vet | Hospitales de todo tamaño | App móvil bien valorada [V] | Daisy Voice (piloto) [V] | Por escalones de usuarios [V] | Shepherd (comparativa de vendedor) |
| Instinct Science | Cloud moderno EE.UU. | Sin detalle | Sin datos | Sin datos; 4,9/5 en ~234 reseñas Capterra [V] | https://www.capterra.ie/reviews/1017062/instinct-emr |
| PetDesk | Capa de comunicación/reserva sobre el PIMS | Reservas, recordatorios, mensajería; se conecta al PIMS [V] | Sin datos | Desde ~$249/mes [V] | https://capterra.com/p/166086/PetDesk/ |
| Hippo Manager | Clínicas pequeñas | Soporte y capacitación gratis e ilimitados (dicho por el vendedor) | Sin datos | $119/vet/mes (Capterra) o $299/6 usuarios (G2) [V, fuentes en conflicto] | https://www.capterra.com/p/134667/Hippo-Manager/ |
| Vetstoria | Reserva online (widget) | Integrado en otros PIMS, p. ej. Neo ($290/3 usuarios) [V] | Sin datos | Sin datos propios | G2/GetApp comparativas |
| Vetcove | Pedidos de insumos | Integración de compras (vía NectarVet) [V]; resto [S] | - | - | - |
| Pawfinity, ClinicPlus, VetBlue, Vetoquinol Cloud, Rhapsody | - | **Sin datos** | - | - | - |
| AgendaPro (CO) | Agenda/CRM multi-rubro con vertical veterinario | Agenda 24/7, historia digital, recordatorio con segundo aviso, control de vacunas, inventario, comisiones automáticas, pagos online, app móvil, marketplace [V]. WhatsApp y DIAN **no** figuran en esa página | Sin datos | No mostrado | https://agendapro.com/co/veterinario/software-para-clinica-veterinaria |
| VetPraxis (LATAM) | Cloud, historias, inventario por lote, agenda | Recordatorios por WhatsApp y correo, facturación electrónica; "9.000 usuarios activos en LATAM" (dato del vendedor) [V comparador]; cumplimiento DIAN **no confirmado** | Sin datos | No listado | https://www.comparasoftware.com/vetpraxis-gestion-veterinaria |
| QVET (LATAM) | Clínicas y hospitales | Facturación electrónica y recordatorios por WhatsApp [V comparador] | Sin datos | Sin datos | https://www.comparasoftware.com/qvet |
| Veti.app (Chile) | Pequeñas clínicas | Plan básico 17.500 CLP/miembro/mes, usuario extra 0,15 UF, demo 15 días [V comparador] | Sin datos | ver celda anterior | https://www.comparasoftware.com/vetiapp |
| VetPaw (Argentina) | Pequeñas clínicas | Incluye app del tutor; "30.000" sin moneda, precio de lanzamiento, sin contrato [V comparador] | Sin datos | idem | https://www.comparasoftware.com/vetpaw |
| LOLVET (Perú) | Veterinarias | Folleto: 40+ módulos incl. historia y facturación [V, marketing] | Sin datos | Sin datos | https://www.lolimsa.com.pe/wp-content/uploads/Brochure%20LOLVET.pdf |
| Vetlogic, VetControl, Veterinario Online, Sivet, Vettix, Pawer, Petnow, Mascotas Plus | Colombia/LATAM | **No aparecieron en búsquedas.** No se afirma nada | - | - | - |

Lectura de la matriz:
- [V] El mercado global converge en: PIMS cloud + app/portal del tutor + SMS/chat bidireccional + **escriba IA (voz -> SOAP)** como diferencial 2025-2026 + telemedicina integrada (a menudo vía tercero, p. ej. Whereby).
- [V] En LATAM los comparadores muestran precios bajos por usuario (p. ej. ~17.500 CLP/mes) y un énfasis en WhatsApp + factura electrónica; la oferta de precios públicos en Colombia es opaca.
- [S] El hueco competitivo colombiano probable: software que integre DIAN + WhatsApp + pagos locales + historia clínica seria. No se pudo verificar qué hacen los locales; hacer un sondeo mystery-shopper de 3-4 productos (demo) antes de fijar precio.

## 2. Top 15 funcionalidades más valoradas por las clínicas
Base: reseñas Capterra/G2 resumidas, comparativas de vendedores y tendencias 2026. El orden es **inferido** (no hay ranking cuantitativo verificado); marca [V] lo que los textos citan explícitamente.

1. Rapidez y estabilidad de la aplicación [V: la queja inversa es la más repetida en Pulse y Cornerstone].
2. Historia clínica única, fácil de llenar, con plantillas SOAP por especie [V/S].
3. Escriba IA / dictado -> SOAP con revisión humana [V: todos los líderes lo lanzan].
4. Recordatorios automáticos y confirmación de cita (SMS/WhatsApp/correo), con mensajería bidireccional [V: Shepherd, Digitail, AgendaPro, QVET].
5. Facturación + inventario integrados al acto clínico (cargo automático, descuento de stock) [V/S].
6. Portal/app del tutor: reservar, ver vacunas, chat, pagar [V: Digitail, VetPaw].
7. Pagos digitales desde cualquier dispositivo (Digitail cita 66%, dato del vendedor) [V].
8. Planes de bienestar/membresías con renovación [V: Covetrus e IDEXX los listan como tendencia 2026].
9. Reportes de ingresos y productividad (por veterinario/servicio) [V: se quejan de reportes limitados en Pulse].
10. Integración con laboratorio e imágenes [V: argumento de ezyVet/IDEXX].
11. Telemedicina en el mismo sistema, sin costo extra [V: Digitail].
12. Tablero de flujo de pacientes / sala de espera [V: Digitail].
13. Compras a proveedores integradas y alertas de mínimos/vencimientos [V parcial: Vetcove/NectarVet].
14. Soporte humano rápido y capacitación gratuita [V: Hippo lo vende; quejas de soporte en ezyVet].
15. Migración de datos limpia desde el sistema anterior [V: queja en Pulse al migrar de DVMax].

## 3. Quejas frecuentes a evitar
| Queja | Evidencia | Qué hacer en este sistema |
|---|---|---|
| Lentitud y cuelgues | [V] Pulse, Cornerstone (Capterra) | Presupuesto de rendimiento (p95 < 500 ms en API); code-splitting (hoy el chunk principal pasa de 500 kB, ver estado Fase 4). |
| Búsqueda/receta/notas que fallan tras una actualización | [V] Pulse | Pruebas e2e de flujos críticos antes de cada release; feature flags. |
| Inventario inexacto | [V] Cornerstone | Ya tenemos kardex append-only y FEFO; añadir conteo físico/ajuste con motivo. |
| Reportes limitados | [V] Pulse | Exportar CSV y reportes por veterinario/servicio (Fase 8). |
| Varias formas de hacer la misma tarea | [V] Cornerstone | Un camino principal por tarea; revisar UX. |
| Subidas de precio sin aviso | [V] Pulse (2025) | Política de precios transparente y congelada por contrato (comercial). |
| Cargos duplicados en factura (p. ej. tarifa de dispensación por medicamento) | [V] Digitail (G2) | Cuidar el generador de cargos desde `ConsultaCerrada`; prueba de no duplicidad. |
| Soporte mediocre, curva de aprendizaje alta | [V] ezyVet (comparativa 2024 de Provet) | Onboarding guiado, datos demo, ayuda contextual. |
| Integraciones restringidas | [V] ezyVet | API pública documentada y webhooks desde el inicio. |
| Facturas que no se imprimen bien | [V] Pulse | Ya figura como pendiente (PDF/impresión de factura): subirlo de prioridad. |
| Sin app móvil | [V] NectarVet (los usuarios la piden) | PWA en sala de consulta y para el tutor. |

## 4. Lo que no habíamos considerado (priorizado)
Escala: valor A/M/B; esfuerzo S (<1 sprint), M (1-2 sprints), L (3+). Estado según `docs/estado-y-roadmap.md`.

| # | Idea | Qué tenemos hoy | Valor | Esfuerzo | Nota |
|---|---|---|---|---|---|
| 1 | Cierre de Fase 2: agenda sin solapes, signos vitales, diagnósticos, recetas, cierre inmutable, línea de tiempo | PARCIAL ~35%. Sin esto lo demás (IA, portal) no tiene base | A | M | Prerrequisito de todo; es la deuda mayor. |
| 2 | Canal WhatsApp (adaptador de `CanalEnvio`) | Interfaz lista, adaptador no hecho | A | M | Tema #1 en LATAM [V comparadores: VetPraxis, QVET]. Plantillas aprobadas por Meta. |
| 3 | Factura electrónica DIAN vía proveedor tecnológico | Puerto `FacturacionElectronicaPort` + adaptador simulado | A | M | Integrar proveedor autorizado, no certificar software propio. Confirmar lista vigente en la DIAN. |
| 4 | Pagos en línea (Wompi: tarjeta, PSE, Nequi, Daviplata, Bancolombia) y links de pago | Pagos solo registrados en caja | A | M | [V] Wompi ofrece links de pago, webhooks, suscripciones (docs.wompi.co; fuente de tercero). Tarifas **no verificadas**. |
| 5 | Planes de bienestar/membresías con cobro recurrente | No existe (M13 del plan) | A | M | [V] Tendencia 2026 (Covetrus, IDEXX). Usa la suscripción de Wompi [S]. |
| 6 | Escriba IA (voz -> SOAP) con revisión obligatoria | No existe (Fase 9) | A | L | Todos los líderes lo ofrecen [V]. Cuidar habeas data (audio) y precisión en español. Empezar con dictado + resumen, no con diagnóstico. |
| 7 | Portal/PWA del tutor: reservar, carnet, pagar, chat | No existe (Fase 7) | A | L | Reutiliza carnet PDF y agenda. |
| 8 | Impresión/PDF de factura y recibos | Pendiente | M | S | Queja real de Pulse [V]. |
| 9 | Confirmación de cita con respuesta del tutor (sí/no) y lista de espera | Parcial (recordatorios sí; respuesta no) | A | S | Reduce inasistencias [S]. |
| 10 | Mensajería bidireccional unificada (bandeja WhatsApp/SMS) | No | M | M | Diferencial de Shepherd/Digitail [V]. |
| 11 | Inventario predictivo (sugerir pedido por consumo y vencimientos) | Alertas de mínimo/vencimiento sí | M | M | Datos del kardex ya existen; sugerencia de compra es SQL + regla. |
| 12 | NPS/encuesta post-consulta y solicitud de reseña | No | M | S | Digitail integra reseñas [V]. |
| 13 | Dashboards de retención (pacientes sin visita en 12 meses, cohortes) y comisiones por veterinario | Dashboard básico + reportes de ventas | A | M | AgendaPro vende comisiones automáticas [V]. |
| 14 | Laboratorio: órdenes y resultados estructurados con rangos | Solo `adjunto` | M | L | Integración con laboratorios externos es [S] y depende de convenios. |
| 15 | Telemedicina (enlace de video de un tercero + nota SOAP ligada) | No | M | S/M | Digitail lo hace con Whereby [V]; no construir video propio. |
| 16 | Hospitalización/cirugía (Fase 6) | No | M | L | Clínicas pequeñas lo usan menos [S]. |
| 17 | Seguridad: refresh token, bloqueo por intentos, 2FA, quitar admin/admin de prod | Pendiente | A | S | Antes de cualquier piloto real. |
| 18 | Importador de datos (CSV/Excel de otros sistemas) | No | A | M | Migración limpia es dolor real [V Pulse]. Palanca de venta. |
| 19 | Modo offline/continuidad (PWA) | No | M | M | Valorado en el plan; [S] útil en jornadas y visitas a domicilio. |
| 20 | API pública + webhooks | Interna | M | M | Evita la queja de integraciones cerradas [V]. |

Orden sugerido (valor/esfuerzo): 17 -> 1 -> 8, 9 -> 3, 2 -> 4, 5 -> 12, 13 -> 18 -> 7 -> 6 -> resto.

## 5. Ideas diferenciales para Colombia
Todas son hipótesis de producto [S] salvo donde se cite normativa; **verificar el marco legal con asesoría** (en esta sesión no se leyó el texto oficial de ninguna norma).

1. **WhatsApp primero**: recordatorios, confirmación con botones, envío de carnet PDF y factura, resultados. Respeta opt-out y consentimiento (ya hay `acepta_whatsapp`).
2. **DIAN sin fricción**: factura electrónica y notas crédito emitidas desde el cierre de la consulta; el cliente final elige consumidor final o NIT/CC. Proveedor tecnológico autorizado (la DIAN publica la lista; una nota de Portafolio hablaba de 41 proveedores, dato antiguo [V]). Documento soporte para compras a proveedores no obligados [S].
3. **Pagos locales**: Wompi (PSE, Nequi, Daviplata, Botón Bancolombia, tarjeta) con link de pago por WhatsApp y abonos a hospitalizaciones/cirugías [V medios; tarifas sin verificar].
4. **Registro ICA/normativa sanitaria**: [S] campos y reportes para vacunación antirrábica y otras jornadas exigidas por autoridades locales; confirmar requisitos con el ICA y la secretaría de salud municipal antes de diseñar.
5. **Microchip y carnet digital**: campo de microchip con validación de formato ISO 15-dígitos [S], búsqueda por chip, certificado de vacunación con código QR verificable; apoyo a certificados para viaje (requiere validar exigencias del ICA).
6. **Planes de salud y membresías** en pesos con cuotas mensuales (vacunas, consulta anual, descuentos en baños), renovación automática y alerta de mora.
7. **Atención a domicilio**: agenda por zona/ruta, desplazamiento como ítem facturable, historia clínica y cobro desde el celular (PWA), mapa del día del veterinario.
8. **Urgencias 24h**: triaje en recepción con prioridad en sala de espera, tarifa nocturna/festiva, guardia por turnos, anticipo obligatorio configurable, consentimiento informado digital firmado.
9. **Vacunación masiva/jornadas**: modo "jornada" con registro rápido por QR/microchip, alta ligera del tutor (cédula + celular), lote único, ficha imprimible y reporte para autoridades.
10. **Fidelización**: puntos o descuentos por vacunas al día, cumpleaños de la mascota por WhatsApp, referidos, recordatorio de "no te vemos hace X meses", encuesta NPS post-visita.
11. **Habeas data (Ley 1581)**: ya hay consentimientos; añadir registro de acceso, exportación y supresión de datos del tutor como diferencial de confianza.
12. **Multi-clínica liviana** para veterinarios independientes y cadenas barriales (la mayoría de clínicas locales son pequeñas [S]): precio por sede, no por usuario, en COP.

## 6. Modelos de precios observados y recomendación
- [V] Por veterinario/usuario por mes es el estándar (Digitail, Provet, Hippo, Veti.app); Shepherd vende precio plano; algunos listados publican "desde" ~$100-300 USD/mes. LATAM: planes en moneda local por miembro.
- [S] Para Colombia conviene: plan por sede con usuarios incluidos, módulos de pago aparte (WhatsApp por mensajes, IA por consumo), facturación electrónica con tope de documentos, y precios en COP con ajuste anual publicado (responde a la queja de alzas sin aviso [V]).

## 7. Próximos pasos de investigación (lo que falta)
1. Demos reales de 3-4 competidores locales (AgendaPro, VetPraxis, QVET y uno colombiano que se identifique) para verificar DIAN, WhatsApp, precios.
2. Leer las reseñas completas de Capterra/G2 de Shepherd, Digitail, ezyVet y Vetspire; esta sesión solo vio resúmenes.
3. Entrevistar 5-8 clínicas colombianas (2 de urgencias 24h, 2 con domicilio) para validar el top 15 y las ideas de la sección 5.
4. Confirmar con asesor legal: historia clínica veterinaria (Ley 576/2000), retención de datos, ICA, consentimiento de grabación de audio para el escriba IA.

## Fuentes consultadas
- https://software.idexx.com/top-veterinary-software-solutions-a-2026-comparison-guide (vendedor IDEXX)
- https://shepherd.vet/blog/8-best-ai-powered-veterinary-practice-management-software-platforms-2026-comparison-guide (vendedor Shepherd, sesgado)
- https://co.vet/post/veterinary-software-comparison/ (vendedor de escriba)
- https://help.digitail.io/en/articles/14327864-telemedicine-appointments-with-whereby ; https://www.g2.com/products/digitail/pricing
- https://www.capterra.com/p/130107/eVetPractice-com/reviews/ ; https://www.capterra.com/p/99976/Cornerstone-Practice-Management/reviews ; https://www.g2.com/compare/covetrus-pulse-vs-ezyvet
- https://capterra.com/p/166086/PetDesk/ ; https://www.capterra.com/p/134667/Hippo-Manager/ ; https://www.capterra.ie/reviews/1017062/instinct-emr
- https://software.idexx.com/resources/blog/7-digital-veterinary-technology-trends-shaping-practices-in-2026 ; https://mkrcpas.com/top-veterinary-technology-trends-for-2026-every-practice-should-know/ ; https://digitail.com/blog/meeting-pet-parent-demands-in-2025-key-trends-for-clinics/
- https://agendapro.com/co/veterinario/software-para-clinica-veterinaria
- https://www.comparasoftware.com/vetpraxis-gestion-veterinaria ; /qvet ; /vetiapp ; /vetpaw
- https://www.portafolio.co/negocios/dian-no-pedira-software-especifico-para-expedir-fe-513649 ; https://apps.shopify.com/wompi-pagos
