package com.veterinaria.laboratorio.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.laboratorio.api.dto.LabDtos.ImportacionResponse;
import com.veterinaria.laboratorio.api.dto.LabDtos.LineaImportacion;
import com.veterinaria.laboratorio.api.dto.LabDtos.LineaJson;
import com.veterinaria.laboratorio.domain.CsvParser;
import com.veterinaria.laboratorio.domain.Evaluado;
import com.veterinaria.laboratorio.domain.Hl7Parser;
import com.veterinaria.laboratorio.domain.LineaEntrada;
import com.veterinaria.laboratorio.domain.ResultadoInvalidoException;
import com.veterinaria.laboratorio.domain.TipoExamen;
import com.veterinaria.laboratorio.internal.CatalogoRepository;
import com.veterinaria.laboratorio.internal.CatalogoRepository.ParametroInfo;
import com.veterinaria.laboratorio.internal.OrdenRepository;
import com.veterinaria.laboratorio.internal.OrdenRepository.ItemRow;
import com.veterinaria.laboratorio.internal.OrdenRepository.OrdenRow;
import com.veterinaria.shared.clinico.PacienteBasico;
import com.veterinaria.shared.clinico.PacienteLector;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.error.ConflictException;
import com.veterinaria.shared.security.UsuarioActual;

import tools.jackson.databind.json.JsonMapper;

/**
 * Importacion de resultados desde HL7 v2 (ORU^R01), CSV o JSON. Cada resultado se empareja con el parametro de la orden
 * por LOINC o por codigo local; los valores entran como BORRADOR (origen ARCHIVO o INTEGRACION) y requieren validacion
 * del veterinario. {@code dryRun} informa que se importaria y que no coincide sin escribir nada.
 */
@Service
public class ImportacionService {

    public static final String ESTADO_IMPORTABLE = "IMPORTABLE";
    public static final String ESTADO_IMPORTADO = "IMPORTADO";

    /** Parametros de una importacion. */
    public record Comando(String formato, String contenido, List<LineaJson> json, Long ordenId, String ordenCodigo,
            boolean dryRun, String origen, String nombreArchivo) {
    }

    private record Destino(OrdenRow orden, String error) {
    }

    private record Aplicable(LineaEntrada entrada, OrdenRow orden, ItemRow item, ParametroInfo parametro, Evaluado evaluado) {
    }

    private final OrdenRepository ordenes;
    private final CatalogoRepository catalogo;
    private final PacienteLector pacientes;
    private final ResultadoService resultadoService;
    private final OrdenService ordenService;
    private final JdbcClient jdbc;
    private final JsonMapper mapper;

    public ImportacionService(OrdenRepository ordenes, CatalogoRepository catalogo, PacienteLector pacientes,
            ResultadoService resultadoService, OrdenService ordenService, JdbcClient jdbc, JsonMapper mapper) {
        this.ordenes = ordenes;
        this.catalogo = catalogo;
        this.pacientes = pacientes;
        this.resultadoService = resultadoService;
        this.ordenService = ordenService;
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public String plantillaCsv() {
        return CsvParser.PLANTILLA;
    }

    @Transactional
    public ImportacionResponse importar(Comando cmd) {
        List<String> avisos = new ArrayList<>();
        List<LineaEntrada> entradas = interpretar(cmd, avisos);
        String origen = cmd.origen() == null ? "ARCHIVO" : cmd.origen();
        Long usuario = UsuarioActual.id().orElse(null);

        // 1) resolver orden de cada linea (la indicada en la solicitud manda sobre la de la linea)
        Map<String, Destino> destinos = new HashMap<>();
        Map<Long, List<ItemRow>> itemsPorOrden = new HashMap<>();
        Map<Long, List<ParametroInfo>> parametrosPorExamen = new HashMap<>();
        Map<Long, PacienteBasico> pacientesPorId = new HashMap<>();

        List<LineaImportacion> salida = new ArrayList<>();
        List<Aplicable> aplicables = new ArrayList<>();
        int noCoinciden = 0;
        int rechazados = 0;
        Set<Long> ordenesTocadas = new LinkedHashSet<>();

        for (LineaEntrada e : entradas) {
            String clave = (e.ordenCodigo() == null ? "" : e.ordenCodigo().toUpperCase(Locale.ROOT)) + "|"
                    + (e.pacienteCodigo() == null ? "" : e.pacienteCodigo().toUpperCase(Locale.ROOT));
            Destino destino = destinos.computeIfAbsent(clave, k -> resolverOrden(cmd, e));
            if (destino.orden() == null) {
                salida.add(linea(e, "SIN_ORDEN", null, null, null, null, null, destino.error()));
                noCoinciden++;
                continue;
            }
            OrdenRow orden = destino.orden();
            if (e.ordenCodigo() != null && cmd.ordenId() != null && !e.ordenCodigo().equalsIgnoreCase(orden.codigo())) {
                salida.add(linea(e, "ORDEN_NO_COINCIDE", null, null, null, null, null,
                        "La linea apunta a la orden " + e.ordenCodigo() + " pero se importa a " + orden.codigo()));
                rechazados++;
                continue;
            }
            List<ItemRow> items = itemsPorOrden.computeIfAbsent(orden.id(), ordenes::items);
            List<LineaImportacion> deEstaLinea = new ArrayList<>();
            boolean hubo = false;
            for (ItemRow item : items) {
                if (TipoExamen.de(item.tipo()) != TipoExamen.RESULTADOS) {
                    continue;
                }
                if (e.examenCodigo() != null && !e.examenCodigo().equalsIgnoreCase(item.examenCodigo())) {
                    continue;
                }
                for (ParametroInfo p : parametrosPorExamen.computeIfAbsent(item.examenId(), catalogo::parametrosInfo)) {
                    if (!p.activo() || !coincide(e, p)) {
                        continue;
                    }
                    hubo = true;
                    String bloqueo = bloqueo(orden, item);
                    if (bloqueo != null) {
                        deEstaLinea.add(linea(e, bloqueo.split(":")[0], item, p, null, null, orden.codigo(), bloqueo.split(":", 2)[1]));
                        rechazados++;
                        continue;
                    }
                    try {
                        PacienteBasico pac = pacientesPorId.computeIfAbsent(orden.pacienteId(), pacientes::obtener);
                        Evaluado ev = resultadoService.evaluar(pac, p, null, e.valor(), e.unidad());
                        aplicables.add(new Aplicable(e, orden, item, p, ev));
                        deEstaLinea.add(linea(e, ESTADO_IMPORTABLE, item, p, ev.bandera(), ev.critico(), orden.codigo(), null));
                        ordenesTocadas.add(orden.id());
                    } catch (ResultadoInvalidoException ex) {
                        deEstaLinea.add(linea(e, ex.codigo(), item, p, null, null, orden.codigo(), ex.getMessage()));
                        rechazados++;
                    }
                }
            }
            if (!hubo) {
                deEstaLinea.add(linea(e, "SIN_COINCIDENCIA", null, null, null, null, orden.codigo(),
                        "Ningun parametro de la orden coincide con " + descripcion(e)));
                noCoinciden++;
            }
            salida.addAll(deEstaLinea);
        }

        // 2) dryRun: solo informa
        if (cmd.dryRun()) {
            return new ImportacionResponse(cmd.formato(), origen, true, entradas.size(), aplicables.size(), noCoinciden,
                    rechazados, List.copyOf(ordenesTocadas), null, salida, avisos);
        }
        if (aplicables.isEmpty()) {
            throw new BusinessException("Ninguna linea se pudo importar (" + noCoinciden + " sin coincidencia, " + rechazados
                    + " rechazadas). Use dryRun=true para ver el detalle.");
        }

        // 3) importacion real
        String contenido = contenidoCanonico(cmd, entradas);
        String hash = sha256(contenido);
        if (jdbc.sql("select count(*) from lab_importacion where hash_sha256 = :h").param("h", hash).query(Long.class).single() > 0) {
            throw new ConflictException("Este mensaje/archivo ya fue importado (mismo contenido)");
        }
        List<LineaImportacion> finales = new ArrayList<>(salida.size());
        for (LineaImportacion l : salida) {
            finales.add(ESTADO_IMPORTABLE.equals(l.estado()) ? conEstado(l, ESTADO_IMPORTADO) : l);
        }
        long importacionId = registrarImportacion(cmd, origen, contenido, hash, finales, entradas.size(), aplicables.size(), usuario);
        for (Long ordenId : ordenesTocadas) {
            ordenes.bloquear(ordenId);
        }
        Set<Long> itemsMarcados = new LinkedHashSet<>();
        Map<Long, Integer> porOrden = new HashMap<>();
        for (Aplicable a : aplicables) {
            String obs = a.entrada().observacion();
            resultadoService.guardarBorrador(a.item(), a.parametro(), a.evaluado(), origen, usuario,
                    obs == null || obs.length() <= 500 ? obs : obs.substring(0, 500), importacionId);
            itemsMarcados.add(a.item().id());
            porOrden.merge(a.orden().id(), 1, Integer::sum);
        }
        for (Long itemId : itemsMarcados) {
            ordenes.marcarCapturado(itemId, usuario);
        }
        for (Long ordenId : ordenesTocadas) {
            OrdenRow actual = ordenService.orden(ordenId);
            ordenService.iniciarProceso(actual, usuario, origen);
            ordenes.evento(ordenId, null, "IMPORTACION", null, null, porOrden.get(ordenId) + " resultado(s) importado(s) por "
                    + cmd.formato() + " (importacion " + importacionId + ")", usuario, origen);
        }
        return new ImportacionResponse(cmd.formato(), origen, false, entradas.size(), aplicables.size(), noCoinciden,
                rechazados, List.copyOf(ordenesTocadas), importacionId, finales, avisos);
    }

    // ------------------------------------------------------------------ interpretacion

    private List<LineaEntrada> interpretar(Comando cmd, List<String> avisos) {
        return switch (cmd.formato()) {
            case "HL7" -> {
                var m = Hl7Parser.parsear(cmd.contenido());
                avisos.addAll(m.advertencias());
                yield m.lineas();
            }
            case "CSV" -> CsvParser.parsear(cmd.contenido());
            case "JSON" -> {
                if (cmd.json() == null || cmd.json().isEmpty()) {
                    throw new BusinessException("Indique al menos un resultado en 'resultados'");
                }
                List<LineaEntrada> r = new ArrayList<>();
                int n = 1;
                for (LineaJson l : cmd.json()) {
                    if ((l.parametroCodigo() == null || l.parametroCodigo().isBlank()) && (l.loinc() == null || l.loinc().isBlank())) {
                        throw new BusinessException("Resultado " + n + ": indique parametroCodigo o loinc");
                    }
                    r.add(new LineaEntrada(n++, cmd.ordenCodigo(), null, vacio(l.examenCodigo()), vacio(l.parametroCodigo()),
                            vacio(l.loinc()), null, l.valor(), vacio(l.unidad()), vacio(l.observacion())));
                }
                yield r;
            }
            default -> throw new BusinessException("Formato no soportado: " + cmd.formato());
        };
    }

    private Destino resolverOrden(Comando cmd, LineaEntrada e) {
        if (cmd.ordenId() != null) {
            return ordenes.orden(cmd.ordenId()).map(o -> new Destino(o, null))
                    .orElseGet(() -> new Destino(null, "La orden " + cmd.ordenId() + " no existe"));
        }
        String codigo = e.ordenCodigo() != null ? e.ordenCodigo() : cmd.ordenCodigo();
        if (codigo != null) {
            return ordenes.ordenPorCodigo(codigo.trim()).map(o -> new Destino(o, null))
                    .orElseGet(() -> new Destino(null, "La orden " + codigo + " no existe"));
        }
        if (e.pacienteCodigo() != null) {
            Optional<Long> paciente = ordenes.pacientePorCodigo(e.pacienteCodigo());
            if (paciente.isEmpty()) {
                return new Destino(null, "El paciente " + e.pacienteCodigo() + " no existe");
            }
            List<Long> abiertas = ordenes.ordenesAbiertasDePaciente(paciente.get());
            if (abiertas.size() == 1) {
                return new Destino(ordenes.orden(abiertas.get(0)).orElseThrow(), null);
            }
            return new Destino(null, abiertas.isEmpty() ? "El paciente " + e.pacienteCodigo() + " no tiene ordenes abiertas"
                    : "El paciente " + e.pacienteCodigo() + " tiene " + abiertas.size() + " ordenes abiertas; indique la orden");
        }
        return new Destino(null, "No se pudo identificar la orden: indique ordenId, ordenCodigo (OBR-2 en HL7) o el paciente");
    }

    private static boolean coincide(LineaEntrada e, ParametroInfo p) {
        for (String c : new String[] {e.loinc(), e.parametroCodigo()}) {
            if (c == null) {
                continue;
            }
            if (p.codigoLoinc() != null && c.equalsIgnoreCase(p.codigoLoinc())) {
                return true;
            }
        }
        return e.parametroCodigo() != null && e.parametroCodigo().equalsIgnoreCase(p.codigo());
    }

    /** Motivo por el que no se puede escribir en este item ("CODIGO:mensaje") o null. */
    private static String bloqueo(OrdenRow orden, ItemRow item) {
        if ("CANCELADA".equals(orden.estado()) || "COMPLETADA".equals(orden.estado())) {
            return "ORDEN_CERRADA:La orden " + orden.codigo() + " esta " + orden.estado();
        }
        if ("VALIDADO".equals(item.estado())) {
            return "ITEM_VALIDADO:El examen " + item.examenCodigo() + " ya esta validado (use una correccion)";
        }
        if ("CANCELADO".equals(item.estado())) {
            return "ITEM_CANCELADO:El examen " + item.examenCodigo() + " esta cancelado";
        }
        if ("SOLICITADA".equals(orden.estado())) {
            return "ORDEN_SIN_MUESTRA:Registre la toma de muestra de la orden " + orden.codigo() + " antes de importar";
        }
        return null;
    }

    private static LineaImportacion linea(LineaEntrada e, String estado, ItemRow item, ParametroInfo p, String bandera,
            Boolean critico, String ordenCodigo, String mensaje) {
        return new LineaImportacion(e.linea(), ordenCodigo != null ? ordenCodigo : e.ordenCodigo(), e.examenCodigo(),
                e.parametroCodigo(), e.loinc(), e.valor(), e.unidad(), estado, item == null ? null : item.id(),
                p == null ? null : p.id(), p == null ? null : p.nombre(), bandera, critico, mensaje);
    }

    private static LineaImportacion conEstado(LineaImportacion l, String estado) {
        return new LineaImportacion(l.linea(), l.ordenCodigo(), l.examenCodigo(), l.parametroCodigo(), l.loinc(), l.valor(),
                l.unidad(), estado, l.itemId(), l.parametroId(), l.parametro(), l.bandera(), l.critico(), l.mensaje());
    }

    private static String descripcion(LineaEntrada e) {
        return (e.loinc() != null ? "LOINC " + e.loinc() : "") + (e.loinc() != null && e.parametroCodigo() != null ? " / " : "")
                + (e.parametroCodigo() != null ? "codigo " + e.parametroCodigo() : "");
    }

    private static String vacio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    // ------------------------------------------------------------------ registro

    private String contenidoCanonico(Comando cmd, List<LineaEntrada> entradas) {
        if (cmd.contenido() != null && !"JSON".equals(cmd.formato())) {
            return cmd.contenido().replace("\r\n", "\n").replace('\r', '\n').trim() + "|orden=" + cmd.ordenId() + "|" + cmd.ordenCodigo();
        }
        try {
            return mapper.writeValueAsString(entradas) + "|orden=" + cmd.ordenId() + "|" + cmd.ordenCodigo();
        } catch (RuntimeException e) {
            throw new IllegalStateException("No se pudo serializar la importacion", e);
        }
    }

    private long registrarImportacion(Comando cmd, String origen, String contenido, String hash, List<LineaImportacion> lineas,
            int total, int importados, Long usuario) {
        String resumen;
        try {
            resumen = mapper.writeValueAsString(lineas);
        } catch (RuntimeException e) {
            resumen = "[]";
        }
        String texto = cmd.contenido() != null ? cmd.contenido() : contenido;
        return jdbc.sql("""
                insert into lab_importacion (formato, origen, nombre_archivo, hash_sha256, contenido, resumen, total, importados, usuario_id)
                values (:f, :o, :n, :h, :c, cast(:r as jsonb), :t, :i, :u) returning id
                """).param("f", cmd.formato()).param("o", origen).param("n", cmd.nombreArchivo()).param("h", hash)
                .param("c", texto).param("r", resumen).param("t", total).param("i", importados).param("u", usuario)
                .query(Long.class).single();
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
