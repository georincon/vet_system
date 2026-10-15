package com.veterinaria.comunicaciones.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.veterinaria.comunicaciones.api.dto.EjecucionResponse;
import com.veterinaria.comunicaciones.api.dto.EnvioLogResponse;
import com.veterinaria.comunicaciones.api.dto.RecordatorioResponse;
import com.veterinaria.inmunizacion.api.dto.PendienteResponse;
import com.veterinaria.inmunizacion.application.PendientesService;
import com.veterinaria.shared.error.ConflictException;
import com.veterinaria.shared.error.ResourceNotFoundException;
import com.veterinaria.shared.security.UsuarioActual;
import com.veterinaria.shared.time.ZonaClinica;
import com.veterinaria.shared.web.PageResponse;

/**
 * Motor de recordatorios: genera (sin duplicados, respetando consentimiento y opt-out) y envia la cola con
 * reintentos limitados, dejando bitacora en envio_log.
 */
@Service
public class RecordatorioService {

    private static final Logger log = LoggerFactory.getLogger(RecordatorioService.class);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final int LOTE_COLA = 200;
    private static final int VENTANA_TARDIA_DIAS = 7;

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final PendientesService pendientes;
    private final OptOutService optOut;
    private final Map<String, CanalEnvio> canales;
    private final LocalTime horaEnvio;
    private final int maxIntentos;
    private final int reintentoMinutos;
    private final String publicUrl;
    private final String clinica;

    public RecordatorioService(JdbcClient jdbc, PlatformTransactionManager txm, PendientesService pendientes,
            OptOutService optOut, List<CanalEnvio> adaptadores,
            @Value("${vet.recordatorios.hora-envio:08:00}") String horaEnvio,
            @Value("${vet.recordatorios.max-intentos:3}") int maxIntentos,
            @Value("${vet.recordatorios.reintento-minutos:30}") int reintentoMinutos,
            @Value("${vet.public-url:http://localhost:9010}") String publicUrl,
            @Value("${vet.clinica.nombre:Clinica Veterinaria}") String clinica) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txm);
        this.pendientes = pendientes;
        this.optOut = optOut;
        this.canales = adaptadores.stream().collect(Collectors.toMap(CanalEnvio::canal, Function.identity()));
        this.horaEnvio = LocalTime.parse(horaEnvio);
        this.maxIntentos = maxIntentos;
        this.reintentoMinutos = reintentoMinutos;
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
        this.clinica = clinica;
    }

    /** Contadores de una ejecucion. */
    private static final class Resumen {
        int generados, yaExistian, sinConsentimiento, sinContacto, reglasSinCanal, enviados, simulados, fallidos,
                reintentos, cancelados, optout;
    }

    private record Regla(Long id, String codigo, String evento, int dias, String canal, Long plantillaId) {
    }

    private record Origen(String tabla, Long id, Long pacienteId, LocalDate fecha) {
    }

    private record Tutor(Long id, String nombre, String email, String telefono, boolean aceptaEmail,
            boolean aceptaWhatsapp, boolean aceptaSms, boolean activo) {
    }

    private record Contexto(LocalDate fecha, LocalTime hora, String paciente, String producto) {
    }

    // ------------------------------------------------------------------ ejecucion

    /** Genera los recordatorios que corresponden a hoy y procesa la cola. */
    public EjecucionResponse ejecutar() {
        Resumen r = new Resumen();
        LocalDate hoy = ZonaClinica.hoy();
        tx.executeWithoutResult(s -> generar(hoy, r));
        procesar(r);
        return respuesta(hoy, r);
    }

    /** Solo envia lo vencido de la cola (reintentos incluidos). */
    public EjecucionResponse procesarCola() {
        Resumen r = new Resumen();
        procesar(r);
        return respuesta(ZonaClinica.hoy(), r);
    }

    private static EjecucionResponse respuesta(LocalDate hoy, Resumen r) {
        return new EjecucionResponse(hoy, r.generados, r.yaExistian, r.sinConsentimiento, r.sinContacto,
                r.reglasSinCanal, r.enviados, r.simulados, r.fallidos, r.reintentos, r.cancelados, r.optout);
    }

    // ------------------------------------------------------------------ generacion

    private void generar(LocalDate hoy, Resumen res) {
        List<Regla> reglas = jdbc.sql("""
                select id, codigo, evento, dias_offset, canal, plantilla_id from regla_recordatorio
                 where activo and plantilla_id is not null order by id
                """).query((rs, n) -> new Regla(rs.getLong("id"), rs.getString("codigo"), rs.getString("evento"),
                rs.getInt("dias_offset"), rs.getString("canal"), rs.getLong("plantilla_id"))).list();
        for (Regla regla : reglas) {
            if (!List.of("VACUNA_PROXIMA", "DESPARASITACION_PROXIMA", "CITA").contains(regla.evento())) {
                continue;
            }
            if (!canales.containsKey(regla.canal())) {
                res.reglasSinCanal++;
                continue;
            }
            // El aviso toca cuando fecha + offset <= hoy; antes del evento sigue vigente hasta el evento y
            // despues del evento solo una semana.
            LocalDate hasta = hoy.minusDays(regla.dias());
            LocalDate desde = regla.dias() <= 0 ? hoy : hoy.minusDays((long) regla.dias() + VENTANA_TARDIA_DIAS);
            for (Origen o : origenes(regla, desde, hasta)) {
                crear(regla, o, res);
            }
        }
    }

    private List<Origen> origenes(Regla regla, LocalDate desde, LocalDate hasta) {
        switch (regla.evento()) {
            case "CITA":
                return jdbc.sql("""
                        select c.id, c.paciente_id, c.fecha from cita c join estado_cita e on e.id = c.estado_cita_id
                         where c.activo and e.codigo in ('PROGRAMADA','CONFIRMADA') and c.fecha between :d and :h
                        """).param("d", desde).param("h", hasta)
                        .query((rs, n) -> new Origen("cita", rs.getLong("id"), rs.getLong("paciente_id"),
                                rs.getDate("fecha").toLocalDate())).list();
            default:
                String tipo = "VACUNA_PROXIMA".equals(regla.evento()) ? "VACUNA" : "ANTIPARASITARIO";
                String tabla = "VACUNA".equals(tipo) ? "inmunizacion" : "desparasitacion";
                List<Origen> res = new ArrayList<>();
                for (PendienteResponse p : pendientes.entre(desde, hasta)) {
                    if (p.tipo().equals(tipo)) {
                        res.add(new Origen(tabla, p.aplicacionId(), p.pacienteId(), p.proximaFecha()));
                    }
                }
                return res;
        }
    }

    private void crear(Regla regla, Origen o, Resumen res) {
        Tutor tutor = tutorPrincipal(o.pacienteId());
        if (tutor == null || !tutor.activo()) {
            res.sinContacto++;
            return;
        }
        switch (contacto(tutor, regla.canal())) {
            case SIN_CONSENTIMIENTO -> {
                res.sinConsentimiento++;
                return;
            }
            case SIN_CONTACTO -> {
                res.sinContacto++;
                return;
            }
            default -> { }
        }
        Instant ideal = ZonaClinica.instante(o.fecha().plusDays(regla.dias()), horaEnvio);
        Instant programado = ideal.isBefore(Instant.now()) ? Instant.now() : ideal;
        int n = jdbc.sql("""
                insert into recordatorio (regla_id, tutor_id, paciente_id, tipo, referencia_tabla, referencia_id, canal,
                                          plantilla_id, programado_para, estado)
                values (:regla, :tutor, :paciente, :tipo, :tabla, :ref, :canal, :plantilla, :prog, 'PENDIENTE')
                on conflict (referencia_tabla, referencia_id, regla_id, canal) do nothing
                """).param("regla", regla.id()).param("tutor", tutor.id()).param("paciente", o.pacienteId())
                .param("tipo", regla.evento()).param("tabla", o.tabla()).param("ref", o.id())
                .param("canal", regla.canal()).param("plantilla", regla.plantillaId())
                .param("prog", Timestamp.from(programado)).update();
        if (n == 1) {
            res.generados++;
        } else {
            res.yaExistian++;
        }
    }

    private enum Contacto { OK, SIN_CONSENTIMIENTO, SIN_CONTACTO }

    private Contacto contacto(Tutor t, String canal) {
        boolean consentimiento = switch (canal) {
            case "EMAIL" -> t.aceptaEmail();
            case "WHATSAPP" -> t.aceptaWhatsapp();
            case "SMS" -> t.aceptaSms();
            default -> false;
        };
        if (!consentimiento || estaDadoDeBaja(t.id(), canal)) {
            return Contacto.SIN_CONSENTIMIENTO;
        }
        String dato = "EMAIL".equals(canal) ? t.email() : t.telefono();
        return dato == null || dato.isBlank() ? Contacto.SIN_CONTACTO : Contacto.OK;
    }

    private boolean estaDadoDeBaja(Long tutorId, String canal) {
        return jdbc.sql("""
                select count(*) from comunicacion_optout where tutor_id = :t and canal in (:c, 'TODOS')
                   and categoria in ('TODAS','SERVICIO')
                """).param("t", tutorId).param("c", canal).query(Long.class).single() > 0;
    }

    private Tutor tutorPrincipal(Long pacienteId) {
        return jdbc.sql(TUTOR_SQL + """
                 join paciente_tutor pt on pt.tutor_id = t.id where pt.paciente_id = :p
                 order by pt.es_principal desc, t.id limit 1
                """).param("p", pacienteId).query(RecordatorioService::mapearTutor).optional().orElse(null);
    }

    private Tutor tutor(Long id) {
        return jdbc.sql(TUTOR_SQL + " where t.id = :id").param("id", id).query(RecordatorioService::mapearTutor)
                .optional().orElse(null);
    }

    private static final String TUTOR_SQL = """
            select t.id, trim(t.nombres || ' ' || coalesce(t.apellidos, '')) as nombre, t.email, t.telefono,
                   t.acepta_email, t.acepta_whatsapp, t.acepta_sms, t.activo from tutor t
            """;

    private static Tutor mapearTutor(ResultSet rs, int n) throws SQLException {
        return new Tutor(rs.getLong("id"), rs.getString("nombre"), rs.getString("email"), rs.getString("telefono"),
                rs.getBoolean("acepta_email"), rs.getBoolean("acepta_whatsapp"), rs.getBoolean("acepta_sms"),
                rs.getBoolean("activo"));
    }

    // ------------------------------------------------------------------ envio

    private void procesar(Resumen res) {
        List<Long> ids = jdbc.sql("""
                select id from recordatorio where estado = 'PENDIENTE' and programado_para <= now()
                 order by programado_para, id limit :n
                """).param("n", LOTE_COLA).query(Long.class).list();
        for (Long id : ids) {
            try {
                tx.executeWithoutResult(s -> enviar(id, res));
            } catch (RuntimeException e) {
                log.error("Error procesando el recordatorio {}", id, e);
            }
        }
    }

    private void enviar(Long id, Resumen res) {
        var fila = jdbc.sql("""
                select tutor_id, tipo, referencia_tabla, referencia_id, canal, plantilla_id, intentos
                  from recordatorio where id = :id and estado = 'PENDIENTE' for update skip locked
                """).param("id", id).query((rs, n) -> new Object[] {rs.getLong("tutor_id"), rs.getString("tipo"),
                rs.getString("referencia_tabla"), rs.getLong("referencia_id"), rs.getString("canal"),
                rs.getObject("plantilla_id") == null ? null : rs.getLong("plantilla_id"), rs.getInt("intentos")})
                .optional();
        if (fila.isEmpty()) {
            return;
        }
        Object[] f = fila.get();
        Long tutorId = (Long) f[0];
        String canal = (String) f[4];
        int intentos = (Integer) f[6];

        Contexto ctx = contexto((String) f[2], (Long) f[3]);
        if (ctx == null) {
            cerrar(id, "CANCELADO", "El evento de origen ya no aplica (anulado, reemplazado o cancelado)");
            res.cancelados++;
            return;
        }
        Tutor tutor = tutor(tutorId);
        Contacto c = tutor == null ? Contacto.SIN_CONTACTO : contacto(tutor, canal);
        if (c == Contacto.SIN_CONSENTIMIENTO) {
            cerrar(id, "OMITIDO_OPTOUT", "El tutor no tiene consentimiento vigente para este canal");
            res.optout++;
            return;
        }
        if (c == Contacto.SIN_CONTACTO) {
            cerrar(id, "CANCELADO", "El tutor no tiene dato de contacto para el canal");
            res.cancelados++;
            return;
        }
        var plantilla = f[5] == null ? java.util.Optional.<String[]>empty()
                : jdbc.sql("select asunto, cuerpo from plantilla_mensaje where id = :id and activo")
                        .param("id", f[5]).query((rs, n) -> new String[] {rs.getString("asunto"), rs.getString("cuerpo")})
                        .optional();
        if (plantilla.isEmpty()) {
            registrarFallo(id, tutorId, canal, destino(tutor, canal), null, null, "Plantilla no disponible", intentos, res);
            return;
        }
        Map<String, String> vars = new HashMap<>();
        vars.put("tutor", tutor.nombre());
        vars.put("paciente", ctx.paciente());
        vars.put("vacuna", ctx.producto() == null ? "" : ctx.producto());
        vars.put("fecha", ctx.fecha().format(FECHA));
        vars.put("hora", ctx.hora() == null ? "" : ctx.hora().format(HORA));
        vars.put("clinica", clinica);
        vars.put("sede", clinica);
        String asunto = PlantillaService.renderizar(plantilla.get()[0], vars);
        String cuerpo = PlantillaService.renderizar(plantilla.get()[1], vars);
        if ("EMAIL".equals(canal)) {
            cuerpo += "\n\n--\nSi no desea recibir mas recordatorios: " + publicUrl
                    + "/api/comunicaciones/optout?token=" + optOut.token(tutorId, canal);
        }
        String destinatario = destino(tutor, canal);
        try {
            CanalEnvio.ResultadoEnvio r = canales.get(canal).enviar(new CanalEnvio.Mensaje(destinatario, asunto, cuerpo));
            String estado = r.simulado() ? "ENVIADO_SIMULADO" : "ENVIADO";
            jdbc.sql("""
                    update recordatorio set estado = :e, intentos = intentos + 1, ultimo_error = null, enviado_en = now()
                     where id = :id
                    """).param("e", estado).param("id", id).update();
            log(id, tutorId, canal, destinatario, asunto, cuerpo, estado, r.proveedor(), r.mensajeId(), null);
            if (r.simulado()) {
                res.simulados++;
            } else {
                res.enviados++;
            }
        } catch (CanalEnvio.EnvioFallidoException e) {
            registrarFallo(id, tutorId, canal, destinatario, asunto, cuerpo, e.getMessage(), intentos, res);
        }
    }

    private void registrarFallo(Long id, Long tutorId, String canal, String destinatario, String asunto, String cuerpo,
            String error, int intentos, Resumen res) {
        String msg = error == null ? "Error de envio" : error.length() > 500 ? error.substring(0, 500) : error;
        int nuevos = intentos + 1;
        log(id, tutorId, canal, destinatario, asunto, cuerpo, "FALLIDO", null, null, msg);
        if (nuevos >= maxIntentos) {
            jdbc.sql("update recordatorio set estado = 'FALLIDO', intentos = :n, ultimo_error = :e where id = :id")
                    .param("n", nuevos).param("e", msg).param("id", id).update();
            res.fallidos++;
        } else {
            jdbc.sql("""
                    update recordatorio set intentos = :n, ultimo_error = :e, programado_para = :p where id = :id
                    """).param("n", nuevos).param("e", msg)
                    .param("p", Timestamp.from(Instant.now().plusSeconds(60L * reintentoMinutos * nuevos)))
                    .param("id", id).update();
            res.reintentos++;
        }
    }

    private void cerrar(Long id, String estado, String motivo) {
        jdbc.sql("update recordatorio set estado = :e, ultimo_error = :m where id = :id")
                .param("e", estado).param("m", motivo).param("id", id).update();
    }

    private void log(Long recordatorioId, Long tutorId, String canal, String destinatario, String asunto, String cuerpo,
            String estado, String proveedor, String mensajeId, String error) {
        jdbc.sql("""
                insert into envio_log (recordatorio_id, tutor_id, canal, destinatario, asunto, cuerpo, estado, proveedor,
                                       proveedor_mensaje_id, error)
                values (:r, :t, :c, :d, :a, :b, :e, :p, :m, :err)
                """).param("r", recordatorioId).param("t", tutorId).param("c", canal)
                .param("d", destinatario == null ? "-" : destinatario).param("a", asunto).param("b", cuerpo)
                .param("e", estado).param("p", proveedor).param("m", mensajeId).param("err", error).update();
    }

    private static String destino(Tutor t, String canal) {
        return t == null ? null : "EMAIL".equals(canal) ? t.email() : t.telefono();
    }

    /** Datos del evento de origen si sigue vigente; {@code null} si ya no aplica. */
    private Contexto contexto(String tabla, Long id) {
        return switch (tabla) {
            case "inmunizacion" -> jdbc.sql("""
                    select i.fecha_proxima_dosis as fecha, v.nombre as producto, p.nombre as paciente
                      from inmunizacion i join vacuna v on v.id = i.vacuna_id join paciente p on p.id = i.paciente_id
                     where i.id = :id and i.estado = 'APLICADA' and i.fecha_proxima_dosis is not null
                       and not exists (select 1 from inmunizacion j where j.paciente_id = i.paciente_id
                            and j.vacuna_id = i.vacuna_id and j.estado = 'APLICADA'
                            and (j.fecha_aplicacion, j.id) > (i.fecha_aplicacion, i.id))
                    """).param("id", id).query(RecordatorioService::mapearContexto).optional().orElse(null);
            case "desparasitacion" -> jdbc.sql("""
                    select d.proxima_fecha as fecha, d.producto as producto, p.nombre as paciente
                      from desparasitacion d join paciente p on p.id = d.paciente_id
                     where d.id = :id and d.estado = 'APLICADA' and d.proxima_fecha is not null
                       and not exists (select 1 from desparasitacion j where j.paciente_id = d.paciente_id
                            and j.antiparasitario_id is not distinct from d.antiparasitario_id and j.estado = 'APLICADA'
                            and (j.fecha_aplicacion, j.id) > (d.fecha_aplicacion, d.id))
                    """).param("id", id).query(RecordatorioService::mapearContexto).optional().orElse(null);
            case "cita" -> jdbc.sql("""
                    select c.fecha as fecha, c.hora_inicio as hora, null as producto, p.nombre as paciente
                      from cita c join estado_cita e on e.id = c.estado_cita_id join paciente p on p.id = c.paciente_id
                     where c.id = :id and c.activo and e.codigo in ('PROGRAMADA','CONFIRMADA')
                    """).param("id", id).query((rs, n) -> new Contexto(rs.getDate("fecha").toLocalDate(),
                    rs.getTime("hora") == null ? null : rs.getTime("hora").toLocalTime(), rs.getString("paciente"), null))
                    .optional().orElse(null);
            default -> null;
        };
    }

    private static Contexto mapearContexto(ResultSet rs, int n) throws SQLException {
        return new Contexto(rs.getDate("fecha").toLocalDate(), null, rs.getString("paciente"), rs.getString("producto"));
    }

    // ------------------------------------------------------------------ consulta y gestion

    @Transactional(readOnly = true)
    public PageResponse<RecordatorioResponse> listar(String estado, String tipo, String canal, Long tutorId,
            Long pacienteId, LocalDate desde, LocalDate hasta, Pageable pageable) {
        String where = """
                 where (cast(:estado as text) is null or r.estado = cast(:estado as text))
                   and (cast(:tipo as text) is null or r.tipo = cast(:tipo as text))
                   and (cast(:canal as text) is null or r.canal = cast(:canal as text))
                   and (cast(:tutor as bigint) is null or r.tutor_id = cast(:tutor as bigint))
                   and (cast(:paciente as bigint) is null or r.paciente_id = cast(:paciente as bigint))
                   and (cast(:desde as timestamptz) is null or r.programado_para >= cast(:desde as timestamptz))
                   and (cast(:hasta as timestamptz) is null or r.programado_para < cast(:hasta as timestamptz))
                """;
        Timestamp d = desde == null ? null : Timestamp.from(ZonaClinica.instante(desde, LocalTime.MIDNIGHT));
        Timestamp h = hasta == null ? null : Timestamp.from(ZonaClinica.instante(hasta.plusDays(1), LocalTime.MIDNIGHT));
        long total = jdbc.sql("select count(*) from recordatorio r" + where).param("estado", estado).param("tipo", tipo)
                .param("canal", canal).param("tutor", tutorId).param("paciente", pacienteId).param("desde", d)
                .param("hasta", h).query(Long.class).single();
        List<RecordatorioResponse> items = jdbc.sql("""
                select r.*, g.codigo as regla, trim(t.nombres || ' ' || coalesce(t.apellidos, '')) as tutor,
                       p.nombre as paciente
                  from recordatorio r left join regla_recordatorio g on g.id = r.regla_id
                  join tutor t on t.id = r.tutor_id left join paciente p on p.id = r.paciente_id
                """ + where + " order by r.programado_para desc, r.id desc limit :size offset :offset")
                .param("estado", estado).param("tipo", tipo).param("canal", canal).param("tutor", tutorId)
                .param("paciente", pacienteId).param("desde", d).param("hasta", h).param("size", pageable.getPageSize())
                .param("offset", pageable.getOffset()).query(RecordatorioService::mapear).list();
        int paginas = (int) Math.ceil(total / (double) pageable.getPageSize());
        return new PageResponse<>(items, pageable.getPageNumber(), pageable.getPageSize(), total, paginas,
                pageable.getPageNumber() == 0, pageable.getPageNumber() + 1 >= paginas);
    }

    @Transactional(readOnly = true)
    public RecordatorioResponse obtener(Long id) {
        return jdbc.sql("""
                select r.*, g.codigo as regla, trim(t.nombres || ' ' || coalesce(t.apellidos, '')) as tutor,
                       p.nombre as paciente
                  from recordatorio r left join regla_recordatorio g on g.id = r.regla_id
                  join tutor t on t.id = r.tutor_id left join paciente p on p.id = r.paciente_id
                 where r.id = :id
                """).param("id", id).query(RecordatorioService::mapear).optional()
                .orElseThrow(() -> new ResourceNotFoundException("Recordatorio", id));
    }

    @Transactional
    public RecordatorioResponse cancelar(Long id) {
        obtener(id);
        String quien = UsuarioActual.username().orElse("sistema");
        int n = jdbc.sql("""
                update recordatorio set estado = 'CANCELADO', ultimo_error = :m where id = :id and estado = 'PENDIENTE'
                """).param("m", "Cancelado manualmente por " + quien).param("id", id).update();
        if (n == 0) {
            throw new ConflictException("Solo se pueden cancelar recordatorios PENDIENTES");
        }
        return obtener(id);
    }

    @Transactional(readOnly = true)
    public List<EnvioLogResponse> bitacora(Long recordatorioId) {
        obtener(recordatorioId);
        return jdbc.sql(ENVIO_SQL + " where recordatorio_id = :id order by id").param("id", recordatorioId)
                .query(RecordatorioService::mapearEnvio).list();
    }

    @Transactional(readOnly = true)
    public PageResponse<EnvioLogResponse> envios(String estado, Long tutorId, Pageable pageable) {
        String where = """
                 where (cast(:estado as text) is null or estado = cast(:estado as text))
                   and (cast(:tutor as bigint) is null or tutor_id = cast(:tutor as bigint))
                """;
        long total = jdbc.sql("select count(*) from envio_log" + where).param("estado", estado).param("tutor", tutorId)
                .query(Long.class).single();
        List<EnvioLogResponse> items = jdbc.sql(ENVIO_SQL + where + " order by id desc limit :size offset :offset")
                .param("estado", estado).param("tutor", tutorId).param("size", pageable.getPageSize())
                .param("offset", pageable.getOffset()).query(RecordatorioService::mapearEnvio).list();
        int paginas = (int) Math.ceil(total / (double) pageable.getPageSize());
        return new PageResponse<>(items, pageable.getPageNumber(), pageable.getPageSize(), total, paginas,
                pageable.getPageNumber() == 0, pageable.getPageNumber() + 1 >= paginas);
    }

    /** Cancela los recordatorios PENDIENTES de los eventos de origen indicados (los invoca el listener de eventos). */
    @Transactional
    public int cancelarPorOrigen(String tabla, List<Long> ids, String motivo) {
        if (ids.isEmpty()) {
            return 0;
        }
        return jdbc.sql("""
                update recordatorio set estado = 'CANCELADO', ultimo_error = :m
                 where estado = 'PENDIENTE' and referencia_tabla = :t and referencia_id in (:ids)
                """).param("m", motivo).param("t", tabla).param("ids", ids).update();
    }

    private static final String ENVIO_SQL = """
            select id, recordatorio_id, tutor_id, canal, destinatario, asunto, cuerpo, estado, proveedor,
                   proveedor_mensaje_id, error, enviado_en from envio_log
            """;

    private static EnvioLogResponse mapearEnvio(ResultSet rs, int n) throws SQLException {
        return new EnvioLogResponse(rs.getLong("id"), rs.getObject("recordatorio_id") == null ? null : rs.getLong("recordatorio_id"),
                rs.getObject("tutor_id") == null ? null : rs.getLong("tutor_id"), rs.getString("canal"),
                rs.getString("destinatario"), rs.getString("asunto"), rs.getString("cuerpo"), rs.getString("estado"),
                rs.getString("proveedor"), rs.getString("proveedor_mensaje_id"), rs.getString("error"),
                rs.getTimestamp("enviado_en").toInstant());
    }

    private static RecordatorioResponse mapear(ResultSet rs, int n) throws SQLException {
        return new RecordatorioResponse(rs.getLong("id"), rs.getObject("regla_id") == null ? null : rs.getLong("regla_id"),
                rs.getString("regla"), rs.getLong("tutor_id"), rs.getString("tutor"),
                rs.getObject("paciente_id") == null ? null : rs.getLong("paciente_id"), rs.getString("paciente"),
                rs.getString("tipo"), rs.getString("referencia_tabla"), rs.getLong("referencia_id"), rs.getString("canal"),
                rs.getTimestamp("programado_para").toInstant(), rs.getString("estado"), rs.getInt("intentos"),
                rs.getString("ultimo_error"),
                rs.getTimestamp("enviado_en") == null ? null : rs.getTimestamp("enviado_en").toInstant(),
                rs.getTimestamp("creado_en").toInstant());
    }
}
