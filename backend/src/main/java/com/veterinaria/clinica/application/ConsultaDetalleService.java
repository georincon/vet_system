package com.veterinaria.clinica.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.clinica.api.dto.AddendumRequest;
import com.veterinaria.clinica.api.dto.AddendumResponse;
import com.veterinaria.clinica.api.dto.DiagnosticoRequest;
import com.veterinaria.clinica.api.dto.DiagnosticoResponse;
import com.veterinaria.clinica.api.dto.RecetaItemDto;
import com.veterinaria.clinica.api.dto.RecetaRequest;
import com.veterinaria.clinica.api.dto.RecetaResponse;
import com.veterinaria.clinica.api.dto.SignoVitalRequest;
import com.veterinaria.clinica.api.dto.SignoVitalResponse;
import com.veterinaria.clinica.domain.CatalogoDiagnostico;
import com.veterinaria.clinica.domain.Consulta;
import com.veterinaria.clinica.domain.ConsultaAddendum;
import com.veterinaria.clinica.domain.Diagnostico;
import com.veterinaria.clinica.domain.Receta;
import com.veterinaria.clinica.domain.RecetaItem;
import com.veterinaria.clinica.domain.SignoVital;
import com.veterinaria.clinica.internal.CatalogoDiagnosticoRepository;
import com.veterinaria.clinica.internal.ConsultaAddendumRepository;
import com.veterinaria.clinica.internal.ConsultaRepository;
import com.veterinaria.seguridad.internal.UsuarioRepository;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.error.ConflictException;
import com.veterinaria.shared.error.ResourceNotFoundException;
import com.veterinaria.shared.error.UnauthorizedException;
import com.veterinaria.shared.security.UsuarioActual;
import com.veterinaria.shared.text.Textos;
import com.veterinaria.shared.time.ZonaClinica;

/**
 * Contenido de la consulta: signos vitales, diagnosticos, recetas y addendums. Todo se bloquea (409) cuando la
 * consulta esta CERRADA, salvo los addendums, que solo se aceptan despues del cierre.
 */
@Service
public class ConsultaDetalleService {

    private final ConsultaRepository consultaRepository;
    private final ConsultaAddendumRepository addendumRepository;
    private final CatalogoDiagnosticoRepository catalogoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PesoService pesoService;

    public ConsultaDetalleService(ConsultaRepository consultaRepository, ConsultaAddendumRepository addendumRepository,
            CatalogoDiagnosticoRepository catalogoRepository, UsuarioRepository usuarioRepository,
            PesoService pesoService) {
        this.consultaRepository = consultaRepository;
        this.addendumRepository = addendumRepository;
        this.catalogoRepository = catalogoRepository;
        this.usuarioRepository = usuarioRepository;
        this.pesoService = pesoService;
    }

    // ------------------------------------------------------------------ signos vitales

    @Transactional
    public SignoVitalResponse agregarSigno(Long consultaId, SignoVitalRequest r) {
        Consulta consulta = obtenerAbierta(consultaId);
        SignoVital sv = new SignoVital();
        sv.setConsulta(consulta);
        sv.setRegistradoPor(UsuarioActual.id().orElse(null));
        aplicar(sv, r);
        if (sinValores(sv)) {
            throw new BusinessException("Registre al menos un signo vital");
        }
        consulta.getSignosVitales().add(sv);
        consultaRepository.flush();
        registrarPeso(consulta, sv);
        return SignoVitalResponse.from(sv);
    }

    @Transactional
    public SignoVitalResponse actualizarSigno(Long consultaId, Long signoId, SignoVitalRequest r) {
        Consulta consulta = obtenerAbierta(consultaId);
        SignoVital sv = consulta.getSignosVitales().stream().filter(s -> s.getId().equals(signoId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("SignoVital", signoId));
        var pesoAnterior = sv.getPesoKg();
        aplicar(sv, r);
        consultaRepository.flush();
        if (sv.getPesoKg() != null && sv.getPesoKg().compareTo(
                pesoAnterior == null ? java.math.BigDecimal.ZERO : pesoAnterior) != 0) {
            registrarPeso(consulta, sv);
        }
        return SignoVitalResponse.from(sv);
    }

    @Transactional
    public void eliminarSigno(Long consultaId, Long signoId) {
        Consulta consulta = obtenerAbierta(consultaId);
        SignoVital sv = consulta.getSignosVitales().stream().filter(s -> s.getId().equals(signoId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("SignoVital", signoId));
        consulta.getSignosVitales().remove(sv);
        consultaRepository.flush();
    }

    private void registrarPeso(Consulta consulta, SignoVital sv) {
        if (sv.getPesoKg() != null) {
            pesoService.registrar(consulta.getPaciente(), sv.getPesoKg(), consulta.getFecha());
        }
    }

    private static void aplicar(SignoVital sv, SignoVitalRequest r) {
        if (r.temperatura() != null) {
            sv.setTemperatura(r.temperatura());
        }
        if (r.frecuenciaCardiaca() != null) {
            sv.setFrecuenciaCardiaca(r.frecuenciaCardiaca());
        }
        if (r.frecuenciaRespiratoria() != null) {
            sv.setFrecuenciaRespiratoria(r.frecuenciaRespiratoria());
        }
        if (r.pesoKg() != null) {
            sv.setPesoKg(r.pesoKg());
        }
        sv.setCondicionCorporal(Textos.actualizar(sv.getCondicionCorporal(), r.condicionCorporal()));
        if (r.puntajeCondicionCorporal() != null) {
            sv.setPuntajeCondicionCorporal(r.puntajeCondicionCorporal().shortValue());
        }
        sv.setMucosas(Textos.actualizar(sv.getMucosas(), r.mucosas()));
        if (r.tiempoLlenadoCapilar() != null) {
            sv.setTiempoLlenadoCapilar(r.tiempoLlenadoCapilar());
        }
        if (r.escalaDolor() != null) {
            sv.setEscalaDolor(r.escalaDolor().shortValue());
        }
        if (r.hidratacionPct() != null) {
            sv.setHidratacionPct(r.hidratacionPct());
        }
        if (r.presionSistolica() != null) {
            sv.setPresionSistolica(r.presionSistolica());
        }
        if (r.spo2() != null) {
            sv.setSpo2(r.spo2());
        }
        if (r.glucosaMgDl() != null) {
            sv.setGlucosaMgDl(r.glucosaMgDl());
        }
        sv.setNotas(Textos.actualizar(sv.getNotas(), r.notas()));
    }

    private static boolean sinValores(SignoVital s) {
        return s.getTemperatura() == null && s.getFrecuenciaCardiaca() == null && s.getFrecuenciaRespiratoria() == null
                && s.getPesoKg() == null && s.getCondicionCorporal() == null && s.getPuntajeCondicionCorporal() == null
                && s.getMucosas() == null && s.getTiempoLlenadoCapilar() == null && s.getEscalaDolor() == null
                && s.getHidratacionPct() == null && s.getPresionSistolica() == null && s.getSpo2() == null
                && s.getGlucosaMgDl() == null && s.getNotas() == null;
    }

    // ------------------------------------------------------------------ diagnosticos

    @Transactional
    public DiagnosticoResponse agregarDiagnostico(Long consultaId, DiagnosticoRequest r) {
        Consulta consulta = obtenerAbierta(consultaId);
        Diagnostico d = new Diagnostico();
        d.setConsulta(consulta);
        d.setTipo("PRESUNTIVO");
        d.setOrden(consulta.getDiagnosticos().size() + 1);
        if (r.catalogoId() != null) {
            d.setCatalogo(obtenerCatalogo(r.catalogoId()));
        }
        String descripcion = Textos.limpiar(r.descripcion());
        d.setDescripcion(descripcion != null ? descripcion : d.getCatalogo() == null ? null : d.getCatalogo().getNombre());
        if (d.getDescripcion() == null) {
            throw new BusinessException("Indique un diagnostico del catalogo (catalogoId) o una descripcion");
        }
        aplicarDiagnostico(d, r);
        consulta.getDiagnosticos().add(d);
        consultaRepository.flush();
        return DiagnosticoResponse.from(d);
    }

    @Transactional
    public DiagnosticoResponse actualizarDiagnostico(Long consultaId, Long dxId, DiagnosticoRequest r) {
        Consulta consulta = obtenerAbierta(consultaId);
        Diagnostico d = consulta.getDiagnosticos().stream().filter(x -> x.getId().equals(dxId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Diagnostico", dxId));
        if (r.catalogoId() != null) {
            d.setCatalogo(obtenerCatalogo(r.catalogoId()));
        }
        if (r.descripcion() != null) {
            String descripcion = Textos.limpiar(r.descripcion());
            if (descripcion == null) {
                throw new BusinessException("La descripcion del diagnostico no puede quedar vacia");
            }
            d.setDescripcion(descripcion);
        }
        aplicarDiagnostico(d, r);
        consultaRepository.flush();
        return DiagnosticoResponse.from(d);
    }

    @Transactional
    public void eliminarDiagnostico(Long consultaId, Long dxId) {
        Consulta consulta = obtenerAbierta(consultaId);
        Diagnostico d = consulta.getDiagnosticos().stream().filter(x -> x.getId().equals(dxId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Diagnostico", dxId));
        consulta.getDiagnosticos().remove(d);
        consultaRepository.flush();
    }

    private static void aplicarDiagnostico(Diagnostico d, DiagnosticoRequest r) {
        d.setCodigoCie(Textos.actualizar(d.getCodigoCie(), r.codigoCie()));
        d.setLateralidad(Textos.actualizar(d.getLateralidad(), r.lateralidad()));
        if (r.tipo() != null) {
            d.setTipo(r.tipo());
        }
        if (r.orden() != null) {
            d.setOrden(r.orden());
        }
        if (r.resuelto() != null) {
            d.setResuelto(r.resuelto());
        }
    }

    private CatalogoDiagnostico obtenerCatalogo(Long id) {
        return catalogoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CatalogoDiagnostico", id));
    }

    // ------------------------------------------------------------------ recetas

    @Transactional
    public RecetaResponse agregarReceta(Long consultaId, RecetaRequest r) {
        Consulta consulta = obtenerAbierta(consultaId);
        if (r.items() == null || r.items().isEmpty()) {
            throw new BusinessException("La receta debe tener al menos un medicamento");
        }
        Receta receta = new Receta();
        receta.setConsulta(consulta);
        receta.setPaciente(consulta.getPaciente());
        receta.setVeterinario(consulta.getVeterinario());
        receta.setFecha(ZonaClinica.hoy());
        aplicarReceta(receta, r);
        reemplazarItems(receta, r.items());
        consulta.getRecetas().add(receta);
        consultaRepository.flush();
        return RecetaResponse.from(receta);
    }

    @Transactional
    public RecetaResponse actualizarReceta(Long consultaId, Long recetaId, RecetaRequest r) {
        Consulta consulta = obtenerAbierta(consultaId);
        Receta receta = obtenerReceta(consulta, recetaId);
        aplicarReceta(receta, r);
        if (r.estado() != null) {
            receta.setEstado(r.estado());
        }
        if (r.items() != null) {
            if (r.items().isEmpty()) {
                throw new BusinessException("La receta debe tener al menos un medicamento");
            }
            reemplazarItems(receta, r.items());
        }
        consultaRepository.flush();
        return RecetaResponse.from(receta);
    }

    /** Anula la receta (estado CANCELADA); no se borra. */
    @Transactional
    public void cancelarReceta(Long consultaId, Long recetaId) {
        Consulta consulta = obtenerAbierta(consultaId);
        obtenerReceta(consulta, recetaId).setEstado("CANCELADA");
        consultaRepository.flush();
    }

    private static Receta obtenerReceta(Consulta consulta, Long recetaId) {
        return consulta.getRecetas().stream().filter(x -> x.getId().equals(recetaId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Receta", recetaId));
    }

    private static void aplicarReceta(Receta receta, RecetaRequest r) {
        receta.setIndicacionesGenerales(Textos.actualizar(receta.getIndicacionesGenerales(), r.indicacionesGenerales()));
        if (r.validaHasta() != null) {
            receta.setValidaHasta(r.validaHasta());
        }
        if (r.esControlada() != null) {
            receta.setEsControlada(r.esControlada());
        }
    }

    private static void reemplazarItems(Receta receta, List<RecetaItemDto> dtos) {
        receta.getItems().clear();
        int orden = 1;
        for (RecetaItemDto dto : dtos) {
            RecetaItem i = new RecetaItem();
            i.setReceta(receta);
            i.setItemId(dto.itemId());
            i.setMedicamento(dto.medicamento().trim());
            i.setDosis(Textos.limpiar(dto.dosis()));
            i.setVia(Textos.limpiar(dto.via()));
            i.setFrecuencia(Textos.limpiar(dto.frecuencia()));
            i.setDuracion(Textos.limpiar(dto.duracion()));
            i.setCantidad(dto.cantidad());
            i.setIndicaciones(Textos.limpiar(dto.indicaciones()));
            i.setOrden(dto.orden() != null ? dto.orden() : orden);
            receta.getItems().add(i);
            orden++;
        }
    }

    // ------------------------------------------------------------------ addendums

    @Transactional
    public AddendumResponse agregarAddendum(Long consultaId, AddendumRequest r) {
        Consulta consulta = obtener(consultaId);
        if (!consulta.estaCerrada()) {
            throw new BusinessException("Solo se registran addendums en consultas CERRADAS; edite la consulta directamente");
        }
        Long autorId = UsuarioActual.id()
                .orElseThrow(() -> new UnauthorizedException("Sesion sin identificador de usuario; inicie sesion de nuevo."));
        ConsultaAddendum a = new ConsultaAddendum();
        a.setConsulta(consulta);
        a.setAutor(usuarioRepository.findById(autorId)
                .orElseThrow(() -> new UnauthorizedException("Usuario de la sesion no encontrado.")));
        a.setTipo(r.tipo() != null ? r.tipo() : "ACLARACION");
        a.setTexto(r.texto().trim());
        return AddendumResponse.from(addendumRepository.saveAndFlush(a));
    }

    @Transactional(readOnly = true)
    public List<AddendumResponse> addendums(Long consultaId) {
        obtener(consultaId);
        return addendumRepository.findByConsultaIdOrderByCreadoEnAscIdAsc(consultaId).stream()
                .map(AddendumResponse::from).toList();
    }

    // ------------------------------------------------------------------ comunes

    private Consulta obtener(Long id) {
        return consultaRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Consulta", id));
    }

    private Consulta obtenerAbierta(Long id) {
        Consulta consulta = obtener(id);
        if (consulta.estaCerrada()) {
            throw new ConflictException("La consulta " + consulta.getCodigo()
                    + " esta CERRADA y es inmutable; registre un addendum");
        }
        return consulta;
    }
}
