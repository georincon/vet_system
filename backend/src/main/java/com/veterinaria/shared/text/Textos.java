package com.veterinaria.shared.text;

/** Utilidades de normalizacion de texto de entrada. */
public final class Textos {

    private Textos() {
    }

    /** Recorta espacios; devuelve {@code null} si queda vacio (los formularios envian "" por opcionales). */
    public static String limpiar(String texto) {
        if (texto == null) {
            return null;
        }
        String recortado = texto.trim();
        return recortado.isEmpty() ? null : recortado;
    }

    /**
     * Valor resultante de una edicion parcial: {@code nuevo == null} conserva {@code actual};
     * un texto (incluso vacio) lo reemplaza, y el vacio limpia el campo.
     */
    public static String actualizar(String actual, String nuevo) {
        return nuevo == null ? actual : limpiar(nuevo);
    }
}
