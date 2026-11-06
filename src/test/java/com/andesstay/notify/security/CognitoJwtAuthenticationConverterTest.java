package com.andesstay.notify.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitarios para {@link CognitoJwtAuthenticationConverter}.
 * <p>
 * Verifica que el converter mapee correctamente el claim {@code cognito:groups}
 * a authorities de Spring Security con prefijo {@code ROLE_} en mayúsculas.
 */
class CognitoJwtAuthenticationConverterTest {

    private final CognitoJwtAuthenticationConverter converter = new CognitoJwtAuthenticationConverter();

    // ─── Helpers ────────────────────────────────────────────────────────────

    private Jwt jwtConGrupos(List<String> grupos) {
        Jwt.Builder builder = Jwt.withTokenValue("token-dummy")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .subject("user-1");
        if (grupos != null) {
            builder.claim("cognito:groups", grupos);
        }
        return builder.build();
    }

    // ─── Tests ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Mapea cognito:groups a ROLE_<GRUPO> en mayúsculas")
    void mapeaGruposARoles() {
        Jwt jwt = jwtConGrupos(List.of("ADMIN", "recepcionista", "Auditor"));

        AbstractAuthenticationToken auth = converter.convert(jwt);

        assertNotNull(auth);
        Set<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        assertTrue(roles.contains("ROLE_ADMIN"), "Debe contener ROLE_ADMIN");
        assertTrue(roles.contains("ROLE_RECEPCIONISTA"), "Debe contener ROLE_RECEPCIONISTA");
        assertTrue(roles.contains("ROLE_AUDITOR"), "Debe contener ROLE_AUDITOR");
        assertEquals(3, roles.size(), "Debe tener exactamente 3 roles");
    }

    @Test
    @DisplayName("Token sin claim cognito:groups produce authorities vacías")
    void sinGruposProduceVacio() {
        Jwt jwt = jwtConGrupos(null);

        AbstractAuthenticationToken auth = converter.convert(jwt);

        assertNotNull(auth);
        Collection<GrantedAuthority> authorities = auth.getAuthorities();
        assertTrue(authorities.isEmpty(), "Sin claim cognito:groups las authorities deben estar vacías");
    }

    @Test
    @DisplayName("Token con cognito:groups vacío produce authorities vacías")
    void gruposVaciosProduceVacio() {
        Jwt jwt = jwtConGrupos(List.of());

        AbstractAuthenticationToken auth = converter.convert(jwt);

        assertNotNull(auth);
        assertTrue(auth.getAuthorities().isEmpty(), "Con lista vacía las authorities deben estar vacías");
    }
}

