package com.andesstay.notify.config;

import com.andesstay.notify.model.NotifyConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Configuración de seguridad HTTP para ms-andesstay-notify.
 * <p>
 * <strong>Política de acceso:</strong>
 * <ul>
 *   <li>{@code /actuator/health}, {@code /actuator/info} — públicos, sin autenticación.</li>
 *   <li>{@code /api/v1/notificaciones/**} — requiere JWT válido (Bearer token en header Authorization).</li>
 *   <li>Cualquier otro endpoint — requiere JWT válido emitido por Azure AD.</li>
 * </ul>
 * <p>
 * <strong>Profile dev:</strong> cuando {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}
 * está vacío (application-dev.yaml), se omite el Resource Server y <em>todas</em> las
 * peticiones son permitidas sin token. Nunca usar en producción.
 * <p>
 * <strong>Profile prod:</strong> el microservicio actúa como un OAuth2 Resource Server.
 * Cada petición a los endpoints protegidos debe incluir un header
 * {@code Authorization: Bearer <token>} con un JWT válido emitido por Azure AD
 * (Microsoft Entra ID). Spring Security valida automáticamente:
 * <ul>
 *   <li>Firma del token (descarga las claves públicas del issuer).</li>
 *   <li>Expiración ({@code exp} claim).</li>
 *   <li>Issuer ({@code iss} claim) contra el configurado en {@code jwt.issuer-uri}.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // Habilita @PreAuthorize, @Secured, etc. a nivel de método
public class SecurityConfig {

    @Value("${app.cors.allowed-origins}")
    private String allowedOriginsRaw;

    @Value("${app.cors.allowed-methods}")
    private String allowedMethodsRaw;

    @Value("${app.cors.allowed-headers}")
    private String allowedHeaders;

    @Value("${app.cors.allow-credentials}")
    private boolean allowCredentials;

    /** Vacío en profile dev → deshabilita JWT. Obligatorio en prod (Azure AD). */
    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}")
    private String jwtIssuerUri;

    /**
     * Cadena de filtros de seguridad principal.
     * <p>
     * Configura:
     * <ol>
     *   <li>CORS desde application.yaml.</li>
     *   <li>CSRF deshabilitado (API stateless con JWT).</li>
     *   <li>Sesiones stateless (no se crea HttpSession).</li>
     *   <li>Autorización por URL path con JWT como filtro de autenticación.</li>
     * </ol>
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, Environment env) throws Exception {
        http
            // ── CORS ──────────────────────────────────────────────────────────
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // ── CSRF deshabilitado: API REST stateless con JWT ─────────────────
            .csrf(csrf -> csrf.disable())
            // ── Sin sesiones HTTP: cada request se autentica con el token ──────
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (jwtIssuerUri == null || jwtIssuerUri.isBlank()) {
            // ── Sin issuer-uri: SOLO se acepta en el profile 'dev' ────────────
            // Si por error se arranca sin profile (o sin JWT_ISSUER_URI en prod),
            // se aborta el arranque en lugar de dejar el servicio abierto.
            if (!env.acceptsProfiles(Profiles.of("dev"))) {
                throw new IllegalStateException(
                        "spring.security.oauth2.resourceserver.jwt.issuer-uri está vacío fuera del profile 'dev'. "
                      + "Define JWT_ISSUER_URI (Azure AD) o arranca con SPRING_PROFILES_ACTIVE=dev solo en local.");
            }
            // ── Profile DEV: sin JWT, todas las peticiones permitidas ─────────
            // NUNCA activar en producción.
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        } else {
            // ── Profile PROD: JWT de Azure AD obligatorio ─────────────────────
            http
                .authorizeHttpRequests(auth -> auth
                    // Endpoints públicos (health checks, info)
                    .requestMatchers("/actuator/health").permitAll()
                    .requestMatchers("/actuator/info").permitAll()
                    // Endpoints de NotificacionController — requieren JWT
                    .requestMatchers(NotifyConstants.API_BASE + "/**").authenticated()
                    // Cualquier otro endpoint también requiere JWT
                    .anyRequest().authenticated()
                )
                // ── OAuth2 Resource Server con JWT ─────────────────────────────
                // Spring Security intercepta cada request, extrae el Bearer token
                // del header Authorization, lo valida contra las claves públicas
                // del issuer, y si es válido, establece el SecurityContext.
                .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> {})
                );
        }

        return http.build();
    }

    /**
     * Decodificador JWT configurado con el issuer-uri.
     * <p>
     * Solo se activa cuando {@code jwtIssuerUri} no está vacío (profile prod).
     * Spring descarga automáticamente la configuración OIDC del issuer
     * ({@code .well-known/openid-configuration}) y las claves públicas (JWKS)
     * para validar la firma de cada token.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        if (jwtIssuerUri == null || jwtIssuerUri.isBlank()) {
            // En profile dev, retornamos un decoder no-op que acepta todo.
            // Esto evita que Spring falle al arrancar sin un issuer real.
            return token -> {
                throw new UnsupportedOperationException(
                    "JWT deshabilitado en profile dev. No debería llegar aquí.");
            };
        }
        return JwtDecoders.fromIssuerLocation(jwtIssuerUri);
    }

    /**
     * Fuente de configuración CORS.
     * Los orígenes, métodos y headers se leen desde application.yaml,
     * por lo que son configurables sin recompilar el servicio.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        List<String> origins = Arrays.asList(allowedOriginsRaw.split(","));
        config.setAllowedOrigins(origins);

        List<String> methods = Arrays.asList(allowedMethodsRaw.split(","));
        config.setAllowedMethods(methods);

        config.setAllowedHeaders(List.of(allowedHeaders));
        config.setAllowCredentials(allowCredentials);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
