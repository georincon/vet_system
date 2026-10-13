package com.veterinaria.seguridad.application;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.seguridad.api.dto.LoginRequest;
import com.veterinaria.seguridad.api.dto.LoginResponse;
import com.veterinaria.seguridad.api.dto.UsuarioResponse;
import com.veterinaria.seguridad.domain.Usuario;
import com.veterinaria.seguridad.internal.UsuarioRepository;
import com.veterinaria.shared.error.UnauthorizedException;

/** Autenticacion por usuario/contrasena y emision del JWT. */
@Service
public class AuthService {

    private static final String ISSUER = "veterinaria";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final long expirationMinutes;

    public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
            @Value("${vet.jwt.expiration-minutes:480}") long expirationMinutes) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.expirationMinutes = expirationMinutes;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        // Mismo mensaje para usuario inexistente, inactivo o contrasena erronea (no revela cual).
        Usuario usuario = usuarioRepository.findByUsername(request.username())
                .filter(u -> u.isActivo() && passwordEncoder.matches(request.password(), u.getPassword()))
                .orElseThrow(() -> new UnauthorizedException("Credenciales invalidas."));

        Instant ahora = Instant.now();
        Instant expira = ahora.plus(Duration.ofMinutes(expirationMinutes));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(usuario.getUsername())
                .issuedAt(ahora)
                .expiresAt(expira)
                .claim("uid", usuario.getId())
                .claim("rol", usuario.getRol().name())
                .claim("nombre", usuario.getNombre())
                .build();
        String token = jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new LoginResponse(token, expira.toEpochMilli(), UsuarioResponse.from(usuario));
    }

    @Transactional(readOnly = true)
    public UsuarioResponse current(String username) {
        return usuarioRepository.findByUsername(username)
                .filter(Usuario::isActivo)
                .map(UsuarioResponse::from)
                .orElseThrow(() -> new UnauthorizedException("Sesion invalida."));
    }
}
