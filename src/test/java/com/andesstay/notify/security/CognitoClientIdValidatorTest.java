package com.andesstay.notify.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitarios para {@link CognitoClientIdValidator}.
 * <p>
 * Verifica que el validador acepte solo access tokens de Cognito cuyo claim
 * {@code client_id} coincida con el App Client ID configurado, y rechace
 * tokens con client_id distinto o sin el claim (ID tokens).
 */
class CognitoClientIdValidatorTest {

    private static final String EXPECTED_CLIENT_ID = "abc123clientid";

    private final CognitoClientIdValidator validator = new CognitoClientIdValidator(EXPECTED_CLIENT_ID);

    // ─── Helpers ────────────────────────────────────────────────────────────

    /** Construye un JWT mínimo con los claims indicados. */
    private Jwt jwtConClaims(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token-dummy")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .subject("user-1");
        claims.forEach(builder::claim);
        return builder.build();
    }

    // ─── Tests ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Acepta token con client_id igual al esperado")
    void aceptaClientIdCorrecto() {
        Jwt jwt = jwtConClaims(Map.of("client_id", EXPECTED_CLIENT_ID));

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertFalse(result.hasErrors(), "No debe tener errores cuando client_id coincide");
    }

    @Test
    @DisplayName("Rechaza token con client_id distinto al esperado")
    void rechazaClientIdIncorrecto() {
        Jwt jwt = jwtConClaims(Map.of("client_id", "otro-client-id"));

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertTrue(result.hasErrors(), "Debe fallar cuando client_id no coincide");
    }

    @Test
    @DisplayName("Rechaza token sin claim client_id (ID token)")
    void rechazaTokenSinClientId() {
        // Un ID token de Cognito trae "aud" pero NO "client_id"
        Jwt jwt = jwtConClaims(Map.of("aud", EXPECTED_CLIENT_ID));

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertTrue(result.hasErrors(), "Debe fallar cuando el token no tiene claim client_id");
    }
}

