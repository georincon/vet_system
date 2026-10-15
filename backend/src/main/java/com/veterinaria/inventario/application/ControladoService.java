package com.veterinaria.inventario.application;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.inventario.api.dto.StockDtos.ControladoResponse;
import com.veterinaria.inventario.api.dto.StockDtos.Persona;
import com.veterinaria.inventario.internal.Filtro;
import com.veterinaria.shared.time.ZonaClinica;
import com.veterinaria.shared.web.PageResponse;

/** Libro de medicamentos de control especial (append-only; lo escribe el kardex al mover items controlados). */
@Service
public class ControladoService {

    private final JdbcClient jdbc;

    public ControladoService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public PageResponse<ControladoResponse> libro(Long itemId, Long pacienteId, String tipo, LocalDate desde,
            LocalDate hasta, Pageable pageable) {
        Filtro f = new Filtro().si(itemId, "r.item_id = :item", "item").si(pacienteId, "r.paciente_id = :pac", "pac")
                .si(tipo, "r.tipo = :tipo", "tipo");
        if (desde != null) {
            f.y("r.ocurrido_en >= :desde", "desde", java.sql.Timestamp.from(ZonaClinica.instante(desde, LocalTime.MIN)));
        }
        if (hasta != null) {
            f.y("r.ocurrido_en < :hasta", "hasta", java.sql.Timestamp.from(ZonaClinica.instante(hasta.plusDays(1), LocalTime.MIN)));
        }
        String from = """
                 from registro_controlado r join item i on i.id = r.item_id left join lote l on l.id = r.lote_id
                 left join paciente p on p.id = r.paciente_id left join usuario u on u.id = r.veterinario_id
                """ + f.sql();
        long total = jdbc.sql("select count(*)" + from).params(f.params()).query(Long.class).single();
        List<ControladoResponse> items = jdbc.sql("""
                select r.*, i.codigo as item_codigo, i.nombre as item_nombre, l.numero_lote, p.nombre as paciente,
                       u.nombre as veterinario
                """ + from + " order by r.ocurrido_en desc, r.id desc limit :lim offset :off")
                .params(f.params()).param("lim", pageable.getPageSize()).param("off", pageable.getOffset())
                .query((rs, n) -> {
                    Long vet = (Long) rs.getObject("veterinario_id");
                    return new ControladoResponse(rs.getLong("id"), rs.getTimestamp("ocurrido_en").toInstant(),
                            rs.getLong("item_id"), rs.getString("item_codigo"), rs.getString("item_nombre"),
                            (Long) rs.getObject("lote_id"), rs.getString("numero_lote"), rs.getString("tipo"),
                            rs.getBigDecimal("cantidad"), rs.getBigDecimal("saldo"), (Long) rs.getObject("paciente_id"),
                            rs.getString("paciente"), (Long) rs.getObject("receta_id"),
                            vet == null ? null : new Persona(vet, rs.getString("veterinario")),
                            rs.getString("observaciones"));
                }).list();
        return PageResponse.from(new PageImpl<>(items, pageable, total), x -> x);
    }
}
