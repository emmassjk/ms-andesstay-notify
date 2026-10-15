package com.andesstay.notify.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Datos de negocio de la reserva, contenidos dentro del {@link ReservationEnvelope}.
 * <p>
 * Campos:
 * <ul>
 *   <li>{@code reservationId}   – ID interno de la reserva en ms-andesstay-reservations.</li>
 *   <li>{@code guestName}       – Nombre completo del huésped (Cliente).</li>
 *   <li>{@code guestEmail}      – Correo electrónico del huésped.</li>
 *   <li>{@code propertyName}    – Nombre del hostal o cabaña.</li>
 *   <li>{@code roomNumber}      – Número o identificador de la habitación.</li>
 *   <li>{@code checkIn}         – Fecha de ingreso.</li>
 *   <li>{@code checkOut}        – Fecha de salida.</li>
 *   <li>{@code totalAmount}     – Monto total de la reserva.</li>
 *   <li>{@code currency}        – Código de moneda (ISO 4217, ej. "CLP", "USD").</li>
 *   <li>{@code operatorEmail}   – Correo del Operador (recepcionista) que debe ser notificado.</li>
 *   <li>{@code specialRequests} – Notas o requerimientos especiales del huésped (opcional).</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReservationPayload(
        Long reservationId,
        String guestName,
        String guestEmail,
        String propertyName,
        String roomNumber,
        LocalDate checkIn,
        LocalDate checkOut,
        BigDecimal totalAmount,
        String currency,
        String operatorEmail,
        String specialRequests
) {}
