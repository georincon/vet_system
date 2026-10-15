package com.veterinaria.laboratorio.application;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.laboratorio.api.dto.LabDtos.CapturaRequest;
import com.veterinaria.laboratorio.api.dto.LabDtos.CorreccionRequest;
import com.veterinaria.laboratorio.api.dto.LabDtos.InformeImagenResponse;
import com.veterinaria.laboratorio.api.dto.LabDtos.InformePatologiaResponse;
import com.veterinaria.laboratorio.api.dto.LabDtos.ItemResponse;
import com.veterinaria.laboratorio.api.dto.LabDtos.ResultadoEntrada;
import com.veterinaria.laboratorio.api.dto.LabDtos.ResultadoResponse;
import com.veterinaria.laboratorio.api.dto.LabDtos.ValidacionRequest;
import com.veterinaria.laboratorio.domain.Banderas;
import com.veterinaria.laboratorio.domain.Evaluado;
import com.veterinaria.laboratorio.domain.ResultadoInvalidoException;
import com.veterinaria.laboratorio.domain.TipoExamen;
import com.veterinaria.laboratorio.domain.Valores;
import com.veterinaria.laboratorio.internal.CatalogoRepository;
import com.veterinaria.laboratorio.internal.CatalogoRepository.ParametroInfo;
import com.veterinaria.laboratorio.internal.InformeRepository;
import com.veterinaria.laboratorio.internal.OrdenRepository;
import com.veterinaria.laboratorio.internal.OrdenRepository.ItemRow;
import com.veterinaria.laboratorio.internal.OrdenRepository.OrdenRow;
import com.veterinaria.laboratorio.internal.ResultadoRepository;
import com.veterinaria.shared.clinico.PacienteBasico;
import com.veterinaria.shared.clinico.PacienteLector;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.error.ConflictException;
import com.veterinaria.shared.error.ResourceNotFoundException;
import com.veterinaria.shared.error.UnauthorizedException;
import com.veterinaria.shared.security.UsuarioActual;
import com.veterinaria.shared.text.Textos;
import com.veterinaria.shared.time.ZonaClinica;

/**
 * Captura de resultados con bandera automatica (rango por especie/sexo/edad, snapshot del rango aplicado), validacion
 * por el veterinario (inmutable) y correccion enlazada append-only: el original pasa a CORREGIDO y se conserva; el
 * nuevo registro queda VALIDADO apuntando al anterior ({@code reemplaza_a}) con el motivo.
 *
 * <p>Consulta cerrada: la orden puede vincularse a cualquier consulta del paciente; al validar o corregir con la
 * consulta ya CERRADA se agrega un addendum (RESULTADO_TARDIO / CORRECCION) sin tocar la consulta inmutable.
 */
@Service
public class ResultadoService {

    private static final int MOTIVO_MIN = 5;

    private final OrdenRepository ordenes;
    private final CatalogoRepository catalogo;
    private final ResultadoRepository resultados;
    private final InformeRepository informes;
    private final PacienteLector pacientes;
    private final OrdenService ordenService;

    public ResultadoService(OrdenRepository ordenes, CatalogoRepository catalogo, ResultadoRepository resultados,
            InformeRepository informes, PacienteLector pacientes, OrdenService ordenService) {
        this.ordenes = ordenes;
        this.catalogo = catalogo;
        this.resultados = resultados;
        this.informes = informes;
        this.pacientes = pacientes;
        this.ordenService = ordenService;
    }

    // ------------------------------------------------------------------ captura

    @Transactional
    public ItemResponse capturar(Long ordenId, Long itemId, CapturaRequest r) {
        ordenes.bloquear(ordenId);
        OrdenRow orden = ordenService.orden(ordenId);
        ItemRow item = item(ordenId, itemId);
        exigirAbierto(orden, item, "capturar resultados");
        exigirTipo(item, TipoExamen.RESULTADOS, "Este examen se informa con el informe de imagen o de patologia");
        exigirMuestra(orden);
        PacienteBasico paciente = pacientes.obtener(orden.pacienteId());
        Long usuario = UsuarioActual.id().orElse(null);

        Set<Long> vistos = new HashSet<>();
        for (ResultadoEntrada e : r.resultados()) {
            ParametroInfo p = resolverParametro(item, e);
            if (!vistos.add(p.id())) {
                throw new BusinessException("El parametro " + p.codigo() + " viene repetido en la solicitud");
            }
            Evaluado ev = evaluar(paciente, p, e.valorNumerico(), e.valorTexto(), e.unidad());
            guardarBorrador(item, p, ev, "MANUAL", usuario, Textos.limpiar(e.observacion()), null);
        }
        ordenes.marcarCapturado(itemId, usuario);
        ordenService.iniciarProceso(orden, usuario, "MANUAL");
        ordenes.evento(ordenId, itemId, "CAPTURA", null, null, vistos.size() + " resultado(s) capturado(s) en " + item.examenCodigo(),
                usuario, "MANUAL");
        return ordenService.itemResponse(ordenId, itemId);
    }

    /** Inserta o reescribe el borrador vigente del parametro (usado por la captura manual y por la importacion). */
    void guardarBorrador(ItemRow item, ParametroInfo p, Evaluado ev, String origen, Long usuario, String observacion,
            Long importacionId) {
        var existente = resultados.vigente(item.id(), p.id());
        if (existente.isPresent()) {
            if (!"BORRADOR".equals(existente.get().estado())) {
                throw new ConflictException("El parametro " + p.codigo() + " ya esta validado; use una correccion");
            }
            resultados.actualizarBorrador(existente.get().id(), ev, origen, usuario, observacion, importacionId);
        } else {
            resultados.insertar(item.id(), p.id(), ev, "BORRADOR", origen, usuario, observacion, null, null, null, importacionId);
        }
    }

    /**
     * Normaliza el valor segun el tipo del parametro, valida la unidad y calcula la bandera contra el rango aplicable
     * (el mas especifico por especie, sexo y edad del paciente), dejando el snapshot del rango usado.
     */
    public Evaluado evaluar(PacienteBasico paciente, ParametroInfo p, BigDecimal numero, String texto, String unidad) {
        String unidadIn = Textos.limpiar(unidad);
        if (unidadIn != null && p.unidad() != null && !Valores.mismaUnidad(unidadIn, p.unidad())) {
            throw new ResultadoInvalidoException("UNIDAD_NO_COINCIDE",
                    "La unidad '" + unidadIn + "' no coincide con la del parametro " + p.codigo() + " (" + p.unidad() + ")");
        }
        String unidadFinal = p.unidad() != null ? p.unidad() : unidadIn;
        String txt = Textos.limpiar(texto);
        switch (p.tipoValor()) {
            case "NUMERICO" -> {
                BigDecimal v = numero != null ? numero : Valores.numero(txt).orElse(null);
                if (v == null) {
                    throw new ResultadoInvalidoException("VALOR_INVALIDO", "El parametro " + p.codigo()
                            + " requiere un valor numerico" + (txt == null ? "" : " (recibido: '" + txt + "')"));
                }
                Integer edad = paciente.edadMeses(ZonaClinica.hoy());
                var rango = Banderas.elegir(catalogo.rangos(p.id(), paciente.especieId()), paciente.sexo(), edad).orElse(null);
                var eval = Banderas.numerica(v, rango);
                String comparador = numero == null && txt != null && txt.matches("^[<>].*") ? txt : null;
                return new Evaluado(v, comparador, unidadFinal, rango == null ? null : rango.id(),
                        rango == null ? null : rango.min(), rango == null ? null : rango.max(),
                        rango == null ? null : rango.criticoMin(), rango == null ? null : rango.criticoMax(),
                        eval.bandera(), eval.critico());
            }
            case "TEXTO" -> {
                if (txt == null) {
                    throw new ResultadoInvalidoException("VALOR_INVALIDO", "El parametro " + p.codigo() + " requiere un valor de texto");
                }
                return new Evaluado(null, txt, unidadFinal, null, null, null, null, null, null, false);
            }
            default -> {
                if (txt == null) {
                    throw new ResultadoInvalidoException("VALOR_INVALIDO", "El parametro " + p.codigo() + " requiere un resultado cualitativo");
                }
                String canonico = Valores.cualitativo(txt, p.opciones());
                if (!Valores.permitido(canonico, p.opciones())) {
                    throw new ResultadoInvalidoException("VALOR_INVALIDO", "Valor '" + txt + "' no permitido para " + p.codigo()
                            + ". Opciones: " + p.opciones().replace("|", ", "));
                }
                var eval = Banderas.cualitativa(canonico, p.valorNormalTexto());
                return new Evaluado(null, canonico, unidadFinal, null, null, null, null, null, eval.bandera(), false);
            }
        }
    }

    // ------------------------------------------------------------------ validacion

    @Transactional
    public ItemResponse validar(Long ordenId, Long itemId, ValidacionRequest r) {
        ordenes.bloquear(ordenId);
        OrdenRow orden = ordenService.orden(ordenId);
        ItemRow item = item(ordenId, itemId);
        if ("VALIDADO".equals(item.estado())) {
            throw new ConflictException("El examen " + item.examenCodigo() + " ya esta validado; use una correccion");
        }
        exigirAbierto(orden, item, "validar");
        Long usuario = UsuarioActual.id().orElseThrow(() -> new UnauthorizedException("Sesion sin identificador de usuario."));
        TipoExamen kind = TipoExamen.de(item.tipo());
        String interpretacion = r == null ? null : Textos.limpiar(r.interpretacion());
        switch (kind) {
            case RESULTADOS -> {
                if (resultados.contarBorradores(itemId) == 0) {
                    throw new BusinessException("No hay resultados capturados para validar en " + item.examenCodigo());
                }
                if (interpretacion == null && resultados.contarCriticosBorrador(itemId) > 0) {
                    throw new BusinessException("Hay valores criticos: la interpretacion del veterinario es obligatoria");
                }
            }
            case IMAGEN -> {
                InformeImagenResponse inf = informes.imagenVigente(itemId).filter(i -> "BORRADOR".equals(i.estado()))
                        .orElseThrow(() -> new BusinessException("Registre el informe de imagen antes de validar"));
                if (Textos.limpiar(inf.hallazgos()) == null || Textos.limpiar(inf.impresionDiagnostica()) == null) {
                    throw new BusinessException("El informe de imagen requiere hallazgos e impresion diagnostica para validarse");
                }
                if (interpretacion == null) {
                    interpretacion = inf.impresionDiagnostica();
                }
            }
            case PATOLOGIA -> {
                InformePatologiaResponse inf = informes.patologiaVigente(itemId).filter(i -> "BORRADOR".equals(i.estado()))
                        .orElseThrow(() -> new BusinessException("Registre el informe de patologia antes de validar"));
                if (Textos.limpiar(inf.diagnosticoMorfologico()) == null) {
                    throw new BusinessException("El informe de patologia requiere el diagnostico morfologico para validarse");
                }
                if (interpretacion == null) {
                    interpretacion = inf.diagnosticoMorfologico();
                }
            }
        }
        int version = ordenes.nuevaVersion(itemId);
        long validacionId = ordenes.insertarValidacion(itemId, version, "VALIDACION", interpretacion, null, usuario);
        switch (kind) {
            case RESULTADOS -> resultados.validarBorradores(itemId, validacionId);
            case IMAGEN -> informes.imagenId(itemId, "BORRADOR").ifPresent(id -> informes.validarImagen(id, validacionId));
            case PATOLOGIA -> informes.patologiaId(itemId, "BORRADOR").ifPresent(id -> informes.validarPatologia(id, validacionId));
        }
        ordenes.marcarValidado(itemId, version, interpretacion, usuario);
        ordenes.evento(ordenId, itemId, "VALIDACION", item.estado(), "VALIDADO", item.examenCodigo() + " v" + version, usuario, "MANUAL");
        addendumSiConsultaCerrada(orden, item, "RESULTADO_TARDIO", "Resultado tardio", usuario, interpretacion);
        ordenService.completarSiCorresponde(ordenId, usuario);
        return ordenService.itemResponse(ordenId, itemId);
    }

    // ------------------------------------------------------------------ correccion enlazada

    @Transactional
    public ItemResponse corregir(Long ordenId, Long itemId, CorreccionRequest r) {
        ordenes.bloquear(ordenId);
        OrdenRow orden = ordenService.orden(ordenId);
        ItemRow item = item(ordenId, itemId);
        if (!"VALIDADO".equals(item.estado())) {
            throw new ConflictException("Solo se corrigen examenes VALIDADOS; el examen esta " + item.estado()
                    + " (en ese estado se edita directamente)");
        }
        if ("CANCELADA".equals(orden.estado())) {
            throw new ConflictException("La orden esta CANCELADA");
        }
        String motivo = Textos.limpiar(r.motivo());
        if (motivo == null || motivo.length() < MOTIVO_MIN) {
            throw new BusinessException("El motivo de la correccion es obligatorio (minimo " + MOTIVO_MIN + " caracteres)");
        }
        Long usuario = UsuarioActual.id().orElseThrow(() -> new UnauthorizedException("Sesion sin identificador de usuario."));
        TipoExamen kind = TipoExamen.de(item.tipo());
        boolean cambia = switch (kind) {
            case RESULTADOS -> r.resultados() != null && !r.resultados().isEmpty();
            case IMAGEN -> r.informeImagen() != null;
            case PATOLOGIA -> r.informePatologia() != null;
        };
        String nuevaInterpretacion = Textos.limpiar(r.interpretacion());
        if (!cambia && nuevaInterpretacion == null) {
            throw new BusinessException("La correccion debe incluir nuevos resultados/informe o una nueva interpretacion");
        }
        String interpretacion = nuevaInterpretacion != null ? nuevaInterpretacion : item.interpretacion();
        int version = ordenes.nuevaVersion(itemId);
        long validacionId = ordenes.insertarValidacion(itemId, version, "CORRECCION", interpretacion, motivo, usuario);

        switch (kind) {
            case RESULTADOS -> corregirResultados(orden, item, r, motivo, validacionId, usuario);
            case IMAGEN -> {
                if (r.informeImagen() != null) {
                    var viejo = informes.imagenVigente(itemId).orElseThrow(() -> new ConflictException("No hay informe de imagen que corregir"));
                    Long informadoPor = r.informeImagen().informadoPorId() != null ? r.informeImagen().informadoPorId() : usuario;
                    informes.corregirImagen(viejo.id());
                    long nuevo = informes.insertarImagen(itemId, r.informeImagen(), "VALIDADO", viejo.version() + 1, informadoPor,
                            usuario, viejo.id(), motivo, validacionId);
                    informes.enlazarImagen(viejo.id(), nuevo);
                }
            }
            case PATOLOGIA -> {
                if (r.informePatologia() != null) {
                    var viejo = informes.patologiaVigente(itemId).orElseThrow(() -> new ConflictException("No hay informe de patologia que corregir"));
                    Long informadoPor = r.informePatologia().informadoPorId() != null ? r.informePatologia().informadoPorId() : usuario;
                    informes.corregirPatologia(viejo.id());
                    long nuevo = informes.insertarPatologia(itemId, r.informePatologia(), "VALIDADO", viejo.version() + 1, informadoPor,
                            usuario, viejo.id(), motivo, validacionId);
                    informes.enlazarPatologia(viejo.id(), nuevo);
                }
            }
        }
        ordenes.marcarValidado(itemId, version, interpretacion, usuario);
        ordenes.evento(ordenId, itemId, "CORRECCION", "VALIDADO", "VALIDADO",
                item.examenCodigo() + " v" + version + ": " + motivo, usuario, "MANUAL");
        addendumSiConsultaCerrada(orden, item, "CORRECCION", "Correccion de resultado (" + motivo + ")", usuario, interpretacion);
        return ordenService.itemResponse(ordenId, itemId);
    }

    private void corregirResultados(OrdenRow orden, ItemRow item, CorreccionRequest r, String motivo, long validacionId, Long usuario) {
        if (r.resultados() == null) {
            return;
        }
        PacienteBasico paciente = pacientes.obtener(orden.pacienteId());
        Set<Long> vistos = new HashSet<>();
        for (ResultadoEntrada e : r.resultados()) {
            ParametroInfo p = resolverParametro(item, e);
            if (!vistos.add(p.id())) {
                throw new BusinessException("El parametro " + p.codigo() + " viene repetido en la solicitud");
            }
            Evaluado ev = evaluar(paciente, p, e.valorNumerico(), e.valorTexto(), e.unidad());
            var vigente = resultados.vigente(item.id(), p.id());
            if (vigente.isPresent()) {
                resultados.marcarCorregido(vigente.get().id());
                long nuevo = resultados.insertar(item.id(), p.id(), ev, "VALIDADO", "MANUAL", usuario,
                        Textos.limpiar(e.observacion()), validacionId, vigente.get().id(), motivo, null);
                resultados.enlazarReemplazo(vigente.get().id(), nuevo);
            } else {
                resultados.insertar(item.id(), p.id(), ev, "VALIDADO", "MANUAL", usuario, Textos.limpiar(e.observacion()),
                        validacionId, null, motivo, null);
            }
        }
    }

    // ------------------------------------------------------------------ utilidades

    ItemRow item(Long ordenId, Long itemId) {
        ordenService.orden(ordenId);
        return ordenes.item(ordenId, itemId).orElseThrow(() -> new ResourceNotFoundException("Item de orden", itemId));
    }

    ParametroInfo resolverParametro(ItemRow item, ResultadoEntrada e) {
        if (e.parametroId() == null && Textos.limpiar(e.parametroCodigo()) == null) {
            throw new BusinessException("Cada resultado requiere parametroId o parametroCodigo");
        }
        ParametroInfo p;
        if (e.parametroId() != null) {
            p = catalogo.parametroInfo(e.parametroId()).orElseThrow(() -> new BusinessException("El parametro " + e.parametroId() + " no existe"));
        } else {
            Long id = catalogo.parametroDeExamenPorCodigo(item.examenId(), e.parametroCodigo().trim())
                    .orElseThrow(() -> new BusinessException("El parametro " + e.parametroCodigo() + " no pertenece a " + item.examenCodigo()));
            p = catalogo.parametroInfo(id).orElseThrow();
        }
        if (!p.examenId().equals(item.examenId())) {
            throw new BusinessException("El parametro " + p.codigo() + " no pertenece a " + item.examenCodigo());
        }
        if (!p.activo()) {
            throw new BusinessException("El parametro " + p.codigo() + " esta inactivo");
        }
        return p;
    }

    static void exigirAbierto(OrdenRow orden, ItemRow item, String accion) {
        if ("CANCELADA".equals(orden.estado()) || "CANCELADO".equals(item.estado())) {
            throw new ConflictException("No se puede " + accion + ": la orden o el examen estan cancelados");
        }
        if ("COMPLETADA".equals(orden.estado()) && !"VALIDADO".equals(item.estado())) {
            throw new ConflictException("La orden esta COMPLETADA");
        }
        if ("VALIDADO".equals(item.estado()) && !accion.equals("validar")) {
            throw new ConflictException("El examen " + item.examenCodigo() + " ya esta VALIDADO y es inmutable; use una correccion enlazada");
        }
    }

    static void exigirTipo(ItemRow item, TipoExamen esperado, String mensaje) {
        if (TipoExamen.de(item.tipo()) != esperado) {
            throw new BusinessException(mensaje);
        }
    }

    static void exigirMuestra(OrdenRow orden) {
        if (!"MUESTRA_TOMADA".equals(orden.estado()) && !"EN_PROCESO".equals(orden.estado())) {
            throw new ConflictException("La orden " + orden.codigo() + " esta " + orden.estado()
                    + ": registre primero la toma de muestra");
        }
    }

    /** Si la consulta vinculada ya esta CERRADA deja constancia en un addendum (la consulta no se modifica). */
    private void addendumSiConsultaCerrada(OrdenRow orden, ItemRow item, String tipo, String prefijo, Long autor, String interpretacion) {
        if (orden.consultaId() == null) {
            return;
        }
        var consulta = ordenes.consulta(orden.consultaId());
        if (consulta.isEmpty() || !"CERRADA".equals(consulta.get().estado())) {
            return;
        }
        String resumen = resultados.porItem(item.id()).stream()
                .filter(x -> !"CORREGIDO".equals(x.estado()) && x.bandera() != null && !"N".equals(x.bandera()))
                .map(x -> x.parametroCodigo() + " " + (x.valorNumerico() != null ? x.valorNumerico().stripTrailingZeros().toPlainString() : x.valorTexto())
                        + " (" + x.bandera() + ")")
                .collect(Collectors.joining(", "));
        String texto = prefijo + ": " + item.examenNombre() + " (orden " + orden.codigo() + ")."
                + (resumen.isEmpty() ? "" : " Fuera de rango: " + resumen + ".")
                + (interpretacion == null ? "" : " Interpretacion: " + interpretacion)
                + " Ver detalle en la orden de laboratorio.";
        ordenes.addendum(orden.consultaId(), autor, tipo, texto);
    }

    List<ResultadoResponse> vigentes(Long itemId) {
        return resultados.porItem(itemId).stream().filter(x -> !"CORREGIDO".equals(x.estado())).toList();
    }
}
