package com.veterinaria.clinica.internal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.veterinaria.clinica.domain.CatalogoDiagnostico;

public interface CatalogoDiagnosticoRepository extends JpaRepository<CatalogoDiagnostico, Long> {

    @Query("""
            select d from CatalogoDiagnostico d
            where d.activo = true
              and (:q is null or lower(d.nombre) like lower(concat('%', cast(:q as string), '%'))
                   or lower(d.codigo) like lower(concat('%', cast(:q as string), '%')))
            order by d.nombre
            """)
    List<CatalogoDiagnostico> buscar(@Param("q") String q);
}
