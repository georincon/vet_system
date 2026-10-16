package com.veterinaria.procedimientos.application;

import org.springframework.stereotype.Component;

import com.veterinaria.shared.documentos.DocumentoCompartible;
import com.veterinaria.shared.error.BusinessException;

/** Reporte de procedimiento/cirugia como documento compartible; solo si el procedimiento esta REALIZADO. */
@Component
class ProcedimientoCompartible implements DocumentoCompartible {

    private final ProcedimientoService procedimientos;
    private final ReporteQuirurgicoService reportes;

    ProcedimientoCompartible(ProcedimientoService procedimientos, ReporteQuirurgicoService reportes) {
        this.procedimientos = procedimientos;
        this.reportes = reportes;
    }

    @Override
    public String tipo() {
        return "PROCEDIMIENTO";
    }

    @Override
    public Generado generar(Long id, Long itemId) {
        var p = procedimientos.obtener(id);
        if (!"REALIZADA".equals(p.estado())) {
            throw new BusinessException("El procedimiento " + p.codigo() + " esta " + p.estado()
                    + "; solo se comparte el reporte de procedimientos REALIZADOS");
        }
        var r = reportes.generar(id);
        return new Generado(p.pacienteId(), (p.tipo().equals("CIRUGIA") ? "Reporte quirurgico " : "Reporte de procedimiento ")
                + r.codigo(), "reporte-" + r.codigo() + ".pdf", r.pdf());
    }
}
