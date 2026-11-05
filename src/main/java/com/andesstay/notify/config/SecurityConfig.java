package com.andesstay.notify.config;

import com.andesstay.notify.model.NotifyConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
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
 * Configuración de seguridad HTTP y CORS para ms-andesstay-notify.
 * <p>
 * <strong>Rol dentro de la arquitectura:</strong>
 * El microservicio recibe peticiones HTTP reenviadas desde el API Gateway / BFF
 * (o llamadas directas autorizadas). Para integrarse de forma segura en la nube:
 * <ol>
 *   <li>Actúa como un <strong>OAuth2 Resource Server</strong>, validando la firma,
 *       issuer y vigencia del JWT (emitido por Azure AD o AWS Cognito).</li>
 *   <li>Configura <strong>CORS</strong> de forma amplia para admitir todos los
 *       orígenes necesarios (frontend Angular, BFF, preflights OPTIONS), exponiendo
 *       el header de Authorization.</li>
 *   <li>Mantiene una política de sesiones <strong>STATELESS</strong> y CSRF deshabilitado
 *       por ser una API REST basada en tokens.</li>
 * </ol>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${app.cors.allowed-origins:*}")
    private String allowedOriginsRaw;

    @Value("${app.cors.allowed-methods:GET,POST,PUT,DELETE,PATCH,OPTIONS}")
    private String allowedMethodsRaw;

    @Value("${app.cors.allowed-headers:*}")
    private String allowedHeaders;

    @Value("${app.cors.allow-credentials:true}")
    private boolean allowCredentials;

    /**
     * URI del emisor de tokens OIDC (Azure AD o AWS Cognito).
     * En producción se inyecta desde la variable de entorno JWT_ISSUER_URI.
     */
    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}")
    private String jwtIssuerUri;

    /**
     * Cadena principal de filtros de Spring Security.
     * Habilita CORS, desactiva CSRF y configura el Resource Server para validar el JWT.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, Environment env) throws Exception {
        http
            // ── Configuración de CORS ───────────────────────────────────────────
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // ── CSRF deshabilitado para API REST stateless con JWT ───────────────
            .csrf(csrf -> csrf.disable())
            // ── Sin estado de sesión HTTP en el servidor ─────────────────────────
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (jwtIssuerUri == null || jwtIssuerUri.isBlank()) {
            // ── Profile dev / test: permite peticiones sin token si no hay issuer configurado ──
            if (!env.acceptsProfiles(Profiles.of("dev", "test"))) {
                throw new IllegalStateException(
                        "spring.security.oauth2.resourceserver.jwt.issuer-uri está vacío fuera de los profiles 'dev' o 'test'. "
                      + "Define JWT_ISSUER_URI para producción o arranca con SPRING_PROFILES_ACTIVE=dev solo en local.");
            }
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        } else {
            // ── Profile prod / cloud: Validación obligatoria de JWT ──────────────
            http
                .authorizeHttpRequests(auth -> auth
                    // Permitir todas las peticiones preflight CORS (OPTIONS) sin requerir token
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    // Endpoints públicos de diagnóstico y healthcheck (Actuator / AWS ALB)
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                    // Endpoints del NotificacionController (/api/v1/notificaciones/**) protegidos
                    .requestMatchers(NotifyConstants.API_BASE + "/**").authenticated()
                    // Cualquier otra petición requiere autenticación
                    .anyRequest().authenticated()
                )
                // ── Configuración como OAuth2 Resource Server para validar JWT del BFF ──
                .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> {})
                );
        }

        return http.build();
    }

    /**
     * Bean de decodificación de JWT a partir del issuer configurado.
     * En producción resuelve las llaves públicas (.well-known/jwks.json) automáticamente.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        if (jwtIssuerUri == null || jwtIssuerUri.isBlank() || jwtIssuerUri.contains("CHANGE_ME")) {
            return token -> {
                throw new UnsupportedOperationException(
                    "JWT no configurado o issuer inválido. Configure JWT_ISSUER_URI para activar el Resource Server.");
            };
        }
        return JwtDecoders.fromIssuerLocation(jwtIssuerUri);
    }

    /**
     * Bean de configuración de CORS.
     * Habilita todos los orígenes mediante patrones (soportando '*' junto con allowCredentials),
     * permite los métodos HTTP básicos y expone el header de Authorization.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // 1. Orígenes permitidos: soporta wildcard '*' o dominios específicos (ej. frontend Angular o BFF)
        if (allowedOriginsRaw == null || allowedOriginsRaw.isBlank() || "*".equals(allowedOriginsRaw.trim())) {
            config.addAllowedOriginPattern("*");
        } else {
            Arrays.stream(allowedOriginsRaw.split(","))
                    .map(String::trim)
                    .filter(origin -> !origin.isEmpty())
                    .forEach(origin -> {
                        if ("*".equals(origin)) {
                            config.addAllowedOriginPattern("*");
                        } else {
                            config.addAllowedOriginPattern(origin);
                        }
                    });
        }

        // 2. Métodos HTTP permitidos
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"));

        // 3. Headers permitidos en las solicitudes (incluye Authorization, Content-Type, etc.)
        config.setAllowedHeaders(List.of("*"));

        // 4. Headers expuestos al frontend / BFF
        config.setExposedHeaders(List.of("Authorization", "Content-Disposition", "Link", "X-Total-Count"));

        // 5. Permitir credenciales / headers de autorización
        config.setAllowCredentials(allowCredentials);

        // 6. Tiempo de expiración del preflight (1 hora de cache)
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
