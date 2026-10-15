package com.andesstay.notify.services;

import com.andesstay.notify.event.ReservationEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Servicio simulado de notificación por correo electrónico.
 * <p>
 * En producción, este servicio integraría con un proveedor de email
 * (p.ej. SendGrid, AWS SES, SMTP) para enviar la confirmación de reserva
 * al huésped y la notificación al Operador.
 * <p>
 * Por ahora registra en logs lo que «enviaría», lo que permite verificar
 * el flujo completo sin infraestructura de email real.
 */
@Service
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    /**
     * Simula el envío de un correo de confirmación de reserva.
     *
     * @param envelope Envelope completo del evento recibido desde RabbitMQ.
     */
    public void sendReservationConfirmation(ReservationEnvelope envelope) {
        var payload = envelope.payload();

        log.info("""
                [EMAIL-SIM] ─────────────────────────────────────────────
                  Destinatario (huésped) : {}
                  Correo                 : {}
                  Asunto                 : Confirmación de reserva #{} en {}
                  Check-in               : {}
                  Check-out              : {}
                  Habitación             : {}
                  Total                  : {} {}
                  eventId (trazabilidad) : {}
                ──────────────────────────────────────────────────────────
                """,
                payload.guestName(),
                payload.guestEmail(),
                payload.reservationId(),
                payload.propertyName(),
                payload.checkIn(),
                payload.checkOut(),
                payload.roomNumber(),
                payload.totalAmount(),
                payload.currency(),
                envelope.eventId()
        );

        log.info("""
                [EMAIL-SIM] ─────────────────────────────────────────────
                  Destinatario (operador): {}
                  Asunto                 : Nueva reserva #{} — acción requerida
                  Huésped                : {}
                  Solicitudes especiales : {}
                  eventId (trazabilidad) : {}
                ──────────────────────────────────────────────────────────
                """,
                payload.operatorEmail(),
                payload.reservationId(),
                payload.guestName(),
                payload.specialRequests() != null ? payload.specialRequests() : "(ninguna)",
                envelope.eventId()
        );
    }
}
