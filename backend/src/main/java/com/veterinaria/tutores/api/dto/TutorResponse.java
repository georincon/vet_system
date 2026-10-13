package com.veterinaria.tutores.api.dto;

import com.veterinaria.tutores.domain.TipoDocumento;
import com.veterinaria.tutores.domain.Tutor;

public record TutorResponse(
        Long id,
        TipoDocumento tipoDocumento,
        String numeroDocumento,
        String nombres,
        String apellidos,
        String telefono,
        String telefonoAlt,
        String email,
        String direccion,
        String ciudad,
        String notas,
        boolean activo) {

    public static TutorResponse from(Tutor t) {
        return new TutorResponse(t.getId(), t.getTipoDocumento(), t.getNumeroDocumento(), t.getNombres(),
                t.getApellidos(), t.getTelefono(), t.getTelefonoAlt(), t.getEmail(), t.getDireccion(),
                t.getCiudad(), t.getNotas(), t.isActivo());
    }
}
