package com.veterinaria.shared.security;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Acceso al usuario autenticado de la solicitud actual (claims del JWT). */
public final class UsuarioActual {

    private UsuarioActual() {
    }

    private static Optional<Jwt> jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof Jwt jwt ? Optional.of(jwt) : Optional.empty();
    }

    /** Id del usuario (claim {@code uid}); vacio si no hay sesion o el token es anterior al claim. */
    public static Optional<Long> id() {
        return jwt().map(j -> j.getClaim("uid")).map(v -> ((Number) v).longValue());
    }

    public static Optional<String> username() {
        return jwt().map(Jwt::getSubject);
    }

    public static Optional<String> rol() {
        return jwt().map(j -> j.getClaimAsString("rol"));
    }
}
