package com.andesstay.notify.security;

import com.andesstay.notify.config.SecurityConfig;
import com.andesstay.notify.controller.NotificacionController;
import com.andesstay.notify.messaging.producer.NotificacionProducer;
import com.andesstay.notify.services.NotificacionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de integración de seguridad para {@link NotificacionController}.
 * <p>
 * Usa {@code @WebMvcTest} con {@code spring-security-test} para verificar que
 * las reglas de autorización por rol (cognito:groups) se apliquen correctamente
 * sin necesidad de un JWT real ni de Cognito.
 * <p>
 * Escenarios:
 * <ul>
 *   <li>Sin token → 401 con body JSON.</li>
 *   <li>GET con ROLE_AUDITOR → 200.</li>
 *   <li>POST /enviar con ROLE_AUDITOR → 403.</li>
 *   <li>POST /enviar con ROLE_RECEPCIONISTA y body válido → 202.</li>
 * </ul>
 */
@WebMvcTest(NotificacionController.class)
@Import({ SecurityConfig.class, CognitoJwtAuthenticationConverter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class })
class NotificacionControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private NotificacionService notificacionService;

    @MockBean
    private NotificacionProducer notificacionProducer;

    // ─── Body válido para POST /enviar ──────────────────────────────────────

    private static final String BODY_ENVIAR = """
            {
              "canal": "EMAIL",
              "reservationId": 1,
              "guestName": "Juan Pérez",
              "guestEmail": "juan@example.com",
              "propertyName": "Hostal Andes",
              "roomNumber": "101",
              "checkIn": "2026-11-01",
              "checkOut": "2026-11-04",
              "totalAmount": 50000,
              "currency": "CLP",
              "operatorEmail": "operador@andesstay.cl"
            }
            """;

    // ─── Tests ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Sin token → 401 Unauthorized con body JSON")
    void sinToken_401() throws Exception {
        mockMvc.perform(get("/api/v1/notificaciones")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("GET /api/v1/notificaciones con ROLE_AUDITOR → 200")
    void getConAuditor_200() throws Exception {
        mockMvc.perform(get("/api/v1/notificaciones")
                .with(jwt().authorities(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_AUDITOR")))
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /enviar con ROLE_AUDITOR → 403 Forbidden")
    void postConAuditor_403() throws Exception {
        mockMvc.perform(post("/api/v1/notificaciones/enviar")
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_AUDITOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_ENVIAR))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

@Test
    @DisplayName("POST /enviar con ROLE_RECEPCIONISTA → 202 Accepted")
    void postConRecepcionista_202() throws Exception {
        // NotificacionProducer.publicar() retorna null por defecto (mock),
        // pero el controller envuelve la respuesta en 202 Accepted igualmente.
        mockMvc.perform(post("/api/v1/notificaciones/enviar")
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_RECEPCIONISTA")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_ENVIAR))
                .andExpect(status().isAccepted());
    }
}
