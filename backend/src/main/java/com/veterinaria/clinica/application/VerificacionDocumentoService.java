package com.veterinaria.clinica.application;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.shared.time.ZonaClinica;

/**
 * Verificacion publica de una formula por su codigo. Solo responde si existe y quien/cuando/donde la emitio: no devuelve
 * paciente, tutor, medicamentos ni diagnosticos (sin datos clinicos).
 */
@Service
public class VerificacionDocumentoService {

    /** {@code estado}: VIGENTE, CUMPLIDA, VENCIDA, ANULADA o BORRADOR (consulta aun abierta). */
    public record Verificacion(boolean valido, String tipoDocumento, String numero, LocalDate fecha, String clinica,
            String veterinario, String tarjetaProfesional, String estado) {

        public static Verificacion invalido() {
            return new Verificacion(false, null, null, null, null, null, null, null);
        }
    }

    private final JdbcClient jdbc;
    private final String clinica;

    public VerificacionDocumentoService(JdbcClient jdbc, @Value("${vet.clinica.nombre:Clinica Veterinaria}") String clinica) {
        this.jdbc = jdbc;
        this.clinica = clinica;
    }

    @Transactional(readOnly = true)
    public Optional<Verificacion> verificar(String codigoCrudo) {
        if (codigoCrudo == null) {
            return Optional.empty();
        }
        String codigo = codigoCrudo.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (codigo.length() != 10) {
            return Optional.empty();
        }
        return jdbc.sql("""
                select r.numero_formula, r.fecha, r.estado, r.valida_hasta, c.estado as consulta_estado, u.nombre,
                       v.numero_profesional
                  from receta r join consulta c on c.id = r.consulta_id join usuario u on u.id = r.veterinario_id
                  left join veterinario v on v.usuario_id = u.id
                 where r.codigo_verificacion = :c
                """).param("c", codigo).query((rs, n) -> {
            String estado = rs.getString("estado");
            LocalDate hasta = rs.getObject("valida_hasta", LocalDate.class);
            String e = "CANCELADA".equals(estado) ? "ANULADA"
                    : !"CERRADA".equals(rs.getString("consulta_estado")) ? "BORRADOR"
                    : "CUMPLIDA".equals(estado) ? "CUMPLIDA"
                    : hasta != null && hasta.isBefore(ZonaClinica.hoy()) ? "VENCIDA" : "VIGENTE";
            return new Verificacion(true, "FORMULA_MEDICA", rs.getString("numero_formula"),
                    rs.getObject("fecha", LocalDate.class), clinica, rs.getString("nombre"),
                    rs.getString("numero_profesional"), e);
        }).optional();
    }
}
