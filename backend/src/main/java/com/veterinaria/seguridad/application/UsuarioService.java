package com.veterinaria.seguridad.application;

import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.veterinaria.seguridad.api.dto.CrearUsuarioRequest;
import com.veterinaria.seguridad.api.dto.UsuarioResponse;
import com.veterinaria.seguridad.domain.Usuario;
import com.veterinaria.seguridad.internal.UsuarioRepository;
import com.veterinaria.shared.error.ConflictException;
import com.veterinaria.shared.error.ResourceNotFoundException;
import com.veterinaria.shared.web.PageResponse;

/** Gestion de usuarios (solo ADMIN, ver {@code SecurityConfig}). */
@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public PageResponse<UsuarioResponse> list(String q, Pageable pageable) {
        String termino = q == null ? "" : q.trim();
        // Con termino vacio "contiene" coincide con todos los usuarios.
        return PageResponse.from(
                usuarioRepository.findByUsernameContainingIgnoreCaseOrNombreContainingIgnoreCase(
                        termino, termino, pageable),
                UsuarioResponse::from);
    }

    @Transactional(readOnly = true)
    public UsuarioResponse findById(Long id) {
        return usuarioRepository.findById(id)
                .map(UsuarioResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));
    }

    @Transactional
    public UsuarioResponse create(CrearUsuarioRequest request) {
        String username = request.username().trim();
        if (usuarioRepository.existsByUsername(username)) {
            throw new ConflictException("El nombre de usuario '%s' ya existe.".formatted(username));
        }
        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setPassword(passwordEncoder.encode(request.password()));
        usuario.setNombre(request.nombre().trim());
        usuario.setEmail(StringUtils.hasText(request.email()) ? request.email().trim() : null);
        usuario.setRol(request.rol());
        usuario.setActivo(request.activo() == null || request.activo());
        return UsuarioResponse.from(usuarioRepository.save(usuario));
    }
}
