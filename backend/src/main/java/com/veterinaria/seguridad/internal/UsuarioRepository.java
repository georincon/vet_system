package com.veterinaria.seguridad.internal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.veterinaria.seguridad.domain.Rol;
import com.veterinaria.seguridad.domain.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByUsername(String username);

    boolean existsByUsername(String username);

    Page<Usuario> findByUsernameContainingIgnoreCaseOrNombreContainingIgnoreCase(
            String username, String nombre, Pageable pageable);

    List<Usuario> findByRolAndActivoTrueOrderByNombreAsc(Rol rol);

    List<Usuario> findByIdIn(java.util.Collection<Long> ids);
}
