package com.veterinaria.seguridad.application;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.seguridad.api.dto.PerfilProfesional;
import com.veterinaria.seguridad.domain.Rol;
import com.veterinaria.seguridad.domain.Usuario;
import com.veterinaria.seguridad.internal.UsuarioRepository;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.error.ResourceNotFoundException;
import com.veterinaria.shared.error.UnauthorizedException;
import com.veterinaria.shared.security.UsuarioActual;
import com.veterinaria.shared.text.Textos;

/** Tarjeta/registro profesional del veterinario (tabla {@code veterinario}); la imprimen la formula y los informes firmados. */
@Service
public class PerfilProfesionalService {

    private final JdbcClient jdbc;
    private final UsuarioRepository usuarios;

    public PerfilProfesionalService(JdbcClient jdbc, UsuarioRepository usuarios) {
        this.jdbc = jdbc;
        this.usuarios = usuarios;
    }

    @Transactional(readOnly = true)
    public PerfilProfesional obtener(Long usuarioId) {
        Usuario u = usuarios.findById(usuarioId).orElseThrow(() -> new ResourceNotFoundException("Usuario", usuarioId));
        return jdbc.sql("select numero_profesional, especialidad from veterinario where usuario_id = :u").param("u", usuarioId)
                .query((rs, n) -> new PerfilProfesional(usuarioId, u.getNombre(), rs.getString(1), rs.getString(2)))
                .optional().orElse(new PerfilProfesional(usuarioId, u.getNombre(), null, null));
    }

    @Transactional(readOnly = true)
    public PerfilProfesional propio() {
        return obtener(UsuarioActual.id().orElseThrow(() -> new UnauthorizedException("Sesion sin identificador de usuario.")));
    }

    @Transactional
    public PerfilProfesional propioActualizar(String numeroProfesional, String especialidad) {
        return actualizar(UsuarioActual.id().orElseThrow(() -> new UnauthorizedException("Sesion sin identificador de usuario.")),
                numeroProfesional, especialidad);
    }

    /** Crea la fila de {@code veterinario} si no existe. Campos null no cambian; "" limpia. */
    @Transactional
    public PerfilProfesional actualizar(Long usuarioId, String numeroProfesional, String especialidad) {
        Usuario u = usuarios.findById(usuarioId).orElseThrow(() -> new ResourceNotFoundException("Usuario", usuarioId));
        if (u.getRol() != Rol.VETERINARIO && u.getRol() != Rol.ADMIN) {
            throw new BusinessException("Solo los usuarios VETERINARIO o ADMIN tienen perfil profesional");
        }
        PerfilProfesional actual = obtener(usuarioId);
        jdbc.sql("""
                insert into veterinario (usuario_id, nombres, apellidos, numero_profesional, especialidad, activo)
                values (:u, :n, '', :t, :e, true)
                on conflict (usuario_id) do update set numero_profesional = excluded.numero_profesional,
                       especialidad = excluded.especialidad, actualizado_en = now()
                """).param("u", usuarioId).param("n", u.getNombre())
                .param("t", Textos.actualizar(actual.numeroProfesional(), numeroProfesional))
                .param("e", Textos.actualizar(actual.especialidad(), especialidad)).update();
        return obtener(usuarioId);
    }
}
