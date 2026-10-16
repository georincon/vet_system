package com.veterinaria.inmunizacion.application;

import org.springframework.stereotype.Component;

import com.veterinaria.shared.documentos.DocumentoCompartible;

/** Carnet de vacunacion/desparasitacion como documento compartible. El {@code id} es el del paciente. */
@Component
class CarnetCompartible implements DocumentoCompartible {

    private final CarnetService carnets;

    CarnetCompartible(CarnetService carnets) {
        this.carnets = carnets;
    }

    @Override
    public String tipo() {
        return "CARNET";
    }

    @Override
    public Generado generar(Long id, Long itemId) {
        CarnetService.Carnet c = carnets.generar(id);
        return new Generado(id, "Carnet de vacunacion " + c.numero(), c.numero() + ".pdf", c.pdf());
    }
}
