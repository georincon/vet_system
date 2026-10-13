package com.veterinaria.shared.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.veterinaria.shared.error.ApiError;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Seguridad HTTP sin estado: JWT firmado con HMAC-SHA256 (clave {@code vet.jwt.secret}),
 * CORS configurable y respuestas 401/403 con el cuerpo de error comun.
 */
@Configuration
public class SecurityConfig {

    private static final int MIN_SECRET_BYTES = 32;

    private static final String ADMIN = "ADMIN";
    private static final String VETERINARIO = "VETERINARIO";
    private static final String RECEPCION = "RECEPCION";
    private static final String AUXILIAR = "AUXILIAR";

    private static final String[] RUTAS_PUBLICAS = {
        "/api/auth/login",
        "/actuator/health", "/actuator/health/**", "/actuator/info",
        "/v3/api-docs", "/v3/api-docs/**",
        "/swagger-ui.html", "/swagger-ui/**",
        "/error"
    };

    @Value("${vet.jwt.secret}")
    private String jwtSecret;

    @Value("${vet.cors.allowed-origins:http://localhost:5173}")
    private String[] allowedOrigins;

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecretKey jwtSecretKey() {
        byte[] bytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("vet.jwt.secret debe tener al menos 32 caracteres.");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** El claim {@code rol} del token se convierte en la autoridad {@code ROLE_<rol>}. */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String rol = jwt.getClaimAsString("rol");
            return rol == null ? List.of() : List.of(new SimpleGrantedAuthority("ROLE_" + rol));
        });
        return converter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, JwtAuthenticationConverter converter, JsonMapper mapper) throws Exception {
        AuthenticationEntryPoint entryPoint = (request, response, ex) -> writeError(
                mapper, response, HttpStatus.UNAUTHORIZED, "No autenticado. Proporcione un token valido.", request.getRequestURI());
        AccessDeniedHandler deniedHandler = (request, response, ex) -> writeError(
                mapper, response, HttpStatus.FORBIDDEN, "No tiene permisos para este recurso.", request.getRequestURI());

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(RUTAS_PUBLICAS).permitAll()
                        .requestMatchers("/api/usuarios/**", "/api/auditoria/**").hasRole("ADMIN")
                        // Agenda: lectura para cualquier rol autenticado; escritura segun rol.
                        .requestMatchers(HttpMethod.POST, "/api/citas/*/iniciar-consulta").hasAnyRole(ADMIN, VETERINARIO)
                        .requestMatchers(HttpMethod.GET, "/api/citas/**", "/api/agenda/**").authenticated()
                        .requestMatchers("/api/agenda/**").hasAnyRole(ADMIN, RECEPCION)
                        .requestMatchers("/api/citas/**").hasAnyRole(ADMIN, RECEPCION, VETERINARIO)
                        // Clinica: AUXILIAR lee y registra signos vitales/pesos; RECEPCION no accede.
                        .requestMatchers("/api/consultas/*/signos-vitales", "/api/consultas/*/signos-vitales/*")
                                .hasAnyRole(ADMIN, VETERINARIO, AUXILIAR)
                        .requestMatchers(HttpMethod.POST, "/api/pacientes/*/pesos").hasAnyRole(ADMIN, VETERINARIO, AUXILIAR)
                        .requestMatchers(HttpMethod.GET, "/api/consultas/**", "/api/clinica/**",
                                "/api/pacientes/*/alergias", "/api/pacientes/*/pesos", "/api/pacientes/*/historia")
                                .hasAnyRole(ADMIN, VETERINARIO, AUXILIAR)
                        .requestMatchers("/api/consultas/**", "/api/clinica/**", "/api/pacientes/*/alergias/**",
                                "/api/pacientes/*/alergias").hasAnyRole(ADMIN, VETERINARIO)
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .build();
    }

    private static void writeError(
            JsonMapper mapper, HttpServletResponse response, HttpStatus status, String message, String path) {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try {
            mapper.writeValue(response.getOutputStream(),
                    ApiError.of(status.value(), status.getReasonPhrase(), message, path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
