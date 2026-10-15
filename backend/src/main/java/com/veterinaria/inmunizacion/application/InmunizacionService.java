package com.veterinaria.inmunizacion.application;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.clinica.ConsultaCerrada;
import com.veterinaria.inmunizacion.InmunizacionAnulada;
import com.veterinaria.inmunizacion.InmunizacionRegistrada;
import com.veterinaria.inmunizacion.TipoAplicacion;
import com.veterinaria.inmunizacion.api.dto.InmunizacionRequest;
import com.veterinaria.inmunizacion.api.dto.InmunizacionResponse;
import com.veterinaria.inmunizacion.api.dto.SugerenciaResponse;
import com.veterinaria.inmunizacion.domain.Antiparasitario;
import com.veterinaria.inmunizacion.domain.CalculadoraProximaDosis;
import com.veterinaria.inmunizacion.domain.Desparasitacion;
import com.veterinaria.inmunizacion.domain.EsquemaVacunal;
import com.veterinaria.inmunizacion.domain.EsquemaVacunalDosis;
import com.veterinaria.inmunizacion.domain.Inmunizacion;
import com.veterinaria.inmunizacion.domain.Vacuna;
import com.veterinaria.inmunizacion.internal.AntiparasitarioRepository;
import com.veterinaria.inmunizacion.internal.DesparasitacionRepository;
import com.veterinaria.inmunizacion.internal.EsquemaVacunalRepository;
import com.veterinaria.inmunizacion.internal.InmunizacionRepository;
import com.veterinaria.inmunizacion.internal.LoteStockRepository;
import com.veterinaria.inmunizacion.internal.VacunaRepository;
import com.veterinaria.seguridad.application.VeterinarioLookup;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.error.ConflictException;
import com.veterinaria.shared.error.ResourceNotFoundException;
import com.veterinaria.shared.security.UsuarioActual;
import com.veterinaria.shared.sede.SedePrincipal;
import com.veterinaria.shared.text.Textos;
import com.veterinaria.shared.time.ZonaClinica;
import com.veterinaria.tutores.domain.Paciente;
import com.veterinaria.tutores.internal.PacienteRepository;

/** Casos de uso de aplicacion de vacunas/antiparasitarios, anulacion, listado y esquema sugerido. */
@Service
public class InmunizacionService {

    private static final Set<String> LOTE_NO_DISPONIBLE = Set.of("CUARENTENA", "RETIRADO", "VENCIDO");
    private static final BigDecimal UNA_DOSIS = BigDecimal.ONE;
    private static final int DIAS_PROXIMA = 30;

    private final InmunizacionRepository inmunizacionRepository;
    private final DesparasitacionRepository desparasitacionRepository;
    private final VacunaRepository vacunaRepository;
    private final AntiparasitarioRepository antiparasitarioRepository;
    private final EsquemaVacunalRepository esquemaRepository;
    private final LoteStockRepository loteStock;
    private final PacienteRepository pacienteRepository;
    private final VeterinarioLookup veterinarioLookup;
    private final SedePrincipal sedePrincipal;
    private final JdbcClient jdbc;
    private final ApplicationEventPublisher eventos;

    public InmunizacionService(InmunizacionRepository inmunizacionRepository,
            DesparasitacionRepository desparasitacionRepository, VacunaRepository vacunaRepository,
            AntiparasitarioRepository antiparasitarioRepository, EsquemaVacunalRepository esquemaRepository,
            LoteStockRepository loteStock, PacienteRepository pacienteRepository, VeterinarioLookup veterinarioLookup,
            SedePrincipal sedePrincipal, JdbcClient jdbc, ApplicationEventPublisher eventos) {
        this.inmunizacionRepository = inmunizacionRepository;
        this.desparasitacionRepository = desparasitacionRepository;
        this.vacunaRepository = vacunaRepository;
        this.antiparasitarioRepository = antiparasitarioRepository;
        this.esquemaRepository = esquemaRepository;
        this.loteStock = loteStock;
        this.pacienteRepository = pacienteRepository;
        this.veterinarioLookup = veterinarioLookup;
        this.sedePrincipal = sedePrincipal;
        this.jdbc = jdbc;
        this.eventos = eventos;
    }

    // ------------------------------------------------------------------ registrar

    @Transactional
    public InmunizacionResponse registrar(Long pacienteId, InmunizacionRequest r) {
        Paciente paciente = paciente(pacienteId);
        if (paciente.isFallecido()) {
            throw new BusinessException("El paciente esta registrado como fallecido");
        }
        LocalDate fecha = r.fechaAplicacion() != null ? r.fechaAplicacion() : ZonaClinica.hoy();
        if (fecha.isAfter(ZonaClinica.hoy())) {
            throw new BusinessException("La fecha de aplicacion no puede ser futura");
        }
        if (paciente.getFechaNacimiento() != null && fecha.isBefore(paciente.getFechaNacimiento())) {
            throw new BusinessException("La fecha de aplicacion es anterior al nacimiento del paciente");
        }
        if (r.proximaFecha() != null && r.proximaFecha().isBefore(fecha)) {
            throw new BusinessException("La proxima fecha no puede ser anterior a la aplicacion");
        }
        Long veterinarioId = resolverVeterinario(r.veterinarioId());
        boolean externa = r.externa() != null && r.externa();
        if (externa && r.loteId() != null) {
            throw new BusinessException("Una aplicacion externa no descuenta stock: omita loteId y use 'lote'");
        }
        return r.tipo() == TipoAplicacion.VACUNA
                ? registrarVacuna(paciente, r, fecha, veterinarioId, externa)
                : registrarAntiparasitario(paciente, r, fecha, veterinarioId);
    }

    private InmunizacionResponse registrarVacuna(Paciente paciente, InmunizacionRequest r, LocalDate fecha,
            Long veterinarioId, boolean externa) {
        if (r.vacunaId() == null) {
            throw new BusinessException("vacunaId es obligatorio para tipo VACUNA");
        }
        Vacuna vacuna = vacunaRepository.findById(r.vacunaId())
                .orElseThrow(() -> new ResourceNotFoundException("Vacuna", r.vacunaId()));
        if (!vacuna.isActivo()) {
            throw new BusinessException("La vacuna esta inactiva en el catalogo");
        }
        if (vacuna.getEspecie() != null && !vacuna.getEspecie().getId().equals(paciente.getEspecie().getId())) {
            throw new BusinessException("La vacuna es de la especie " + vacuna.getEspecie().getNombre());
        }
        List<Inmunizacion> previas = inmunizacionRepository
                .findByPacienteIdAndVacunaIdAndEstadoOrderByFechaAplicacionDescIdDesc(
                        paciente.getId(), vacuna.getId(), Inmunizacion.APLICADA);
        int dosisNumero = r.dosisNumero() != null ? r.dosisNumero() : previas.size() + 1;

        LoteStockRepository.Lote lote = r.loteId() == null ? null
                : validarLote(r.loteId(), fecha, vacuna.getItemId());
        String numeroLote = lote != null ? lote.numeroLote() : Textos.limpiar(r.lote());
        LocalDate vencimiento = lote != null ? lote.vencimiento() : r.fechaVencimientoLote();
        validarVencimiento(lote == null, vencimiento, fecha);

        Inmunizacion i = new Inmunizacion();
        i.setPaciente(paciente);
        i.setVacuna(vacuna);
        i.setConsultaId(r.consultaId());
        i.setFechaAplicacion(fecha);
        i.setLote(numeroLote);
        i.setLoteId(lote == null ? null : lote.id());
        i.setFechaVencimientoLote(vencimiento);
        i.setLaboratorio(Textos.limpiar(r.laboratorio()));
        i.setVeterinarioId(veterinarioId);
        i.setObservaciones(Textos.limpiar(r.observaciones()));
        i.setReaccionAdversa(Textos.limpiar(r.reaccionAdversa()));
        i.setSitioAplicacion(Textos.limpiar(r.sitioAplicacion()));
        i.setViaAdministracion(r.viaAdministracion() != null ? Textos.limpiar(r.viaAdministracion())
                : vacuna.getViaAdministracion());
        i.setPesoKg(r.pesoKg() != null ? r.pesoKg() : paciente.getPesoActual());
        i.setExterna(externa);
        calcularProxima(i, vacuna, paciente, dosisNumero, r.proximaFecha());
        Inmunizacion guardada = inmunizacionRepository.saveAndFlush(i);
        if (lote != null) {
            loteStock.salida(lote.id(), UNA_DOSIS, "Aplicacion de vacuna " + vacuna.getNombre(),
                    "INMUNIZACION:" + guardada.getId(), UsuarioActual.id().orElse(null), paciente.getId(),
                    r.consultaId());
        }
        List<Long> reemplazadas = previas.stream().map(Inmunizacion::getId).toList();
        eventos.publishEvent(new InmunizacionRegistrada(TipoAplicacion.VACUNA.name(), guardada.getId(),
                paciente.getId(), reemplazadas));
        return aRespuesta(guardada, nombres(List.of(guardada)).get(guardada.getVeterinarioId()));
    }

    /** Fija dosis, esquema y proxima fecha (manual si se indico; si no, por esquema/edad/dosis). */
    private void calcularProxima(Inmunizacion i, Vacuna vacuna, Paciente paciente, int dosisNumero, LocalDate manual) {
        List<EsquemaVacunalDosis> calendario = calendario(vacuna, paciente);
        i.setDosisNumero(dosisNumero);
        CalculadoraProximaDosis.dosisDe(calendario, dosisNumero).ifPresent(d -> i.setEsquemaDosisId(d.getId()));
        i.setFechaProximaDosis(manual != null ? manual : CalculadoraProximaDosis.proxima(calendario, dosisNumero,
                i.getFechaAplicacion(), paciente.getFechaNacimiento(), vacuna.getPeriodoEficaciaDias()));
    }

    private InmunizacionResponse registrarAntiparasitario(Paciente paciente, InmunizacionRequest r, LocalDate fecha,
            Long veterinarioId) {
        if (r.antiparasitarioId() == null) {
            throw new BusinessException("antiparasitarioId es obligatorio para tipo ANTIPARASITARIO");
        }
        Antiparasitario ap = antiparasitarioRepository.findById(r.antiparasitarioId())
                .orElseThrow(() -> new ResourceNotFoundException("Antiparasitario", r.antiparasitarioId()));
        if (!ap.isActivo()) {
            throw new BusinessException("El antiparasitario esta inactivo en el catalogo");
        }
        if (ap.getEspecie() != null && !ap.getEspecie().getId().equals(paciente.getEspecie().getId())) {
            throw new BusinessException("El antiparasitario es de la especie " + ap.getEspecie().getNombre());
        }
        LoteStockRepository.Lote lote = r.loteId() == null ? null : validarLote(r.loteId(), fecha, ap.getItemId());
        String numeroLote = lote != null ? lote.numeroLote() : Textos.limpiar(r.lote());
        LocalDate vencimiento = lote != null ? lote.vencimiento() : r.fechaVencimientoLote();
        validarVencimiento(lote == null, vencimiento, fecha);

        Desparasitacion d = new Desparasitacion();
        d.setPaciente(paciente);
        d.setAntiparasitario(ap);
        d.setTipo(ap.getTipo());
        d.setItemId(ap.getItemId());
        d.setProducto(ap.getNombre());
        d.setPrincipioActivo(ap.getPrincipioActivo());
        d.setDosis(Textos.limpiar(r.dosis()));
        d.setPesoKg(r.pesoKg() != null ? r.pesoKg() : paciente.getPesoActual());
        d.setFechaAplicacion(fecha);
        d.setProximaFecha(r.proximaFecha() != null ? r.proximaFecha() : fecha.plusDays(ap.getIntervaloDias()));
        d.setLote(numeroLote);
        d.setLoteId(lote == null ? null : lote.id());
        d.setFechaVencimientoLote(vencimiento);
        d.setConsultaId(r.consultaId());
        d.setVeterinarioId(veterinarioId);
        d.setSedeId(sedePrincipal.id());
        d.setObservaciones(Textos.limpiar(r.observaciones()));
        List<Desparasitacion> previas = desparasitacionRepository
                .findByPacienteIdAndAntiparasitarioIdAndEstadoOrderByFechaAplicacionDescIdDesc(
                        paciente.getId(), ap.getId(), Inmunizacion.APLICADA);
        Desparasitacion guardada = desparasitacionRepository.saveAndFlush(d);
        if (lote != null) {
            loteStock.salida(lote.id(), UNA_DOSIS, "Aplicacion de antiparasitario " + ap.getNombre(),
                    "DESPARASITACION:" + guardada.getId(), UsuarioActual.id().orElse(null), paciente.getId(),
                    r.consultaId());
        }
        eventos.publishEvent(new InmunizacionRegistrada(TipoAplicacion.ANTIPARASITARIO.name(), guardada.getId(),
                paciente.getId(), previas.stream().map(Desparasitacion::getId).toList()));
        return aRespuesta(guardada, nombreUsuario(guardada.getVeterinarioId()));
    }

    /** Bloquea el lote y valida estado, vencimiento, producto y stock (>= 1 dosis). */
    private LoteStockRepository.Lote validarLote(Long loteId, LocalDate fecha, Long itemEsperado) {
        LoteStockRepository.Lote lote = loteStock.bloquear(loteId)
                .orElseThrow(() -> new ResourceNotFoundException("Lote", loteId));
        if (lote.vencimiento() != null && lote.vencimiento().isBefore(fecha)) {
            throw new BusinessException("El lote " + lote.numeroLote() + " esta vencido (" + lote.vencimiento() + ")");
        }
        if (lote.estado() != null && LOTE_NO_DISPONIBLE.contains(lote.estado())) {
            throw new BusinessException("El lote " + lote.numeroLote() + " no esta disponible (" + lote.estado() + ")");
        }
        if (itemEsperado != null && !itemEsperado.equals(lote.itemId())) {
            throw new BusinessException("El lote no corresponde al producto seleccionado");
        }
        if (lote.cantidad().compareTo(UNA_DOSIS) < 0) {
            throw new ConflictException("Stock insuficiente en el lote " + lote.numeroLote());
        }
        return lote;
    }

    private static void validarVencimiento(boolean textoLibre, LocalDate vencimiento, LocalDate fecha) {
        if (textoLibre && vencimiento != null && vencimiento.isBefore(fecha)) {
            throw new BusinessException("El lote esta vencido (" + vencimiento + ")");
        }
    }

    private Long resolverVeterinario(Long solicitado) {
        String rol = UsuarioActual.rol().orElse("");
        if (solicitado != null) {
            return veterinarioLookup.obtener(solicitado).getId();
        }
        if ("AUXILIAR".equals(rol)) {
            throw new BusinessException("El auxiliar debe indicar el veterinario supervisor (veterinarioId)");
        }
        return "VETERINARIO".equals(rol) ? UsuarioActual.id().orElse(null) : null;
    }

    // ------------------------------------------------------------------ anular

    @Transactional
    public InmunizacionResponse anular(Long pacienteId, TipoAplicacion tipo, Long id, String motivo) {
        paciente(pacienteId);
        Long usuario = UsuarioActual.id().orElse(null);
        if (tipo == TipoAplicacion.VACUNA) {
            Inmunizacion i = inmunizacionRepository.findById(id).filter(x -> x.getPaciente().getId().equals(pacienteId))
                    .orElseThrow(() -> new ResourceNotFoundException("Inmunizacion", id));
            if (Inmunizacion.ANULADA.equals(i.getEstado())) {
                throw new ConflictException("La aplicacion ya esta anulada");
            }
            i.setEstado(Inmunizacion.ANULADA);
            i.setMotivoAnulacion(motivo.trim());
            i.setAnuladoPor(usuario);
            i.setAnuladoEn(Instant.now());
            if (i.getLoteId() != null) {
                loteStock.bloquear(i.getLoteId());
                loteStock.devolucion(i.getLoteId(), UNA_DOSIS, "Anulacion de inmunizacion: " + motivo.trim(),
                        "INMUNIZACION:" + id, usuario, pacienteId, i.getConsultaId());
            }
            inmunizacionRepository.saveAndFlush(i);
            eventos.publishEvent(new InmunizacionAnulada(tipo.name(), id, pacienteId));
            return aRespuesta(i, nombreUsuario(i.getVeterinarioId()));
        }
        Desparasitacion d = desparasitacionRepository.findById(id).filter(x -> x.getPaciente().getId().equals(pacienteId))
                .orElseThrow(() -> new ResourceNotFoundException("Desparasitacion", id));
        if (Inmunizacion.ANULADA.equals(d.getEstado())) {
            throw new ConflictException("La aplicacion ya esta anulada");
        }
        d.setEstado(Inmunizacion.ANULADA);
        d.setMotivoAnulacion(motivo.trim());
        d.setAnuladoPor(usuario);
        d.setAnuladoEn(Instant.now());
        if (d.getLoteId() != null) {
            loteStock.bloquear(d.getLoteId());
            loteStock.devolucion(d.getLoteId(), UNA_DOSIS, "Anulacion de desparasitacion: " + motivo.trim(),
                    "DESPARASITACION:" + id, usuario, pacienteId, d.getConsultaId());
        }
        desparasitacionRepository.saveAndFlush(d);
        eventos.publishEvent(new InmunizacionAnulada(tipo.name(), id, pacienteId));
        return aRespuesta(d, nombreUsuario(d.getVeterinarioId()));
    }

    // ------------------------------------------------------------------ consultar

    /** Vacunas y antiparasitarios del paciente, mas reciente primero ({@code tipo} nulo = ambos). */
    @Transactional(readOnly = true)
    public List<InmunizacionResponse> listar(Long pacienteId, TipoAplicacion tipo, boolean incluirAnuladas) {
        paciente(pacienteId);
        List<Inmunizacion> vacunas = tipo == TipoAplicacion.ANTIPARASITARIO ? List.of()
                : inmunizacionRepository.findByPacienteIdOrderByFechaAplicacionDescIdDesc(pacienteId).stream()
                        .filter(i -> incluirAnuladas || Inmunizacion.APLICADA.equals(i.getEstado())).toList();
        List<Desparasitacion> despar = tipo == TipoAplicacion.VACUNA ? List.of()
                : desparasitacionRepository.findByPacienteIdOrderByFechaAplicacionDescIdDesc(pacienteId).stream()
                        .filter(d -> incluirAnuladas || Inmunizacion.APLICADA.equals(d.getEstado())).toList();
        Map<Long, String> nombres = nombresUsuarios(
                java.util.stream.Stream.concat(vacunas.stream().map(Inmunizacion::getVeterinarioId),
                        despar.stream().map(Desparasitacion::getVeterinarioId)).toList());
        List<InmunizacionResponse> res = new ArrayList<>();
        vacunas.forEach(i -> res.add(aRespuesta(i, nombres.get(i.getVeterinarioId()))));
        despar.forEach(d -> res.add(aRespuesta(d, nombres.get(d.getVeterinarioId()))));
        res.sort(Comparator.comparing(InmunizacionResponse::fechaAplicacion).reversed()
                .thenComparing(InmunizacionResponse::tipo).thenComparing(InmunizacionResponse::id,
                        Comparator.reverseOrder()));
        return res;
    }

    @Transactional(readOnly = true)
    public SugerenciaResponse sugerido(Long pacienteId) {
        Paciente p = paciente(pacienteId);
        LocalDate hoy = ZonaClinica.hoy();
        Integer edad = p.getFechaNacimiento() == null ? null
                : (int) ChronoUnit.WEEKS.between(p.getFechaNacimiento(), hoy);
        List<SugerenciaResponse.Item> items = new ArrayList<>();
        for (EsquemaVacunal e : esquemaRepository.findByActivoTrueOrderByNombreAsc()) {
            if (!e.getVacuna().isActivo()
                    || e.getEspecie() != null && !e.getEspecie().getId().equals(p.getEspecie().getId())) {
                continue;
            }
            items.add(itemSugerido(p, e, hoy));
        }
        return new SugerenciaResponse(p.getId(), p.getNombre(), p.getEspecie().getNombre(), p.getFechaNacimiento(),
                edad, items);
    }

    private SugerenciaResponse.Item itemSugerido(Paciente p, EsquemaVacunal e, LocalDate hoy) {
        Vacuna v = e.getVacuna();
        List<EsquemaVacunalDosis> dosis = e.getDosis();
        List<Inmunizacion> previas = inmunizacionRepository
                .findByPacienteIdAndVacunaIdAndEstadoOrderByFechaAplicacionDescIdDesc(
                        p.getId(), v.getId(), Inmunizacion.APLICADA);
        int aplicadas = previas.size();
        Integer numero;
        String nombre;
        LocalDate fecha;
        String estado;
        LocalDate ultima = null;
        if (aplicadas == 0) {
            numero = 1;
            nombre = dosis.isEmpty() ? "Primera dosis" : dosis.get(0).getNombreDosis();
            LocalDate minima = !dosis.isEmpty() && dosis.get(0).getEdadMinimaSemanas() != null
                    && p.getFechaNacimiento() != null ? p.getFechaNacimiento().plusWeeks(dosis.get(0).getEdadMinimaSemanas())
                    : hoy;
            fecha = minima.isBefore(hoy) ? hoy : minima;
            estado = fecha.isAfter(hoy) ? (fecha.isAfter(hoy.plusDays(DIAS_PROXIMA)) ? "FUTURA" : "PROXIMA")
                    : "TOCA_AHORA";
        } else {
            Inmunizacion ult = previas.get(0);
            ultima = ult.getFechaAplicacion();
            numero = (ult.getDosisNumero() != null ? ult.getDosisNumero() : aplicadas) + 1;
            nombre = CalculadoraProximaDosis.dosisDe(dosis, numero).map(EsquemaVacunalDosis::getNombreDosis)
                    .orElse("Refuerzo");
            fecha = ult.getFechaProximaDosis();
            if (fecha == null) {
                estado = "COMPLETA";
                numero = null;
                nombre = null;
            } else if (fecha.isBefore(hoy)) {
                estado = "VENCIDA";
            } else if (fecha.isEqual(hoy)) {
                estado = "TOCA_AHORA";
            } else {
                estado = fecha.isAfter(hoy.plusDays(DIAS_PROXIMA)) ? "AL_DIA" : "PROXIMA";
            }
        }
        return new SugerenciaResponse.Item(e.getId(), e.getNombre(), v.getId(), v.getNombre(), v.getClasificacion(),
                v.isObligatoriaLey(), aplicadas, ultima, numero, nombre, fecha, estado);
    }

    // ------------------------------------------------------------------ desde la consulta cerrada

    /**
     * Convierte las vacunas de una consulta cerrada en inmunizaciones. Idempotente: cada renglon
     * {@code consulta_vacuna} genera como maximo una inmunizacion (indice unico). No toca la consulta.
     *
     * @return numero de inmunizaciones creadas
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public int registrarDesdeConsulta(ConsultaCerrada evento) {
        Paciente paciente = paciente(evento.pacienteId());
        int creadas = 0;
        for (ConsultaCerrada.VacunaAplicada va : evento.vacunas()) {
            if (inmunizacionRepository.existsByConsultaVacunaId(va.id())) {
                continue;
            }
            Vacuna vacuna = resolverVacuna(va.nombre(), paciente);
            LocalDate fecha = va.fechaAplicacion() != null ? va.fechaAplicacion() : evento.fecha();
            List<Inmunizacion> previas = inmunizacionRepository
                    .findByPacienteIdAndVacunaIdAndEstadoOrderByFechaAplicacionDescIdDesc(
                            paciente.getId(), vacuna.getId(), Inmunizacion.APLICADA);
            Inmunizacion i = new Inmunizacion();
            i.setPaciente(paciente);
            i.setVacuna(vacuna);
            i.setConsultaId(evento.consultaId());
            i.setConsultaVacunaId(va.id());
            i.setFechaAplicacion(fecha);
            i.setLote(va.lote());
            i.setVeterinarioId(evento.veterinarioId());
            i.setViaAdministracion(vacuna.getViaAdministracion());
            i.setPesoKg(paciente.getPesoActual());
            calcularProxima(i, vacuna, paciente, previas.size() + 1,
                    va.proximaFecha() != null && !va.proximaFecha().isBefore(fecha) ? va.proximaFecha() : null);
            Inmunizacion guardada = inmunizacionRepository.saveAndFlush(i);
            eventos.publishEvent(new InmunizacionRegistrada(TipoAplicacion.VACUNA.name(), guardada.getId(),
                    paciente.getId(), previas.stream().map(Inmunizacion::getId).toList()));
            creadas++;
        }
        return creadas;
    }

    /** Busca la vacuna del catalogo por codigo o nombre; si no existe la crea (NO_NUCLEO, 365 dias) para no perder el dato. */
    private Vacuna resolverVacuna(String nombre, Paciente paciente) {
        String buscado = nombre.trim();
        Optional<Vacuna> existente = vacunaRepository.findFirstByNombreIgnoreCase(buscado)
                .or(() -> vacunaRepository.findByCodigo(buscado.toUpperCase(Locale.ROOT)));
        if (existente.isPresent()) {
            return existente.get();
        }
        String base = "AUTO-" + Normalizer.normalize(buscado, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9]+", "-").toUpperCase(Locale.ROOT);
        String codigo = base.length() > 46 ? base.substring(0, 46) : base;
        int n = 1;
        String candidato = codigo;
        while (vacunaRepository.existsByCodigo(candidato)) {
            candidato = codigo + "-" + (++n);
        }
        Vacuna v = new Vacuna();
        v.setCodigo(candidato);
        v.setNombre(buscado.length() > 255 ? buscado.substring(0, 255) : buscado);
        v.setEspecie(paciente.getEspecie());
        v.setPeriodoEficaciaDias(365);
        return vacunaRepository.saveAndFlush(v);
    }

    // ------------------------------------------------------------------ utilidades

    private List<EsquemaVacunalDosis> calendario(Vacuna vacuna, Paciente paciente) {
        return esquemaRepository.findByVacunaIdAndActivoTrue(vacuna.getId()).stream()
                .filter(e -> e.getEspecie() == null || e.getEspecie().getId().equals(paciente.getEspecie().getId()))
                .max(Comparator.comparingInt(e -> e.getEspecie() == null ? 0 : 1))
                .map(EsquemaVacunal::getDosis).orElse(List.of());
    }

    private Paciente paciente(Long id) {
        return pacienteRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Paciente", id));
    }

    private Map<Long, String> nombres(List<Inmunizacion> lista) {
        return nombresUsuarios(lista.stream().map(Inmunizacion::getVeterinarioId).toList());
    }

    private String nombreUsuario(Long id) {
        return id == null ? null : nombresUsuarios(List.of(id)).get(id);
    }

    private Map<Long, String> nombresUsuarios(List<Long> ids) {
        List<Long> distintos = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> res = new HashMap<>();
        if (!distintos.isEmpty()) {
            jdbc.sql("select id, nombre from usuario where id in (:ids)").param("ids", distintos)
                    .query((rs, n) -> {
                        res.put(rs.getLong("id"), rs.getString("nombre"));
                        return 0;
                    }).list();
        }
        return res;
    }

    private InmunizacionResponse aRespuesta(Inmunizacion i, String vet) {
        Vacuna v = i.getVacuna();
        String dosisNombre = i.getEsquemaDosisId() == null ? null
                : jdbc.sql("select nombre_dosis from esquema_vacunal_dosis where id = :id").param("id", i.getEsquemaDosisId())
                        .query(String.class).optional().orElse(null);
        return new InmunizacionResponse(TipoAplicacion.VACUNA.name(), i.getId(), i.getPaciente().getId(), v.getId(),
                v.getNombre(), v.getCodigo(), v.getClasificacion(), i.getDosisNumero(), dosisNombre, null,
                i.getFechaAplicacion(), i.getFechaProximaDosis(), i.getLoteId(), i.getLote(),
                i.getFechaVencimientoLote(),
                i.getVeterinarioId() == null ? null : new InmunizacionResponse.Persona(i.getVeterinarioId(), vet),
                i.getSitioAplicacion(), i.getViaAdministracion(), i.getPesoKg(), i.getLaboratorio(),
                i.getObservaciones(), i.getReaccionAdversa(), i.isExterna(), i.getConsultaId(), i.getEstado(),
                i.getMotivoAnulacion(), i.getAnuladoEn());
    }

    private InmunizacionResponse aRespuesta(Desparasitacion d, String vet) {
        Antiparasitario a = d.getAntiparasitario();
        return new InmunizacionResponse(TipoAplicacion.ANTIPARASITARIO.name(), d.getId(), d.getPaciente().getId(),
                a == null ? null : a.getId(), d.getProducto(), a == null ? null : a.getCodigo(), d.getTipo(), null,
                null, d.getDosis(), d.getFechaAplicacion(), d.getProximaFecha(), d.getLoteId(), d.getLote(),
                d.getFechaVencimientoLote(),
                d.getVeterinarioId() == null ? null : new InmunizacionResponse.Persona(d.getVeterinarioId(), vet),
                null, null, d.getPesoKg(), null, d.getObservaciones(), null, false, d.getConsultaId(), d.getEstado(),
                d.getMotivoAnulacion(), d.getAnuladoEn());
    }
}
