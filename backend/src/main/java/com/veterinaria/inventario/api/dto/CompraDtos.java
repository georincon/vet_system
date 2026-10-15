package com.veterinaria.inventario.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.veterinaria.shared.web.Alta;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** DTOs de proveedores, ordenes de compra y recepciones. */
public final class CompraDtos {

    private CompraDtos() {
    }

    public record ProveedorRequest(
            @Pattern(regexp = "NIT|CC|CE|PASAPORTE|OTRO", message = "Tipo de documento: NIT, CC, CE, PASAPORTE u OTRO")
            String tipoDocumento,
            @NotBlank(groups = Alta.class, message = "El numero de documento es obligatorio")
            @Size(max = 30, message = "Maximo 30 caracteres") String numeroDocumento,
            @NotBlank(groups = Alta.class, message = "La razon social es obligatoria")
            @Size(max = 255, message = "Maximo 255 caracteres") String razonSocial,
            @Size(max = 150) String contacto,
            @Size(max = 30) String telefono,
            @Email(message = "Correo invalido") @Size(max = 255) String email,
            @Size(max = 255) String direccion,
            @Size(max = 120) String ciudad,
            @Min(value = 0, message = "El plazo no puede ser negativo") Integer plazoPagoDias,
            String notas,
            Boolean activo) {
    }

    public record ProveedorResponse(Long id, String tipoDocumento, String numeroDocumento, String razonSocial,
            String contacto, String telefono, String email, String direccion, String ciudad, int plazoPagoDias,
            String notas, boolean activo) {
    }

    public record OrdenItemRequest(
            @NotNull(message = "El item es obligatorio") Long itemId,
            @NotNull(message = "La cantidad es obligatoria")
            @DecimalMin(value = "0.001", message = "La cantidad debe ser mayor a cero") BigDecimal cantidad,
            @NotNull(message = "El costo unitario es obligatorio")
            @DecimalMin(value = "0", message = "El costo no puede ser negativo") BigDecimal costoUnitario,
            @DecimalMin(value = "0") @DecimalMax(value = "100") BigDecimal impuestoPct) {
    }

    /** Alta (proveedorId e items obligatorios) y edicion de una orden en BORRADOR (items reemplaza el detalle). */
    public record OrdenRequest(
            @NotNull(groups = Alta.class, message = "El proveedor es obligatorio") Long proveedorId,
            Long bodegaDestinoId,
            LocalDate fechaEsperada,
            String notas,
            @NotEmpty(groups = Alta.class, message = "La orden necesita al menos un item") @Valid List<OrdenItemRequest> items) {
    }

    public record OrdenItemResponse(Long id, Long itemId, String codigo, String nombre, BigDecimal cantidad,
            BigDecimal cantidadRecibida, BigDecimal pendiente, BigDecimal costoUnitario, BigDecimal impuestoPct) {
    }

    public record OrdenResponse(Long id, String codigo, Long proveedorId, String proveedor, Long bodegaDestinoId,
            String bodegaDestino, LocalDate fecha, LocalDate fechaEsperada, String estado, BigDecimal subtotal,
            BigDecimal impuestos, BigDecimal total, String notas, List<OrdenItemResponse> items) {
    }

    public record RecepcionItemRequest(
            Long ordenCompraItemId,
            Long itemId,
            @NotNull(message = "La cantidad es obligatoria")
            @DecimalMin(value = "0.001", message = "La cantidad debe ser mayor a cero") BigDecimal cantidad,
            @NotBlank(message = "El numero de lote es obligatorio") @Size(max = 60) String numeroLote,
            LocalDate fechaVencimiento,
            @DecimalMin(value = "0", message = "El costo no puede ser negativo") BigDecimal costoUnitario,
            @DecimalMin(value = "0") @DecimalMax(value = "100") BigDecimal impuestoPct) {
    }

    /** Recepcion contra una orden ({@code ordenCompraId}) o directa (proveedorId + items con itemId). */
    public record RecepcionRequest(
            Long ordenCompraId,
            Long proveedorId,
            Long bodegaId,
            @Size(max = 40) String numeroFacturaProveedor,
            LocalDate fecha,
            LocalDate fechaVencimientoPago,
            String notas,
            @NotEmpty(message = "La recepcion necesita al menos un item") @Valid List<RecepcionItemRequest> items) {
    }

    public record RecepcionItemResponse(Long id, Long itemId, String itemCodigo, String itemNombre, Long loteId,
            String numeroLote, LocalDate fechaVencimiento, BigDecimal cantidad, BigDecimal costoUnitario) {
    }

    public record RecepcionResponse(Long id, Long ordenCompraId, String ordenEstado, Long proveedorId, String proveedor,
            Long bodegaId, String bodega, String numeroFacturaProveedor, LocalDate fecha, BigDecimal total,
            LocalDate fechaVencimientoPago, String estadoPago, Instant creadoEn, String notas,
            List<RecepcionItemResponse> items) {
    }
}
