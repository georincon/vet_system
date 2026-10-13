package com.veterinaria.tutores.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.veterinaria.tutores.domain.TipoDocumento;
import com.veterinaria.tutores.domain.Tutor;

public interface TutorRepository extends JpaRepository<Tutor, Long> {

    boolean existsByTipoDocumentoAndNumeroDocumento(TipoDocumento tipo, String numero);

    boolean existsByTipoDocumentoAndNumeroDocumentoAndIdNot(TipoDocumento tipo, String numero, Long id);

    /** Busca por nombres, apellidos, documento o correo (coincidencia parcial, sin distinguir mayusculas). */
    @Query("""
            select t from Tutor t
            where lower(t.nombres) like lower(concat('%', :q, '%'))
               or lower(coalesce(t.apellidos, '')) like lower(concat('%', :q, '%'))
               or lower(t.numeroDocumento) like lower(concat('%', :q, '%'))
               or lower(coalesce(t.email, '')) like lower(concat('%', :q, '%'))
            """)
    Page<Tutor> buscar(@Param("q") String q, Pageable pageable);
}
