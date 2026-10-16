package com.veterinaria.shared.documentos;

/**
 * Puerto que cada modulo clinico implementa para que el modulo {@code compartir} pueda generar sus documentos PDF sin
 * depender de el (Modulith limpio: {@code compartir -> shared <- laboratorio, procedimientos, inmunizacion, clinica}).
 *
 * <p>Contrato: {@link #generar} devuelve el PDF final del documento o lanza {@code ResourceNotFoundException} (404) si no
 * existe y {@code BusinessException} (422) si todavia no esta VALIDADO/CERRADO (no se comparten borradores).
 */
public interface DocumentoCompartible {

    /** Tipo: FORMULA, LABORATORIO, IMAGEN, PATOLOGIA, PROCEDIMIENTO, CARNET o RESUMEN_CONSULTA. */
    String tipo();

    /**
     * @param id     identificador del documento segun el tipo (receta, orden, procedimiento, paciente para CARNET, consulta)
     * @param itemId opcional: item de la orden (LABORATORIO/IMAGEN/PATOLOGIA); sin el, todos los items de ese tipo
     */
    Generado generar(Long id, Long itemId);

    /** {@code titulo}: nombre legible (p. ej. "Formula medica FOR-20261015-00001"); {@code nombreArchivo} termina en .pdf. */
    record Generado(Long pacienteId, String titulo, String nombreArchivo, byte[] pdf) {
    }
}
