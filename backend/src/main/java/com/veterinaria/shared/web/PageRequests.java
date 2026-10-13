package com.veterinaria.shared.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** Construye {@link PageRequest} acotando pagina y tamano a valores validos. */
public final class PageRequests {

    private static final int MAX_SIZE = 200;

    private PageRequests() {
    }

    public static PageRequest of(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_SIZE), sort);
    }
}
