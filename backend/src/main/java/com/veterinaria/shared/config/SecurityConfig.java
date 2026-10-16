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
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.veterinaria.shared.error.ApiError;
import com.veterinaria.shared.security.ApiKeyAuthenticationFilter;

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
    /** Rol tecnico (no es un rol de usuario) que concede la API key de analizadores/LIS. */
    private static final String LAB_INTEGRACION = "INTEGRACION_LAB";

    private static final String[] RUTAS_PUBLICAS = {
        "/api/auth/login",
        "/actuator/health", "/actuator/health/**", "/actuator/info",
        "/v3/api-docs", "/v3/api-docs/**",
        "/swagger-ui.html", "/swagger-ui/**",
        "/error"
    };

    @Value("${vet.jwt.secret}")
    private String jwtSecret;

    /** Clave de los analizadores/LIS (cabecera X-API-Key); vacia = integracion deshabilitada. */
    @Value("${vet.laboratorio.api-key:}")
    private String laboratorioApiKey;

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
                        // Fase 3 - inmunizacion: ADMIN/VETERINARIO registran; AUXILIAR registra con veterinario supervisor
                        // (se valida en el servicio) pero no anula; RECEPCION no accede a datos clinicos del paciente.
                        .requestMatchers(HttpMethod.POST, "/api/pacientes/*/inmunizaciones")
                                .hasAnyRole(ADMIN, VETERINARIO, AUXILIAR)
                        .requestMatchers(HttpMethod.POST, "/api/pacientes/*/inmunizaciones/*/anular")
                                .hasAnyRole(ADMIN, VETERINARIO)
                        .requestMatchers(HttpMethod.GET, "/api/pacientes/*/inmunizaciones",
                                "/api/pacientes/*/esquema-sugerido", "/api/pacientes/*/carnet.pdf")
                                .hasAnyRole(ADMIN, VETERINARIO, AUXILIAR)
                        .requestMatchers(HttpMethod.GET, "/api/inmunizacion/**").authenticated()
                        .requestMatchers("/api/inmunizacion/**").hasRole(ADMIN)
                        // Fase 3 - comunicaciones: opt-out publico (token firmado); job manual solo ADMIN;
                        // plantillas/reglas: lectura ADMIN+RECEPCION, escritura ADMIN; recordatorios: ADMIN+RECEPCION.
                        .requestMatchers(HttpMethod.GET, "/api/comunicaciones/optout").permitAll()
                        .requestMatchers("/api/comunicaciones/ejecutar").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.GET, "/api/comunicaciones/plantillas/**",
                                "/api/comunicaciones/reglas/**").hasAnyRole(ADMIN, RECEPCION)
                        .requestMatchers("/api/comunicaciones/plantillas/**", "/api/comunicaciones/reglas/**")
                                .hasRole(ADMIN)
                        .requestMatchers("/api/comunicaciones/**").hasAnyRole(ADMIN, RECEPCION)
                        // Fase 4 - inventario: lectura para todos los roles; uso clinico ADMIN/VET/AUX; libro de
                        // controlados ADMIN/VET; compras, proveedores, ajustes, salidas y escritura de catalogo solo ADMIN.
                        .requestMatchers(HttpMethod.GET, "/api/inventario/controlados").hasAnyRole(ADMIN, VETERINARIO)
                        .requestMatchers(HttpMethod.GET, "/api/inventario/items", "/api/inventario/items/*",
                                "/api/inventario/categorias", "/api/inventario/bodegas", "/api/inventario/lotes",
                                "/api/inventario/lotes/*", "/api/inventario/movimientos", "/api/inventario/alertas")
                                .hasAnyRole(ADMIN, RECEPCION, VETERINARIO, AUXILIAR)
                        .requestMatchers(HttpMethod.POST, "/api/inventario/movimientos/uso")
                                .hasAnyRole(ADMIN, VETERINARIO, AUXILIAR)
                        .requestMatchers("/api/inventario/**").hasRole(ADMIN)
                        // Fase 4 - facturacion: ADMIN y RECEPCION operan caja, facturas, pagos, POS y cartera; el
                        // VETERINARIO solo lee presupuestos (el servicio filtra a sus consultas); notas credito,
                        // resoluciones, reintento DIAN y reporte de ventas solo ADMIN; AUXILIAR sin acceso.
                        .requestMatchers(HttpMethod.GET, "/api/facturacion/presupuestos", "/api/facturacion/presupuestos/*")
                                .hasAnyRole(ADMIN, RECEPCION, VETERINARIO)
                        .requestMatchers("/api/facturacion/resoluciones/**", "/api/facturacion/reportes/ventas")
                                .hasRole(ADMIN)
                        .requestMatchers(HttpMethod.POST, "/api/facturacion/facturas/*/notas-credito",
                                "/api/facturacion/facturas/*/dian/reintentar").hasRole(ADMIN)
                        .requestMatchers("/api/facturacion/**").hasAnyRole(ADMIN, RECEPCION)
                        // Etapa A - archivos clinicos: ADMIN/VETERINARIO/AUXILIAR suben y leen; baja logica solo ADMIN/VETERINARIO;
                        // RECEPCION sin acceso.
                        .requestMatchers(HttpMethod.DELETE, "/api/archivos/*").hasAnyRole(ADMIN, VETERINARIO)
                        .requestMatchers("/api/archivos", "/api/archivos/**", "/api/pacientes/*/archivos")
                                .hasAnyRole(ADMIN, VETERINARIO, AUXILIAR)
                        // Etapa A - laboratorio/imagen/patologia: analizadores y LIS usan solo la API key (no JWT); ADMIN/VETERINARIO
                        // ordenan, validan, corrigen e informan; AUXILIAR registra muestra, captura e importa (no valida).
                        .requestMatchers("/api/laboratorio/integracion/**").hasRole(LAB_INTEGRACION)
                        .requestMatchers(HttpMethod.GET, "/api/laboratorio/examenes", "/api/laboratorio/examenes/*",
                                "/api/laboratorio/plantilla-csv", "/api/laboratorio/ordenes", "/api/laboratorio/ordenes/*",
                                "/api/laboratorio/ordenes/*/informe.pdf", "/api/pacientes/*/laboratorio",
                                "/api/pacientes/*/laboratorio/tendencia").hasAnyRole(ADMIN, VETERINARIO, AUXILIAR)
                        .requestMatchers(HttpMethod.POST, "/api/laboratorio/ordenes/*/muestra", "/api/laboratorio/importar")
                                .hasAnyRole(ADMIN, VETERINARIO, AUXILIAR)
                        .requestMatchers(HttpMethod.PUT, "/api/laboratorio/ordenes/*/items/*/resultados")
                                .hasAnyRole(ADMIN, VETERINARIO, AUXILIAR)
                        .requestMatchers("/api/laboratorio/**").hasAnyRole(ADMIN, VETERINARIO)
                        // Etapa A - procedimientos y cirugia: AUXILIAR lee y registra monitoreo anestesico.
                        .requestMatchers(HttpMethod.POST, "/api/procedimientos/*/monitoreo-anestesico")
                                .hasAnyRole(ADMIN, VETERINARIO, AUXILIAR)
                        .requestMatchers(HttpMethod.GET, "/api/procedimientos", "/api/procedimientos/*",
                                "/api/procedimientos/*/monitoreo-anestesico", "/api/pacientes/*/procedimientos")
                                .hasAnyRole(ADMIN, VETERINARIO, AUXILIAR)
                        .requestMatchers("/api/procedimientos/**").hasAnyRole(ADMIN, VETERINARIO)
                        // Etapa B - formula en PDF por receta (misma regla que las consultas: ADMIN/VET/AUX leen); verificacion de
                        // documentos y descarga por enlace seguro: PUBLICOS; compartir con el tutor: ADMIN/VETERINARIO (AUXILIAR
                        // solo si vet.compartir.auxiliar-habilitado, validado en el servicio; RECEPCION nunca); perfil profesional.
                        .requestMatchers(HttpMethod.GET, "/api/recetas/*/formula.pdf").hasAnyRole(ADMIN, VETERINARIO, AUXILIAR)
                        .requestMatchers(HttpMethod.GET, "/api/verificar/*", "/api/publico/documentos/*").permitAll()
                        .requestMatchers("/api/compartir", "/api/compartir/**").hasAnyRole(ADMIN, VETERINARIO, AUXILIAR)
                        .requestMatchers("/api/perfil/profesional").hasAnyRole(ADMIN, VETERINARIO)
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .addFilterAfter(new ApiKeyAuthenticationFilter("/api/laboratorio/integracion/", "X-API-Key", laboratorioApiKey,
                        LAB_INTEGRACION), BearerTokenAuthenticationFilter.class)
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
