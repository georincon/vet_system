package com.veterinaria.facturacion.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.veterinaria.facturacion.api.dto.FacturacionDtos.LineaRequest;
import com.veterinaria.facturacion.domain.Importes;
import com.veterinaria.inventario.CatalogoApi;
import com.veterinaria.inventario.CatalogoApi.ItemVenta;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.security.UsuarioActual;
import com.veterinaria.shared.sede.SedePrincipal;
import com.veterinaria.shared.text.Textos;

/**
 * Convierte renglones de la solicitud en renglones valorizados. Con itemId el precio vigente (por sede) y el IVA salen
 * del catalogo; sobrescribirlos o facturar renglones libres (sin item) es privilegio de ADMIN.
 */
@Component
class LineaResolver {

    private final CatalogoApi catalogo;
    private final SedePrincipal sede;

    LineaResolver(CatalogoApi catalogo, SedePrincipal sede) {
        this.catalogo = catalogo;
        this.sede = sede;
    }

    List<LineaResuelta> resolver(List<LineaRequest> items, Long pacienteDefecto, Long consultaDefecto) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("Se necesita al menos un renglon");
        }
        boolean admin = "ADMIN".equals(UsuarioActual.rol().orElse(""));
        List<LineaResuelta> res = new ArrayList<>();
        for (LineaRequest l : items) {
            res.add(resolverUna(l, pacienteDefecto, consultaDefecto, admin));
        }
        return res;
    }

    private LineaResuelta resolverUna(LineaRequest l, Long pacienteDefecto, Long consultaDefecto, boolean admin) {
        String descripcion = Textos.limpiar(l.descripcion());
        BigDecimal precio;
        BigDecimal pct;
        boolean controla = false;
        String codigo = null;
        Long itemId = l.itemId();
        if (itemId != null) {
            ItemVenta item = catalogo.item(itemId)
                    .orElseThrow(() -> new BusinessException("El item " + itemId + " no existe"));
            if (!item.activo()) {
                throw new BusinessException("El item " + item.nombre() + " esta inactivo");
            }
            BigDecimal catalogoPrecio = catalogo.precioVigente(itemId, sede.id());
            precio = l.precioUnitario() != null ? l.precioUnitario() : catalogoPrecio;
            pct = l.impuestoPct() != null ? l.impuestoPct() : item.impuestoPct();
            if (!admin && (precio.compareTo(catalogoPrecio) != 0 || pct.compareTo(item.impuestoPct()) != 0)) {
                throw new BusinessException("Solo un ADMIN puede modificar el precio o el IVA de " + item.nombre()
                        + " (catalogo: " + catalogoPrecio + ")");
            }
            if (descripcion == null) {
                descripcion = item.nombre();
            }
            controla = item.controlaStock();
            codigo = item.codigo();
        } else {
            if (!admin) {
                throw new BusinessException("Los renglones libres (sin itemId) solo los registra un ADMIN");
            }
            if (descripcion == null || l.precioUnitario() == null) {
                throw new BusinessException("Un renglon libre exige descripcion y precioUnitario");
            }
            precio = l.precioUnitario();
            pct = l.impuestoPct() == null ? BigDecimal.ZERO : l.impuestoPct();
        }
        boolean descontar = controla && (l.descontarStock() == null || l.descontarStock());
        if (controla && !descontar && !admin) {
            throw new BusinessException("Solo un ADMIN puede facturar sin descontar stock");
        }
        Importes.Linea calc = Importes.calcular(l.cantidad(), precio, l.descuento(), pct);
        return new LineaResuelta(itemId, codigo, descripcion, l.cantidad(), precio, pct, calc.descuento(), calc,
                l.pacienteId() != null ? l.pacienteId() : pacienteDefecto, l.veterinarioId(),
                l.consultaId() != null ? l.consultaId() : consultaDefecto, controla, descontar, null, null);
    }
}
