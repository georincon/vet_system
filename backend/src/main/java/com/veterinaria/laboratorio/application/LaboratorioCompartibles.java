package com.veterinaria.laboratorio.application;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.veterinaria.laboratorio.application.InformePdfService.Informe;
import com.veterinaria.laboratorio.domain.TipoExamen;
import com.veterinaria.shared.documentos.DocumentoCompartible;

/**
 * Informes de laboratorio, imagen y patologia como documentos compartibles con el tutor. El {@code id} es el de la orden;
 * {@code itemId} opcional limita a un examen. Solo examenes VALIDADOS (un borrador/preliminar da 422).
 */
@Configuration
class LaboratorioCompartibles {

    private final InformePdfService informes;
    private final OrdenService ordenes;

    LaboratorioCompartibles(InformePdfService informes, OrdenService ordenes) {
        this.informes = informes;
        this.ordenes = ordenes;
    }

    private DocumentoCompartible de(String tipoDocumento, TipoExamen tipo, String prefijo) {
        return new DocumentoCompartible() {
            @Override
            public String tipo() {
                return tipoDocumento;
            }

            @Override
            public Generado generar(Long id, Long itemId) {
                Informe i = informes.generar(id, itemId, tipo, true);
                Long paciente = ordenes.obtener(id).pacienteId();
                return new Generado(paciente, prefijo + i.codigo(), "informe-" + i.codigo() + ".pdf", i.pdf());
            }
        };
    }

    @Bean
    DocumentoCompartible laboratorioCompartible() {
        return de("LABORATORIO", TipoExamen.RESULTADOS, "Informe de laboratorio ");
    }

    @Bean
    DocumentoCompartible imagenCompartible() {
        return de("IMAGEN", TipoExamen.IMAGEN, "Informe de imagenologia ");
    }

    @Bean
    DocumentoCompartible patologiaCompartible() {
        return de("PATOLOGIA", TipoExamen.PATOLOGIA, "Informe de patologia ");
    }
}
