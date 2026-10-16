package com.andesstay.notify.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO de respuesta para la API REST de notificaciones.
 * <p>
 * Desacopla la entidad JPA del contrato de la API. Los controladores
 * devuelven este record en lugar de la entidad {@code Notificacion}.
 *
 * @param id            ID interno de la notificación.
 * @param canal         Canal de notificación (EMAIL, HOUSEKEEPING, VOUCHER).
 * @param destinatario  Destinatario de la notificación.
 * @param asunto        Asunto o título descriptivo.
 * @param contenido     Cuerpo de la notificación.
 * @param estado        Estado actual (PENDIENTE, ENVIADA, FALLIDA).
 * @param eventId       ID del evento de RabbitMQ que la originó.
 * @param reservationId ID de la reserva asociada.
 * @param createdAt     Fecha de creación.
 * @param errorMessage  Mensaje de error si aplica.
 */
public record NotificacionDTO(
        Long id,
        String canal,
        String destinatario,
        String asunto,
        String contenido,
        String estado,
        UUID eventId,
        Long reservationId,
        Instant createdAt,
        String errorMessage
) {}
