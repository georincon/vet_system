package com.veterinaria.dashboard.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.veterinaria.dashboard.api.dto.DashboardResumen;
import com.veterinaria.dashboard.application.DashboardService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "Resumen operativo")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/resumen")
    @Operation(summary = "Indicadores del dia y proximas citas (cualquier rol autenticado)")
    public DashboardResumen resumen() {
        return service.resumen();
    }
}
