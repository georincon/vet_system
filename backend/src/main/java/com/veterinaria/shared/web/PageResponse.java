package com.veterinaria.shared.web;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/** Pagina de resultados con la forma JSON que consume el frontend. */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    /** Convierte una pagina de entidades aplicando {@code mapper} a cada elemento. */
    public static <E, T> PageResponse<T> from(Page<E> pagina, Function<E, T> mapper) {
        return new PageResponse<>(
                pagina.getContent().stream().map(mapper).toList(),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getTotalPages(),
                pagina.isFirst(),
                pagina.isLast());
    }
}
