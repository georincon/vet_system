/**
 * Facturacion interna y caja: presupuestos, facturas con IVA/descuento, pagos mixtos y abonos, cartera por tutor,
 * notas credito (unica via de anulacion), turnos de caja, POS de mostrador, numeracion por resoluciones, reportes
 * y cargos automaticos desde {@code clinica.ConsultaCerrada}. La emision electronica DIAN es un puerto
 * ({@code FacturacionElectronicaPort}) con adaptador simulado hasta la Fase 5.
 *
 * <p>Modulo cerrado. Depende de inventario (solo {@link com.veterinaria.inventario.StockApi} y
 * {@link com.veterinaria.inventario.CatalogoApi}) y del evento de clinica; ningun modulo depende de el salvo
 * dashboard, que usa {@link com.veterinaria.facturacion.VentasApi}.
 */
@ApplicationModule(displayName = "Facturacion")
package com.veterinaria.facturacion;

import org.springframework.modulith.ApplicationModule;
