package com.andesstay.notify.config;

import com.andesstay.notify.model.NotifyConstants;
import com.andesstay.notify.security.CognitoClientIdValidator;
import com.andesstay.notify.security.CognitoJwtAuthenticationConverter;
import com.andesstay.notify.security.RestAccessDeniedHandler;
import com.andesstay.notify.security.RestAuthenticationEntryPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
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
 *       issuer, vigencia y {@code client_id} del access token JWT emitido por
 *       <strong>AWS Cognito</strong>.</li>
 *   <li>Autoriza por <strong>rol</strong> (claim {@code cognito:groups}) a nivel
 *       de URL: los GET de consulta requieren ADMIN, RECEPCIONISTA o AUDITOR;
 *       el POST de envío requiere ADMIN o RECEPCIONISTA.</li>
 *   <li>Configura <strong>CORS</strong> de forma amplia para admitir todos los
 *       orígenes necesarios (frontend Angular, BFF, preflights OPTIONS), exponiendo
 *       el header de Authorization.</li>
 *   <li>Mantiene una política de sesiones <strong>STATELESS</strong> y CSRF deshabilitado
 *       por ser una API REST basada en tokens.</li>
 *   <li>Responde <strong>401</strong> y <strong>403</strong> en formato JSON
 *       mediante handlers personalizados.</li>
 * </ol>
 * <p>
 * <strong>⚠️ CORS y API Gateway:</strong> si AWS API Gateway ya maneja CORS en
 * el stage, se recomienda centralizar la configuración en un solo punto (Gateway
 * o backend) para evitar duplicación de headers.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Value("${app.cors.allowed-origins:*}")
    private String allowedOriginsRaw;

    @Value("${app.cors.allowed-methods:GET,POST,PUT,DELETE,PATCH,OPTIONS}")
    private String allowedMethodsRaw;

    @Value("${app.cors.allowed-headers:*}")
    private String allowedHeaders;

    @Value("${app.cors.allow-credentials:true}")
    private boolean allowCredentials;

    /**
     * URI del emisor de tokens OIDC (AWS Cognito User Pool).
     * En producción se construye a partir de COGNITO_REGION y COGNITO_USER_POOL_ID.
     */
    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}")
    private String jwtIssuerUri;

    /**
     * App Client ID del User Pool de AWS Cognito.
     * Se valida contra el claim {@code client_id} del access token.
     */
    @Value("${cognito.app-client-id:}")
    private String cognitoAppClientId;

    private final CognitoJwtAuthenticationConverter jwtAuthenticationConverter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(CognitoJwtAuthenticationConverter jwtAuthenticationConverter,
                          RestAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler) {
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    /**
     * Cadena principal de filtros de Spring Security.
     * Habilita CORS, desactiva CSRF, configura el Resource Server con JWT de
     * Cognito, autorización por roles y handlers de error JSON.
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
                      + "Define COGNITO_REGION, COGNITO_USER_POOL_ID y COGNITO_APP_CLIENT_ID para producción "
                      + "o arranca con SPRING_PROFILES_ACTIVE=dev solo en local.");
            }
            log.info("[SECURITY] Profile dev/test activo: JWT deshabilitado, todos los endpoints permitidos.");
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        } else {
            // ── Profile prod / cloud: Validación obligatoria de JWT de Cognito ──
            log.info("[SECURITY] JWT habilitado. issuer-uri={}", jwtIssuerUri);
            http
                .authorizeHttpRequests(auth -> auth
                    // Preflight CORS (OPTIONS) siempre permitido sin token
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    // Actuator público para healthchecks (ALB, ECS, etc.)
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                    // POST /enviar: solo ADMIN y RECEPCIONISTA pueden encolar notificaciones
                    .requestMatchers(HttpMethod.POST, NotifyConstants.API_BASE + NotifyConstants.API_ENVIAR)
                        .hasAnyRole("ADMIN", "RECEPCIONISTA")
                    // GET /api/v1/notificaciones/**: ADMIN, RECEPCIONISTA y AUDITOR consultan historial
                    .requestMatchers(HttpMethod.GET, NotifyConstants.API_BASE + "/**")
                        .hasAnyRole("ADMIN", "RECEPCIONISTA", "AUDITOR")
                    // Cualquier otra petición autenticada requiere token válido
                    .anyRequest().authenticated()
                )
                // ── OAuth2 Resource Server: JWT de AWS Cognito ──
                .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt
                        .jwtAuthenticationConverter(jwtAuthenticationConverter)
                    )
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler)
                )
                // ── Handler de acceso denegado (403) en la capa de autorización ──
                .exceptionHandling(ex -> ex
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler)
                );
        }

        return http.build();
    }

    /**
     * Bean de decodificación de JWT con validación de issuer + expiración +
     * {@code client_id} (claim propio de access tokens de Cognito).
     * <p>
     * Usa {@link DelegatingOAuth2TokenValidator} para combinar los validadores
     * estándar de Spring (issuer, firma, {@code exp}) con el validador
     * personalizado {@link CognitoClientIdValidator}.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        if (jwtIssuerUri == null || jwtIssuerUri.isBlank()) {
            // En dev/test no se necesita decoder real
            return token -> {
                throw new UnsupportedOperationException(
                    "JWT no configurado. Configure las variables COGNITO_* para activar el Resource Server.");
            };
        }

        // 1. Decoder estándar de Spring: resuelve JWKS desde .well-known/openid-configuration
        NimbusJwtDecoder decoder = (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(jwtIssuerUri);

        // 2. Validadores: issuer + expiración (estándar) + client_id (personalizado)
        OAuth2TokenValidator<Jwt> defaultValidators = JwtValidators.createDefaultWithIssuer(jwtIssuerUri);
        OAuth2TokenValidator<Jwt> timestampValidator = new JwtTimestampValidator();

        // El validador de client_id se aplica solo si hay un App Client ID configurado
        if (cognitoAppClientId != null && !cognitoAppClientId.isBlank()) {
            OAuth2TokenValidator<Jwt> clientIdValidator = new CognitoClientIdValidator(cognitoAppClientId);
            OAuth2TokenValidator<Jwt> combined = new DelegatingOAuth2TokenValidator<>(
                    defaultValidators, timestampValidator, clientIdValidator);
            decoder.setJwtValidator(combined);
            log.info("[SECURITY] JwtDecoder configurado con validación de issuer + client_id={}", cognitoAppClientId);
        } else {
            OAuth2TokenValidator<Jwt> combined = new DelegatingOAuth2TokenValidator<>(
                    defaultValidators, timestampValidator);
            decoder.setJwtValidator(combined);
            log.warn("[SECURITY] JwtDecoder configurado SIN validación de client_id (cognito.app-client-id vacío).");
        }

        return decoder;
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
