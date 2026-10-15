package com.veterinaria.facturacion.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.veterinaria.shared.web.Alta;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** DTOs de caja (turnos y movimientos) y de resoluciones de numeracion. */
public final class CajaDtos {

    private CajaDtos() {
    }

    public record CajaResponse(Long id, String nombre, boolean activo, Long turnoAbiertoId, String turnoAbiertoPor) {
    }

    public record AperturaRequest(
            Long cajaId,
            @NotNull(message = "El monto de apertura es obligatorio")
            @DecimalMin(value = "0", message = "El monto de apertura no puede ser negativo") BigDecimal montoApertura,
            @Size(max = 500) String observaciones) {
    }

    public record MovimientoCajaRequest(
            @NotBlank(message = "El tipo es obligatorio")
            @Pattern(regexp = "INGRESO|EGRESO|RETIRO", message = "Tipo: INGRESO, EGRESO o RETIRO") String tipo,
            @NotNull(message = "El monto es obligatorio")
            @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero") BigDecimal monto,
            @NotBlank(message = "El concepto es obligatorio") @Size(max = 255) String concepto) {
    }

    /** Cierre con arqueo: {@code efectivoContado} es lo contado fisicamente. La diferencia no bloquea; si existe exige observaciones. */
    public record CierreRequest(
            @NotNull(message = "El efectivo contado es obligatorio")
            @DecimalMin(value = "0", message = "El efectivo contado no puede ser negativo") BigDecimal efectivoContado,
            @Size(max = 500) String observaciones) {
    }

    public record MovimientoCajaResponse(Long id, String tipo, BigDecimal monto, String concepto, Long pagoId,
            Instant ocurridoEn, Long usuarioId) {
    }

    public record PorMetodo(String metodo, BigDecimal monto, long pagos) {
    }

    public record ResumenTurno(BigDecimal montoApertura, BigDecimal ingresosEfectivo, List<PorMetodo> ingresosPorMetodo,
            BigDecimal totalCobrado, BigDecimal ingresosManuales, BigDecimal egresos, BigDecimal retiros,
            BigDecimal efectivoEsperado) {
    }

    public record TurnoResponse(Long id, Long cajaId, String caja, Long usuarioId, String usuario, Instant abiertoEn,
            Instant cerradoEn, String estado, BigDecimal montoApertura, BigDecimal efectivoEsperado,
            BigDecimal efectivoContado, BigDecimal diferencia, String observaciones, ResumenTurno resumen,
            List<MovimientoCajaResponse> movimientos) {
    }

    public record ResolucionRequest(
            @NotBlank(groups = Alta.class, message = "El tipo de documento es obligatorio")
            @Pattern(regexp = "FACTURA|NOTA_CREDITO|NOTA_DEBITO|POS", message = "Tipo: FACTURA, NOTA_CREDITO, NOTA_DEBITO o POS") String tipoDocumento,
            @NotBlank(groups = Alta.class, message = "El prefijo es obligatorio") @Size(max = 10) String prefijo,
            @NotBlank(groups = Alta.class, message = "El numero de resolucion es obligatorio") @Size(max = 30) String numeroResolucion,
            LocalDate fechaResolucion,
            @NotNull(groups = Alta.class, message = "rangoDesde es obligatorio") @Min(0) Long rangoDesde,
            @NotNull(groups = Alta.class, message = "rangoHasta es obligatorio") @Min(1) Long rangoHasta,
            LocalDate vigenteDesde,
            LocalDate vigenteHasta,
            @Pattern(regexp = "HABILITACION|PRODUCCION", message = "Ambiente: HABILITACION o PRODUCCION") String ambiente,
            Boolean activo) {
    }

    public record ResolucionResponse(Long id, String tipoDocumento, String prefijo, String numeroResolucion,
            LocalDate fechaResolucion, long rangoDesde, long rangoHasta, long consecutivoActual, long disponibles,
            LocalDate vigenteDesde, LocalDate vigenteHasta, String ambiente, boolean activo, String siguienteNumero) {
    }
}
