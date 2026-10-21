package com.andesstay.notify.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Respuesta del endpoint producer. El procesamiento es asíncrono: el mensaje queda
 * encolado y la notificación aparece en el historial cuando el consumer la procesa.
 *
 * @param eventId    Identificador del evento publicado (sirve para trazar y para idempotencia).
 * @param canal      Canal elegido.
 * @param routingKey Routing key usada en el exchange direct.
 * @param estado     Siempre "ENCOLADA" cuando RabbitMQ aceptó el mensaje.
 * @param timestamp  Momento de publicación.
 */
public record EnviarNotificacionResponse(
        UUID eventId,
        String canal,
        String routingKey,
        String estado,
        Instant timestamp
) {}
