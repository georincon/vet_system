package com.veterinaria.inventario.api.dto;

import java.math.BigDecimal;

import com.veterinaria.shared.web.Alta;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** DTOs de items/servicios y categorias. */
public final class ItemDtos {

    private ItemDtos() {
    }

    /** Alta (POST: codigo, nombre y tipo obligatorios) y edicion parcial (PUT: ausente/null = sin cambio; "" limpia texto). */
    public record ItemRequest(
            @NotBlank(groups = Alta.class, message = "El codigo es obligatorio")
            @Size(max = 50, message = "El codigo no puede exceder 50 caracteres") String codigo,
            @NotBlank(groups = Alta.class, message = "El nombre es obligatorio")
            @Size(max = 255, message = "El nombre no puede exceder 255 caracteres") String nombre,
            @NotBlank(groups = Alta.class, message = "El tipo es obligatorio")
            @Pattern(regexp = "SERVICIO|PRODUCTO|MEDICAMENTO|ALIMENTO|EXAMEN_LAB|EXAMEN_IMAGEN",
                    message = "Tipo: SERVICIO, PRODUCTO, MEDICAMENTO, ALIMENTO, EXAMEN_LAB o EXAMEN_IMAGEN") String tipo,
            Long categoriaId,
            @DecimalMin(value = "0", message = "El precio no puede ser negativo") BigDecimal precioVenta,
            @DecimalMin(value = "0", message = "El costo no puede ser negativo") BigDecimal costo,
            @DecimalMin(value = "0", message = "El IVA no puede ser negativo")
            @DecimalMax(value = "100", message = "El IVA no puede exceder 100") BigDecimal impuestoPct,
            Boolean controlaStock,
            Boolean activo,
            @DecimalMin(value = "0", message = "El stock minimo no puede ser negativo") BigDecimal stockMinimo,
            @Size(max = 15) String unidadMedida,
            @Size(max = 100) String presentacion,
            @Size(max = 100) String marca,
            @Size(max = 150) String principioActivo,
            @Size(max = 60) String concentracion,
            @Size(max = 30) String viaAdministracion,
            @Size(max = 40) String registroIca,
            Boolean esControlado,
            Boolean requiereFormula,
            @Size(max = 50) String codigoBarras,
            Boolean tieneVencimiento,
            Long proveedorPreferidoId,
            Boolean requiereVeterinario,
            Integer duracionMinutos,
            String descripcion) {
    }

    public record ItemResponse(
            Long id, String codigo, String nombre, String tipo, Long categoriaId, String categoria,
            BigDecimal precioVenta, BigDecimal costo, BigDecimal impuestoPct, boolean controlaStock, boolean activo,
            BigDecimal stockMinimo, BigDecimal stockActual, BigDecimal stockTotal, boolean bajoMinimo,
            String unidadMedida, String presentacion, String marca, String principioActivo, String concentracion,
            String viaAdministracion, String registroIca, boolean esControlado, boolean requiereFormula,
            String codigoBarras, boolean tieneVencimiento, Long proveedorPreferidoId, boolean requiereVeterinario,
            Integer duracionMinutos, String descripcion) {
    }

    public record CategoriaRequest(
            @NotBlank(groups = Alta.class, message = "El nombre es obligatorio")
            @Size(max = 100, message = "El nombre no puede exceder 100 caracteres") String nombre,
            @NotBlank(groups = Alta.class, message = "El tipo es obligatorio")
            @Pattern(regexp = "SERVICIOS|PRODUCTOS|MEDICAMENTOS|ALIMENTOS|EXAMENES",
                    message = "Tipo: SERVICIOS, PRODUCTOS, MEDICAMENTOS, ALIMENTOS o EXAMENES") String tipo,
            Boolean activo) {
    }

    public record CategoriaResponse(Long id, String nombre, String tipo, boolean activo) {
    }

    public record BodegaRequest(
            @NotBlank(groups = Alta.class, message = "El nombre es obligatorio")
            @Size(max = 100, message = "El nombre no puede exceder 100 caracteres") String nombre,
            @Pattern(regexp = "PRINCIPAL|FARMACIA|QUIROFANO|LABORATORIO|VEHICULO",
                    message = "Tipo: PRINCIPAL, FARMACIA, QUIROFANO, LABORATORIO o VEHICULO") String tipo,
            Boolean activo) {
    }

    public record BodegaResponse(Long id, String nombre, String tipo, boolean activo, Long sedeId) {
    }
}
