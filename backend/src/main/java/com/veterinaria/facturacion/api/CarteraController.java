package com.veterinaria.facturacion.api;

import java.time.LocalDate;

import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.veterinaria.facturacion.api.dto.FacturacionDtos.AbonoRequest;
import com.veterinaria.facturacion.api.dto.FacturacionDtos.AbonoResponse;
import com.veterinaria.facturacion.api.dto.ReporteDtos.CarteraDetalle;
import com.veterinaria.facturacion.api.dto.ReporteDtos.CarteraTutor;
import com.veterinaria.facturacion.api.dto.ReporteDtos.ReporteCartera;
import com.veterinaria.facturacion.api.dto.ReporteDtos.ReporteVentas;
import com.veterinaria.facturacion.application.CarteraService;
import com.veterinaria.facturacion.application.ReporteService;
import com.veterinaria.shared.web.PageRequests;
import com.veterinaria.shared.web.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/facturacion")
@Tag(name = "Facturacion - cartera y reportes", description = "Cartera por tutor, abonos y reportes (ventas: solo ADMIN)")
public class CarteraController {

    private final CarteraService cartera;
    private final ReporteService reportes;

    public CarteraController(CarteraService cartera, ReporteService reportes) {
        this.cartera = cartera;
        this.reportes = reportes;
    }

    @GetMapping("/cartera")
    @Operation(summary = "Saldo por tutor (soloVencidas=true filtra los que tienen mora)")
    public PageResponse<CarteraTutor> cartera(@RequestParam(required = false) Boolean soloVencidas,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return cartera.porTutor(soloVencidas, PageRequests.of(page, size, Sort.unsorted()));
    }

    @GetMapping("/cartera/tutores/{tutorId}")
    public CarteraDetalle detalle(@PathVariable Long tutorId) {
        return cartera.detalleTutor(tutorId);
    }

    @PostMapping("/cartera/tutores/{tutorId}/abonos")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Abono (pagos mixtos) aplicado a las facturas con saldo mas antiguas; requiere turno abierto")
    public AbonoResponse abonar(@PathVariable Long tutorId, @Valid @RequestBody AbonoRequest r) {
        return cartera.abonar(tutorId, r);
    }

    @GetMapping("/reportes/cartera")
    @Operation(summary = "Cartera por edades de mora y principales tutores (ADMIN, RECEPCION)")
    public ReporteCartera reporteCartera() {
        return cartera.reporte();
    }

    @GetMapping("/reportes/ventas")
    @Operation(summary = "Ventas netas por dia, metodo de pago e item en [desde, hasta] (ADMIN)")
    public ReporteVentas ventas(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return reportes.ventas(desde, hasta);
    }
}
