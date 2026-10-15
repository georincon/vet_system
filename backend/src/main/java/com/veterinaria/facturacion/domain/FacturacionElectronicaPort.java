package com.veterinaria.facturacion.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * Puerto de salida hacia el proveedor tecnologico de factura electronica (DIAN). La Fase 5 aporta el adaptador
 * real (Siigo/Alegra u otro); hoy {@code FacturacionElectronicaSimulada} responde PENDIENTE sin salir a la red.
 * Los estados coinciden con {@code factura.estado_dian}: NO_APLICA, PENDIENTE, ENVIADA, ACEPTADA, RECHAZADA, ERROR.
 */
public interface FacturacionElectronicaPort {

    record Linea(String descripcion, BigDecimal cantidad, BigDecimal precioUnitario, BigDecimal impuestoPct,
            BigDecimal descuento, BigDecimal total) {
    }

    /** Documento a emitir. {@code tipoDocumento}: FACTURA, POS o NOTA_CREDITO; la nota referencia {@code numeroOrigen}. */
    record Documento(Long facturaId, String numero, String tipoDocumento, String prefijo, String numeroResolucion,
            String tutorDocumento, String tutorNombre, BigDecimal subtotal, BigDecimal descuento, BigDecimal impuestos,
            BigDecimal total, String numeroOrigen, List<Linea> lineas) {
    }

    record Resultado(String estado, String cufe, String mensaje) {
    }

    /** Solicita la emision. Debe ser idempotente por {@code facturaId}. */
    Resultado emitir(Documento documento);

    /** Consulta el estado actual del documento en el proveedor. */
    Resultado consultar(Long facturaId, String numero);
}
