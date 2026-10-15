package com.veterinaria.facturacion.api;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.veterinaria.facturacion.api.dto.CajaDtos.AperturaRequest;
import com.veterinaria.facturacion.api.dto.CajaDtos.CajaResponse;
import com.veterinaria.facturacion.api.dto.CajaDtos.CierreRequest;
import com.veterinaria.facturacion.api.dto.CajaDtos.MovimientoCajaRequest;
import com.veterinaria.facturacion.api.dto.CajaDtos.ResolucionRequest;
import com.veterinaria.facturacion.api.dto.CajaDtos.ResolucionResponse;
import com.veterinaria.facturacion.api.dto.CajaDtos.TurnoResponse;
import com.veterinaria.facturacion.application.CajaService;
import com.veterinaria.facturacion.application.NumeracionService;
import com.veterinaria.shared.web.Alta;
import com.veterinaria.shared.web.PageRequests;
import com.veterinaria.shared.web.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/facturacion")
@Tag(name = "Facturacion - caja y numeracion", description = "Turnos de caja (ADMIN, RECEPCION) y resoluciones (ADMIN)")
public class CajaController {

    private final CajaService caja;
    private final NumeracionService numeracion;

    public CajaController(CajaService caja, NumeracionService numeracion) {
        this.caja = caja;
        this.numeracion = numeracion;
    }

    @GetMapping("/caja")
    public List<CajaResponse> cajas() {
        return caja.cajas();
    }

    @PostMapping("/caja/turnos/abrir")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Abre un turno con monto de apertura (409 si la caja o el usuario ya tienen uno abierto)")
    public TurnoResponse abrir(@Valid @RequestBody AperturaRequest r) {
        return caja.abrir(r);
    }

    @GetMapping("/caja/turnos/actual")
    @Operation(summary = "Turno abierto del usuario actual con su resumen (404 si no tiene)")
    public TurnoResponse actual() {
        return caja.actual();
    }

    @GetMapping("/caja/turnos")
    public PageResponse<TurnoResponse> turnos(@RequestParam(required = false) Long cajaId,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return caja.turnos(cajaId, estado, desde, hasta, PageRequests.of(page, size, Sort.unsorted()));
    }

    @GetMapping("/caja/turnos/{id}")
    public TurnoResponse turno(@PathVariable Long id) {
        return caja.turno(id);
    }

    @PostMapping("/caja/turnos/{id}/movimientos")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ingreso, egreso o retiro de efectivo del turno (dueno del turno o ADMIN)")
    public TurnoResponse movimiento(@PathVariable Long id, @Valid @RequestBody MovimientoCajaRequest r) {
        return caja.movimiento(id, r);
    }

    @PostMapping("/caja/turnos/{id}/cerrar")
    @Operation(summary = "Cierra el turno con el efectivo contado; reporta diferencia (exige observaciones si != 0)")
    public TurnoResponse cerrar(@PathVariable Long id, @Valid @RequestBody CierreRequest r) {
        return caja.cerrar(id, r);
    }

    // ---- resoluciones de numeracion (ADMIN)
    @GetMapping("/resoluciones")
    public List<ResolucionResponse> resoluciones(@RequestParam(required = false) String tipo,
            @RequestParam(required = false) Boolean activo) {
        return numeracion.resoluciones(tipo, activo);
    }

    @PostMapping("/resoluciones")
    @ResponseStatus(HttpStatus.CREATED)
    public ResolucionResponse crearResolucion(@Validated(Alta.class) @RequestBody ResolucionRequest r) {
        return numeracion.crear(r);
    }

    @PutMapping("/resoluciones/{id}")
    @Operation(summary = "Edita vigencia, ambiente, activo y amplia el rango (prefijo/tipo/rangoDesde no cambian)")
    public ResolucionResponse actualizarResolucion(@PathVariable Long id, @Valid @RequestBody ResolucionRequest r) {
        return numeracion.actualizar(id, r);
    }
}
