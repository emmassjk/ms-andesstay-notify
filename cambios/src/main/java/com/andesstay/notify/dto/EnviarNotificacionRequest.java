package com.andesstay.notify.dto;

import com.andesstay.notify.entities.Notificacion.CanalNotificacion;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cuerpo del {@code POST /api/v1/notificaciones/enviar}.
 * <p>
 * Es lo que manda el frontend (o cualquier cliente) para pedir una notificación.
 * El microservicio lo transforma en un {@code ReservationEnvelope} y lo publica
 * en RabbitMQ; el consumer del canal elegido lo procesa de forma asíncrona.
 *
 * @param canal           EMAIL, HOUSEKEEPING o VOUCHER (define la cola de destino).
 * @param reservationId   ID de la reserva asociada (opcional).
 * @param guestName       Nombre del huésped.
 * @param guestEmail      Correo del huésped.
 * @param propertyName    Nombre de la cabaña u hostal.
 * @param roomNumber      Identificador de la unidad.
 * @param checkIn         Fecha de ingreso.
 * @param checkOut        Fecha de salida.
 * @param totalAmount     Monto total.
 * @param currency        Moneda ISO 4217 (ej. CLP).
 * @param operatorEmail   Correo del operador que debe ser notificado.
 * @param specialRequests Notas del huésped (opcional).
 */
public record EnviarNotificacionRequest(
        @NotNull(message = "canal es obligatorio (EMAIL, HOUSEKEEPING o VOUCHER)")
        CanalNotificacion canal,
        Long reservationId,
        @NotBlank(message = "guestName es obligatorio") String guestName,
        @NotBlank(message = "guestEmail es obligatorio") @Email(message = "guestEmail no es un correo válido") String guestEmail,
        @NotBlank(message = "propertyName es obligatorio") String propertyName,
        @NotBlank(message = "roomNumber es obligatorio") String roomNumber,
        @NotNull(message = "checkIn es obligatorio") LocalDate checkIn,
        @NotNull(message = "checkOut es obligatorio") LocalDate checkOut,
        @NotNull(message = "totalAmount es obligatorio") @PositiveOrZero(message = "totalAmount no puede ser negativo") BigDecimal totalAmount,
        @NotBlank(message = "currency es obligatorio") String currency,
        @NotBlank(message = "operatorEmail es obligatorio") @Email(message = "operatorEmail no es un correo válido") String operatorEmail,
        String specialRequests
) {}
