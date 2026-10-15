package com.andesstay.notify.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

/**
 * Envelope común de todos los mensajes publicados por ms-andesstay-reservations.
 * <p>
 * Estructura JSON esperada:
 * <pre>
 * {
 *   "type"          : "RESERVATION_CREATED",
 *   "eventId"       : "550e8400-e29b-41d4-a716-446655440000",
 *   "timestamp"     : "2024-10-14T10:00:00Z",
 *   "traceId"       : "abc123",
 *   "correlationId" : "xyz789",
 *   "payload"       : { ... ReservationPayload ... }
 * }
 * </pre>
 *
 * @param type          Tipo semántico del evento (ej. "RESERVATION_CREATED").
 * @param eventId       Identificador único del evento (UUID). Se usa para idempotencia.
 * @param timestamp     Momento de creación del evento en ISO-8601 / UTC.
 * @param traceId       ID de traza distribuida (correlaciona varios servicios).
 * @param correlationId ID de correlación del flujo de negocio (ej. sesión de reserva).
 * @param payload       Datos de negocio específicos de la reserva.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReservationEnvelope(
        String type,
        UUID eventId,
        Instant timestamp,
        String traceId,
        String correlationId,
        ReservationPayload payload
) {}
