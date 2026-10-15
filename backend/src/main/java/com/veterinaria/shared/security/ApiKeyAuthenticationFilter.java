package com.veterinaria.shared.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Autentica llamadas maquina-a-maquina (analizadores/LIS) con una clave en un encabezado. Solo actua sobre las rutas
 * que empiezan por {@code prefijo}; si la clave coincide (comparacion en tiempo constante) concede la autoridad
 * {@code ROLE_<rol>}; si no, la solicitud sigue sin autenticar y Spring Security responde 401/403.
 * Una clave vacia deshabilita la integracion.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final String prefijo;
    private final String encabezado;
    private final byte[] clave;
    private final String rol;

    public ApiKeyAuthenticationFilter(String prefijo, String encabezado, String clave, String rol) {
        this.prefijo = prefijo;
        this.encabezado = encabezado;
        this.clave = clave == null ? new byte[0] : clave.getBytes(StandardCharsets.UTF_8);
        this.rol = rol;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(prefijo);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String recibida = request.getHeader(encabezado);
        if (clave.length > 0 && recibida != null
                && MessageDigest.isEqual(clave, recibida.getBytes(StandardCharsets.UTF_8))) {
            var auth = new UsernamePasswordAuthenticationToken("api-key:" + encabezado, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        chain.doFilter(request, response);
    }
}
