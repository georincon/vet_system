package com.veterinaria.inventario.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.veterinaria.shared.web.Alta;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** DTOs de lotes, kardex, salidas, ajustes, alertas y libro de controlados. */
public final class StockDtos {

    private StockDtos() {
    }

    public record LoteResponse(Long id, Long itemId, String itemCodigo, String itemNombre, Long bodegaId, String bodega,
            String numeroLote, LocalDate fechaVencimiento, Long diasParaVencer, boolean vencido,
            BigDecimal cantidadInicial, BigDecimal cantidadActual, BigDecimal costoUnitario, String estado,
            boolean vigente, Long proveedorId, Instant creadoEn) {
    }

    /** Alta manual de un lote (carga inicial de inventario); genera un movimiento ENTRADA. */
    public record LoteRequest(
            @NotNull(message = "El item es obligatorio") Long itemId,
            @NotNull(message = "La bodega es obligatoria") Long bodegaId,
            @NotBlank(message = "El numero de lote es obligatorio") @Size(max = 60) String numeroLote,
            LocalDate fechaVencimiento,
            @NotNull(message = "La cantidad es obligatoria")
            @DecimalMin(value = "0.001", message = "La cantidad debe ser mayor a cero") BigDecimal cantidad,
            @DecimalMin(value = "0", message = "El costo no puede ser negativo") BigDecimal costoUnitario,
            Long proveedorId,
            @Size(max = 255) String motivo) {
    }

    public record EstadoLoteRequest(
            @NotBlank(message = "El estado es obligatorio")
            @Pattern(regexp = "DISPONIBLE|CUARENTENA", message = "Estado: DISPONIBLE o CUARENTENA") String estado) {
    }

    public record RetiroLoteRequest(
            @NotBlank(message = "El motivo es obligatorio") @Size(max = 255) String motivo) {
    }

    public record MovimientoResponse(Long id, Instant ocurridoEn, String tipo, BigDecimal delta, BigDecimal cantidad,
            Long loteId, String numeroLote, Long itemId, String itemCodigo, String itemNombre, String bodega,
            String motivo, String referencia, Persona usuario, Long pacienteId, Long consultaId, Long facturaItemId,
            Long recepcionItemId, BigDecimal costoUnitario, BigDecimal saldoLote) {
    }

    public record Persona(Long id, String nombre) {
    }

    /** Salida manual. Con {@code loteId} usa ese lote; si no, FEFO sobre el item (y bodega opcional). */
    public record SalidaRequest(
            Long itemId,
            Long loteId,
            Long bodegaId,
            @NotNull(message = "La cantidad es obligatoria")
            @DecimalMin(value = "0.001", message = "La cantidad debe ser mayor a cero") BigDecimal cantidad,
            @Pattern(regexp = "USO_CLINICO|CONSUMO_INTERNO|MUESTRA|MERMA|DANIO|DESECHO",
                    message = "Motivo: USO_CLINICO, CONSUMO_INTERNO, MUESTRA, MERMA, DANIO o DESECHO") String motivo,
            @Size(max = 255) String observaciones,
            Long pacienteId,
            Long consultaId) {
    }

    public record UsoRequest(
            Long itemId,
            Long loteId,
            Long bodegaId,
            @NotNull(message = "La cantidad es obligatoria")
            @DecimalMin(value = "0.001", message = "La cantidad debe ser mayor a cero") BigDecimal cantidad,
            @Size(max = 255) String observaciones,
            Long pacienteId,
            Long consultaId) {
    }

    public record SalidaResponse(Long itemId, String itemNombre, String motivo, BigDecimal cantidad,
            List<LoteUsado> lotes, BigDecimal stockRestante) {
    }

    public record LoteUsado(Long loteId, String numeroLote, LocalDate fechaVencimiento, BigDecimal cantidad) {
    }

    /** Ajuste por conteo fisico ({@code cantidadReal}) o por diferencia ({@code delta}); exactamente uno. */
    public record AjusteRequest(
            @NotNull(message = "El lote es obligatorio") Long loteId,
            @DecimalMin(value = "0", message = "La cantidad real no puede ser negativa") BigDecimal cantidadReal,
            BigDecimal delta,
            @NotBlank(message = "El motivo es obligatorio") @Size(max = 255) String motivo) {
    }

    public record AjusteResponse(Long loteId, String numeroLote, BigDecimal anterior, BigDecimal actual, BigDecimal delta,
            String motivo) {
    }

    public record AlertaStock(Long itemId, String codigo, String nombre, BigDecimal stock, BigDecimal minimo,
            BigDecimal faltante) {
    }

    public record AlertaLote(Long loteId, Long itemId, String itemCodigo, String itemNombre, String bodega,
            String numeroLote, LocalDate fechaVencimiento, long diasParaVencer, BigDecimal cantidad, String estado) {
    }

    public record AlertasResponse(int dias, Resumen resumen, List<AlertaStock> bajoMinimo, List<AlertaLote> porVencer,
            List<AlertaLote> vencidos) {
        public record Resumen(int bajoMinimo, int porVencer, int vencidos) {
        }
    }

    public record ControladoResponse(Long id, Instant ocurridoEn, Long itemId, String itemCodigo, String itemNombre,
            Long loteId, String numeroLote, String tipo, BigDecimal cantidad, BigDecimal saldo, Long pacienteId,
            String paciente, Long recetaId, Persona veterinario, String observaciones) {
    }
}
