package com.veterinaria.facturacion.internal;

import org.springframework.stereotype.Component;

import com.veterinaria.facturacion.domain.FacturacionElectronicaPort;

/** Adaptador no-op (el adaptador real de la Fase 5 se marca {@code @Primary}): deja el documento en PENDIENTE (sin CUFE) hasta que exista integracion real (Fase 5). */
@Component
class FacturacionElectronicaSimulada implements FacturacionElectronicaPort {

    private static final String MENSAJE = "Proveedor de factura electronica no configurado (adaptador simulado): documento pendiente.";

    @Override
    public Resultado emitir(Documento documento) {
        return new Resultado("PENDIENTE", null, MENSAJE);
    }

    @Override
    public Resultado consultar(Long facturaId, String numero) {
        return new Resultado("PENDIENTE", null, MENSAJE);
    }
}
