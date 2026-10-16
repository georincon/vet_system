package com.veterinaria.compartir.application;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.veterinaria.comunicaciones.application.CanalEnvio;
import com.veterinaria.comunicaciones.application.OptOutService;
import com.veterinaria.comunicaciones.application.PlantillaService;
import com.veterinaria.comunicaciones.application.Telefonos;
import com.veterinaria.compartir.api.dto.CompartirDtos.CompartirRequest;
import com.veterinaria.compartir.api.dto.CompartirDtos.DocumentoEnvio;
import com.veterinaria.compartir.api.dto.CompartirDtos.DocumentoPrevio;
import com.veterinaria.compartir.api.dto.CompartirDtos.DocumentoRef;
import com.veterinaria.compartir.api.dto.CompartirDtos.EnlaceInfo;
import com.veterinaria.compartir.api.dto.CompartirDtos.EnvioResponse;
import com.veterinaria.compartir.api.dto.CompartirDtos.Previsualizacion;
import com.veterinaria.shared.clinico.PacienteBasico;
import com.veterinaria.shared.clinico.PacienteLector;
import com.veterinaria.shared.documentos.DocumentoCompartible;
import com.veterinaria.shared.documentos.DocumentoCompartible.Generado;
import com.veterinaria.shared.error.AccesoDenegadoException;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.error.ConflictException;
import com.veterinaria.shared.error.ResourceNotFoundException;
import com.veterinaria.shared.security.UsuarioActual;
import com.veterinaria.shared.text.Textos;
import com.veterinaria.shared.web.PageResponse;

/**
 * Compartir documentos clinicos con el tutor por EMAIL (PDF adjunto) o WHATSAPP (enlace seguro). Exige consentimiento del
 * canal, autorizacion de tratamiento de datos y ausencia de baja; solo documentos VALIDADOS/CERRADOS; el texto del mensaje
 * nunca incluye datos clinicos (solo saludo, nombre de la mascota, tipo de documento y enlace).
 */
@Service
public class CompartirService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> TIPOS_AUXILIAR = Set.of("FORMULA", "LABORATORIO", "IMAGEN", "PATOLOGIA", "CARNET");
    private static final Map<String, String> ETIQUETAS = Map.of("FORMULA", "formula medica", "LABORATORIO", "informe de laboratorio",
            "IMAGEN", "informe de imagenologia", "PATOLOGIA", "informe de patologia", "PROCEDIMIENTO", "reporte de procedimiento",
            "CARNET", "carnet de vacunacion", "RESUMEN_CONSULTA", "resumen de consulta");

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final Map<String, DocumentoCompartible> generadores;
    private final Map<String, CanalEnvio> canales;
    private final PacienteLector pacientes;
    private final OptOutService optOut;
    private final String publicUrl;
    private final String clinica;
    private final int vigenciaHoras;
    private final int maxDescargas;
    private final boolean auxiliarHabilitado;
    private final String pais;

    public CompartirService(JdbcClient jdbc, PlatformTransactionManager txm, List<DocumentoCompartible> generadores,
            List<CanalEnvio> canales, PacienteLector pacientes, OptOutService optOut,
            @Value("${vet.public-url:http://localhost:9010}") String publicUrl,
            @Value("${vet.clinica.nombre:Clinica Veterinaria}") String clinica,
            @Value("${vet.compartir.vigencia-horas:72}") int vigenciaHoras,
            @Value("${vet.compartir.max-descargas:5}") int maxDescargas,
            @Value("${vet.compartir.auxiliar-habilitado:false}") boolean auxiliarHabilitado,
            @Value("${vet.whatsapp.pais-defecto:57}") String pais) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txm);
        this.generadores = generadores.stream().collect(Collectors.toMap(DocumentoCompartible::tipo, Function.identity()));
        this.canales = canales.stream().collect(Collectors.toMap(CanalEnvio::canal, Function.identity()));
        this.pacientes = pacientes;
        this.optOut = optOut;
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
        this.clinica = clinica;
        this.vigenciaHoras = vigenciaHoras;
        this.maxDescargas = maxDescargas;
        this.auxiliarHabilitado = auxiliarHabilitado;
        this.pais = pais;
    }

    private record Tutor(Long id, String nombre, String email, String telefono, boolean aceptaEmail, boolean aceptaWhatsapp,
            boolean autoriza, boolean activo) {
    }

    private record Evaluacion(List<String> faltantes, String destino, String mascara, boolean consentimiento, boolean autoriza,
            boolean baja, boolean contacto) {
    }

    private record Doc(Long id, DocumentoRef ref, String titulo, String nombre, byte[] pdf) {
    }

    private record Nuevo(String token, Long docId, String nombre, String url, Instant expira) {
    }

    // ------------------------------------------------------------------ permisos

    private void exigirPermiso(List<String> tipos, Long responsableId, boolean soloVeterinario) {
        String rol = UsuarioActual.rol().orElse("");
        switch (rol) {
            case "ADMIN", "VETERINARIO" -> { }
            case "AUXILIAR" -> {
                if (!auxiliarHabilitado || soloVeterinario) {
                    throw new AccesoDenegadoException("El rol AUXILIAR no puede compartir documentos clinicos con el tutor.");
                }
                if (tipos.stream().anyMatch(t -> !TIPOS_AUXILIAR.contains(t))) {
                    throw new AccesoDenegadoException("El AUXILIAR solo puede compartir formula, resultados validados y carnet.");
                }
                if (tipos.isEmpty()) {
                    return;
                }
                if (responsableId == null) {
                    throw new BusinessException("Indique el veterinario responsable (veterinarioResponsableId)");
                }
                Long vet = jdbc.sql("select count(*) from usuario where id = :id and rol = 'VETERINARIO' and activo")
                        .param("id", responsableId).query(Long.class).single();
                if (vet == 0) {
                    throw new BusinessException("El veterinario responsable no existe o no esta activo");
                }
            }
            default -> throw new AccesoDenegadoException("Su rol no puede compartir documentos clinicos con el tutor.");
        }
    }

    // ------------------------------------------------------------------ tutor y consentimiento

    private Tutor resolverTutor(Long pacienteId, Long tutorId) {
        String sql = """
                select t.id, trim(t.nombres || ' ' || coalesce(t.apellidos, '')) as nombre, t.email, t.telefono,
                       t.acepta_email, t.acepta_whatsapp, t.autoriza_tratamiento_datos, t.activo
                  from tutor t join paciente_tutor pt on pt.tutor_id = t.id where pt.paciente_id = :p
                   and (cast(:t as bigint) is null or t.id = cast(:t as bigint))
                 order by pt.es_principal desc, t.id limit 1
                """;
        return jdbc.sql(sql).param("p", pacienteId).param("t", tutorId).query((rs, n) -> new Tutor(rs.getLong("id"),
                rs.getString("nombre"), rs.getString("email"), rs.getString("telefono"), rs.getBoolean("acepta_email"),
                rs.getBoolean("acepta_whatsapp"), rs.getBoolean("autoriza_tratamiento_datos"), rs.getBoolean("activo")))
                .optional().orElseThrow(() -> new BusinessException(tutorId == null
                        ? "El paciente no tiene tutor registrado" : "El tutor " + tutorId + " no esta vinculado al paciente"));
    }

    private Evaluacion evaluar(Tutor t, String canal) {
        List<String> f = new ArrayList<>();
        boolean email = "EMAIL".equals(canal);
        boolean consent = email ? t.aceptaEmail() : t.aceptaWhatsapp();
        boolean baja = jdbc.sql("""
                select count(*) from comunicacion_optout where tutor_id = :t and canal in (:c, 'TODOS')
                   and categoria in ('TODAS','SERVICIO')
                """).param("t", t.id()).param("c", canal).query(Long.class).single() > 0;
        String destino = email ? Textos.limpiar(t.email()) : Telefonos.aE164(t.telefono(), pais);
        if (!t.activo()) {
            f.add("El tutor esta inactivo");
        }
        if (!t.autoriza()) {
            f.add("El tutor no ha autorizado el tratamiento de datos personales (autorizaTratamientoDatos)");
        }
        if (!consent) {
            f.add("El tutor no ha dado su consentimiento para recibir comunicaciones por " + canal
                    + (email ? " (aceptaEmail)" : " (aceptaWhatsapp)"));
        }
        if (baja) {
            f.add("El tutor se dio de baja de las comunicaciones por " + canal + " (opt-out)");
        }
        if (destino == null) {
            f.add(email ? "El tutor no tiene correo registrado"
                    : "El telefono del tutor no es un numero valido para WhatsApp (se espera formato E.164, p. ej. +573001234567)");
        }
        return new Evaluacion(f, destino, Telefonos.enmascarar(destino), consent, t.autoriza(), baja, destino != null);
    }

    // ------------------------------------------------------------------ documentos

    private Generado generar(DocumentoRef ref, Long pacienteId) {
        DocumentoCompartible g = generadores.get(ref.tipo());
        if (g == null) {
            throw new BusinessException("Tipo de documento no soportado: " + ref.tipo());
        }
        Generado d = g.generar(ref.id(), ref.itemId());
        if (!pacienteId.equals(d.pacienteId())) {
            throw new BusinessException("El documento " + ref.tipo() + " " + ref.id() + " no pertenece al paciente " + pacienteId);
        }
        return d;
    }

    private static List<DocumentoRef> sinDuplicados(List<DocumentoRef> refs) {
        return new ArrayList<>(new LinkedHashSet<>(refs));
    }

    // ------------------------------------------------------------------ mensajes

    private String[] plantilla(List<DocumentoRef> docs, String canal) {
        String codigo = docs.size() == 1 ? "DOC_" + docs.get(0).tipo() : "DOC_VARIOS";
        return jdbc.sql("select asunto, cuerpo from plantilla_mensaje where codigo = :c and canal = :k and idioma = 'es' and activo")
                .param("c", codigo).param("k", canal).query((rs, n) -> new String[] {rs.getString(1), rs.getString(2)})
                .optional().orElseGet(() -> new String[] {"Documentos de {{paciente}} - {{clinica}}",
                        "Hola {{tutor}}, te compartimos " + ("EMAIL".equals(canal) ? "los documentos adjuntos" : "los documentos: {{enlace}}")
                                + " de {{paciente}}. {{clinica}}"});
    }

    private Map<String, String> variables(Tutor t, String paciente, List<DocumentoRef> docs, String enlace) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("tutor", t.nombre());
        v.put("paciente", paciente);
        v.put("clinica", clinica);
        v.put("sede", clinica);
        v.put("documento", docs.stream().map(d -> ETIQUETAS.getOrDefault(d.tipo(), d.tipo())).distinct().collect(Collectors.joining(", ")));
        v.put("enlace", enlace == null ? "" : enlace);
        return v;
    }

    // ------------------------------------------------------------------ previsualizar

    public Previsualizacion previsualizar(CompartirRequest r) {
        List<DocumentoRef> refs = sinDuplicados(r.documentos());
        exigirPermiso(refs.stream().map(DocumentoRef::tipo).toList(), r.veterinarioResponsableId(), false);
        PacienteBasico p = pacientes.obtener(r.pacienteId());
        Tutor t = resolverTutor(r.pacienteId(), r.tutorId());
        Evaluacion ev = evaluar(t, r.canal());
        List<DocumentoPrevio> docs = new ArrayList<>();
        for (DocumentoRef ref : refs) {
            try {
                Generado g = generar(ref, r.pacienteId());
                docs.add(new DocumentoPrevio(ref.tipo(), ref.id(), g.titulo(), true, null));
            } catch (BusinessException | ResourceNotFoundException e) {
                docs.add(new DocumentoPrevio(ref.tipo(), ref.id(), null, false, e.getMessage()));
            }
        }
        String[] pl = plantilla(refs, r.canal());
        Map<String, String> vars = variables(t, p.nombre(), refs, "[enlace seguro]");
        String cuerpo = PlantillaService.renderizar(pl[1], vars) + nota(r.mensaje());
        return new Previsualizacion(t.id(), t.nombre(), r.canal(), ev.mascara(), ev.consentimiento(), ev.autoriza(), ev.baja(),
                ev.contacto(), ev.faltantes(), ev.faltantes().isEmpty() && docs.stream().allMatch(DocumentoPrevio::enviable),
                PlantillaService.renderizar(pl[0], vars), cuerpo, docs);
    }

    private static String nota(String mensaje) {
        String m = Textos.limpiar(mensaje);
        return m == null ? "" : "\n\n" + m;
    }

    // ------------------------------------------------------------------ enviar

    public EnvioResponse enviar(CompartirRequest r) {
        List<DocumentoRef> refs = sinDuplicados(r.documentos());
        exigirPermiso(refs.stream().map(DocumentoRef::tipo).toList(), r.veterinarioResponsableId(), false);
        PacienteBasico p = pacientes.obtener(r.pacienteId());
        Tutor t = resolverTutor(r.pacienteId(), r.tutorId());
        Evaluacion ev = evaluar(t, r.canal());
        if (!ev.faltantes().isEmpty()) {
            throw new BusinessException("No se puede compartir con el tutor: " + String.join("; ", ev.faltantes()));
        }
        List<Doc> docs = new ArrayList<>();
        for (DocumentoRef ref : refs) {
            Generado g = generar(ref, r.pacienteId());
            docs.add(new Doc(null, ref, g.titulo(), g.nombreArchivo(), g.pdf()));
        }
        Long usuario = UsuarioActual.id().orElse(null);
        Long envioId = tx.execute(s -> {
            Long id = jdbc.sql("""
                    insert into compartir_envio (paciente_id, tutor_id, canal, destinatario_mascara, estado, responsable_id, creado_por)
                    values (:p, :t, :c, :m, 'PENDIENTE', :resp, :u) returning id
                    """).param("p", p.id()).param("t", t.id()).param("c", r.canal()).param("m", ev.mascara())
                    .param("resp", r.veterinarioResponsableId()).param("u", usuario).query(Long.class).single();
            for (Doc d : docs) {
                jdbc.sql("""
                        insert into compartir_envio_documento (envio_id, tipo, documento_id, item_id, titulo, nombre_archivo,
                                                               tamano_bytes, hash_sha256, contenido)
                        values (:e, :t, :d, :i, :ti, :n, :sz, :h, :b)
                        """).param("e", id).param("t", d.ref().tipo()).param("d", d.ref().id()).param("i", d.ref().itemId())
                        .param("ti", d.titulo()).param("n", d.nombre()).param("sz", d.pdf().length).param("h", sha256(d.pdf()))
                        .param("b", d.pdf()).update();
            }
            return id;
        });
        return ejecutar(envioId, Textos.limpiar(r.mensaje()));
    }

    public EnvioResponse reenviar(Long id) {
        existe(id);
        List<String> tipos = jdbc.sql("select tipo from compartir_envio_documento where envio_id = :id").param("id", id)
                .query(String.class).list();
        Long resp = jdbc.sql("select coalesce(responsable_id, 0) from compartir_envio where id = :id").param("id", id)
                .query(Long.class).single();
        if (resp == 0) {
            resp = null;
        }
        exigirPermiso(tipos, resp, false);
        return ejecutar(id, null);
    }

    private EnvioResponse ejecutar(Long envioId, String nota) {
        var e = jdbc.sql("select paciente_id, tutor_id, canal from compartir_envio where id = :id").param("id", envioId)
                .query((rs, n) -> new Object[] {rs.getLong(1), rs.getLong(2), rs.getString(3)}).single();
        Long pacienteId = (Long) e[0];
        String canal = (String) e[2];
        PacienteBasico p = pacientes.obtener(pacienteId);
        Tutor t = resolverTutor(pacienteId, (Long) e[1]);
        Evaluacion ev = evaluar(t, canal);
        if (!ev.faltantes().isEmpty()) {
            throw new BusinessException("No se puede compartir con el tutor: " + String.join("; ", ev.faltantes()));
        }
        List<Doc> docs = jdbc.sql("""
                select id, tipo, documento_id, item_id, titulo, nombre_archivo, contenido from compartir_envio_documento
                 where envio_id = :e order by id
                """).param("e", envioId).query((rs, n) -> new Doc(rs.getLong("id"),
                new DocumentoRef(rs.getString("tipo"), rs.getLong("documento_id"), (Long) rs.getObject("item_id")),
                rs.getString("titulo"), rs.getString("nombre_archivo"), rs.getBytes("contenido"))).list();
        List<DocumentoRef> refs = docs.stream().map(Doc::ref).toList();
        boolean wa = "WHATSAPP".equals(canal);

        // enlaces nuevos (los anteriores quedan revocados); el token solo se conoce aqui
        List<Nuevo> enlaces = wa ? tx.execute(s -> {
            jdbc.sql("update compartir_enlace set revocado_en = now(), revocado_por = :u where envio_id = :e and revocado_en is null")
                    .param("u", UsuarioActual.id().orElse(null)).param("e", envioId).update();
            List<Nuevo> l = new ArrayList<>();
            Instant expira = Instant.now().plusSeconds(3600L * vigenciaHoras);
            for (Doc d : docs) {
                String token = nuevoToken();
                jdbc.sql("""
                        insert into compartir_enlace (envio_documento_id, envio_id, token_hash, token_prefijo, expira_en, max_descargas)
                        values (:d, :e, :h, :pre, :x, :m)
                        """).param("d", d.id()).param("e", envioId).param("h", sha256(token.getBytes(StandardCharsets.UTF_8)))
                        .param("pre", token.substring(0, 6)).param("x", Timestamp.from(expira)).param("m", maxDescargas).update();
                l.add(new Nuevo(token, d.id(), d.nombre(), publicUrl + "/api/publico/documentos/" + token, expira));
            }
            return l;
        }) : List.of();

        String[] pl = plantilla(refs, canal);
        String asunto = PlantillaService.renderizar(pl[0], variables(t, p.nombre(), refs, null));
        String guardado = PlantillaService.renderizar(pl[1], variables(t, p.nombre(), refs, "[enlace seguro]")) + nota(nota);
        String estado;
        String error = null;
        String proveedor = null;
        List<String> ids = new ArrayList<>();
        String urlWaMe = null;
        try {
            CanalEnvio adaptador = canales.get(canal);
            if (adaptador == null) {
                throw new CanalEnvio.EnvioFallidoException("Canal " + canal + " no disponible", null);
            }
            boolean simulado = false;
            if (!wa) {
                String cuerpo = PlantillaService.renderizar(pl[1], variables(t, p.nombre(), refs, null)) + nota(nota)
                        + "\n\n--\nSi no desea recibir mas mensajes de la clinica: " + publicUrl
                        + "/api/comunicaciones/optout?token=" + optOut.token(t.id(), "EMAIL");
                List<CanalEnvio.Adjunto> adj = docs.stream()
                        .map(d -> new CanalEnvio.Adjunto(d.nombre(), "application/pdf", d.pdf())).toList();
                var res = adaptador.enviar(new CanalEnvio.Mensaje(ev.destino(), asunto, cuerpo, adj, List.of(), Map.of()));
                simulado = res.simulado();
                proveedor = res.proveedor();
                ids.add(res.mensajeId());
            } else {
                for (Nuevo n : enlaces) {
                    Doc d = docs.stream().filter(x -> x.id().equals(n.docId())).findFirst().orElseThrow();
                    List<DocumentoRef> uno = List.of(d.ref());
                    String[] plUno = plantilla(uno, canal);
                    Map<String, String> vars = variables(t, p.nombre(), uno, n.url());
                    String cuerpo = PlantillaService.renderizar(plUno[1], vars) + nota(nota);
                    var res = adaptador.enviar(new CanalEnvio.Mensaje(ev.destino(), null, cuerpo, List.of(),
                            List.of(new CanalEnvio.DocumentoEnlace(n.nombre(), n.url())), vars));
                    simulado = res.simulado();
                    proveedor = res.proveedor();
                    ids.add(res.mensajeId());
                }
                String todos = enlaces.stream().map(Nuevo::url).collect(Collectors.joining("\n"));
                String texto = PlantillaService.renderizar(pl[1], variables(t, p.nombre(), refs, todos)) + nota(nota);
                urlWaMe = "https://wa.me/" + ev.destino().substring(1) + "?text="
                        + URLEncoder.encode(texto, StandardCharsets.UTF_8).replace("+", "%20");
            }
            estado = simulado ? "ENVIADO_SIMULADO" : "ENVIADO";
        } catch (CanalEnvio.EnvioFallidoException ex) {
            estado = "FALLIDO";
            error = recortar(ex.getMessage());
            if (wa) {
                String todos = enlaces.stream().map(Nuevo::url).collect(Collectors.joining("\n"));
                String texto = PlantillaService.renderizar(pl[1], variables(t, p.nombre(), refs, todos)) + nota(nota);
                urlWaMe = "https://wa.me/" + ev.destino().substring(1) + "?text="
                        + URLEncoder.encode(texto, StandardCharsets.UTF_8).replace("+", "%20");
            }
        }
        String estadoFinal = estado;
        String errorFinal = error;
        String proveedorFinal = proveedor;
        tx.executeWithoutResult(s -> jdbc.sql("""
                update compartir_envio set estado = :e, intentos = intentos + 1, ultimo_error = :err, proveedor = :pr,
                       proveedor_mensaje_id = :mid, asunto = :a, mensaje = :m, revocado_en = null, revocado_por = null,
                       motivo_revocacion = null, enviado_en = case when :e = 'FALLIDO' then enviado_en else now() end
                 where id = :id
                """).param("e", estadoFinal).param("err", errorFinal).param("pr", proveedorFinal)
                .param("mid", recortar(String.join(",", ids), 120)).param("a", asunto).param("m", guardado)
                .param("id", envioId).update());
        Map<Long, String> urls = enlaces.stream().collect(Collectors.toMap(Nuevo::docId, Nuevo::url));
        return cargar(envioId, urls, urlWaMe);
    }

    // ------------------------------------------------------------------ revocar / historial

    public EnvioResponse revocar(Long id, String motivo) {
        exigirPermiso(List.of(), null, true);
        existe(id);
        Integer n = tx.execute(s -> {
            int c = jdbc.sql("update compartir_enlace set revocado_en = now(), revocado_por = :u where envio_id = :e and revocado_en is null")
                    .param("u", UsuarioActual.id().orElse(null)).param("e", id).update();
            if (c > 0) {
                jdbc.sql("update compartir_envio set revocado_en = now(), revocado_por = :u, motivo_revocacion = :m where id = :e")
                        .param("u", UsuarioActual.id().orElse(null)).param("m", Textos.limpiar(motivo)).param("e", id).update();
            }
            return c;
        });
        if (n == null || n == 0) {
            throw new ConflictException("El envio no tiene enlaces activos que revocar (los envios por EMAIL llevan el PDF adjunto "
                    + "y no se pueden revocar; los enlaces ya revocados no se repiten)");
        }
        return cargar(id, Map.of(), null);
    }

    public PageResponse<EnvioResponse> historial(Long pacienteId, Pageable pageable) {
        exigirPermiso(List.of(), null, false);
        long total = jdbc.sql("select count(*) from compartir_envio where (cast(:p as bigint) is null or paciente_id = cast(:p as bigint))")
                .param("p", pacienteId).query(Long.class).single();
        List<Long> ids = jdbc.sql("""
                select id from compartir_envio where (cast(:p as bigint) is null or paciente_id = cast(:p as bigint))
                 order by id desc limit :s offset :o
                """).param("p", pacienteId).param("s", pageable.getPageSize()).param("o", pageable.getOffset())
                .query(Long.class).list();
        List<EnvioResponse> items = ids.stream().map(i -> cargar(i, Map.of(), null)).toList();
        int paginas = (int) Math.ceil(total / (double) pageable.getPageSize());
        return new PageResponse<>(items, pageable.getPageNumber(), pageable.getPageSize(), total, paginas,
                pageable.getPageNumber() == 0, pageable.getPageNumber() + 1 >= paginas);
    }

    public EnvioResponse obtener(Long id) {
        exigirPermiso(List.of(), null, false);
        return cargar(id, Map.of(), null);
    }

    private void existe(Long id) {
        if (jdbc.sql("select count(*) from compartir_envio where id = :id").param("id", id).query(Long.class).single() == 0) {
            throw new ResourceNotFoundException("Envio", id);
        }
    }

    private EnvioResponse cargar(Long id, Map<Long, String> urls, String urlWaMe) {
        List<DocumentoEnvio> docs = jdbc.sql("""
                select id, tipo, documento_id, item_id, titulo, nombre_archivo, tamano_bytes, hash_sha256
                  from compartir_envio_documento where envio_id = :e order by id
                """).param("e", id).query((rs, n) -> new DocumentoEnvio(rs.getLong("id"), rs.getString("tipo"),
                rs.getLong("documento_id"), (Long) rs.getObject("item_id"), rs.getString("titulo"),
                rs.getString("nombre_archivo"), rs.getInt("tamano_bytes"), rs.getString("hash_sha256"))).list();
        List<EnlaceInfo> enlaces = jdbc.sql("""
                select id, envio_documento_id, token_prefijo, expira_en, max_descargas, descargas, revocado_en, ultimo_acceso
                  from compartir_enlace where envio_id = :e order by id
                """).param("e", id).query((rs, n) -> new EnlaceInfo(rs.getLong("id"), rs.getLong("envio_documento_id"),
                rs.getString("token_prefijo"), urls.get(rs.getLong("envio_documento_id")),
                rs.getTimestamp("expira_en").toInstant(), rs.getInt("max_descargas"), rs.getInt("descargas"),
                rs.getTimestamp("revocado_en") != null, rs.getTimestamp("ultimo_acceso") == null ? null
                        : rs.getTimestamp("ultimo_acceso").toInstant())).list();
        return jdbc.sql("""
                select e.*, u.nombre as creado_por_nombre from compartir_envio e left join usuario u on u.id = e.creado_por
                 where e.id = :id
                """).param("id", id).query((rs, n) -> new EnvioResponse(rs.getLong("id"), rs.getLong("paciente_id"),
                rs.getLong("tutor_id"), rs.getString("canal"), rs.getString("destinatario_mascara"), rs.getString("asunto"),
                rs.getString("mensaje"), rs.getString("estado"), rs.getInt("intentos"), rs.getString("ultimo_error"),
                rs.getString("proveedor"), (Long) rs.getObject("creado_por"), rs.getString("creado_por_nombre"),
                (Long) rs.getObject("responsable_id"), rs.getTimestamp("creado_en").toInstant(),
                rs.getTimestamp("enviado_en") == null ? null : rs.getTimestamp("enviado_en").toInstant(),
                rs.getTimestamp("revocado_en") != null,
                rs.getTimestamp("revocado_en") == null ? null : rs.getTimestamp("revocado_en").toInstant(), docs, enlaces,
                urlWaMe)).optional().orElseThrow(() -> new ResourceNotFoundException("Envio", id));
    }

    // ------------------------------------------------------------------ utilidades

    static String nuevoToken() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public static String sha256(byte[] datos) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(datos));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String recortar(String s) {
        return recortar(s, 500);
    }

    private static String recortar(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max) : s;
    }
}
