package com.veterinaria.facturacion.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.veterinaria.shared.web.Alta;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** DTOs de presupuestos, facturas, pagos y notas credito. Montos en COP con 2 decimales. */
public final class FacturacionDtos {

    private FacturacionDtos() {
    }

    /**
     * Renglon de presupuesto/factura. Con {@code itemId} la descripcion, precio vigente e IVA salen del catalogo; solo
     * ADMIN puede sobrescribir precio/IVA o facturar renglones libres (sin itemId). {@code descuento} es un monto.
     */
    public record LineaRequest(
            Long itemId,
            @Size(max = 255) String descripcion,
            @NotNull(message = "La cantidad es obligatoria")
            @DecimalMin(value = "0.01", message = "La cantidad debe ser mayor a cero") BigDecimal cantidad,
            @DecimalMin(value = "0", message = "El precio no puede ser negativo") BigDecimal precioUnitario,
            @DecimalMin(value = "0") @DecimalMax(value = "100") BigDecimal impuestoPct,
            @DecimalMin(value = "0", message = "El descuento no puede ser negativo") BigDecimal descuento,
            Long pacienteId,
            Long veterinarioId,
            Long consultaId,
            Boolean descontarStock) {
    }

    public record LineaResponse(Long id, Long itemId, String itemCodigo, String descripcion, BigDecimal cantidad,
            BigDecimal precioUnitario, BigDecimal impuestoPct, BigDecimal descuento, BigDecimal base,
            BigDecimal impuesto, BigDecimal total, Long consultaId, Long pacienteId, Long veterinarioId, Long loteId,
            BigDecimal cantidadAcreditada, BigDecimal cantidadStockCubierta) {
    }

    public record Persona(Long id, String nombre) {
    }

    public record TutorResumen(Long id, String nombre, String documento) {
    }

    // ------------------------------------------------------------------ presupuestos

    public record PresupuestoRequest(
            @NotNull(groups = Alta.class, message = "El tutor es obligatorio") Long tutorId,
            Long pacienteId,
            Long consultaId,
            LocalDate vigenteHasta,
            String notas,
            @NotEmpty(groups = Alta.class, message = "El presupuesto necesita al menos un renglon") @Valid List<LineaRequest> items) {
    }

    public record EstadoPresupuestoRequest(
            @NotBlank(message = "El estado es obligatorio")
            @Pattern(regexp = "ENVIADO|ACEPTADO|RECHAZADO", message = "Estado: ENVIADO, ACEPTADO o RECHAZADO") String estado) {
    }

    public record PresupuestoResponse(Long id, String codigo, TutorResumen tutor, Long pacienteId, String paciente,
            Long consultaId, Instant fecha, String estado, String origen, LocalDate vigenteHasta, BigDecimal subtotal,
            BigDecimal descuento, BigDecimal impuestos, BigDecimal total, String notas, Long facturaId,
            List<LineaResponse> items) {
    }

    // ------------------------------------------------------------------ pagos y facturas

    public record PagoRequest(
            @NotBlank(message = "El metodo de pago es obligatorio")
            @Pattern(regexp = "EFECTIVO|TARJETA|TRANSFERENCIA|NEQUI|DAVIPLATA|PSE|DATAFONO|CHEQUE|OTRO",
                    message = "Metodo: EFECTIVO, TARJETA, TRANSFERENCIA, NEQUI, DAVIPLATA, PSE, DATAFONO, CHEQUE u OTRO") String metodo,
            @NotNull(message = "El monto es obligatorio")
            @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero") BigDecimal monto,
            @Size(max = 100) String referencia) {
    }

    public record PagoResponse(Long id, Instant fecha, String metodo, BigDecimal monto, String referencia, String estado,
            Long turnoId, Persona usuario) {
    }

    /** Factura directa (items) o a partir de un presupuesto ({@code presupuestoId}, sin items). */
    public record FacturaRequest(
            Long tutorId,
            Long pacienteId,
            Long consultaId,
            Long presupuestoId,
            @Pattern(regexp = "CONTADO|CREDITO", message = "Condicion de pago: CONTADO o CREDITO") String condicionPago,
            LocalDate fechaVencimiento,
            String notas,
            Long bodegaId,
            @Valid List<LineaRequest> items,
            @Valid List<PagoRequest> pagos) {
    }

    /** Facturar un presupuesto existente (renglones y precios del presupuesto). */
    public record FacturarPresupuestoRequest(
            @Pattern(regexp = "CONTADO|CREDITO", message = "Condicion de pago: CONTADO o CREDITO") String condicionPago,
            LocalDate fechaVencimiento,
            String notas,
            Long bodegaId,
            @Valid List<PagoRequest> pagos) {
    }

    /** Venta de mostrador: sin tutor = Consumidor final; los pagos deben cuadrar con el total. */
    public record VentaPosRequest(
            Long tutorId,
            Long pacienteId,
            String notas,
            Long bodegaId,
            @NotEmpty(message = "La venta necesita al menos un renglon") @Valid List<LineaRequest> items,
            @NotEmpty(message = "La venta POS exige pagos que cuadren con el total") @Valid List<PagoRequest> pagos) {
    }

    public record NotaResumen(Long id, String numero, Instant fecha, BigDecimal total, String motivo) {
    }

    public record ResolucionResumen(Long id, String prefijo, String numeroResolucion, String ambiente) {
    }

    public record FacturaResponse(Long id, String numero, String tipoDocumento, String estado, String estadoDian,
            TutorResumen tutor, Long pacienteId, String paciente, Long consultaId, Long presupuestoId,
            Long facturaOrigenId, String numeroOrigen, Instant fecha, BigDecimal subtotal, BigDecimal descuento,
            BigDecimal impuestos, BigDecimal total, BigDecimal pagado, BigDecimal saldo, String condicionPago,
            LocalDate fechaVencimiento, String notas, String motivoAnulacion, String cufe, String dianMensaje,
            ResolucionResumen resolucion, List<LineaResponse> items, List<PagoResponse> pagos,
            List<NotaResumen> notasCredito) {
    }

    public record FacturaResumen(Long id, String numero, String tipoDocumento, String estado, String estadoDian,
            TutorResumen tutor, Instant fecha, BigDecimal total, BigDecimal saldo, String condicionPago,
            LocalDate fechaVencimiento) {
    }

    // ------------------------------------------------------------------ notas credito

    public record DevolucionRequest(
            @Pattern(regexp = "EFECTIVO|TARJETA|TRANSFERENCIA|NEQUI|DAVIPLATA|PSE|DATAFONO|CHEQUE|OTRO",
                    message = "Metodo de devolucion invalido") String metodo) {
    }

    public record NotaItemRequest(
            @NotNull(message = "El renglon de la factura es obligatorio") Long facturaItemId,
            @NotNull(message = "La cantidad es obligatoria")
            @DecimalMin(value = "0.01", message = "La cantidad debe ser mayor a cero") BigDecimal cantidad) {
    }

    /** Sin {@code items} acredita todo lo que falte por acreditar (anulacion total). */
    public record NotaCreditoRequest(
            @NotBlank(message = "El motivo es obligatorio") @Size(max = 255) String motivo,
            @Valid List<NotaItemRequest> items,
            Boolean reintegrarStock,
            @Valid DevolucionRequest devolucion) {
    }

    public record NotaCreditoResponse(FacturaResponse nota, FacturaResponse factura, BigDecimal montoADevolver,
            boolean devolucionRegistradaEnCaja, BigDecimal stockReintegrado) {
    }

    // ------------------------------------------------------------------ abonos de cartera

    public record AbonoRequest(
            @NotEmpty(message = "Indique al menos un pago") @Valid List<PagoRequest> pagos,
            List<Long> facturaIds) {
    }

    public record AbonoAplicado(Long facturaId, String numero, BigDecimal monto, BigDecimal saldoRestante) {
    }

    public record AbonoResponse(Long tutorId, BigDecimal total, List<AbonoAplicado> aplicado, BigDecimal saldoTutor) {
    }
}
