package com.andesstay.notify.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Configuración de seguridad HTTP para ms-andesstay-notify.
 * <p>
 * Política de acceso:
 * <ul>
 *   <li>{@code /actuator/health} — público, sin autenticación.</li>
 *   <li>Cualquier otro endpoint — requiere JWT válido emitido por Azure AD.</li>
 * </ul>
 * <p>
 * <strong>Profile dev:</strong> cuando {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}
 * está vacío (application-dev.yaml), se omite el Resource Server y <em>todas</em> las
 * peticiones son permitidas sin token. Nunca usar en producción.
 */
@Configuration
@EnableWebSecurity
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

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, Environment env) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
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
                    .requestMatchers("/actuator/health").permitAll()
                    .requestMatchers("/actuator/info").permitAll()
                    .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> {})
                );
        }

        return http.build();
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
