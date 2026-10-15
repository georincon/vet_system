package com.veterinaria.inventario.application;

import com.veterinaria.shared.security.UsuarioActual;

/** Reglas de que datos ve cada rol (los costos son solo para ADMIN). */
final class Visibilidad {

    private Visibilidad() {
    }

    static boolean veCostos() {
        return "ADMIN".equals(UsuarioActual.rol().orElse(""));
    }
}
