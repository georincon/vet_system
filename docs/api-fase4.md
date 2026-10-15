# Contrato API - Fase 4 (inventario/farmacia y facturacion interna/caja)

Base `http://localhost:9010`, `Authorization: Bearer <jwt>`. Migracion nueva: `V28__fase4_inventario_facturacion.sql`.
Convenciones iguales a `docs/api-sprint1.md` (paginas `{items,page,size,totalElements,totalPages,first,last}`, fechas `YYYY-MM-DD`,
PUT parcial: ausente/null = sin cambio, `""` limpia texto). Montos COP con 2 decimales, cantidades con hasta 3.
Errores: 400 validacion/enum, 401, 403, 404, **409** (stock insuficiente, duplicado, estado no permitido, rango agotado), **422** regla de negocio.

## Modulos
- `inventario` (cerrado): API publica `StockApi`, `CatalogoApi`, `ProductoClinicoResolver` (implementado por `inmunizacion`). No depende de otros modulos de negocio.
- `facturacion` (cerrado): depende de `inventario` (StockApi/CatalogoApi) y del evento `clinica.ConsultaCerrada`. Puerto `FacturacionElectronicaPort` (adaptador simulado -> `PENDIENTE`).
- `inmunizacion -> inventario` (descuenta lote via `StockApi`; `LoteStockRepository` es ahora un adaptador). `dashboard -> inventario, facturacion`.
- Sin ciclos; `ModularityTests` verde.

## Migracion V28 (justificacion)
1. `item.stock_minimo` (no existia minimo por item). 2. `presupuesto.origen` (MANUAL/CONSULTA) + estado `FACTURADO` + `creado_por`; indice unico por consulta para el presupuesto automatico; `presupuesto_item.origen_tabla/origen_id` (idempotencia por renglon).
3. `factura.creado_por`, `factura_item.cantidad_stock_cubierta` (evita doble descuento) y `factura_item_origen_id` (renglon que acredita la nota). 4. Secuencias `seq_presupuesto`, `seq_orden_compra`; resoluciones internas FI/NC/POS.
5. Kardex append-only (`movimiento_stock`) y triggers de auditoria (item, bodega, proveedor, orden_compra, recepcion_compra, presupuesto, caja_turno, caja_movimiento, resolucion_facturacion; factura y pago ya los tenian). 6. Items `VAC-<codigo>` por vacuna (NO se enlaza `vacuna.item_id` para no alterar la validacion de lote de inmunizacion; ver "Vinculo vacuna-item").

## Matriz de roles
| Recurso | ADMIN | RECEPCION | VETERINARIO | AUXILIAR |
|---|---|---|---|---|
| GET inventario: items, categorias, bodegas, lotes, movimientos, alertas | si | si | si | si |
| Costos (`costo`, `costoUnitario`) | si | oculto (null) | oculto | oculto |
| `POST /inventario/movimientos/uso` | si | no | si | si |
| `GET /inventario/controlados` (libro) | si | no | si | no |
| Items/categorias/bodegas (escritura), lotes (carga/estado/retiro), ajustes, salidas con motivo, proveedores, ordenes de compra, recepciones | si | no | no | no |
| Presupuestos: GET | si | si | **solo los de sus consultas** (404 si no) | no |
| Presupuestos: crear/editar/estado/facturar; facturas, pagos, POS, caja, cartera, abonos, `reportes/cartera` | si | si | no | no |
| Notas credito, reintento DIAN, resoluciones, `reportes/ventas` | si | no | no | no |
| Dashboard `ventasHoy/ventasMes` | si | si | null | null |

Justificacion: no se introduce rol "bodega" (`usuario.rol` tiene CHECK de 4 roles y afectaria seguridad); compras, precios, ajustes y notas credito (devuelven dinero) solo ADMIN. RECEPCION solo vende a precio de catalogo (cambiar precio/IVA, renglon libre o facturar sin descontar stock exigen ADMIN, 422).

## Inventario (`/api/inventario`)
- `GET /items?q&tipo&categoriaId&controlaStock&esControlado&activo&bajoMinimo&page&size`, `GET /items/{id}`, `POST /items` (201), `PUT /items/{id}` (activo=false da de baja).
  Body: `{codigo,nombre,tipo(SERVICIO|PRODUCTO|MEDICAMENTO|ALIMENTO|EXAMEN_LAB|EXAMEN_IMAGEN),categoriaId,precioVenta,costo,impuestoPct,controlaStock,activo,stockMinimo,unidadMedida,presentacion,marca,principioActivo,concentracion,viaAdministracion,registroIca,esControlado,requiereFormula,codigoBarras,tieneVencimiento,proveedorPreferidoId,requiereVeterinario,duracionMinutos,descripcion}`.
  Response agrega `id,categoria,stockActual` (vigente: disponible y no vencido), `stockTotal`, `bajoMinimo`. Codigo repetido 409; solo medicamento/producto/alimento controlan stock y un controlado exige `controlaStock` (422).
- `GET|POST /categorias`, `PUT /categorias/{id}`; `GET|POST /bodegas`, `PUT /bodegas/{id}` (no se desactiva con existencias, 409).
- `GET /lotes?itemId&vacunaId&antiparasitarioId&bodegaId&soloVigentes&conStock&q` -> lista FEFO (max 500). **Selector de vacunacion:** `GET /lotes?vacunaId=2&soloVigentes=true`.
  `{id,itemId,itemCodigo,itemNombre,bodegaId,bodega,numeroLote,fechaVencimiento,diasParaVencer,vencido,cantidadInicial,cantidadActual,costoUnitario,estado,vigente,proveedorId,creadoEn}`.
  `vigente` = estado DISPONIBLE, cantidad > 0 y no vencido (vence el dia siguiente a `fechaVencimiento`).
- `POST /lotes` (carga inicial: `{itemId,bodegaId,numeroLote,fechaVencimiento,cantidad,costoUnitario,proveedorId,motivo}`; vencido 422; repetido 409) genera ENTRADA.
  `POST /lotes/{id}/estado {estado: DISPONIBLE|CUARENTENA}`; `POST /lotes/{id}/retirar {motivo}` (baja de existencias: movimiento VENCIMIENTO, estado VENCIDO o RETIRADO, no reactivable).
- `GET /movimientos?itemId&loteId&tipo&desde&hasta&pacienteId&consultaId&referencia` (kardex, mas reciente primero):
  `{id,ocurridoEn,tipo,delta,cantidad,loteId,numeroLote,itemId,itemCodigo,itemNombre,bodega,motivo,referencia,usuario{id,nombre},pacienteId,consultaId,facturaItemId,recepcionItemId,costoUnitario,saldoLote}`. Referencias: `INMUNIZACION:id`, `DESPARASITACION:id`, `FACTURA:<numero>`, `RECEPCION:id`, `SALIDA_<MOTIVO>`, `AJUSTE_MANUAL`, `BAJA_LOTE:id`. El kardex es append-only (trigger).
- `POST /movimientos/uso {itemId|loteId,bodegaId,cantidad,observaciones,pacienteId,consultaId}` y `POST /movimientos/salida {..., motivo: USO_CLINICO|CONSUMO_INTERNO|MUESTRA|MERMA|DANIO|DESECHO}` -> `{itemId,itemNombre,motivo,cantidad,lotes:[{loteId,numeroLote,fechaVencimiento,cantidad}],stockRestante}`.
  Sin `loteId` reparte por FEFO. Con `loteId` vencido/no disponible solo admite MERMA/DANIO/DESECHO (422).
- `POST /movimientos/ajuste {loteId, cantidadReal | delta, motivo}` -> `{loteId,numeroLote,anterior,actual,delta,motivo}` (nunca negativo: 409; sin cambio o ambos campos: 422).
- `GET /alertas?dias=30` -> `{dias,resumen{bajoMinimo,porVencer,vencidos},bajoMinimo[{itemId,codigo,nombre,stock,minimo,faltante}],porVencer[lote...diasParaVencer],vencidos[lote con existencias]}`. Bajo minimo: `stock vigente < stock_minimo` (minimo > 0, item activo con stock).
- `GET /controlados?itemId&pacienteId&tipo&desde&hasta` (libro): `{id,ocurridoEn,itemId,...,loteId,numeroLote,tipo(ENTRADA|SALIDA|DESECHO|AJUSTE),cantidad,saldo,pacienteId,paciente,veterinario{id,nombre},observaciones}`; `saldo` = existencia total del item tras el movimiento. Todo movimiento de un item `esControlado` lo escribe automaticamente; una salida (no desecho) sin `pacienteId` es 422. Append-only.
- Proveedores: `GET|POST /proveedores`, `GET|PUT /proveedores/{id}` `{tipoDocumento,numeroDocumento,razonSocial,contacto,telefono,email,direccion,ciudad,plazoPagoDias,notas,activo}` (documento repetido 409).
- Ordenes de compra: `POST /ordenes-compra {proveedorId,bodegaDestinoId,fechaEsperada,notas,items:[{itemId,cantidad,costoUnitario,impuestoPct}]}` (BORRADOR, codigo `OC-000001`, subtotal/impuestos/total), `PUT` (solo BORRADOR), `POST /{id}/enviar`, `POST /{id}/cancelar`, `GET` lista/detalle (items con `cantidadRecibida` y `pendiente`).
- Recepciones: `POST /ordenes-compra/{id}/recepciones` o `POST /recepciones` (directa: `proveedorId` + items con `itemId` y `costoUnitario`):
  `{bodegaId,numeroFacturaProveedor,fecha,fechaVencimientoPago,notas,items:[{ordenCompraItemId,itemId,cantidad,numeroLote,fechaVencimiento,costoUnitario,impuestoPct}]}`.
  Crea el lote (o suma a uno existente con el mismo item/bodega/numero y vencimiento), movimiento ENTRADA con costo y `recepcionItemId`, actualiza `item.costo` y `item_proveedor`, y la orden pasa a PARCIAL/RECIBIDA. 409: orden no enviada, factura de proveedor repetida; 422: mas de lo pendiente, lote vencido, falta vencimiento.

## Facturacion (`/api/facturacion`)
### Presupuestos
`GET /presupuestos?tutorId&pacienteId&consultaId&estado`, `GET /presupuestos/{id}`, `POST` (`{tutorId,pacienteId,consultaId,vigenteHasta,notas,items:[Linea]}`), `PUT /{id}` (solo BORRADOR), `POST /{id}/estado {estado: ENVIADO|ACEPTADO|RECHAZADO}`, `POST /{id}/facturar {condicionPago,fechaVencimiento,notas,bodegaId,pagos}` -> factura (el presupuesto queda FACTURADO).
`Linea = {itemId,descripcion,cantidad,precioUnitario,impuestoPct,descuento(monto),pacienteId,veterinarioId,consultaId,descontarStock}`. Con itemId, precio (vigente por sede) e IVA salen del catalogo.
Calculo por renglon: `bruto=cant*precio; base=bruto-descuento; IVA=base*pct; total=base+IVA` (HALF_UP 2 decimales). Factura: `subtotal=sum(bruto)`, `descuento`, `impuestos`, `total`. Response de presupuesto: `{id,codigo(PRE-000001),tutor,paciente,consultaId,estado,origen,vigenteHasta,subtotal,descuento,impuestos,total,facturaId,items[{...,base,impuesto,total}]}`.

### Facturas, pagos y POS
- `POST /facturas` `{tutorId,pacienteId,consultaId,condicionPago(CONTADO|CREDITO),fechaVencimiento,notas,bodegaId,items,pagos:[{metodo,monto,referencia}]}` (o `presupuestoId` sin items). 201.
  Reglas: CONTADO con pagos exige que sumen el total (422; sin pagos queda EMITIDA para pagar luego); CREDITO admite abono parcial y fija vencimiento (por defecto +30 dias); Consumidor final no compra a credito; tutor con `bloqueado_cartera` debe pagar completo.
  Todo en una transaccion: numero (resolucion con bloqueo), renglones, **descuento FEFO**, pagos y solicitud al puerto DIAN. Si falla (p. ej. 409 de stock) no queda nada, ni numero consumido.
- Response: `{id,numero,tipoDocumento,estado,estadoDian,tutor,pacienteId,paciente,consultaId,presupuestoId,facturaOrigenId,numeroOrigen,fecha,subtotal,descuento,impuestos,total,pagado,saldo,condicionPago,fechaVencimiento,notas,motivoAnulacion,cufe,dianMensaje,resolucion{prefijo,numeroResolucion,ambiente},items[{...,loteId,cantidadAcreditada,cantidadStockCubierta}],pagos[{id,fecha,metodo,monto,referencia,estado,turnoId,usuario}],notasCredito[]}`.
- Estados: EMITIDA -> PARCIAL (pagos o notas) -> PAGADA; ANULADA solo por nota credito total. `saldo = max(0, (total - notas credito) - pagos)`. Una factura emitida **no cambia montos** (trigger V19 + API sin edicion).
- `POST /facturas/{id}/pagos {metodo(EFECTIVO|TARJETA|TRANSFERENCIA|NEQUI|DAVIPLATA|PSE|DATAFONO|CHEQUE|OTRO),monto,referencia}` (pagos mixtos = varias llamadas). Exige turno de caja ABIERTO del usuario (422); monto > saldo 422; factura saldada/anulada 409.
- `GET /facturas?tutorId&pacienteId&estado&tipo&q&desde&hasta&conSaldo`, `GET /facturas/{id}`. `POST /facturas/{id}/dian/reintentar` (ADMIN): vuelve a llamar al puerto; estado DIAN `PENDIENTE` sin CUFE.
- `POST /pos/ventas {tutorId?,pacienteId,notas,bodegaId,items,pagos}`: venta de mostrador (tipo POS, serie `POS-`); sin tutorId = Consumidor final (se crea si falta); pagos obligatorios y deben cuadrar con el total.

### Notas credito (solo ADMIN)
`POST /facturas/{id}/notas-credito {motivo,items:[{facturaItemId,cantidad}],reintegrarStock,devolucion:{metodo}}` -> `{nota,factura,montoADevolver,devolucionRegistradaEnCaja,stockReintegrado}`.
Sin `items` acredita todo lo pendiente (anulacion total -> factura ANULADA, montos intactos). La nota (serie `NC-`) referencia los renglones; no se acredita mas de lo facturado (422) ni una factura ANULADA (409). La nota reduce primero el saldo; lo ya pagado que sobre es `montoADevolver` (con `devolucion.metodo=EFECTIVO` se registra un EGRESO en el turno abierto del usuario; sin turno 422). `reintegrarStock` devuelve a los lotes de origen solo lo que descontó esa factura.

### Caja
`GET /caja`; `POST /caja/turnos/abrir {cajaId?,montoApertura}` (409 si la caja o el usuario ya tienen turno abierto); `GET /caja/turnos/actual` (404 si no hay); `GET /caja/turnos?cajaId&estado&desde&hasta`; `GET /caja/turnos/{id}`;
`POST /caja/turnos/{id}/movimientos {tipo: INGRESO|EGRESO|RETIRO,monto,concepto}` (egreso/retiro no puede exceder el efectivo en caja); `POST /caja/turnos/{id}/cerrar {efectivoContado,observaciones}`.
`efectivoEsperado = apertura + pagos en EFECTIVO + ingresos - egresos - retiros`; `diferencia = contado - esperado` se guarda y reporta (si != 0 las observaciones son obligatorias, 422). Solo el dueño del turno o ADMIN lo cierra/mueve (403). Turno: `{...,estado,montoApertura,efectivoEsperado,efectivoContado,diferencia,resumen{ingresosEfectivo,ingresosPorMetodo[],totalCobrado,ingresosManuales,egresos,retiros,efectivoEsperado},movimientos[]}`.

### Numeracion
`GET /resoluciones?tipo&activo`, `POST` (`{tipoDocumento,prefijo,numeroResolucion,fechaResolucion,rangoDesde,rangoHasta,vigenteDesde,vigenteHasta,ambiente,activo}`), `PUT /{id}` (vigencia, ambiente, activo, ampliar `rangoHasta`). El consecutivo se toma con `FOR UPDATE` en la transaccion del documento (sin saltos); usa la resolucion activa mas reciente con rango disponible y vigente, si no 409. Series internas sembradas: FACTURA `FI-`, NOTA_CREDITO `NC-`, POS `POS-` (formato `PREFIJO-000001`).

### Cartera y reportes
- `GET /cartera?soloVencidas` (por tutor: `{tutorId,tutor,documento,facturas,saldo,saldoVencido,masAntigua,diasMoraMax}`), `GET /cartera/tutores/{id}` (facturas con saldo, `vencimiento`, `diasMora`), `POST /cartera/tutores/{id}/abonos {pagos:[{metodo,monto}],facturaIds?}` (aplica a las mas antiguas; excede el saldo 422; requiere turno).
- `GET /reportes/cartera` (edades: vigente, 1-30, 31-60, 61-90, >90) ADMIN/RECEPCION. `GET /reportes/ventas?desde&hasta` (ADMIN): `{totales{documentos,bruto,descuentos,impuestos,facturado,notasCredito,neto},porDia[],porMetodoPago[],porItem[]}`; neto = facturas y POS menos notas credito por fecha de emision (zona Bogota); por metodo no resta devoluciones; maximo 366 dias.
- Dashboard `GET /api/dashboard/resumen`: campos nuevos `ventasHoy`, `ventasMes` (null para VET/AUX), `stockBajo`, `lotesPorVencer` (30 dias).

## Cargos automaticos (evento `ConsultaCerrada`)
Tras confirmarse el cierre (AFTER_COMMIT, transaccion propia) `facturacion` crea o completa **un** presupuesto BORRADOR `origen=CONSULTA` por consulta con: servicio (item del tipo de cita o por tipo de consulta: GENERAL->SRV-CONS-GEN, CONTROL/POSTQUIRURGICA->SRV-CONS-CTL, URGENCIA, TELEMEDICINA, DOMICILIO), procedimientos con item (o con precio), medicamentos de receta con `item_id` (cantidad de la receta), vacunas aplicadas (item de la vacuna o `VAC-<codigo>`) y el acto de vacunacion (SRV-PRC-VAC). Tutor = principal del paciente.
**Idempotencia:** indice unico (una consulta, un presupuesto automatico) y renglones por `(origen_tabla, origen_id)`: reentregar el evento no duplica ni pisa precios/descuentos editados; si el presupuesto ya no es BORRADOR no se toca. No descuenta stock.

## Decision de descuento de stock
- **Se descuenta al emitir la factura/venta POS** (no al cotizar, ni al cerrar la consulta, ni al aceptar el presupuesto), por FEFO sobre todas las bodegas (o `bodegaId`), omitiendo lotes vencidos o en CUARENTENA/RETIRADO/VENCIDO. Sin stock vigente suficiente: 409 y la operacion completa se revierte.
- **Sin doble descuento:** la aplicacion de vacuna/antiparasitario con `loteId` (inmunizacion) ya descuenta su dosis con `consulta_id`. Al facturar un renglon con consulta se resta lo ya descontado por esos actos (salidas - devoluciones con referencia `INMUNIZACION:`/`DESPARASITACION:`) menos lo ya cubierto por otras facturas; lo cubierto queda en `cantidad_stock_cubierta`.
- ADMIN puede facturar un renglon con `descontarStock:false` (ajuste manual); RECEPCION no.
- Notas credito con `reintegrarStock` devuelven solo lo descontado por el propio renglon de factura.

## Vinculo vacuna - item - lote
`ProductoClinicoResolver` (inmunizacion): `vacuna.item_id` si existe; si no, item `VAC-<codigo>` (sembrado en V28). Antiparasitarios ya traen `item_id`. `GET /lotes?vacunaId=` usa ese vinculo; `POST /pacientes/{id}/inmunizaciones` con `loteId` sigue validando contra `vacuna.itemId` solo si esta enlazado (`PUT /api/inmunizacion/vacunas/{id} {itemId}`). El descuento ahora pasa por `StockApi` (kardex y libro de controlados unificados; contrato de inmunizacion sin cambios).

## Auditoria
Triggers (`fn_auditar_cambio` + `app.usuario_id`) en item, bodega, proveedor, orden_compra, recepcion_compra, presupuesto, caja_turno, caja_movimiento, resolucion_facturacion, factura, pago; el kardex y el libro son append-only.

## Pendientes
Fase 5 DIAN (adaptador real del puerto, CUFE/XML/PDF, reintentos asincronos, resoluciones reales, notas debito); traslados entre bodegas; minimos por bodega (`item_bodega_stock`) y sugerido de compra; cuentas por pagar a proveedores (estado de pago de recepciones); anticipos/saldo a favor (tabla `anticipo`) y reversion de pagos; cargos de hospitalizacion/cirugia/laboratorio (`cargo_pendiente`); precios por sede (editor); backfill de presupuestos de consultas cerradas antes de V28; marca automatica VENCIDO por job; UI de Fase 4.
