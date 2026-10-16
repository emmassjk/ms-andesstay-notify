package com.andesstay.notify.model;

/**
 * Enum que define los tipos de evento de dominio publicados por
 * ms-andesstay-reservations a través de RabbitMQ.
 * <p>
 * Estos tipos se mapean al campo {@code type} del
 * {@link com.andesstay.notify.event.ReservationEnvelope}.
 */
public enum TipoEvento {

    /** Se creó una nueva reserva. */
    RESERVATION_CREATED,

    /** La reserva fue confirmada (pago validado, etc.). */
    RESERVATION_CONFIRMED,

    /** La reserva fue cancelada. */
    RESERVATION_CANCELLED,

    /** La reserva fue modificada (cambio de fechas, habitación, etc.). */
    RESERVATION_MODIFIED,

    /** Recordatorio previo al check-in. */
    CHECKIN_REMINDER
}
