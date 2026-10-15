package com.veterinaria.laboratorio.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.laboratorio.api.dto.LabDtos.InformeImagenRequest;
import com.veterinaria.laboratorio.api.dto.LabDtos.InformePatologiaRequest;
import com.veterinaria.laboratorio.api.dto.LabDtos.ItemResponse;
import com.veterinaria.laboratorio.domain.TipoExamen;
import com.veterinaria.laboratorio.internal.InformeRepository;
import com.veterinaria.laboratorio.internal.OrdenRepository;
import com.veterinaria.laboratorio.internal.OrdenRepository.ItemRow;
import com.veterinaria.laboratorio.internal.OrdenRepository.OrdenRow;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.error.UnauthorizedException;
import com.veterinaria.shared.security.UsuarioActual;

/**
 * Informes estructurados de imagen/ecografia y de patologia/citologia (borrador editable hasta validar).
 * Las imagenes y PDF se asocian como archivos del item ({@code ordenItemId}).
 */
@Service
public class InformeItemService {

    private final OrdenRepository ordenes;
    private final InformeRepository informes;
    private final OrdenService ordenService;
    private final ResultadoService resultadoService;

    public InformeItemService(OrdenRepository ordenes, InformeRepository informes, OrdenService ordenService,
            ResultadoService resultadoService) {
        this.ordenes = ordenes;
        this.informes = informes;
        this.ordenService = ordenService;
        this.resultadoService = resultadoService;
    }

    @Transactional
    public ItemResponse guardarImagen(Long ordenId, Long itemId, InformeImagenRequest r) {
        ordenes.bloquear(ordenId);
        OrdenRow orden = ordenService.orden(ordenId);
        ItemRow item = resultadoService.item(ordenId, itemId);
        ResultadoService.exigirAbierto(orden, item, "informar");
        ResultadoService.exigirTipo(item, TipoExamen.IMAGEN, "Este examen no es de imagen: use resultados o el informe de patologia");
        Long usuario = UsuarioActual.id().orElseThrow(() -> new UnauthorizedException("Sesion sin identificador de usuario."));
        Long informadoPor = informante(r.informadoPorId(), usuario);
        var borrador = informes.imagenId(itemId, "BORRADOR");
        if (borrador.isPresent()) {
            informes.actualizarImagenBorrador(borrador.get(), r, informadoPor);
        } else {
            informes.insertarImagen(itemId, r, "BORRADOR", 1, informadoPor, usuario, null, null, null);
        }
        registrar(orden, item, usuario, "Informe de imagen guardado");
        return ordenService.itemResponse(ordenId, itemId);
    }

    @Transactional
    public ItemResponse guardarPatologia(Long ordenId, Long itemId, InformePatologiaRequest r) {
        ordenes.bloquear(ordenId);
        OrdenRow orden = ordenService.orden(ordenId);
        ItemRow item = resultadoService.item(ordenId, itemId);
        ResultadoService.exigirAbierto(orden, item, "informar");
        ResultadoService.exigirTipo(item, TipoExamen.PATOLOGIA, "Este examen no es de patologia/citologia");
        ResultadoService.exigirMuestra(orden);
        Long usuario = UsuarioActual.id().orElseThrow(() -> new UnauthorizedException("Sesion sin identificador de usuario."));
        Long informadoPor = informante(r.informadoPorId(), usuario);
        var borrador = informes.patologiaId(itemId, "BORRADOR");
        if (borrador.isPresent()) {
            informes.actualizarPatologiaBorrador(borrador.get(), r, informadoPor);
        } else {
            informes.insertarPatologia(itemId, r, "BORRADOR", 1, informadoPor, usuario, null, null, null);
        }
        registrar(orden, item, usuario, "Informe de patologia guardado");
        return ordenService.itemResponse(ordenId, itemId);
    }

    private void registrar(OrdenRow orden, ItemRow item, Long usuario, String detalle) {
        ordenes.marcarCapturado(item.id(), usuario);
        ordenService.iniciarProceso(orden, usuario, "MANUAL");
        ordenes.evento(orden.id(), item.id(), "INFORME", null, null, detalle + " (" + item.examenCodigo() + ")", usuario, "MANUAL");
    }

    /** Quien firma el informe: el indicado (debe ser VETERINARIO o ADMIN activo) o el usuario actual. */
    private Long informante(Long informadoPorId, Long usuarioActual) {
        if (informadoPorId == null || informadoPorId.equals(usuarioActual)) {
            return usuarioActual;
        }
        return ordenes.usuarioActivoConRol(informadoPorId, List.of("VETERINARIO", "ADMIN"))
                .orElseThrow(() -> new BusinessException("informadoPorId debe ser un veterinario o administrador activo"));
    }
}
