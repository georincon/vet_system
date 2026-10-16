package com.veterinaria.clinica.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.clinica.application.FormulaService.Formula;
import com.veterinaria.shared.documentos.DocumentoCompartible;
import com.veterinaria.shared.error.BusinessException;

/** Documentos de clinica que se pueden compartir con el tutor (FORMULA y RESUMEN_CONSULTA); solo de consultas CERRADAS. */
@Component
class ClinicaCompartibles {

    @Component
    static class FormulaCompartible implements DocumentoCompartible {

        private final FormulaService formulas;

        FormulaCompartible(FormulaService formulas) {
            this.formulas = formulas;
        }

        @Override
        public String tipo() {
            return "FORMULA";
        }

        @Override
        @Transactional
        public Generado generar(Long id, Long itemId) {
            Formula f = formulas.porReceta(id);
            if (f.anulada()) {
                throw new BusinessException("La formula " + f.numero() + " esta ANULADA y no se puede compartir");
            }
            if (!f.consultaCerrada()) {
                throw new BusinessException("La formula " + f.numero() + " pertenece a una consulta aun ABIERTA; cierre la consulta para compartirla");
            }
            return new Generado(f.pacienteId(), "Formula medica " + f.numero(), f.numero() + ".pdf", f.pdf());
        }
    }

    @Component
    static class ResumenCompartible implements DocumentoCompartible {

        private final ResumenConsultaService resumenes;

        ResumenCompartible(ResumenConsultaService resumenes) {
            this.resumenes = resumenes;
        }

        @Override
        public String tipo() {
            return "RESUMEN_CONSULTA";
        }

        @Override
        @Transactional(readOnly = true)
        public Generado generar(Long id, Long itemId) {
            var r = resumenes.generar(id);
            return new Generado(r.pacienteId(), "Resumen de consulta " + r.codigo(), "resumen-" + r.codigo() + ".pdf", r.pdf());
        }
    }
}
