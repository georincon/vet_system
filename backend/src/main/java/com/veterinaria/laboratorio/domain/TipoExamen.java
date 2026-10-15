package com.veterinaria.laboratorio.domain;

/** Forma de informar un examen segun su tipo de catalogo. */
public enum TipoExamen {
    /** Parametros numericos/cualitativos con rangos (LAB). */
    RESULTADOS,
    /** Informe estructurado de imagen (IMAGEN y FUNCIONAL, p. ej. ECG). */
    IMAGEN,
    /** Informe de patologia/citologia. */
    PATOLOGIA;

    public static TipoExamen de(String tipoCatalogo) {
        return switch (tipoCatalogo) {
            case "IMAGEN", "FUNCIONAL" -> IMAGEN;
            case "PATOLOGIA" -> PATOLOGIA;
            default -> RESULTADOS;
        };
    }

    /** Requiere toma de muestra antes de capturar (laboratorio y patologia). */
    public boolean requiereMuestra() {
        return this != IMAGEN;
    }
}
