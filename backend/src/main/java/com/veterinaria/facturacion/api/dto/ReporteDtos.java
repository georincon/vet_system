package com.veterinaria.facturacion.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.veterinaria.facturacion.api.dto.FacturacionDtos.FacturaResumen;
import com.veterinaria.facturacion.api.dto.FacturacionDtos.TutorResumen;

/** DTOs de cartera y reportes. */
public final class ReporteDtos {

    private ReporteDtos() {
    }

    public record TotalesVentas(long documentos, BigDecimal bruto, BigDecimal descuentos, BigDecimal impuestos,
            BigDecimal facturado, BigDecimal notasCredito, BigDecimal neto) {
    }

    public record VentaDia(LocalDate fecha, long facturas, BigDecimal facturado, BigDecimal notasCredito, BigDecimal neto) {
    }

    public record VentaMetodo(String metodo, long pagos, BigDecimal monto) {
    }

    public record VentaItem(Long itemId, String codigo, String descripcion, BigDecimal cantidad, BigDecimal total) {
    }

    /** Ventas netas = facturas y POS menos notas credito, por fecha de emision (zona de la clinica). */
    public record ReporteVentas(LocalDate desde, LocalDate hasta, TotalesVentas totales, List<VentaDia> porDia,
            List<VentaMetodo> porMetodoPago, List<VentaItem> porItem) {
    }

    public record CarteraTutor(Long tutorId, String tutor, String documento, long facturas, BigDecimal saldo,
            BigDecimal saldoVencido, java.time.Instant masAntigua, long diasMoraMax) {
    }

    public record FacturaCartera(FacturaResumen factura, LocalDate vencimiento, long diasMora) {
    }

    public record CarteraDetalle(TutorResumen tutor, BigDecimal saldo, BigDecimal saldoVencido,
            List<FacturaCartera> facturas) {
    }

    public record EdadCartera(BigDecimal vigente, BigDecimal dias1a30, BigDecimal dias31a60, BigDecimal dias61a90,
            BigDecimal masDe90) {
    }

    public record ReporteCartera(LocalDate corte, BigDecimal total, long facturas, EdadCartera edades,
            List<CarteraTutor> tutores) {
    }
}
